package com.github.luben.zstd;

import com.github.luben.zstd.util.Native;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class ZstdDecompressCtx extends AutoCloseBase {

    static {
        Native.load();
    }

    private long nativePtr = 0;

    /* The same ZSTD_DCtx as `nativePtr`, in the shape a downcall takes. Both are kept:
     * setMagicless goes through Zstd.setDecompressionMagicless, which takes the pointer
     * as a long. `nativePtr` stays the closed flag, as in the JNI implementation. */
    @NotNull
    private MemorySegment dctx = MemorySegment.NULL;

    // Note: keeps a reference to the dictionary so it's not garbage collected
    @Nullable
    private ZstdDictDecompress decompression_dict = null;

    /* The streaming call stores the new destination and source buffer positions here,
     * and updateStreamPositions applies them after a successful call - as
     * in ZstdCompressCtx, in place of the JNI implementation's packed-long result. */
    private int streamDstPosition = 0;
    private int streamSrcPosition = 0;

    /* libzstd's size_t* out-params for that call. Built on first use: the one-shot
     * entry points need no position slots, and every Zstd.decompress* static builds a
     * context, uses one of them and closes it. */
    @Nullable
    private ZstdBinding.SizeTRef dstPos = null;

    @Nullable
    private ZstdBinding.SizeTRef srcPos = null;

    /**
     * Create a context for faster compress operations
     * One such context is required for each thread - put this in a ThreadLocal.
     */
    public ZstdDecompressCtx() {
        dctx = ZstdBinding.createDCtx();
        nativePtr = dctx.address();
        if (0 == nativePtr) {
            throw new IllegalStateException("ZSTD_createDeCompressCtx failed");
        }
        storeFence();
    }

    void doClose() {
        if (nativePtr != 0) {
            ZstdBinding.freeDCtx(dctx);
            dctx = MemorySegment.NULL;
            nativePtr = 0;
        }
        if (decompression_dict != null) {
            decompression_dict.releaseSharedLock();
            decompression_dict = null;
        }
    }

    /**
     * Enable or disable magicless frames
     * @param magiclessFlag A 32-bits checksum of content is written at end of frame, default: false
     */
    @NotNull
    public ZstdDecompressCtx setMagicless(boolean magiclessFlag) {
        ensureOpen();
        acquireSharedLock();
        Zstd.setDecompressionMagicless(nativePtr, magiclessFlag);
        releaseSharedLock();
        return this;
    }

    /**
     * Load decompression dictionary
     *
     * @param dict the dictionary
     */
    @NotNull
    public ZstdDecompressCtx loadDict(@Nullable ZstdDictDecompress dict) {
        ensureOpen();
        acquireSharedLock();
        if (dict != null) {
            dict.acquireSharedLock();
        }
        try {
            long result = loadDDictFast0(dict);
            if (Zstd.isError(result)) {
                throw new ZstdException(result);
            }
            if (decompression_dict != null) {
                decompression_dict.releaseSharedLock();
            }
            // keep a reference to the dictionary so it's not garbage collected
            decompression_dict = dict;
        } finally {
            releaseSharedLock();
        }
        return this;
    }

    /* nativePtr() replaces the C's GetLongField on the dict; a closed one is null and
     * is rejected here rather than reaching libzstd, as in the C. */
    private long loadDDictFast0(@Nullable ZstdDictDecompress dict) {
        if (dict == null) {
            // remove dictionary
            return ZstdBinding.refDDict(dctx, MemorySegment.NULL);
        }
        long ddict = dict.nativePtr();
        if (ddict == 0) {
            return -ZstdBinding.ZSTD_ERROR_DICTIONARY_WRONG;
        }
        return ZstdBinding.refDDict(dctx, MemorySegment.ofAddress(ddict));
    }

    /**
     * Load decompression dictionary.
     *
     * @param dict the dictionary or `null` to remove loaded dictionary
     */
    @NotNull
    public ZstdDecompressCtx loadDict(byte @Nullable [] dict) {
        ensureOpen();
        acquireSharedLock();
        try {
            long result = loadDDict0(dict);
            if (Zstd.isError(result)) {
                throw new ZstdException(result);
            }
            if (decompression_dict != null) {
                decompression_dict.releaseSharedLock();
                decompression_dict = null;
            }
        } finally {
            releaseSharedLock();
        }
        return this;
    }

    /* Replaces jni_fast_zstd.c:691-705. The critical downcall passes the heap array
     * directly to libzstd, which copies it and retains no pointer. JNI could return a
     * memory-allocation error when acquiring the array; MemorySegment.ofArray cannot fail. */
    private long loadDDict0(byte @Nullable [] dict) {
        if (dict == null) {
            // remove dictionary
            return ZstdBinding.loadDDictionary(dctx, MemorySegment.NULL, 0);
        }
        return ZstdBinding.loadDDictionary(dctx, MemorySegment.ofArray(dict), dict.length);
    }

    /**
     * Clear all state and parameters from the decompression context. This leaves the object in a
     * state identical to a newly created decompression context.
     */
    public void reset() {
        ensureOpen();
        acquireSharedLock();
        try {
            long result = ZstdBinding.resetDCtx(dctx, ZstdBinding.ZSTD_RESET_SESSION_AND_PARAMETERS);
            if (Zstd.isError(result)) {
                throw new ZstdException(result);
            }
            if (decompression_dict != null) {
                decompression_dict.releaseSharedLock();
                decompression_dict = null;
            }
        } finally {
            releaseSharedLock();
        }

    }

    private void ensureOpen() {
        if (nativePtr == 0) {
            throw new IllegalStateException("Decompression context is closed");
        }
    }

    /**
     * Decompress as much of the <code>src</code> {@link ByteBuffer} into the <code>dst</code> {@link
     * ByteBuffer} as possible. Both buffers may be direct or array-backed heap buffers. The
     * destination buffer must be writable.
     *
     * @param dst destination of uncompressed data
     * @param src buffer to decompress
     * @return true if all state has been flushed from internal buffers
     * @throws IllegalArgumentException if either buffer is unsupported or the destination is read-only
     */
    public boolean decompressByteBufferStream(@NotNull ByteBuffer dst, @NotNull ByteBuffer src) {
        ensureOpen();
        if (dst.isReadOnly()) {
            throw new IllegalArgumentException("dst must be writable");
        }
        if (!dst.isDirect() && !dst.hasArray()) {
            throw new IllegalArgumentException("dst must be a direct or array-backed buffer");
        }
        if (!src.isDirect() && !src.hasArray()) {
            throw new IllegalArgumentException("src must be a direct or array-backed buffer");
        }

        acquireSharedLock();
        try {
            final long result;
            if (dst.isDirect()) {
                if (src.isDirect()) {
                    result = decompressDirectByteBufferStream0(dst, dst.position(), dst.limit(), src,
                            src.position(), src.limit());
                } else {
                    result = decompressByteArrayToDirectByteBufferStream0(dst, dst.position(), dst.limit(),
                            src.array(), src.arrayOffset(), src.position(), src.limit());
                }
            } else if (src.isDirect()) {
                result = decompressDirectByteBufferToByteArrayStream0(dst.array(), dst.arrayOffset(),
                        dst.position(), dst.limit(), src, src.position(), src.limit());
            } else {
                result = decompressByteArrayStream0(dst.array(), dst.arrayOffset(), dst.position(), dst.limit(),
                        src.array(), src.arrayOffset(), src.position(), src.limit());
            }
            return updateStreamPositions(result, dst, src);
        } finally {
            releaseSharedLock();
        }
    }

    /**
     * Decompress as much of the <code>src</code> {@link ByteBuffer} into the <code>dst</code> {@link
     * ByteBuffer} as possible.
     *
     * @param dst destination of uncompressed data
     * @param src buffer to decompress
     * @return true if all state has been flushed from internal buffers
     */
    public boolean decompressDirectByteBufferStream(@NotNull ByteBuffer dst, @NotNull ByteBuffer src) {
        ensureOpen();
        acquireSharedLock();
        try {
            long result = decompressDirectByteBufferStream0(dst, dst.position(), dst.limit(), src, src.position(), src.limit());
            return updateStreamPositions(result, dst, src);
        } finally {
            releaseSharedLock();
        }
    }

    private boolean updateStreamPositions(long result, @NotNull ByteBuffer dst, @NotNull ByteBuffer src) {
        if (ZstdBinding.isError(result)) {
            /* Decode libzstd's result before masking: its low byte alone is not the
             * error code. Keep the JNI implementation's mask and negation so the
             * exception receives the same negative code. */
            long code = -(Zstd.getErrorCode(result) & 0xFF);
            throw new ZstdException(code, Zstd.getErrorName(code));
        }
        src.position(streamSrcPosition);
        dst.position(streamDstPosition);
        return result == 0;
    }

    /* Cover the buffer's full capacity so buffer positions can be used as offsets.
     * Clear a duplicate to leave the caller's position and limit unchanged. */
    private static @NotNull MemorySegment directSegment(@NotNull ByteBuffer buffer) {
        return MemorySegment.ofBuffer(buffer.duplicate().clear());
    }

    /* Match JNI's GetDirectBufferCapacity: return -1 for heap buffers. The public
     * caller does not check isDirect(), so a heap buffer does reach the size checks
     * below, and this is what makes them reject it, as in the C. */
    private static int directCapacity(@NotNull ByteBuffer buffer) {
        return buffer.isDirect() ? buffer.capacity() : -1;
    }

    /* jni_fast_zstd.c validate_compress_stream_bounds, which the heap-buffer stream
     * variants share with ZstdCompressCtx. The `size` arguments are absolute end offsets. */
    private static long validateStreamBounds(int dstOffset, int dstSize, int srcOffset, int srcSize) {
        if (0 > dstOffset || dstOffset > dstSize) return -ZstdBinding.ZSTD_ERROR_DST_SIZE_TOO_SMALL;
        if (0 > srcOffset || srcOffset > srcSize) return -ZstdBinding.ZSTD_ERROR_SRC_SIZE_WRONG;
        return 0;
    }

    /* jni_fast_zstd.c is_valid_array_stream_buffer. */
    private static boolean isValidArrayStreamBuffer(byte @NotNull [] buffer, int arrayOffset, int size) {
        if (0 > arrayOffset || 0 > size) return false;
        int capacity = buffer.length;
        return arrayOffset <= capacity && size <= capacity - arrayOffset;
    }

    /* Replaces jni_fast_zstd.c decompress_buffer_stream.
     * Heap segments cover the whole backing array. Add each buffer's array offset
     * (shift) before the native call, then subtract it from the returned positions.
     * Direct buffers use shift 0. dstSize and srcSize are end offsets, not byte counts.
     *
     * Callers must validate heap-buffer ranges with isValidArrayStreamBuffer first.
     * This keeps shift + size within the array and prevents integer overflow,
     * which could otherwise pass a huge size_t value to libzstd. */
    private long decompressBufferStream(@NotNull MemorySegment dst, int dstShift, int dstOffset, int dstSize,
                                        @NotNull MemorySegment src, int srcShift, int srcOffset, int srcSize) {
        ZstdBinding.SizeTRef dstPos = this.dstPos;
        if (dstPos == null) {
            dstPos = ZstdBinding.newSizeTRef();
            this.dstPos = dstPos;
        }
        ZstdBinding.SizeTRef srcPos = this.srcPos;
        if (srcPos == null) {
            srcPos = ZstdBinding.newSizeTRef();
            this.srcPos = srcPos;
        }

        dstPos.set(dstShift + dstOffset);
        srcPos.set(srcShift + srcOffset);
        long result = ZstdBinding.decompressStream(
                dctx,
                dst, dstShift + dstSize, dstPos.segment,
                src, srcShift + srcSize, srcPos.segment);
        streamDstPosition = (int) dstPos.get() - dstShift;
        streamSrcPosition = (int) srcPos.get() - srcShift;
        return result;
    }

    /* Replaces jni_fast_zstd.c decompress_direct_buffer_stream. The NULL dst / src
     * guards are dropped - the public callers have already dereferenced both - and so
     * are the GetDirectBufferAddress guards: directCapacity already rejects heap
     * buffers. A zero-capacity direct buffer at address 0 is the one difference: JNI
     * returned memory_allocation, here libzstd gets NULL with size 0, which it accepts. */
    private long decompressDirectByteBufferStream0(@NotNull ByteBuffer dst, int dstOffset, int dstSize,
            @NotNull ByteBuffer src, int srcOffset, int srcSize) {
        if (0 > dstOffset) return -ZstdBinding.ZSTD_ERROR_DST_SIZE_TOO_SMALL;
        if (0 > srcOffset) return -ZstdBinding.ZSTD_ERROR_SRC_SIZE_WRONG;
        if (0 > dstSize) return -ZstdBinding.ZSTD_ERROR_DST_SIZE_TOO_SMALL;
        if (0 > srcSize) return -ZstdBinding.ZSTD_ERROR_SRC_SIZE_WRONG;

        if (dstSize > directCapacity(dst)) return -ZstdBinding.ZSTD_ERROR_DST_SIZE_TOO_SMALL;
        if (srcSize > directCapacity(src)) return -ZstdBinding.ZSTD_ERROR_SRC_SIZE_WRONG;

        return decompressBufferStream(directSegment(dst), 0, dstOffset, dstSize,
                                      directSegment(src), 0, srcOffset, srcSize);
    }

    /* jni_fast_zstd.c decompress_byte_array_to_direct_buffer_stream. */
    private long decompressByteArrayToDirectByteBufferStream0(@NotNull ByteBuffer dst, int dstOffset, int dstSize,
            byte @NotNull [] src, int srcArrayOffset, int srcOffset, int srcSize) {
        long result = validateStreamBounds(dstOffset, dstSize, srcOffset, srcSize);
        if (ZstdBinding.isError(result)) return result;

        if (dstSize > directCapacity(dst)) return -ZstdBinding.ZSTD_ERROR_DST_SIZE_TOO_SMALL;
        if (!isValidArrayStreamBuffer(src, srcArrayOffset, srcSize)) return -ZstdBinding.ZSTD_ERROR_SRC_SIZE_WRONG;

        return decompressBufferStream(directSegment(dst), 0, dstOffset, dstSize,
                                      MemorySegment.ofArray(src), srcArrayOffset, srcOffset, srcSize);
    }

    /* Replaces jni_fast_zstd.c decompress_direct_buffer_to_byte_array_stream. As in
     * ZstdCompressCtx, the critical downcall writes straight into the caller's array,
     * so no copy-back step is needed. */
    private long decompressDirectByteBufferToByteArrayStream0(byte @NotNull [] dst, int dstArrayOffset,
            int dstOffset, int dstSize, @NotNull ByteBuffer src, int srcOffset, int srcSize) {
        long result = validateStreamBounds(dstOffset, dstSize, srcOffset, srcSize);
        if (ZstdBinding.isError(result)) return result;

        if (!isValidArrayStreamBuffer(dst, dstArrayOffset, dstSize)) return -ZstdBinding.ZSTD_ERROR_DST_SIZE_TOO_SMALL;
        if (srcSize > directCapacity(src)) return -ZstdBinding.ZSTD_ERROR_SRC_SIZE_WRONG;

        return decompressBufferStream(MemorySegment.ofArray(dst), dstArrayOffset, dstOffset, dstSize,
                                      directSegment(src), 0, srcOffset, srcSize);
    }

    /* Replaces jni_fast_zstd.c decompress_byte_array_stream. JNI may copy both arrays
     * with GetByteArrayElements; the critical downcall uses them in place. See
     * ZstdCompressCtx.compressByteArrayStream0 for what that means when src and dst
     * overlap in one array. */
    private long decompressByteArrayStream0(byte @NotNull [] dst, int dstArrayOffset, int dstOffset, int dstSize,
            byte @NotNull [] src, int srcArrayOffset, int srcOffset, int srcSize) {
        long result = validateStreamBounds(dstOffset, dstSize, srcOffset, srcSize);
        if (ZstdBinding.isError(result)) return result;

        if (!isValidArrayStreamBuffer(dst, dstArrayOffset, dstSize)) return -ZstdBinding.ZSTD_ERROR_DST_SIZE_TOO_SMALL;
        if (!isValidArrayStreamBuffer(src, srcArrayOffset, srcSize)) return -ZstdBinding.ZSTD_ERROR_SRC_SIZE_WRONG;

        return decompressBufferStream(MemorySegment.ofArray(dst), dstArrayOffset, dstOffset, dstSize,
                                      MemorySegment.ofArray(src), srcArrayOffset, srcOffset, srcSize);
    }

    /**
     * Decompresses buffer 'srcBuff' into buffer 'dstBuff' using this ZstdDecompressCtx.
     * <p>
     * Destination buffer should be sized to be larger of equal to the originalSize.
     * This is a low-level function that does not take into account or affect the `limit`
     * or `position` of source or destination buffers.
     *
     * @param dstBuff   the destination buffer - must be direct
     * @param dstOffset the start offset of 'dstBuff'
     * @param dstSize   the size of 'dstBuff'
     * @param srcBuff   the source buffer - must be direct
     * @param srcOffset the start offset of 'srcBuff'
     * @param srcSize   the size of 'srcBuff'
     * @return the number of bytes decompressed into destination buffer (originalSize)
     */
    public int decompressDirectByteBuffer(@NotNull ByteBuffer dstBuff, int dstOffset, int dstSize, @NotNull ByteBuffer srcBuff, int srcOffset, int srcSize) {
        ensureOpen();
        if (!srcBuff.isDirect()) {
            throw new IllegalArgumentException("srcBuff must be a direct buffer");
        }
        if (!dstBuff.isDirect()) {
            throw new IllegalArgumentException("dstBuff must be a direct buffer");
        }
        Objects.checkFromIndexSize(srcOffset, srcSize, srcBuff.limit());
        Objects.checkFromIndexSize(dstOffset, dstSize, dstBuff.limit());

        acquireSharedLock();

        try {
            long size = decompressDirectByteBuffer0(dstBuff, dstOffset, dstSize, srcBuff, srcOffset, srcSize);
            if (Zstd.isError(size)) {
                throw new ZstdException(size);
            }
            if (size > Integer.MAX_VALUE) {
                throw new ZstdException(Zstd.errGeneric(), "Output size is greater than MAX_INT");
            }
            return (int) size;
        } finally {
            releaseSharedLock();
        }
    }

    /* Replaces jni_fast_zstd.c:890-913. Each segment is sliced to the requested offset
     * and length, matching the pointers and lengths the C passes. The public caller
     * already checks that both buffers are direct and the ranges fit their limits, so
     * plain capacity() suffices; the checks are redundant but kept in the C's order.
     * Reset is a separate downcall and its result is ignored, as in the C. */
    private long decompressDirectByteBuffer0(@NotNull ByteBuffer dst, int dstOffset, int dstSize,
            @NotNull ByteBuffer src, int srcOffset, int srcSize) {
        if (0 > dstOffset) return -ZstdBinding.ZSTD_ERROR_DST_SIZE_TOO_SMALL;
        if (0 > srcOffset) return -ZstdBinding.ZSTD_ERROR_SRC_SIZE_WRONG;
        if (0 > srcSize) return -ZstdBinding.ZSTD_ERROR_SRC_SIZE_WRONG;

        if (dstOffset + dstSize > dst.capacity()) return -ZstdBinding.ZSTD_ERROR_DST_SIZE_TOO_SMALL;
        if (srcOffset + srcSize > src.capacity()) return -ZstdBinding.ZSTD_ERROR_SRC_SIZE_WRONG;

        ZstdBinding.resetDCtx(dctx, ZstdBinding.ZSTD_RESET_SESSION_ONLY);
        return ZstdBinding.decompressDCtxNative(dctx,
                directSegment(dst).asSlice(dstOffset, dstSize), dstSize,
                directSegment(src).asSlice(srcOffset, srcSize), srcSize);
    }

    /**
     * Decompresses byte array 'srcBuff' into byte array 'dstBuff' using this ZstdDecompressCtx.
     * <p>
     * Destination buffer should be sized to be larger of equal to the originalSize.
     *
     * @param dstBuff   the destination buffer
     * @param dstOffset the start offset of 'dstBuff'
     * @param dstSize   the size of 'dstBuff'
     * @param srcBuff   the source buffer
     * @param srcOffset the start offset of 'srcBuff'
     * @param srcSize   the size of 'srcBuff'
     * @return the number of bytes decompressed into destination buffer (originalSize)
     */
    public int decompressByteArray(byte @NotNull [] dstBuff, int dstOffset, int dstSize, byte @NotNull [] srcBuff, int srcOffset, int srcSize) {
        Objects.checkFromIndexSize(srcOffset, srcSize, srcBuff.length);
        Objects.checkFromIndexSize(dstOffset, dstSize, dstBuff.length);

        ensureOpen();
        acquireSharedLock();

        try {
            long size = decompressByteArray0(dstBuff, dstOffset, dstSize, srcBuff, srcOffset, srcSize);
            if (Zstd.isError(size)) {
                throw new ZstdException(size);
            }
            if (size > Integer.MAX_VALUE) {
                throw new ZstdException(Zstd.errGeneric(), "Output size is greater than MAX_INT");
            }
            return (int) size;
        } finally {
            releaseSharedLock();
        }
    }

    /* Replaces jni_fast_zstd.c:920-944. The critical downcall accesses both arrays
     * directly, each sliced to the requested range, so there is no acquisition,
     * release or acquisition-error path. The redundant checks keep the C's order,
     * including the source end before the destination. */
    private long decompressByteArray0(byte @NotNull [] dst, int dstOffset, int dstSize,
            byte @NotNull [] src, int srcOffset, int srcSize) {
        if (0 > dstOffset) return -ZstdBinding.ZSTD_ERROR_DST_SIZE_TOO_SMALL;
        if (0 > srcOffset) return -ZstdBinding.ZSTD_ERROR_SRC_SIZE_WRONG;
        if (0 > srcSize) return -ZstdBinding.ZSTD_ERROR_SRC_SIZE_WRONG;

        if (srcOffset + srcSize > src.length) return -ZstdBinding.ZSTD_ERROR_SRC_SIZE_WRONG;
        if (dstOffset + dstSize > dst.length) return -ZstdBinding.ZSTD_ERROR_DST_SIZE_TOO_SMALL;

        ZstdBinding.resetDCtx(dctx, ZstdBinding.ZSTD_RESET_SESSION_ONLY);
        return ZstdBinding.decompressDCtx(dctx,
                MemorySegment.ofArray(dst).asSlice(dstOffset, dstSize), dstSize,
                MemorySegment.ofArray(src).asSlice(srcOffset, srcSize), srcSize);
    }

    public int decompressByteArrayToDirectByteBuffer(@NotNull ByteBuffer dstBuff, int dstOffset, int dstSize, byte @NotNull [] srcBuff, int srcOffset, int srcSize) {
        if (!dstBuff.isDirect()) {
            throw new IllegalArgumentException("dstBuff must be a direct buffer");
        }

        Objects.checkFromIndexSize(srcOffset, srcSize, srcBuff.length);
        Objects.checkFromIndexSize(dstOffset, dstSize, dstBuff.limit());

        ensureOpen();
        acquireSharedLock();

        try {
            long size = decompressByteArrayToDirectByteBuffer0(dstBuff, dstOffset, dstSize, srcBuff, srcOffset, srcSize);
            if (Zstd.isError(size)) {
                throw new ZstdException(size);
            }
            if (size > Integer.MAX_VALUE) {
                throw new ZstdException(Zstd.errGeneric(), "Output size is greater than MAX_INT");
            }
            return (int) size;
        } finally {
            releaseSharedLock();
        }
    }

    /* Replaces jni_fast_zstd.c:951-978, checks in the C's order. Its bounds checks are
     * written as `offset > length - size`, which cannot overflow, unlike the two
     * same-kind entry points above. The public caller has already checked that dst
     * is direct. */
    private long decompressByteArrayToDirectByteBuffer0(@NotNull ByteBuffer dst, int dstOffset, int dstSize,
            byte @NotNull [] src, int srcOffset, int srcSize) {
        if (0 > dstOffset) return -ZstdBinding.ZSTD_ERROR_DST_SIZE_TOO_SMALL;
        if (0 > dstSize) return -ZstdBinding.ZSTD_ERROR_DST_SIZE_TOO_SMALL;
        if (0 > srcOffset) return -ZstdBinding.ZSTD_ERROR_SRC_SIZE_WRONG;
        if (0 > srcSize) return -ZstdBinding.ZSTD_ERROR_SRC_SIZE_WRONG;

        if (srcOffset > src.length - srcSize) return -ZstdBinding.ZSTD_ERROR_SRC_SIZE_WRONG;
        if (dstOffset > dst.capacity() - dstSize) return -ZstdBinding.ZSTD_ERROR_DST_SIZE_TOO_SMALL;

        ZstdBinding.resetDCtx(dctx, ZstdBinding.ZSTD_RESET_SESSION_ONLY);
        return ZstdBinding.decompressDCtx(dctx,
                directSegment(dst).asSlice(dstOffset, dstSize), dstSize,
                MemorySegment.ofArray(src).asSlice(srcOffset, srcSize), srcSize);
    }

    public int decompressDirectByteBufferToByteArray(byte @NotNull [] dstBuff, int dstOffset, int dstSize, @NotNull ByteBuffer srcBuff, int srcOffset, int srcSize) {
        if (!srcBuff.isDirect()) {
            throw new IllegalArgumentException("srcBuff must be a direct buffer");
        }

        Objects.checkFromIndexSize(srcOffset, srcSize, srcBuff.limit());
        Objects.checkFromIndexSize(dstOffset, dstSize, dstBuff.length);

        ensureOpen();
        acquireSharedLock();

        try {
            long size = decompressDirectByteBufferToByteArray0(dstBuff, dstOffset, dstSize, srcBuff, srcOffset, srcSize);
            if (Zstd.isError(size)) {
                throw new ZstdException(size);
            }
            if (size > Integer.MAX_VALUE) {
                throw new ZstdException(Zstd.errGeneric(), "Output size is greater than MAX_INT");
            }
            return (int) size;
        } finally {
            releaseSharedLock();
        }
    }

    /* Replaces jni_fast_zstd.c:985-1012, checks in the C's order. JNI released dst with
     * mode 0 to copy back a possible temporary; under critical(true) libzstd writes the
     * caller's array directly, so there is nothing to copy back. */
    private long decompressDirectByteBufferToByteArray0(byte @NotNull [] dst, int dstOffset, int dstSize,
            @NotNull ByteBuffer src, int srcOffset, int srcSize) {
        if (0 > dstOffset) return -ZstdBinding.ZSTD_ERROR_DST_SIZE_TOO_SMALL;
        if (0 > dstSize) return -ZstdBinding.ZSTD_ERROR_DST_SIZE_TOO_SMALL;
        if (0 > srcOffset) return -ZstdBinding.ZSTD_ERROR_SRC_SIZE_WRONG;
        if (0 > srcSize) return -ZstdBinding.ZSTD_ERROR_SRC_SIZE_WRONG;

        if (dstOffset > dst.length - dstSize) return -ZstdBinding.ZSTD_ERROR_DST_SIZE_TOO_SMALL;
        if (srcOffset > src.capacity() - srcSize) return -ZstdBinding.ZSTD_ERROR_SRC_SIZE_WRONG;

        ZstdBinding.resetDCtx(dctx, ZstdBinding.ZSTD_RESET_SESSION_ONLY);
        return ZstdBinding.decompressDCtx(dctx,
                MemorySegment.ofArray(dst).asSlice(dstOffset, dstSize), dstSize,
                directSegment(src).asSlice(srcOffset, srcSize), srcSize);
    }

    /* Covenience methods */

    /**
     * Decompresses buffer 'srcBuff' into buffer 'dstBuff' using this ZstdDecompressCtx.
     * <p>
     * Destination buffer should be sized to be larger of equal to the originalSize.
     *
     * @param dstBuf the destination buffer - must be direct. It is assumed that the `position()` of this buffer marks the offset
     *               at which the decompressed data are to be written, and that the `limit()` of this buffer is the maximum
     *               decompressed data size to allow.
     *               <p>
     *               When this method returns successfully, its `position()` will be set to its current `position()` plus the
     *               decompressed size of the data.
     *               </p>
     * @param srcBuf the source buffer - must be direct. It is assumed that the `position()` of this buffer marks the beginning of the
     *               compressed data to be decompressed, and that the `limit()` of this buffer marks its end.
     *               <p>
     *               When this method returns successfully, its `position()` will be set to the initial `limit()`.
     *               </p>
     * @return the size of the decompressed data.
     */
    public int decompress(@NotNull ByteBuffer dstBuf, @NotNull ByteBuffer srcBuf) throws ZstdException {
        int size = decompressDirectByteBuffer(dstBuf,  // decompress into dstBuf
                dstBuf.position(),                      // write decompressed data at offset position()
                dstBuf.limit() - dstBuf.position(),     // write no more than limit() - position()
                srcBuf,                                 // read compressed data from srcBuf
                srcBuf.position(),                      // read starting at offset position()
                srcBuf.limit() - srcBuf.position());    // read no more than limit() - position()
        srcBuf.position(srcBuf.limit());
        dstBuf.position(dstBuf.position() + size);
        return size;
    }

    public int decompress(@NotNull ByteBuffer dstBuf, byte @NotNull [] src) throws ZstdException {
        int size = decompressByteArrayToDirectByteBuffer(dstBuf,  // decompress into dstBuf
                dstBuf.position(),                      // write decompressed data at offset position()
                dstBuf.limit() - dstBuf.position(),     // write no more than limit() - position()
                src,                                 // read compressed data from src
                0,
                src.length);
        dstBuf.position(dstBuf.position() + size);
        return size;
    }

    public int decompress(byte @NotNull [] dst, @NotNull ByteBuffer srcBuf) throws ZstdException {
        int size = decompressDirectByteBufferToByteArray(dst,  // decompress into dst
                0,
                dst.length,
                srcBuf,                                 // read compressed data from srcBuf
                srcBuf.position(),                      // read starting at offset position()
                srcBuf.limit() - srcBuf.position());    // read no more than limit() - position()
        srcBuf.position(srcBuf.limit());
        return size;
    }

    @NotNull
    public ByteBuffer decompress(@NotNull ByteBuffer srcBuf, int originalSize) throws ZstdException {
        ByteBuffer dstBuf = ByteBuffer.allocateDirect(originalSize);
        int size = decompressDirectByteBuffer(dstBuf, 0, originalSize, srcBuf, srcBuf.position(), srcBuf.limit() - srcBuf.position());
        srcBuf.position(srcBuf.limit());
        // Since we allocated the buffer ourselves, we know it cannot be used to hold any further decompressed data,
        // so leave the position at zero where the caller surely wants it, ready to read
        return dstBuf;
    }

    public int decompress(byte @NotNull [] dst, byte @NotNull [] src) {
        return decompressByteArray(dst, 0, dst.length, src, 0, src.length);
    }

    /**
     * Decompress data
     *
     * @param src          the source buffer
     * @param originalSize the maximum size of the uncompressed data.
     *                     If originalSize is greater than the actual uncompressed size, additional memory copy going to happen.
     *                     If originalSize is smaller than the uncompressed size, {@link ZstdException} will be thrown.
     * @return byte array with the decompressed data
     */
    public byte @NotNull [] decompress(byte @NotNull [] src, int originalSize) throws ZstdException {
        return decompress(src, 0, src.length, originalSize);
    }

    /**
     * Decompress data
     *
     * @param src          the source buffer
     * @param srcOffset    the start offset of 'src'
     * @param srcSize      the size of 'src'
     * @param originalSize the maximum size of the uncompressed data.
     *                     If originalSize is greater than the actual uncompressed size, additional memory copy going to happen.
     *                     If originalSize is smaller than the uncompressed size, {@link ZstdException} will be thrown.
     * @return byte array with the decompressed data
     */
    public byte @NotNull [] decompress(byte @NotNull [] src, int srcOffset, int srcSize, int originalSize) throws ZstdException {
        if (originalSize < 0) {
            throw new ZstdException(Zstd.errGeneric(), "Original size should not be negative");
        }
        byte[] dst = new byte[originalSize];
        int size = decompressByteArray(dst, 0, dst.length, src, srcOffset, srcSize);
        if (size != originalSize) {
            return Arrays.copyOfRange(dst, 0, size);
        } else {
            return dst;
        }
    }
}

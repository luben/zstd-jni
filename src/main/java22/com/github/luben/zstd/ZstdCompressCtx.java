package com.github.luben.zstd;

import com.github.luben.zstd.util.Native;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class ZstdCompressCtx extends AutoCloseBase {

    static {
        Native.load();
    }

    private long nativePtr = 0;

    /* The same ZSTD_CCtx as `nativePtr`, in the shape a downcall takes. Both are kept:
     * the parameter setters below still go through Zstd's JNI natives, which want the
     * raw pointer. `nativePtr` stays the closed flag, as in the JNI implementation. */
    @NotNull
    private MemorySegment cctx = MemorySegment.NULL;

    @Nullable
    private ZstdDictCompress compression_dict = null;

    @Nullable
    private SequenceProducer seqprod = null;

    private long seqprod_state = 0;

    /* Streaming calls store the new destination and source buffer positions here.
     * After a successful call, updateStreamPositions applies them to the ByteBuffers.
     * Keeping positions in separate fields lets each call return libzstd's result directly. */
    private int streamDstPosition = 0;
    private int streamSrcPosition = 0;

    /* libzstd's size_t* out-params for those same four calls. Built on first use rather
     * than in the constructor: the one-shot entry points need no position slots at all,
     * and every Zstd.compress* static builds a context, uses one of them and closes it. */
    @Nullable
    private ZstdBinding.SizeTRef dstPos = null;

    @Nullable
    private ZstdBinding.SizeTRef srcPos = null;

    /* The cell ZSTD_getFrameProgression writes its struct into, built on first use for
     * the same reason as the two above: nothing else in the class needs it. Reused
     * across calls, so a context polled for progression from two threads at once would
     * race on it - as it would on the position slots, and as the class's own "one per
     * thread, put this in a ThreadLocal" contract rules out. */
    @Nullable
    private ZstdBinding.FrameProgressionBuffer progression = null;

    /**
     * Create a context for faster compress operations
     * One such context is required for each thread - put this in a ThreadLocal.
     */
    public ZstdCompressCtx() {
        cctx = ZstdBinding.createCCtx();
        nativePtr = cctx.address();
        if (0 == nativePtr) {
            throw new IllegalStateException("ZSTD_createCompressCtx failed");
        }
        storeFence();
    }

    void doClose() {
        if (nativePtr != 0) {
            ZstdBinding.freeCCtx(cctx);
            cctx = MemorySegment.NULL;
            nativePtr = 0;
            if (seqprod != null) {
                seqprod.freeState(seqprod_state);
                seqprod = null;
            }
        }
        if (compression_dict != null) {
            compression_dict.releaseSharedLock();
            compression_dict = null;
        }
    }

    private void ensureOpen() {
        if (nativePtr == 0) {
            throw new IllegalStateException("Compression context is closed");
        }
    }

    /**
     * Set compression level
     * @param level compression level, default: {@link Zstd#defaultCompressionLevel()}
     */
    @NotNull
    public ZstdCompressCtx setLevel(int level) {
        ensureOpen();
        acquireSharedLock();
        setLevel0(level);
        releaseSharedLock();
        return this;
    }

    /* As in the C, the result of every one of these four is ignored. */
    private void setLevel0(int level) {
        ZstdBinding.setCCtxParameter(cctx, ZstdBinding.ZSTD_C_COMPRESSION_LEVEL, level);
    }

    /**
     * Enable or disable magicless frames
     * @param magiclessFlag A 32-bits magic number is written at start of frame, default: false
     */
    @NotNull
    public ZstdCompressCtx setMagicless(boolean magiclessFlag) {
        ensureOpen();
        acquireSharedLock();
        Zstd.setCompressionMagicless(nativePtr, magiclessFlag);
        releaseSharedLock();
        return this;
    }

    /**
     * Enable or disable compression checksums
     * @param checksumFlag A 32-bits checksum of content is written at end of frame, default: false
     */
    @NotNull
    public ZstdCompressCtx setChecksum(boolean checksumFlag) {
        ensureOpen();
        acquireSharedLock();
        setChecksum0(checksumFlag);
        releaseSharedLock();
        return this;
    }

    private void setChecksum0(boolean checksumFlag) {
        ZstdBinding.setCCtxParameter(cctx, ZstdBinding.ZSTD_C_CHECKSUM_FLAG, checksumFlag ? 1 : 0);
    }

    @NotNull
    public ZstdCompressCtx setWorkers(int workers) {
        ensureOpen();
        acquireSharedLock();
        try {
            long result = Zstd.setCompressionWorkers(nativePtr, workers);
            if (Zstd.isError(result)) {
                throw new ZstdException(result);
            }
        } finally {
            releaseSharedLock();
        }
        return this;
    }

    @NotNull
    public ZstdCompressCtx setOverlapLog(int overlapLog) {
        ensureOpen();
        acquireSharedLock();
        try {
            long result = Zstd.setCompressionOverlapLog(nativePtr, overlapLog);
            if (Zstd.isError(result)) {
                throw new ZstdException(result);
            }
        } finally {
            releaseSharedLock();
        }
        return this;
    }

    @NotNull
    public ZstdCompressCtx setJobSize(int jobSize) {
        ensureOpen();
        acquireSharedLock();
        try {
            long result = Zstd.setCompressionJobSize(nativePtr, jobSize);
            if (Zstd.isError(result)) {
                throw new ZstdException(result);
            }
        } finally {
            releaseSharedLock();
        }
        return this;
    }

    @NotNull
    public ZstdCompressCtx setTargetLength(int targetLength) {
        ensureOpen();
        acquireSharedLock();
        try {
            long result = Zstd.setCompressionTargetLength(nativePtr, targetLength);
            if (Zstd.isError(result)) {
                throw new ZstdException(result);
            }
        } finally {
            releaseSharedLock();
        }
        return this;
    }

    @NotNull
    public ZstdCompressCtx setMinMatch(int minMatch) {
        ensureOpen();
        acquireSharedLock();
        try {
            long result = Zstd.setCompressionMinMatch(nativePtr, minMatch);
            if (Zstd.isError(result)) {
                throw new ZstdException(result);
            }
        } finally {
            releaseSharedLock();
        }
        return this;
    }

    @NotNull
    public ZstdCompressCtx setSearchLog(int searchLog) {
        ensureOpen();
        acquireSharedLock();
        try {
            long result = Zstd.setCompressionSearchLog(nativePtr, searchLog);
            if (Zstd.isError(result)) {
                throw new ZstdException(result);
            }
        } finally {
            releaseSharedLock();
        }
        return this;
    }

    @NotNull
    public ZstdCompressCtx setChainLog(int chainLog) {
        ensureOpen();
        acquireSharedLock();
        try {
            long result = Zstd.setCompressionChainLog(nativePtr, chainLog);
            if (Zstd.isError(result)) {
                throw new ZstdException(result);
            }
        } finally {
            releaseSharedLock();
        }
        return this;
    }

    @NotNull
    public ZstdCompressCtx setHashLog(int hashLog) {
        ensureOpen();
        acquireSharedLock();
        try {
            long result = Zstd.setCompressionHashLog(nativePtr, hashLog);
            if (Zstd.isError(result)) {
                throw new ZstdException(result);
            }
        } finally {
            releaseSharedLock();
        }
        return this;
    }

    @NotNull
    public ZstdCompressCtx setWindowLog(int windowLog) {
        ensureOpen();
        acquireSharedLock();
        try {
            long result = Zstd.setCompressionWindowLog(nativePtr, windowLog);
            if (Zstd.isError(result)) {
                throw new ZstdException(result);
            }
        } finally {
            releaseSharedLock();
        }
        return this;
    }

    @NotNull
    public ZstdCompressCtx setStrategy(int strategy) {
        ensureOpen();
        acquireSharedLock();
        try {
            long result = Zstd.setCompressionStrategy(nativePtr, strategy);
            if (Zstd.isError(result)) {
                throw new ZstdException(result);
            }
        } finally {
            releaseSharedLock();
        }
        return this;
    }

    /**
     * Enable or disable content size
     * @param contentSizeFlag Content size will be written into frame header _whenever known_, default: true
     */
    @NotNull
    public ZstdCompressCtx setContentSize(boolean contentSizeFlag) {
        ensureOpen();
        acquireSharedLock();
        setContentSize0(contentSizeFlag);
        releaseSharedLock();
        return this;
    }

    private void setContentSize0(boolean contentSizeFlag) {
        ZstdBinding.setCCtxParameter(cctx, ZstdBinding.ZSTD_C_CONTENT_SIZE_FLAG, contentSizeFlag ? 1 : 0);
    }

    /**
     * Enable or disable dictID
     * @param dictIDFlag When applicable, dictionary's ID is written into frame header, default: true
     */
    @NotNull
    public ZstdCompressCtx setDictID(boolean dictIDFlag) {
        ensureOpen();
        acquireSharedLock();
        setDictID0(dictIDFlag);
        releaseSharedLock();
        return this;
    }

    private void setDictID0(boolean dictIDFlag) {
        ZstdBinding.setCCtxParameter(cctx, ZstdBinding.ZSTD_C_DICT_ID_FLAG, dictIDFlag ? 1 : 0);
    }

    /**
     * Enable or disable LongDistanceMatching and set the window size
     * @param windowLog Maximum allowed back-reference distance, expressed as power of 2.
     *                  This will set a memory budget for streaming decompression,
     *                  with larger values requiring more memory and typically compressing more.
     *                  Must be clamped between 10 and 32/64 but values greater than 27 may not
     *                  be decompressable in all context as they require more memory.
     *                  0 disables LDM.
     */
    @NotNull
    public ZstdCompressCtx setLong(int windowLog) {
        ensureOpen();
        acquireSharedLock();
        Zstd.setCompressionLong(nativePtr, windowLog);
        releaseSharedLock();
        return this;
    }

    /**
     * Register an external sequence producer
     * @param producer the user-defined {@link SequenceProducer} to register.
     */
    @NotNull
    public ZstdCompressCtx registerSequenceProducer(@Nullable SequenceProducer producer) {
        ensureOpen();
        acquireSharedLock();
        try {
            if (this.seqprod != null) {
                this.seqprod.freeState(seqprod_state);
                this.seqprod = null;
            }

            if (producer == null) {
                Zstd.registerSequenceProducer(nativePtr, 0, 0);
            } else {
                seqprod_state = producer.createState();
                Zstd.registerSequenceProducer(nativePtr, seqprod_state, producer.getFunctionPointer());
                this.seqprod = producer;
            }
        } catch (Exception e) {
            this.seqprod = null;
            Zstd.registerSequenceProducer(nativePtr, 0, 0);
            throw e;
        } finally {
            releaseSharedLock();
        }
        return this;
    }

    /**
     * Enable or disable sequence producer fallback
     * @param fallbackFlag fall back to the default internal sequence producer if an external
     *                     sequence producer returns an error code, default: false
     */
    @NotNull
    public ZstdCompressCtx setSequenceProducerFallback(boolean fallbackFlag) {
        ensureOpen();
        acquireSharedLock();
        try {
            long result = Zstd.setSequenceProducerFallback(nativePtr, fallbackFlag);
            if (Zstd.isError(result)) {
                throw new ZstdException(result);
            }
        } finally {
            releaseSharedLock();
        }
        return this;
    }

    /**
     * Set whether to search external sequences for repeated offsets that can be
     * encoded as repcodes.
     * @param searchRepcodes whether to search for repcodes
     */
    @NotNull
    public ZstdCompressCtx setSearchForExternalRepcodes(@NotNull Zstd.ParamSwitch searchRepcodes) {
        ensureOpen();
        acquireSharedLock();
        try {
            long result = Zstd.setSearchForExternalRepcodes(nativePtr, searchRepcodes.getValue());
            if (Zstd.isError(result)) {
                throw new ZstdException(result);
            }
        } finally {
            releaseSharedLock();
        }
        return this;
    }

    /**
     * Enable or disable sequence validation. Useful for the sequence-level API
     * and with external sequence producers.
     * @param validateSequences whether to enable sequence validation
     */
    @NotNull
    public ZstdCompressCtx setValidateSequences(@NotNull Zstd.ParamSwitch validateSequences) {
        ensureOpen();
        acquireSharedLock();
        try {
            long result = Zstd.setValidateSequences(nativePtr, validateSequences.getValue());
            if (Zstd.isError(result)) {
                throw new ZstdException(result);
            }
        } finally {
            releaseSharedLock();
        }
        return this;
    }

    /**
     * Enable or disable long-distance matching.
     * @param enableLDM whether to enable long-distance matching.
     */
    @NotNull
    public ZstdCompressCtx setEnableLongDistanceMatching(@NotNull Zstd.ParamSwitch enableLDM) {
        ensureOpen();
        acquireSharedLock();
        try {
            long result = Zstd.setEnableLongDistanceMatching(nativePtr, enableLDM.getValue());
            if (Zstd.isError(result)) {
                throw new ZstdException(result);
            }
        } finally {
            releaseSharedLock();
        }
        return this;
    }

    // Used in tests
    long getNativePtr() {
        return nativePtr;
    }

    /**
     * Load compression dictionary to be used for subsequently compressed frames.
     *
     * @param dict the dictionary
     */
    @NotNull
    public ZstdCompressCtx loadDict(@Nullable ZstdDictCompress dict) {
        ensureOpen();
        acquireSharedLock();
        if (dict != null) {
            dict.acquireSharedLock();
        }
        try {
            long result = loadCDictFast0(dict);
            if (Zstd.isError(result)) {
                throw new ZstdException(result);
            }
            if (compression_dict != null) {
                compression_dict.releaseSharedLock();
            }
            // keep a reference to the dictionary so it's not garbage collected
            compression_dict = dict;
        } finally {
            releaseSharedLock();
        }
        return this;
    }

    /* nativePtr() replaces the C's GetLongField on the dict; a closed one is null and
     * is rejected here rather than reaching libzstd, as in the C. */
    private long loadCDictFast0(@Nullable ZstdDictCompress dict) {
        if (dict == null) {
            // remove dictionary
            return ZstdBinding.refCDict(cctx, MemorySegment.NULL);
        }
        long cdict = dict.nativePtr();
        if (cdict == 0) {
            return -ZstdBinding.ZSTD_ERROR_DICTIONARY_WRONG;
        }
        return ZstdBinding.refCDict(cctx, MemorySegment.ofAddress(cdict));
    }

    /**
     * Load compression dictionary to be used for subsequently compressed frames.
     *
     * @param dict the dictionary or `null` to remove loaded dictionary
     */
    @NotNull
    public ZstdCompressCtx loadDict(byte @Nullable [] dict) {
        ensureOpen();
        acquireSharedLock();
        try {
            long result = loadCDict0(dict);
            if (Zstd.isError(result)) {
                throw new ZstdException(result);
            }
            if (compression_dict != null) {
                compression_dict.releaseSharedLock();
                compression_dict = null;
            }
        } finally {
            releaseSharedLock();
        }
        return this;
    }

    /* Replaces jni_fast_zstd.c:343-357. The critical downcall passes the heap array
     * directly to libzstd, which copies its contents without modifying the array or
     * retaining its pointer. A null or empty dictionary removes the current dictionary;
     * the empty-array case was checked to produce the same frames as JNI.
     * JNI could return a memory-allocation error when acquiring the array pointer;
     * MemorySegment.ofArray needs no equivalent error check. */
    private long loadCDict0(byte @Nullable [] dict) {
        if (dict == null) {
            // remove dictionary
            return ZstdBinding.loadDictionary(cctx, MemorySegment.NULL, 0);
        }
        return ZstdBinding.loadDictionary(cctx, MemorySegment.ofArray(dict), dict.length);
    }

    /**
     * Tells how much data has been ingested (read from input),
     * consumed (input actually compressed) and produced (output) for current frame.
     */
    @NotNull
    public ZstdFrameProgression getFrameProgression() {
        ensureOpen();
        acquireSharedLock();
        try {
            if (progression == null) {
                progression = new ZstdBinding.FrameProgressionBuffer();
            }
            return ZstdBinding.frameProgression(cctx, progression);
        } finally {
            releaseSharedLock();
        }
    }

    /**
     * Clear all state and parameters from the compression context. This leaves the object in a
     * state identical to a newly created compression context.
     */
    public void reset() {
        ensureOpen();
        acquireSharedLock();
        try {
            long result = ZstdBinding.resetCCtx(cctx, ZstdBinding.ZSTD_RESET_SESSION_AND_PARAMETERS);
            if (Zstd.isError(result)) {
                throw new ZstdException(result);
            }
            if (compression_dict != null) {
                compression_dict.releaseSharedLock();
                compression_dict = null;
            }
        } finally {
            releaseSharedLock();
        }

    }

    /**
     * Promise to compress a certain number of source bytes. Knowing the number of bytes to compress
     * up front helps to choose proper compression settings and size internal buffers. Additionally,
     * the pledged size is stored in the header of the output stream, allowing decompressors to know
     * how much uncompressed data to expect.
     *
     * Attempting to compress more or less than the pledged size will result in an error.
     */
    public void setPledgedSrcSize(long srcSize) {
        ensureOpen();
        acquireSharedLock();
        try {
            long result = setPledgedSrcSize0(srcSize);
            if (Zstd.isError(result)) {
                throw new ZstdException(result);
            }
        } finally {
            releaseSharedLock();
        }
    }

    private long setPledgedSrcSize0(long srcSize) {
        if (srcSize < 0) {
            return -ZstdBinding.ZSTD_ERROR_SRC_SIZE_WRONG;
        }
        return ZstdBinding.setPledgedSrcSize(cctx, srcSize);
    }

    /**
     * Compress as much of the <code>src</code> {@link ByteBuffer} into the <code>dst</code> {@link
     * ByteBuffer} as possible. Both buffers may be direct or array-backed heap buffers. The
     * destination buffer must be writable.
     *
     * @param dst destination of compressed data
     * @param src buffer to compress
     * @param endOp directive for handling the end of the stream
     * @return true if all state has been flushed from internal buffers
     * @throws IllegalArgumentException if either buffer is unsupported or the destination is read-only
     */
    public boolean compressByteBufferStream(@NotNull ByteBuffer dst, @NotNull ByteBuffer src, @NotNull EndDirective endOp) {
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
                    result = compressDirectByteBufferStream0(dst, dst.position(), dst.limit(), src,
                            src.position(), src.limit(), endOp.value());
                } else {
                    result = compressByteArrayToDirectByteBufferStream0(dst, dst.position(), dst.limit(),
                            src.array(), src.arrayOffset(), src.position(), src.limit(), endOp.value());
                }
            } else if (src.isDirect()) {
                result = compressDirectByteBufferToByteArrayStream0(dst.array(), dst.arrayOffset(),
                        dst.position(), dst.limit(), src, src.position(), src.limit(), endOp.value());
            } else {
                result = compressByteArrayStream0(dst.array(), dst.arrayOffset(), dst.position(), dst.limit(),
                        src.array(), src.arrayOffset(), src.position(), src.limit(), endOp.value());
            }
            return updateStreamPositions(result, dst, src);
        } finally {
            releaseSharedLock();
        }
    }

    /**
     * Compress as much of the <code>src</code> {@link ByteBuffer} into the <code>dst</code> {@link
     * ByteBuffer} as possible.
     *
     * @param dst destination of compressed data
     * @param src buffer to compress
     * @param endOp directive for handling the end of the stream
     * @return true if all state has been flushed from internal buffers
     */
    public boolean compressDirectByteBufferStream(@NotNull ByteBuffer dst, @NotNull ByteBuffer src, @NotNull EndDirective endOp) {
        ensureOpen();
        acquireSharedLock();
        try {
            long result = compressDirectByteBufferStream0(dst, dst.position(), dst.limit(), src, src.position(), src.limit(), endOp.value());
            return updateStreamPositions(result, dst, src);
        } finally {
            releaseSharedLock();
        }
    }

    /* Check for errors in Java on every call. Only the error path uses JNI to
     * decode the error and look up its message. */
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

    /* jni_fast_zstd.c:392-397, validate_compress_stream_bounds. The `size` arguments are
     * absolute end offsets, which is why they are compared against the offsets.
     *
     * Returns 0 or a negated ZSTD_error_* constant, which the four callers test with
     * ZstdBinding.isError exactly as the C tests it with ZSTD_isError. */
    private static long validateCompressStreamBounds(int dstOffset, int dstSize, int srcOffset, int srcSize) {
        if (0 > dstOffset || dstOffset > dstSize) return -ZstdBinding.ZSTD_ERROR_DST_SIZE_TOO_SMALL;
        if (0 > srcOffset || srcOffset > srcSize) return -ZstdBinding.ZSTD_ERROR_SRC_SIZE_WRONG;
        return 0;
    }

    /* jni_fast_zstd.c:399-404, is_valid_array_stream_buffer. */
    private static boolean isValidArrayStreamBuffer(byte @NotNull [] buffer, int arrayOffset, int size) {
        if (0 > arrayOffset || 0 > size) return false;
        int capacity = buffer.length;
        return arrayOffset <= capacity && size <= capacity - arrayOffset;
    }

    /* Cover the buffer's full capacity so buffer positions can be used as offsets.
     * Clear a duplicate to leave the caller's position and limit unchanged.
     * No data is copied or erased. */
    private static @NotNull MemorySegment directSegment(@NotNull ByteBuffer buffer) {
        return MemorySegment.ofBuffer(buffer.duplicate().clear());
    }

    /* Match JNI's GetDirectBufferCapacity: return -1 for heap buffers.
     * The size checks below then reject them, ensuring only direct buffers
     * reach directSegment without needing a separate address check. */
    private static int directCapacity(@NotNull ByteBuffer buffer) {
        return buffer.isDirect() ? buffer.capacity() : -1;
    }

    /* Replaces jni_fast_zstd.c:406-423, compress_buffer_stream.
     * Heap segments cover the whole backing array. Add each buffer's array offset
     * (shift) before the native call, then subtract it from the returned positions.
     * Direct buffers use shift 0. dstSize and srcSize are end offsets, not byte counts.
     *
     * Callers must validate heap-buffer ranges with isValidArrayStreamBuffer first.
     * This keeps shift + size within the array and prevents integer overflow,
     * which could otherwise pass a huge size_t value to libzstd. */
    private long compressBufferStream(@NotNull MemorySegment dst, int dstShift, int dstOffset, int dstSize,
                                      @NotNull MemorySegment src, int srcShift, int srcOffset, int srcSize,
                                      int endOp) {
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
        long result = ZstdBinding.compressStream2(
                cctx,
                dst, dstShift + dstSize, dstPos.segment,
                src, srcShift + srcSize, srcPos.segment,
                endOp);
        streamDstPosition = (int) dstPos.get() - dstShift;
        streamSrcPosition = (int) srcPos.get() - srcShift;
        return result;
    }

    /* jni_fast_zstd.c:437-456, compress_direct_buffer_stream. The NULL dst / src guards
     * the C opens with are dropped from all four of these: a Java caller trips a
     * NullPointerException before reaching here. */
    private long compressDirectByteBufferStream0(@NotNull ByteBuffer dst, int dstOffset, int dstSize,
            @NotNull ByteBuffer src, int srcOffset, int srcSize, int endOp) {
        long result = validateCompressStreamBounds(dstOffset, dstSize, srcOffset, srcSize);
        if (ZstdBinding.isError(result)) return result;

        if (dstSize > directCapacity(dst)) return -ZstdBinding.ZSTD_ERROR_DST_SIZE_TOO_SMALL;
        if (srcSize > directCapacity(src)) return -ZstdBinding.ZSTD_ERROR_SRC_SIZE_WRONG;

        return compressBufferStream(directSegment(dst), 0, dstOffset, dstSize,
                                    directSegment(src), 0, srcOffset, srcSize, endOp);
    }

    /* jni_fast_zstd.c:458-479, compress_byte_array_to_direct_buffer_stream. */
    private long compressByteArrayToDirectByteBufferStream0(@NotNull ByteBuffer dst, int dstOffset, int dstSize,
            byte @NotNull [] src, int srcArrayOffset, int srcOffset, int srcSize, int endOp) {
        long result = validateCompressStreamBounds(dstOffset, dstSize, srcOffset, srcSize);
        if (ZstdBinding.isError(result)) return result;

        if (dstSize > directCapacity(dst)) return -ZstdBinding.ZSTD_ERROR_DST_SIZE_TOO_SMALL;
        if (!isValidArrayStreamBuffer(src, srcArrayOffset, srcSize)) return -ZstdBinding.ZSTD_ERROR_SRC_SIZE_WRONG;

        return compressBufferStream(directSegment(dst), 0, dstOffset, dstSize,
                                    MemorySegment.ofArray(src), srcArrayOffset, srcOffset, srcSize, endOp);
    }

    /* Replaces jni_fast_zstd.c:481-502, compress_direct_buffer_to_byte_array_stream.
     * JNI released the destination array with mode 0 to preserve writes if a temporary
     * copy was used. Here, critical(true) lets libzstd write directly into the caller's
     * array, so no copy-back step is needed. */
    private long compressDirectByteBufferToByteArrayStream0(byte @NotNull [] dst, int dstArrayOffset,
            int dstOffset, int dstSize, @NotNull ByteBuffer src, int srcOffset, int srcSize, int endOp) {
        long result = validateCompressStreamBounds(dstOffset, dstSize, srcOffset, srcSize);
        if (ZstdBinding.isError(result)) return result;

        if (!isValidArrayStreamBuffer(dst, dstArrayOffset, dstSize)) return -ZstdBinding.ZSTD_ERROR_DST_SIZE_TOO_SMALL;
        if (srcSize > directCapacity(src)) return -ZstdBinding.ZSTD_ERROR_SRC_SIZE_WRONG;

        return compressBufferStream(MemorySegment.ofArray(dst), dstArrayOffset, dstOffset, dstSize,
                                    directSegment(src), 0, srcOffset, srcSize, endOp);
    }

    /* Replaces jni_fast_zstd.c:504-529, compress_byte_array_stream.
     * JNI uses GetByteArrayElements, which may copy both arrays. A critical downcall
     * can access both arrays directly, avoiding copies and their allocation-error checks.
     *
     * If source and destination overlap in the same array, writes can affect unread
     * input. Results may therefore differ from JNI when it copies the arrays.
     * JNI never guaranteed those copies. Sharing an array without overlap is unaffected. */
    private long compressByteArrayStream0(byte @NotNull [] dst, int dstArrayOffset, int dstOffset, int dstSize,
            byte @NotNull [] src, int srcArrayOffset, int srcOffset, int srcSize, int endOp) {
        long result = validateCompressStreamBounds(dstOffset, dstSize, srcOffset, srcSize);
        if (ZstdBinding.isError(result)) return result;

        if (!isValidArrayStreamBuffer(dst, dstArrayOffset, dstSize)) return -ZstdBinding.ZSTD_ERROR_DST_SIZE_TOO_SMALL;
        if (!isValidArrayStreamBuffer(src, srcArrayOffset, srcSize)) return -ZstdBinding.ZSTD_ERROR_SRC_SIZE_WRONG;

        return compressBufferStream(MemorySegment.ofArray(dst), dstArrayOffset, dstOffset, dstSize,
                                    MemorySegment.ofArray(src), srcArrayOffset, srcOffset, srcSize, endOp);
    }

    /**
     * Compresses buffer 'srcBuff' into buffer 'dstBuff' reusing this ZstdCompressCtx.
     *
     * Destination buffer should be sized to handle worst cases situations (input
     * data not compressible). Worst case size evaluation is provided by function
     * ZSTD_compressBound(). This is a low-level function that does not take into
     * account or affect the `limit` or `position` of source or destination buffers.
     *
     * @param dstBuff the destination buffer - must be direct
     * @param dstOffset the start offset of 'dstBuff'
     * @param dstSize the size of 'dstBuff' (after 'dstOffset')
     * @param srcBuff the source buffer - must be direct
     * @param srcOffset the start offset of 'srcBuff'
     * @param srcSize the length of 'srcBuff' (after 'srcOffset')
     * @return  the number of bytes written into buffer 'dstBuff'.
     */
    public int compressDirectByteBuffer(@NotNull ByteBuffer dstBuff, int dstOffset, int dstSize, @NotNull ByteBuffer srcBuff, int srcOffset, int srcSize) {
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
            long size = compressDirectByteBuffer0(dstBuff, dstOffset, dstSize, srcBuff, srcOffset, srcSize);
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

    /* Replaces jni_fast_zstd.c:586-608. Slice each segment to the requested offset
     * and length, matching the pointers and lengths passed by JNI.
     *
     * The public caller already checks that both buffers are direct and that the
     * ranges fit within their limits. Plain capacity() is therefore sufficient;
     * the checks below are redundant but retained to match the C implementation. */
    private long compressDirectByteBuffer0(@NotNull ByteBuffer dst, int dstOffset, int dstSize,
            @NotNull ByteBuffer src, int srcOffset, int srcSize) {
        if (0 > dstOffset) return -ZstdBinding.ZSTD_ERROR_DST_SIZE_TOO_SMALL;
        if (0 > srcOffset) return -ZstdBinding.ZSTD_ERROR_SRC_SIZE_WRONG;
        if (0 > srcSize) return -ZstdBinding.ZSTD_ERROR_SRC_SIZE_WRONG;

        if (dstOffset + dstSize > dst.capacity()) return -ZstdBinding.ZSTD_ERROR_DST_SIZE_TOO_SMALL;
        if (srcOffset + srcSize > src.capacity()) return -ZstdBinding.ZSTD_ERROR_SRC_SIZE_WRONG;

        ZstdBinding.resetCCtx(cctx, ZstdBinding.ZSTD_RESET_SESSION_ONLY);
        return ZstdBinding.compress2(cctx,
                directSegment(dst).asSlice(dstOffset, dstSize), dstSize,
                directSegment(src).asSlice(srcOffset, srcSize), srcSize);
    }

    /**
     * Compresses byte array 'srcBuff' into byte array 'dstBuff' reusing this ZstdCompressCtx.
     *
     * Destination buffer should be sized to handle worst cases situations (input
     * data not compressible). Worst case size evaluation is provided by function
     * ZSTD_compressBound().
     *
     * @param dstBuff the destination buffer (byte array)
     * @param dstOffset the start offset of 'dstBuff'
     * @param dstSize the size of 'dstBuff' (after 'dstOffset')
     * @param srcBuff the source buffer (byte array)
     * @param srcOffset the start offset of 'srcBuff'
     * @param srcSize the length of 'srcBuff' (after 'srcOffset')
     * @return  the number of bytes written into buffer 'dstBuff'.
     */
    public int compressByteArray(byte @NotNull [] dstBuff, int dstOffset, int dstSize, byte @NotNull [] srcBuff, int srcOffset, int srcSize) {
        Objects.checkFromIndexSize(srcOffset, srcSize, srcBuff.length);
        Objects.checkFromIndexSize(dstOffset, dstSize, dstBuff.length);

        ensureOpen();
        acquireSharedLock();

        try {
            long size = compressByteArray0(dstBuff, dstOffset, dstSize, srcBuff, srcOffset, srcSize);
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

    /* Replaces jni_fast_zstd.c:615-639. The critical downcall accesses both arrays
     * directly, with each segment sliced to the requested range. No JNI array
     * acquisition, release, or acquisition-error handling is needed.
     *
     * The public caller already validates both ranges. Keep these redundant checks
     * in the original C order, including checking the source end before the destination.
     *
     * Reset runs as a separate downcall because it only touches the context.
     * Its result is ignored, as in JNI. */
    private long compressByteArray0(byte @NotNull [] dst, int dstOffset, int dstSize,
            byte @NotNull [] src, int srcOffset, int srcSize) {
        if (0 > dstOffset) return -ZstdBinding.ZSTD_ERROR_DST_SIZE_TOO_SMALL;
        if (0 > srcOffset) return -ZstdBinding.ZSTD_ERROR_SRC_SIZE_WRONG;
        if (0 > srcSize) return -ZstdBinding.ZSTD_ERROR_SRC_SIZE_WRONG;

        if (srcOffset + srcSize > src.length) return -ZstdBinding.ZSTD_ERROR_SRC_SIZE_WRONG;
        if (dstOffset + dstSize > dst.length) return -ZstdBinding.ZSTD_ERROR_DST_SIZE_TOO_SMALL;

        ZstdBinding.resetCCtx(cctx, ZstdBinding.ZSTD_RESET_SESSION_ONLY);
        return ZstdBinding.compress2(cctx,
                MemorySegment.ofArray(dst).asSlice(dstOffset, dstSize), dstSize,
                MemorySegment.ofArray(src).asSlice(srcOffset, srcSize), srcSize);
    }

    /* Convenience methods */

    /**
     * Compresses the data in buffer 'srcBuf'
     *
     * @param dstBuf the destination buffer - must be direct. It is assumed that the `position()` of this buffer marks the offset
     *               at which the compressed data are to be written, and that the `limit()` of this buffer is the maximum
     *               compressed data size to allow.
     *               <p>
     *               When this method returns successfully, its `position()` will be set to its current `position()` plus the
     *               compressed size of the data.
     *               </p>
     * @param srcBuf the source buffer - must be direct. It is assumed that the `position()` of this buffer marks the beginning of the
     *               uncompressed data to be compressed, and that the `limit()` of this buffer marks its end.
     *               <p>
     *               When this method returns successfully, its `position()` will be set to the initial `limit()`.
     *               </p>
     * @return the size of the compressed data
     */
    public int compress(@NotNull ByteBuffer dstBuf, @NotNull ByteBuffer srcBuf) {
        int size = compressDirectByteBuffer(dstBuf, // compress into dstBuf
                dstBuf.position(),                   // write compressed data starting at offset position()
                dstBuf.limit() - dstBuf.position(),  // write no more than limit() - position() bytes
                srcBuf,                              // read data to compress from srcBuf
                srcBuf.position(),                   // start reading at position()
                srcBuf.limit() - srcBuf.position()   // read limit() - position() bytes
            );
        srcBuf.position(srcBuf.limit());
        dstBuf.position(dstBuf.position() + size);
        return size;
    }

    /**
     * Compresses the data in buffer 'srcBuf'
     *
     * @param srcBuf the source buffer - must be direct. It is assumed that the `position()` of the
     *               buffer marks the beginning of the uncompressed data to be compressed, and that
     *               the `limit()` of this buffer marks its end.
     *               <p>
     *               When this method returns successfully, its `position()` will be set to its initial `limit()`.
     *               </p>
     * @return A newly allocated direct ByteBuffer containing the compressed data.
     */
    @NotNull
    public ByteBuffer compress(@NotNull ByteBuffer srcBuf) throws ZstdException {
        long maxDstSize = Zstd.compressBound((long)(srcBuf.limit() - srcBuf.position()));
        if (maxDstSize > Integer.MAX_VALUE) {
            throw new ZstdException(Zstd.errGeneric(), "Max output size is greater than MAX_INT");
        }
        ByteBuffer dstBuf = ByteBuffer.allocateDirect((int) maxDstSize);
        int size = compressDirectByteBuffer(dstBuf,    // compress into dstBuf
                  0,                                   // starting at offset 0
                  (int) maxDstSize,                    // writing no more than maxDstSize
                  srcBuf,                              // read data to be compressed from srcBuf
                  srcBuf.position(),                   // start reading at offset position()
                  srcBuf.limit() - srcBuf.position()   // read limit() - position() bytes
            );
        srcBuf.position(srcBuf.limit());

        dstBuf.limit(size);
        // Since we allocated the buffer ourselves, we know it cannot be used to hold any further compressed data,
        // so leave the position at zero where the caller surely wants it, ready to read

        return dstBuf;
    }

    public int compress(byte @NotNull [] dst, byte @NotNull [] src) {
        return compressByteArray(dst, 0, dst.length, src, 0, src.length);
    }

    public byte @NotNull [] compress(byte @NotNull [] src) {
        long maxDstSize = Zstd.compressBound(src.length);
        if (maxDstSize > Integer.MAX_VALUE) {
            throw new ZstdException(Zstd.errGeneric(), "Max output size is greater than MAX_INT");
        }
        byte[] dst = new byte[(int) maxDstSize];
        int size = compressByteArray(dst, 0, dst.length, src, 0, src.length);
        return Arrays.copyOfRange(dst, 0, size);
    }
}

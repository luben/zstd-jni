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

    /* Where the four streaming calls leave the new buffer positions. The C packed them
     * into the return value - `(1<<31)|errcode` on error, else `(dstPos<<32)|srcPos`
     * with bit 63 set when everything was flushed - because a JNI function has one
     * jlong to say it all in. Those methods are Java now and updateStreamPositions,
     * the protocol's only decoder, is in this same class, so they hand back libzstd's
     * own result and write the positions to these two fields. Both are buffer-relative,
     * as the C's `out.pos` / `in.pos` are. */
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

    /* jni_fast_zstd.c:343-357. The heap segment goes in under Linker.Option.critical,
     * as the C's GetPrimitiveArrayCritical does: ZSTD_CCtx_loadDictionary copies the
     * dictionary (ZSTD_dlm_byCopy), so nothing retains the pointer past the call, and
     * the C's matching release is JNI_ABORT because libzstd takes it as const. Line for
     * line the same but for the -ZSTD_error_memory_allocation the C returns when
     * GetPrimitiveArrayCritical fails, which has no analogue - wrapping an array cannot
     * fail. A zero-length dict reaches libzstd as a zero-length segment rather than as
     * the C's real pointer with size 0; both mean "invalidate the dictionary" and were
     * checked to produce identical frames. */
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

    /* The two Zstd.* calls inside the branch are JNI methods and stay that way: an
     * exception is not a hot path, and they are the C's own ZSTD_getErrorCode and
     * ZSTD_getErrorName. The test around them is not, which is why it is the binding's. */
    private boolean updateStreamPositions(long result, @NotNull ByteBuffer dst, @NotNull ByteBuffer src) {
        if (ZstdBinding.isError(result)) {
            /* The base copy reads the error code straight out of the packed value, whose
             * low bits the C had already filled with ZSTD_getErrorCode(result) - a small
             * positive enum. Here `result` is libzstd's own return, which is
             * (size_t) -enum, so the ZSTD_getErrorCode call the C made has to be made
             * too; masking this value with 0xFF directly would yield 256 - enum. The
             * -(x & 0xFF) around it is the base copy's, unchanged, and produces the same
             * negative code Zstd.getErrorName and ZstdException are given there. */
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

    /* The C's GetDirectBufferAddress: the buffer's whole window, ignoring its position
     * and limit. ofBuffer spans [position, limit), so the span is widened on a
     * duplicate rather than on the caller's buffer. */
    private static @NotNull MemorySegment directSegment(@NotNull ByteBuffer buffer) {
        return MemorySegment.ofBuffer(buffer.duplicate().clear());
    }

    /* The C's GetDirectBufferCapacity, including its -1 for a buffer that is not
     * direct. That -1 is load-bearing rather than defensive: compressDirectByteBufferStream
     * is public and checks nothing, so a heap buffer reaches the capacity comparisons
     * below and every non-negative size fails against -1. It is also why the C's two
     * GetDirectBufferAddress NULL guards are dropped here as they were in steps 3 and 4
     * - nothing can now get past this and still be non-direct. */
    private static int directCapacity(@NotNull ByteBuffer buffer) {
        return buffer.isDirect() ? buffer.capacity() : -1;
    }

    /* jni_fast_zstd.c:406-423, compress_buffer_stream. `shift` is the C's pointer
     * arithmetic on an array base (`(char *) buff + array_offset`): a heap segment is
     * handed over whole and the offsets are moved instead, so the slots hold
     * array-absolute positions and the buffer-relative ones are recovered on the way
     * out. Zero for a direct buffer, whose segment already starts where C's pointer
     * does. `dstSize` / `srcSize` are absolute end offsets, as `out.size` / `in.size`
     * are in the C.
     *
     * Folding the shift into the offsets is why every caller passing a non-zero one must
     * have run isValidArrayStreamBuffer first: `shift + size` is int arithmetic where the
     * C's is pointer arithmetic, and that check is what bounds it by the array's length.
     * Without it the sum could wrap negative and reach libzstd as a huge size_t. */
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

    /* jni_fast_zstd.c:481-502, compress_direct_buffer_to_byte_array_stream. The mirror of
     * the method above, and the one place the C's two array releases differ: this one is
     * ReleasePrimitiveArrayCritical mode 0, not JNI_ABORT, because dst is the destination
     * and a copying GetPrimitiveArrayCritical would otherwise discard everything libzstd
     * wrote. Neither mode has an analogue here - critical(true) hands over the array
     * itself, heap base plus offset, pinned rather than staged - so the writes land in
     * the caller's byte[] and there is nothing to copy back. */
    private long compressDirectByteBufferToByteArrayStream0(byte @NotNull [] dst, int dstArrayOffset,
            int dstOffset, int dstSize, @NotNull ByteBuffer src, int srcOffset, int srcSize, int endOp) {
        long result = validateCompressStreamBounds(dstOffset, dstSize, srcOffset, srcSize);
        if (ZstdBinding.isError(result)) return result;

        if (!isValidArrayStreamBuffer(dst, dstArrayOffset, dstSize)) return -ZstdBinding.ZSTD_ERROR_DST_SIZE_TOO_SMALL;
        if (srcSize > directCapacity(src)) return -ZstdBinding.ZSTD_ERROR_SRC_SIZE_WRONG;

        return compressBufferStream(MemorySegment.ofArray(dst), dstArrayOffset, dstOffset, dstSize,
                                    directSegment(src), 0, srcOffset, srcSize, endOp);
    }

    /* jni_fast_zstd.c:504-529, compress_byte_array_stream. The C takes the slower
     * GetByteArrayElements here - on HotSpot a copy of both whole arrays in and the
     * destination back out, on every call - to avoid nesting two critical regions.
     * Linker.Option.critical has no such restriction: several heap segments may be
     * pinned for one downcall, so this path is copy-free like the other three, and the
     * two -ZSTD_error_memory_allocation returns go with the copies, since wrapping an
     * array cannot fail where allocating a copy of one can.
     *
     * One consequence beyond speed: the C's copies mean libzstd reads a snapshot, so two
     * heap buffers over the same array with *overlapping* windows behave differently
     * there than here, where both segments are the live array. Not a contract being
     * broken - GetByteArrayElements is free to pin instead of copy and this C ignores its
     * isCopy out-param, so a JVM that pins already behaves the way this does. Buffers
     * that merely share an array without overlapping are unaffected either way. */
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

    /* jni_fast_zstd.c:586-608. Unlike the streaming entry points this one has no
     * position slots to shift, so each segment is cut to the span libzstd is given -
     * the C's `buff + offset` with a length. Plain capacity() rather than
     * directCapacity(): compressDirectByteBuffer rejects a non-direct buffer itself,
     * so the C's -1 case is dead here.
     *
     * So are all five checks below, and they are kept only because the C has them: the
     * caller's two Objects.checkFromIndexSize calls reject a negative offset or size and
     * bound offset + size by limit(), which is at most capacity(). One consequence is
     * that the C's `dst_offset + dst_size` overflow - jint arithmetic there, int
     * arithmetic here, wrapping negative in both and passing the comparison in both - is
     * reproduced rather than fixed, and only what follows differs: the C goes on to write
     * past the end of the buffer where asSlice throws. */
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

    /* jni_fast_zstd.c:615-639. Both arrays go in under Linker.Option.critical, which is
     * what the C's two GetPrimitiveArrayCritical calls do; the goto unwinding and the
     * -ZSTD_error_memory_allocation they could return have no analogue, since a heap
     * segment cannot fail to be acquired.
     *
     * The bounds checks run src before dst, the opposite of compressDirectByteBuffer0
     * above. That is the C's own inconsistency between its two one-shot functions, and
     * each method here follows the one it ports: the order decides which code wins when
     * both sides are invalid, so settling on one would change the JNI-visible contract.
     * As there, the checks are dead - the caller's Objects.checkFromIndexSize calls bound
     * the same offsets against the same array lengths.
     *
     * ZSTD_CCtx_reset moves out of the critical region the C holds while calling it;
     * critical(true) scopes to one downcall, not to a region, and the reset touches only
     * the context, whose return both builds ignore. */
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

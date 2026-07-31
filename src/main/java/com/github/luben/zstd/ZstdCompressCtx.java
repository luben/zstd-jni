package com.github.luben.zstd;

import com.github.luben.zstd.util.Native;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class ZstdCompressCtx extends AutoCloseBase {

    static {
        Native.load();
    }

    private long nativePtr = 0;

    @Nullable
    private ZstdDictCompress compression_dict = null;

    @Nullable
    private SequenceProducer seqprod = null;

    private long seqprod_state = 0;

    private static native long init();

    private static native void free(long ptr);

    /**
     * Create a context for faster compress operations
     * One such context is required for each thread - put this in a ThreadLocal.
     */
    public ZstdCompressCtx() {
        nativePtr = init();
        if (0 == nativePtr) {
            throw new IllegalStateException("ZSTD_createCompressCtx failed");
        }
        storeFence();
    }

    void doClose() {
        if (nativePtr != 0) {
            free(nativePtr);
            nativePtr = 0;
            if (seqprod != null) {
                seqprod.freeState(seqprod_state);
                seqprod = null;
            }
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
        setLevel0(nativePtr, level);
        releaseSharedLock();
        return this;
    }

    private static native void setLevel0(long ptr, int level);

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
        setChecksum0(nativePtr, checksumFlag);
        releaseSharedLock();
        return this;
    }
    private static native void setChecksum0(long ptr, boolean checksumFlag);


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
        setContentSize0(nativePtr, contentSizeFlag);
        releaseSharedLock();
        return this;
    }
    private static native void setContentSize0(long ptr, boolean contentSizeFlag);

    /**
     * Enable or disable dictID
     * @param dictIDFlag When applicable, dictionary's ID is written into frame header, default: true
     */
    @NotNull
    public ZstdCompressCtx setDictID(boolean dictIDFlag) {
        ensureOpen();
        acquireSharedLock();
        setDictID0(nativePtr, dictIDFlag);
        releaseSharedLock();
        return this;
    }
    private static native void setDictID0(long ptr, boolean dictIDFlag);

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
            long result = loadCDictFast0(nativePtr, dict);
            if (Zstd.isError(result)) {
                throw new ZstdException(result);
            }
            // keep a reference to the dictionary so it's not garbage collected
            compression_dict = dict;
        } finally {
            if (dict != null) {
                dict.releaseSharedLock();
            }
            releaseSharedLock();
        }
        return this;
    }
    private native long loadCDictFast0(long ptr, @Nullable ZstdDictCompress dict);

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
            long result = loadCDict0(nativePtr, dict);
            if (Zstd.isError(result)) {
                throw new ZstdException(result);
            }
            compression_dict = null;
        } finally {
            releaseSharedLock();
        }
        return this;
    }
    private native long loadCDict0(long ptr, byte @Nullable [] dict);

    /**
     * Tells how much data has been ingested (read from input),
     * consumed (input actually compressed) and produced (output) for current frame.
     */
    @NotNull
    public ZstdFrameProgression getFrameProgression() {
        ensureOpen();
        acquireSharedLock();
        try {
            return getFrameProgression0(nativePtr);
        } finally {
            releaseSharedLock();
        }
    }
    @NotNull
    private static native ZstdFrameProgression getFrameProgression0(long ptr);

    /**
     * Clear all state and parameters from the compression context. This leaves the object in a
     * state identical to a newly created compression context.
     */
    public void reset() {
        ensureOpen();
        acquireSharedLock();
        try {
            long result = reset0(nativePtr);
            if (Zstd.isError(result)) {
                throw new ZstdException(result);
            }
        } finally {
            releaseSharedLock();
        }

    }
    private static native long reset0(long ptr);

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
            long result = setPledgedSrcSize0(nativePtr, srcSize);
            if (Zstd.isError(result)) {
                throw new ZstdException(result);
            }
        } finally {
            releaseSharedLock();
        }
    }
    private static native long setPledgedSrcSize0(long ptr, long srcSize);

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
                    result = compressDirectByteBufferStream0(nativePtr, dst, dst.position(), dst.limit(), src,
                            src.position(), src.limit(), endOp.value());
                } else {
                    result = compressByteArrayToDirectByteBufferStream0(nativePtr, dst, dst.position(), dst.limit(),
                            src.array(), src.arrayOffset(), src.position(), src.limit(), endOp.value());
                }
            } else if (src.isDirect()) {
                result = compressDirectByteBufferToByteArrayStream0(nativePtr, dst.array(), dst.arrayOffset(),
                        dst.position(), dst.limit(), src, src.position(), src.limit(), endOp.value());
            } else {
                result = compressByteArrayStream0(nativePtr, dst.array(), dst.arrayOffset(), dst.position(), dst.limit(),
                        src.array(), src.arrayOffset(), src.position(), src.limit(), endOp.value());
            }
            return updateStreamPositions(result, dst, src);
        } finally {
            releaseSharedLock();
        }
    }

    private static native long compressByteArrayToDirectByteBufferStream0(long ptr, @NotNull ByteBuffer dst,
            int dstOffset, int dstSize, byte @NotNull [] src, int srcArrayOffset, int srcOffset, int srcSize, int endOp);

    private static native long compressDirectByteBufferToByteArrayStream0(long ptr, byte @NotNull [] dst,
            int dstArrayOffset, int dstOffset, int dstSize, @NotNull ByteBuffer src, int srcOffset, int srcSize, int endOp);

    private static native long compressByteArrayStream0(long ptr, byte @NotNull [] dst, int dstArrayOffset,
            int dstOffset, int dstSize,
            byte @NotNull [] src, int srcArrayOffset, int srcOffset, int srcSize, int endOp);

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
            long result = compressDirectByteBufferStream0(nativePtr, dst, dst.position(), dst.limit(), src, src.position(), src.limit(), endOp.value());
            return updateStreamPositions(result, dst, src);
        } finally {
            releaseSharedLock();
        }
    }

    /**
     * 4 pieces of information are packed into the return value of this method, which must be
     * treated as an unsigned long. The highest bit is set if all data has been flushed from
     * internal buffers. The next 31 bits are the new position of the destination buffer. The next
     * bit is set if an error occurred. If an error occurred, the lowest 31 bits encode a zstd error
     * code. Otherwise, the lowest 31 bits are the new position of the source buffer.
     */
    private static native long compressDirectByteBufferStream0(long ptr, @NotNull ByteBuffer dst, int dstOffset, int dstSize,
            @NotNull ByteBuffer src, int srcOffset, int srcSize, int endOp);

    private static boolean updateStreamPositions(long result, @NotNull ByteBuffer dst, @NotNull ByteBuffer src) {
        if ((result & 0x80000000L) != 0) {
            long code = -(result & 0xFF);
            throw new ZstdException(code, Zstd.getErrorName(code));
        }
        src.position((int)(result & 0x7FFFFFFF));
        dst.position((int)(result >>> 32) & 0x7FFFFFFF);
        return (result >>> 63) == 1;
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
            long size = compressDirectByteBuffer0(nativePtr, dstBuff, dstOffset, dstSize, srcBuff, srcOffset, srcSize);
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

    private static native long compressDirectByteBuffer0(long ptr, @NotNull ByteBuffer dst, int dstOffset, int dstSize, @NotNull ByteBuffer src, int srcOffset, int srcSize);

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
            long size = compressByteArray0(nativePtr, dstBuff, dstOffset, dstSize, srcBuff, srcOffset, srcSize);
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

    private static native long compressByteArray0(long ptr, byte @NotNull [] dst, int dstOffset, int dstSize, byte @NotNull [] src, int srcOffset, int srcSize);

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

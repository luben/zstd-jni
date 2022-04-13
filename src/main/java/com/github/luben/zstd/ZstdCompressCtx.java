package com.github.luben.zstd;

import com.github.luben.zstd.util.Native;
import com.github.luben.zstd.ZstdDictCompress;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class ZstdCompressCtx extends AutoCloseBase {

    static {
        Native.load();
    }

    private long nativePtr = 0;

    private ZstdDictCompress compression_dict = null;

    private native void init();

    private native void free();

    /**
     * Create a context for faster compress operations
     * One such context is required for each thread - put this in a ThreadLocal.
     */
    public ZstdCompressCtx() {
        init();
        if (0 == nativePtr) {
            throw new IllegalStateException("ZSTD_createCompressCtx failed");
        }
        storeFence();
    }

    void  doClose() {
        if (nativePtr != 0) {
            free();
            nativePtr = 0;
        }
    }

    /**
     * Set compression level
     * @param level compression level, default: {@link Zstd#defaultCompressionLevel()}
     */
    public ZstdCompressCtx setLevel(int level) {
        if (nativePtr == 0) {
            throw new IllegalStateException("Compression context is closed");
        }
        acquireSharedLock();
        setLevel0(level);
        releaseSharedLock();
        return this;
    }

    private native void setLevel0(int level);

    /**
     * Enable or disable compression checksums
     * @param checksumFlag A 32-bits checksum of content is written at end of frame, default: false
     */
    public ZstdCompressCtx setChecksum(boolean checksumFlag) {
        if (nativePtr == 0) {
            throw new IllegalStateException("Compression context is closed");
        }
        acquireSharedLock();
        setChecksum0(checksumFlag);
        releaseSharedLock();
        return this;
    }
    private native void setChecksum0(boolean checksumFlag);


    public ZstdCompressCtx setWorkers(int workers) {
        acquireSharedLock();
        Zstd.setCompressionWorkers(nativePtr, workers);
        releaseSharedLock();
        return this;
    }

    /**
     * Enable or disable content size
     * @param contentSizeFlag Content size will be written into frame header _whenever known_, default: true
     */
    public ZstdCompressCtx setContentSize(boolean contentSizeFlag) {
        if (nativePtr == 0) {
            throw new IllegalStateException("Compression context is closed");
        }
        acquireSharedLock();
        setContentSize0(contentSizeFlag);
        releaseSharedLock();
        return this;
    }
    private native void setContentSize0(boolean contentSizeFlag);

    /**
     * Enable or disable dictID
     * @param dictIDFlag When applicable, dictionary's ID is written into frame header, default: true
     */
    public ZstdCompressCtx setDictID(boolean dictIDFlag) {
        if (nativePtr == 0) {
            throw new IllegalStateException("Compression context is closed");
        }
        acquireSharedLock();
        setDictID0(dictIDFlag);
        releaseSharedLock();
        return this;
    }
    private native void setDictID0(boolean dictIDFlag);

    /**
     * Enable or disable LongDistanceMatching and set the window size
     * @param windowLog Maximum allowed back-reference distance, expressed as power of 2.
     *                  This will set a memory budget for streaming decompression,
     *                  with larger values requiring more memory and typically compressing more.
     *                  Must be clamped between 10 and 32/64 but values greater then 27 may not
     *                  be decompressable in all context as they require more memory.
     *                  0 disables LDM.
     */
    public ZstdCompressCtx setLong(int windowLog) {
        if (nativePtr == 0) {
            throw new IllegalStateException("Compression context is closed");
        }
        acquireSharedLock();
        Zstd.setCompressionLong(nativePtr, windowLog);
        releaseSharedLock();
        return this;
    }

    /**
     * Load compression dictionary to be used for subsequently compressed frames.
     *
     * @param dict the dictionary or `null` to remove loaded dictionary
     */
    public ZstdCompressCtx loadDict(ZstdDictCompress dict) {
        if (nativePtr == 0) {
            throw new IllegalStateException("Compression context is closed");
        }

        acquireSharedLock();
        dict.acquireSharedLock();
        try {
            long result = loadCDictFast0(dict);
            if (Zstd.isError(result)) {
                throw new ZstdException(result);
            }
            // keep a reference to the dictionary so it's not garbage collected
            compression_dict = dict;
        } finally {
            dict.releaseSharedLock();
            releaseSharedLock();
        }
        return this;
    }
    private native long loadCDictFast0(ZstdDictCompress dict);

    /**
     * Load compression dictionary to be used for subsequently compressed frames.
     *
     * @param dict the dictionary or `null` to remove loaded dictionary
     */
    public ZstdCompressCtx loadDict(byte[] dict) {
        if (nativePtr == 0) {
            throw new IllegalStateException("Compression context is closed");
        }
        acquireSharedLock();
        try {
            long result = loadCDict0(dict);
            if (Zstd.isError(result)) {
                throw new ZstdException(result);
            }
            compression_dict = null;
        } finally {
            releaseSharedLock();
        }
        return this;
    }
    private native long loadCDict0(byte[] dict);

    private void ensureOpen() {
        if (nativePtr == 0) {
            throw new IllegalStateException("Compression context is closed");
        }
    }

    /**
     * Clear all state and parameters from the compression context. This leaves the object in a
     * state identical to a newly created compression context.
     */
    public void reset() {
        ensureOpen();
        long result = reset0();
        if (Zstd.isError(result)) {
            throw new ZstdException(result);
        }
    }
    private native long reset0();

    /**
     * Promise to compress a certain number of source bytes. Knowing the number of bytes to compress
     * up front helps to choose proper compression settings and size internal buffers. Additionally,
     * the pledged size is stored in the header of the output stream, allowing decompressors to know
     * how much uncompressed data to expect.
     *
     * Attempting to compress more or less than than the pledged size will result in an error.
     */
    public void setPledgedSrcSize(long srcSize) {
        ensureOpen();
        long result = setPledgedSrcSize0(srcSize);
        if (Zstd.isError(result)) {
            throw new ZstdException(result);
        }
    }
    private native long setPledgedSrcSize0(long srcSize);

    /**
     * Compress as much of the <code>src</code> {@link ByteBuffer} into the <code>dst</code> {@link
     * ByteBuffer} as possible.
     *
     * @param dst destination of compressed data
     * @param src buffer to compress
     * @param endOp directive for handling the end of the stream
     * @return true if all state has been flushed from internal buffers
     */
    public boolean compressDirectByteBufferStream(ByteBuffer dst, ByteBuffer src, EndDirective endOp) {
        ensureOpen();
        long result = compressDirectByteBufferStream0(dst, dst.position(), dst.limit(), src, src.position(), src.limit(), endOp.value());
        if ((result & 0x80000000L) != 0) {
            long code = result & 0xFF;
            throw new ZstdException(code, Zstd.getErrorName(code));
        }
        src.position((int)(result & 0x7FFFFFFF));
        dst.position((int)(result >>> 32) & 0x7FFFFFFF);
        return (result >>> 63) == 1;
    }

    /**
     * 4 pieces of information are packed into the return value of this method, which must be
     * treated as an unsigned long. The highest bit is set if all data has been flushed from
     * internal buffers. The next 31 bits are the new position of the destination buffer. The next
     * bit is set if an error occurred. If an error occurred, the lowest 31 bits encode a zstd error
     * code. Otherwise, the lowest 31 bits are the new position of the source buffer.
     */
    private native long compressDirectByteBufferStream0(ByteBuffer dst, int dstOffset, int dstSize, ByteBuffer src, int srcSize, int srcOffset, int endOp);

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
    public int compressDirectByteBuffer(ByteBuffer dstBuff, int dstOffset, int dstSize, ByteBuffer srcBuff, int srcOffset, int srcSize) {
        if (nativePtr == 0) {
            throw new IllegalStateException("Compression context is closed");
        }
        if (!srcBuff.isDirect()) {
            throw new IllegalArgumentException("srcBuff must be a direct buffer");
        }
        if (!dstBuff.isDirect()) {
            throw new IllegalArgumentException("dstBuff must be a direct buffer");
        }

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

    private native long compressDirectByteBuffer0(ByteBuffer dst, int dstOffset, int dstSize, ByteBuffer src, int srcOffset, int srcSize);

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
    public int compressByteArray(byte[] dstBuff, int dstOffset, int dstSize, byte[] srcBuff, int srcOffset, int srcSize) {
        if (nativePtr == 0) {
            throw new IllegalStateException("Compression context is closed");
        }

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

    private native long compressByteArray0(byte[] dst, int dstOffset, int dstSize, byte[] src, int srcOffset, int srcSize);

    /** Convenience methods */

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
    public int compress(ByteBuffer dstBuf, ByteBuffer srcBuf) {

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
    public ByteBuffer compress(ByteBuffer srcBuf) throws ZstdException {
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

    public int compress(byte[] dst, byte[] src) {
        return compressByteArray(dst, 0, dst.length, src, 0, src.length);
    }

    public byte[] compress(byte[] src) {
        long maxDstSize = Zstd.compressBound(src.length);
        if (maxDstSize > Integer.MAX_VALUE) {
            throw new ZstdException(Zstd.errGeneric(), "Max output size is greater than MAX_INT");
        }
        byte[] dst = new byte[(int) maxDstSize];
        int size = compressByteArray(dst, 0, dst.length, src, 0, src.length);
        return Arrays.copyOfRange(dst, 0, size);
    }
}

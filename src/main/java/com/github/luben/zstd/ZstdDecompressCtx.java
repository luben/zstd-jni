package com.github.luben.zstd;

import com.github.luben.zstd.util.Native;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class ZstdDecompressCtx extends AutoCloseBase {

    static {
        Native.load();
    }

    private long nativePtr = 0;
    // Note: keeps a reference to the dictionary so it's not garbage collected
    private ZstdDictDecompress decompression_dict = null;

    private static native long init();

    private static native void free(long nativePtr);

    /**
     * Create a context for faster compress operations
     * One such context is required for each thread - put this in a ThreadLocal.
     */
    public ZstdDecompressCtx() {
        nativePtr = init();
        if (0 == nativePtr) {
            throw new IllegalStateException("ZSTD_createDeCompressCtx failed");
        }
        storeFence();
    }

    void doClose() {
        if (nativePtr != 0) {
            free(nativePtr);
            nativePtr = 0;
        }
    }

    /**
     * Enable or disable magicless frames
     * @param magiclessFlag A 32-bits checksum of content is written at end of frame, default: false
     */
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
     * @param dict the dictionary or `null` to remove loaded dictionary
     */
    public ZstdDecompressCtx loadDict(ZstdDictDecompress dict) {
        ensureOpen();
        acquireSharedLock();
        dict.acquireSharedLock();
        try {
            long result = loadDDictFast0(nativePtr, dict);
            if (Zstd.isError(result)) {
                throw new ZstdException(result);
            }
            // keep a reference to the dictionary so it's not garbage collected
            decompression_dict = dict;
        } finally {
            dict.releaseSharedLock();
            releaseSharedLock();
        }
        return this;
    }

    private static native long loadDDictFast0(long nativePtr, ZstdDictDecompress dict);

    /**
     * Load decompression dictionary.
     *
     * @param dict the dictionary or `null` to remove loaded dictionary
     */
    public ZstdDecompressCtx loadDict(byte[] dict) {
        ensureOpen();
        acquireSharedLock();
        try {
            long result = loadDDict0(nativePtr, dict);
            if (Zstd.isError(result)) {
                throw new ZstdException(result);
            }
            decompression_dict = null;
        } finally {
            releaseSharedLock();
        }
        return this;
    }

    private static native long loadDDict0(long nativePtr, byte[] dict);

    /**
     * Clear all state and parameters from the decompression context. This leaves the object in a
     * state identical to a newly created decompression context.
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

    private static native long reset0(long nativePtr);

    private void ensureOpen() {
        if (nativePtr == 0) {
            throw new IllegalStateException("Decompression context is closed");
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
    public boolean decompressDirectByteBufferStream(ByteBuffer dst, ByteBuffer src) {
        ensureOpen();
        acquireSharedLock();
        try {
            long result = decompressDirectByteBufferStream0(nativePtr, dst, dst.position(), dst.limit(), src, src.position(), src.limit());
            if ((result & 0x80000000L) != 0) {
                long code = -(result & 0xFF);
                throw new ZstdException(code, Zstd.getErrorName(code));
            }
            src.position((int) (result & 0x7FFFFFFF));
            dst.position((int) (result >>> 32) & 0x7FFFFFFF);
            return (result >>> 63) == 1;
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
    private static native long decompressDirectByteBufferStream0(long nativePtr, ByteBuffer dst, int dstOffset, int dstSize, ByteBuffer src, int srcOffset, int srcSize);

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
    public int decompressDirectByteBuffer(ByteBuffer dstBuff, int dstOffset, int dstSize, ByteBuffer srcBuff, int srcOffset, int srcSize) {
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
            long size = decompressDirectByteBuffer0(nativePtr, dstBuff, dstOffset, dstSize, srcBuff, srcOffset, srcSize);
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

    private static native long decompressDirectByteBuffer0(long nativePtr, ByteBuffer dst, int dstOffset, int dstSize, ByteBuffer src, int srcOffset, int srcSize);

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
    public int decompressByteArray(byte[] dstBuff, int dstOffset, int dstSize, byte[] srcBuff, int srcOffset, int srcSize) {
        Objects.checkFromIndexSize(srcOffset, srcSize, srcBuff.length);
        Objects.checkFromIndexSize(dstOffset, dstSize, dstBuff.length);

        ensureOpen();
        acquireSharedLock();

        try {
            long size = decompressByteArray0(nativePtr, dstBuff, dstOffset, dstSize, srcBuff, srcOffset, srcSize);
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

    private static native long decompressByteArray0(long nativePtr, byte[] dst, int dstOffset, int dstSize, byte[] src, int srcOffset, int srcSize);

    public int decompressByteArrayToDirectByteBuffer(ByteBuffer dstBuff, int dstOffset, int dstSize, byte[] srcBuff, int srcOffset, int srcSize) {
        if (!dstBuff.isDirect()) {
            throw new IllegalArgumentException("dstBuff must be a direct buffer");
        }

        Objects.checkFromIndexSize(srcOffset, srcSize, srcBuff.length);
        Objects.checkFromIndexSize(dstOffset, dstSize, dstBuff.limit());

        ensureOpen();
        acquireSharedLock();

        try {
            long size = decompressByteArrayToDirectByteBuffer0(nativePtr, dstBuff, dstOffset, dstSize, srcBuff, srcOffset, srcSize);
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

    private static native long decompressByteArrayToDirectByteBuffer0(long nativePtr, ByteBuffer dst, int dstOffset, int dstSize, byte[] src, int srcOffset, int srcSize);

    public int decompressDirectByteBufferToByteArray(byte[] dstBuff, int dstOffset, int dstSize, ByteBuffer srcBuff, int srcOffset, int srcSize) {
        if (!srcBuff.isDirect()) {
            throw new IllegalArgumentException("srcBuff must be a direct buffer");
        }

        Objects.checkFromIndexSize(srcOffset, srcSize, srcBuff.limit());
        Objects.checkFromIndexSize(dstOffset, dstSize, dstBuff.length);

        ensureOpen();
        acquireSharedLock();

        try {
            long size = decompressDirectByteBufferToByteArray0(nativePtr, dstBuff, dstOffset, dstSize, srcBuff, srcOffset, srcSize);
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

    private static native long decompressDirectByteBufferToByteArray0(long nativePtr, byte[] dst, int dstOffset, int dstSize, ByteBuffer src, int srcOffset, int srcSize);

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
    public int decompress(ByteBuffer dstBuf, ByteBuffer srcBuf) throws ZstdException {
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

    public int decompress(ByteBuffer dstBuf, byte[] src) throws ZstdException {
        int size = decompressByteArrayToDirectByteBuffer(dstBuf,  // decompress into dstBuf
                dstBuf.position(),                      // write decompressed data at offset position()
                dstBuf.limit() - dstBuf.position(),     // write no more than limit() - position()
                src,                                 // read compressed data from src
                0,
                src.length);
        dstBuf.position(dstBuf.position() + size);
        return size;
    }

    public int decompress(byte[] dst, ByteBuffer srcBuf) throws ZstdException {
        int size = decompressDirectByteBufferToByteArray(dst,  // decompress into dst
                0,
                dst.length,
                srcBuf,                                 // read compressed data from srcBuf
                srcBuf.position(),                      // read starting at offset position()
                srcBuf.limit() - srcBuf.position());    // read no more than limit() - position()
        srcBuf.position(srcBuf.limit());
        return size;
    }

    public ByteBuffer decompress(ByteBuffer srcBuf, int originalSize) throws ZstdException {
        ByteBuffer dstBuf = ByteBuffer.allocateDirect(originalSize);
        int size = decompressDirectByteBuffer(dstBuf, 0, originalSize, srcBuf, srcBuf.position(), srcBuf.limit() - srcBuf.position());
        srcBuf.position(srcBuf.limit());
        // Since we allocated the buffer ourselves, we know it cannot be used to hold any further decompressed data,
        // so leave the position at zero where the caller surely wants it, ready to read
        return dstBuf;
    }

    public int decompress(byte[] dst, byte[] src) {
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
    public byte[] decompress(byte[] src, int originalSize) throws ZstdException {
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
    public byte[] decompress(byte[] src, int srcOffset, int srcSize, int originalSize) throws ZstdException {
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

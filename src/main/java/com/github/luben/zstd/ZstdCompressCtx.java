package com.github.luben.zstd;

import com.github.luben.zstd.util.Native;
import com.github.luben.zstd.ZstdDictCompress;

import java.nio.ByteBuffer;

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
     */
    public void setLevel(int level) {
        if (nativePtr == 0) {
            throw new IllegalStateException("Compression context is closed");
        }
        acquireSharedLock();
        setLevel0(level);
        releaseSharedLock();
    }
    private native void setLevel0(int level);

    /**
     * Enable or disable compression checksums
     */
    public void setChecksum(boolean checksum) {
        if (nativePtr == 0) {
            throw new IllegalStateException("Compression context is closed");
        }
        acquireSharedLock();
        setChecksum0(checksum);
        releaseSharedLock();
    }
    private native void setChecksum0(boolean checksum);

    /**
     * Load compression dictionary to be used for subsequently compressed frames.
     *
     * @param dict the dictionary or `null` to remove loaded dictionary
     * @return 0 or error code
     */
    public long loadDict(ZstdDictCompress dict) {
        if (nativePtr == 0) {
            throw new IllegalStateException("Compression context is closed");
        }
        // keep a reference to the ditionary so it's not garbage collected
        compression_dict = dict;
        acquireSharedLock();
        dict.acquireSharedLock();
        try {
            long result = loadCDictFast0(dict);
            if (Zstd.isError(result)) {
                throw new ZstdException(result);
            }
            return result;
        } finally {
            dict.releaseSharedLock();
            releaseSharedLock();
        }
    }
    private native long loadCDictFast0(ZstdDictCompress dict);

    /**
     * Load compression dictionary to be used for subsequently compressed frames.
     *
     * @param dict the dictionary or `null` to remove loaded dictionary
     * @return 0 or error code
     */
    public long loadDict(byte[] dict) {
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
            return result;
        } finally {
            releaseSharedLock();
        }
    }
    private native long loadCDict0(byte[] dict);

    /**
     * Compresses buffer 'srcBuff' into buffer 'dstBuff' reusing this ZstdCompressCtx.
     *
     * Destination buffer should be sized to handle worst cases situations (input
     * data not compressible). Worst case size evaluation is provided by function
     * ZSTD_compressBound().
     * Note: compression level and parameters are
     *
     * @param dstBuff the destination buffer - must be direct
     * @param dstOffset the start offset of 'dstBuff'
     * @param dstSize the size of 'dstBuff'
     * @param srcBuff the source buffer - must be direct
     * @param srcOffset the start offset of 'srcBuff'
     * @param srcSize the length of 'srcBuff'
     * @return  the number of bytes written into buffer 'dstBuff'.
     */
    public long compressDirectByteBuffer(ByteBuffer dstBuff, int dstOffset, int dstSize, ByteBuffer srcBuff, int srcOffset, int srcSize) {
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
            long result = compressDirectByteBuffer0(dstBuff, dstOffset, dstSize, srcBuff, srcOffset, srcSize);
            if (Zstd.isError(result)) {
                throw new ZstdException(result);
            }
            return result;

        } finally {
            releaseSharedLock();
        }
    }

    private native long compressDirectByteBuffer0(ByteBuffer dst, int dstOffset, int dstSize, ByteBuffer src, int srcOffset, int srcSize);


    /**
     * Compresses buffer 'srcBuff' into buffer 'dstBuff' with dictionary reusing this ZstdCompressCtx.
     *
     * Destination buffer should be sized to handle worst cases situations (input
     * data not compressible). Worst case size evaluation is provided by function
     * ZSTD_compressBound().
     * Note : compression level is _decided at dictionary creation time_,
     *     and frame parameters are hardcoded (dictID=yes, contentSize=yes, checksum=no)
     *
     * @param dstBuff the destination buffer - must be direct
     * @param dstOffset the start offset of 'dstBuff'
     * @param dstSize the size of 'dstBuff'
     * @param srcBuff the source buffer - must be direct
     * @param srcOffset the start offset of 'srcBuff'
     * @param srcSize the length of 'srcBuff'
     * @param dict the dictionary
     * @return  the number of bytes written into buffer 'dstBuff'.
     */
    public long compressDirectByteBufferFastDict(ByteBuffer dstBuff, int dstOffset, int dstSize, ByteBuffer srcBuff, int srcOffset, int srcSize, ZstdDictCompress dict) {
        if (nativePtr == 0) {
            throw new IllegalStateException("Compression context is closed");
        }
        if (!srcBuff.isDirect()) {
            throw new IllegalArgumentException("srcBuff must be a direct buffer");
        }
        if (!dstBuff.isDirect()) {
            throw new IllegalArgumentException("dstBuff must be a direct buffer");
        }

        dict.acquireSharedLock();
        acquireSharedLock();

        try {
            long result = compressDirectByteBufferFastDict0(dstBuff, dstOffset, dstSize, srcBuff, srcOffset, srcSize, dict);
            if (Zstd.isError(result)) {
                throw new ZstdException(result);
            }
            return result;
        } finally {
            releaseSharedLock();
            dict.releaseSharedLock();
        }
    }

    private native long compressDirectByteBufferFastDict0(ByteBuffer dst, int dstOffset, int dstSize, ByteBuffer src, int srcOffset, int srcSize, ZstdDictCompress dict);
}

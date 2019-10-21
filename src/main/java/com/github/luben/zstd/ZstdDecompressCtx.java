package com.github.luben.zstd;

import com.github.luben.zstd.util.Native;

import java.nio.ByteBuffer;

public class ZstdDecompressCtx extends AutoCloseBase {

    static {
        Native.load();
    }

    private long nativePtr = 0;

    private native void init();

    private native void free();

    /**
     * Create a context for faster compress operations
     * One such context is required for each thread - put this in a ThreadLocal.
     */
    public ZstdDecompressCtx() {
        init();
        if (0 == nativePtr) {
            throw new IllegalStateException("ZSTD_createDeCompressCtx failed");
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
     * Decompresses buffer 'srcBuff' into buffer 'dstBuff' with dictionary using this ZstdDecompressCtx.
     *
     * Destination buffer should be sized to be larger of equal to the originalSize
     *
     * @param dstBuff the destination buffer - must be direct
     * @param dstOffset the start offset of 'dstBuff'
     * @param dstSize the size of 'dstBuff'
     * @param srcBuff the source buffer - must be direct
     * @param srcOffset the start offset of 'srcBuff'
     * @param srcSize the size of 'srcBuff'
     * @param dict the dictionary
     * @return the number of bytes decompressed into destination buffer (originalSize)
     */
    public int decompressDirectByteBufferFastDict(ByteBuffer dstBuff, int dstOffset, int dstSize, ByteBuffer srcBuff, int srcOffset, int srcSize, ZstdDictDecompress dict) {
        if (!srcBuff.isDirect()) {
            throw new IllegalArgumentException("srcBuff must be a direct buffer");
        }
        if (!dstBuff.isDirect()) {
            throw new IllegalArgumentException("dstBuff must be a direct buffer");
        }

        dict.acquireSharedLock();
        acquireSharedLock();

        try {
            long result = decompressDirectByteBufferFastDict0(dstBuff, dstOffset, dstSize, srcBuff, srcOffset, srcSize, dict);
            if (Zstd.isError(result)) {
                throw new ZstdException(result);
            }
            return (int) result;

        } finally {
            releaseSharedLock();
            dict.releaseSharedLock();
        }
    }

    private native long decompressDirectByteBufferFastDict0(ByteBuffer dst, int dstOffset, int dstSize, ByteBuffer src, int srcOffset, int srcSize, ZstdDictDecompress dict);
}

package com.github.luben.zstd;

import com.github.luben.zstd.util.Native;

import java.nio.ByteBuffer;

public class ZstdCompressCtx extends AutoCloseBase {

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
     * Compresses buffer 'srcBuff' into buffer 'dstBuff' with dictionary reusing this ZstdCompressCtx.
     *
     * Destination buffer should be sized to handle worst cases situations (input
     * data not compressible). Worst case size evaluation is provided by function
     * ZSTD_compressBound().
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
    public int compressDirectByteBufferFastDict(ByteBuffer dstBuff, int dstOffset, int dstSize, ByteBuffer srcBuff, int srcOffset, int srcSize, ZstdDictCompress dict) {
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
            return (int) result;

        } finally {
            releaseSharedLock();
            dict.releaseSharedLock();
        }
    }

    public native long compressDirectByteBufferFastDict0(ByteBuffer dst, int dstOffset, int dstSize, ByteBuffer src, int srcOffset, int srcSize, ZstdDictCompress dict);
}

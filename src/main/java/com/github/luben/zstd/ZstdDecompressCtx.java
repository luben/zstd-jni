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
     * Decompresses buffer 'src' into buffer 'dst' with dictionary using this ZstdDecompressCtx.
     *
     * Destination buffer should be sized to be larger of equal to the originalSize
     *
     * @param dst the destination buffer
     * @param dstOffset the start offset of 'dst'
     * @param dstSize the size of 'dst'
     * @param src the source buffer
     * @param srcOffset the start offset of 'src'
     * @param srcSize the size of 'src'
     * @param dict the dictionary
     * @return the number of bytes decompressed into destination buffer (originalSize)
     *          or an errorCode if it fails (which can be tested using ZSTD_isError())
     *
     */
    public long decompressDirectByteBufferFastDict(ByteBuffer dst, int dstOffset, int dstSize, ByteBuffer src, int srcOffset, int srcSize, ZstdDictDecompress dict) {
        dict.acquireSharedLock();
        acquireSharedLock();
        try {
            return decompressDirectByteBufferFastDict0(dst, dstOffset, dstSize, src, srcOffset, srcSize, dict);
        } finally {
            releaseSharedLock();
            dict.releaseSharedLock();
        }
    }

    private native long decompressDirectByteBufferFastDict0(ByteBuffer dst, int dstOffset, int dstSize, ByteBuffer src, int srcOffset, int srcSize, ZstdDictDecompress dict);

}

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
     * Compresses buffer 'src' into buffer 'dst' with dictionary reusing this ZstdCompressCtx.
     *
     * Destination buffer should be sized to handle worst cases situations (input
     * data not compressible). Worst case size evaluation is provided by function
     * ZSTD_compressBound().
     *
     * @param dst the destination buffer
     * @param dstOffset the start offset of 'dst'
     * @param dstSize the size of 'dst'
     * @param src the source buffer
     * @param srcOffset the start offset of 'src'
     * @param srcSize the length of 'src'
     * @param dict the dictionary
     * @return  the number of bytes written into buffer 'dst' or an error code if
     *          it fails (which can be tested using ZSTD_isError())
     */
    public long compressDirectByteBufferFastDict(ByteBuffer dst, int dstOffset, int dstSize, ByteBuffer src, int srcOffset, int srcSize, ZstdDictCompress dict) {
        dict.acquireSharedLock();
        acquireSharedLock();
        try {
            return compressDirectByteBufferFastDict0(dst, dstOffset, dstSize, src, srcOffset, srcSize, dict);
        } finally {
            releaseSharedLock();
            dict.releaseSharedLock();
        }
    }

    public native long compressDirectByteBufferFastDict0(ByteBuffer dst, int dstOffset, int dstSize, ByteBuffer src, int srcOffset, int srcSize, ZstdDictCompress dict);

}

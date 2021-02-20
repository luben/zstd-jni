package com.github.luben.zstd;

import java.io.InputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.lang.IndexOutOfBoundsException;
import java.nio.ByteBuffer;

import com.github.luben.zstd.util.Native;

/**
 * InputStreamNoFinalizer filter that decompresses the data provided
 * by the underlying InputStream using Zstd compression.
 *
 * It does not support mark/reset methods. It also does not have finalizer,
 * so if you rely on finalizers to clean the native memory and release
 * buffers use `ZstdInputStream` instead.
 */

public class ZstdInputStreamNoFinalizer extends ZstdInputStreamBase<ZstdInputStreamNoFinalizer> {

    /**
     * create a new decompressing InputStreamNoFinalizer
     * @param inStream the stream to wrap
     */
    public ZstdInputStreamNoFinalizer(InputStream inStream) throws IOException {
        super(inStream, NoPool.INSTANCE);
    }

    /**
     * create a new decompressing InputStreamNoFinalizer
     * @param inStream the stream to wrap
     * @param bufferPool the pool to fetch and return buffers
     */
    public ZstdInputStreamNoFinalizer(InputStream inStream, BufferPool bufferPool) throws IOException {
        super(inStream);
    }
}

package com.github.luben.zstd;

import java.io.OutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import com.github.luben.zstd.util.Native;

/**
 * OutputStream filter that compresses the data using Zstd compression.
 *
 * If you rely in finalizers for releasing the native memory and the buffers,
 * use `ZstdOutputStream` instead.
 */
public class ZstdOutputStreamNoFinalizer extends ZstdOutputStreamBase<ZstdOutputStreamNoFinalizer> {


    /**
     * create a new compressing OutputStream
     * @param outStream the stream to wrap
     * @param level the compression level
     */
    public ZstdOutputStreamNoFinalizer(OutputStream outStream, int level) throws IOException {
        super(outStream, NoPool.INSTANCE, level);
    }

    /**
     * create a new compressing OutputStream
     * @param outStream the stream to wrap
     */
    public ZstdOutputStreamNoFinalizer(OutputStream outStream) throws IOException {
        super(outStream, NoPool.INSTANCE);
    }

    /**
     * create a new compressing OutputStream
     * @param outStream the stream to wrap
     * @param bufferPool the pool to fetch and return buffers
     */
    public ZstdOutputStreamNoFinalizer(OutputStream outStream, BufferPool bufferPool) throws IOException {
        super(outStream, bufferPool);
    }
}

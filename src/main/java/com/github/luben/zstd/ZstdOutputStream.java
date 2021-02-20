package com.github.luben.zstd;

import java.io.OutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import com.github.luben.zstd.util.Native;

/**
 * OutputStream filter that compresses the data using Zstd compression
 */
public class ZstdOutputStream extends ZstdOutputStreamBase<ZstdOutputStream> {

    /**
     *  @deprecated
     *  Use ZstdOutputStream() or ZstdOutputStream(level) and set the other params with the setters
     **/
    public ZstdOutputStream(OutputStream outStream, int level, boolean closeFrameOnFlush, boolean useChecksums) throws IOException {
        this(outStream);
        super.setCloseFrameOnFlush(closeFrameOnFlush);
        super.setLevel(level);
        super.setChecksum(useChecksums);
    }

    /**
     *  @deprecated
     *  Use ZstdOutputStream() or ZstdOutputStream(level) and set the other params with the setters
     **/
    public ZstdOutputStream(OutputStream outStream, int level, boolean closeFrameOnFlush) throws IOException {
        this(outStream);
        super.setCloseFrameOnFlush(closeFrameOnFlush);
        super.setLevel(level);
    }

    /**
     * create a new compressing OutputStream
     * @param outStream the stream to wrap
     * @param level the compression level
     */
    public ZstdOutputStream(OutputStream outStream, int level) throws IOException {
        super(outStream, level);
    }

    /**
     * create a new compressing OutputStream
     * @param outStream the stream to wrap
     */
    public ZstdOutputStream(OutputStream outStream) throws IOException {
        super(outStream, NoPool.INSTANCE);
    }

    /**
     * create a new compressing OutputStream
     * @param outStream the stream to wrap
     * @param bufferPool the pool to fetch and return buffers
     */
    public ZstdOutputStream(OutputStream outStream, BufferPool bufferPool, int level) throws IOException {
        super(outStream, bufferPool, level);
    }


    /**
     * create a new compressing OutputStream
     * @param outStream the stream to wrap
     * @param bufferPool the pool to fetch and return buffers
     */
    public ZstdOutputStream(OutputStream outStream, BufferPool bufferPool) throws IOException {
        super(outStream, bufferPool);
    }

    /**
     * Enable or disable class finalizers
     *
     * If finalizers are disabled the responsibility fir calling the `close` method is on the consumer.
     *
     * @param finalize default `true` - finalizers are enabled
     *
     * @deprecated
     * If you don't rely on finalizers, use `ZstdOutputStreamNoFinalizer` instead.
     */
    public void setFinalize(boolean finalize) {
    }

    @Override
    protected void finalize() throws Throwable {
        close();
    }
}

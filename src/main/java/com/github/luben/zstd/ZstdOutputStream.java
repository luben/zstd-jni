package com.github.luben.zstd;

import java.io.OutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import com.github.luben.zstd.util.Native;

/**
 * OutputStream filter that compresses the data using Zstd compression
 */
public class ZstdOutputStream extends FilterOutputStream{

    private ZstdOutputStreamNoFinalizer inner;

    /**
     *  @deprecated
     *  Use ZstdOutputStream() or ZstdOutputStream(level) and set the other params with the setters
     **/
    @Deprecated
    public ZstdOutputStream(OutputStream outStream, int level, boolean closeFrameOnFlush, boolean useChecksums) throws IOException {
        super(outStream);
        inner = new ZstdOutputStreamNoFinalizer(outStream, level);
        inner.setCloseFrameOnFlush(closeFrameOnFlush);
        inner.setChecksum(useChecksums);
    }

    /**
     *  @deprecated
     *  Use ZstdOutputStream() or ZstdOutputStream(level) and set the other params with the setters
     **/
    @Deprecated
    public ZstdOutputStream(OutputStream outStream, int level, boolean closeFrameOnFlush) throws IOException {
        super(outStream);
        inner = new ZstdOutputStreamNoFinalizer(outStream, level);
        inner.setCloseFrameOnFlush(closeFrameOnFlush);
    }

    /**
     * create a new compressing OutputStream
     * @param outStream the stream to wrap
     * @param level the compression level
     */
    public ZstdOutputStream(OutputStream outStream, int level) throws IOException {
        this(outStream, NoPool.INSTANCE);
        inner.setLevel(level);
    }

    /**
     * create a new compressing OutputStream
     * @param outStream the stream to wrap
     */
    public ZstdOutputStream(OutputStream outStream) throws IOException {
        this(outStream, NoPool.INSTANCE);
    }

    /**
     * create a new compressing OutputStream
     * @param outStream the stream to wrap
     * @param bufferPool the pool to fetch and return buffers
     */
    public ZstdOutputStream(OutputStream outStream, BufferPool bufferPool, int level) throws IOException {
        this(outStream, bufferPool);
        inner.setLevel(level);
    }

    /**
     * create a new compressing OutputStream
     * @param outStream the stream to wrap
     * @param bufferPool the pool to fetch and return buffers
     */
    public ZstdOutputStream(OutputStream outStream, BufferPool bufferPool) throws IOException {
        super(outStream);
        inner = new ZstdOutputStreamNoFinalizer(outStream, bufferPool);
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
    @Deprecated
    public void setFinalize(boolean finalize) {
    }

    @Override
    protected void finalize() throws Throwable {
        close();
    }

    public static long recommendedCOutSize() {
        return ZstdOutputStreamNoFinalizer.recommendedCOutSize();
    }

    /**
     * Enable checksums for the compressed stream.
     *
     * Default: false
     */
    public ZstdOutputStream setChecksum(boolean useChecksums) throws IOException {
        inner.setChecksum(useChecksums);
        return this;
    }

    /**
     * Set the compression level.
     *
     * Default: {@link Zstd#defaultCompressionLevel()}
     */
    public ZstdOutputStream setLevel(int level) throws IOException {
        inner.setLevel(level);
        return this;
    }

    /**
     * Set the Long Distance Matching.
     *
     * Values for windowLog outside the range 10-27 will disable and reset LDM
     */
    public ZstdOutputStream setLong(int windowLog) throws IOException {
        inner.setLong(windowLog);
        return this;
    }

    /**
     * Enable use of worker threads for parallel compression.
     *
     * Default: no worker threads.
     */
    public ZstdOutputStream setWorkers(int n) throws IOException {
        inner.setWorkers(n);
        return this;
    }

    /**
     * Enable closing the frame on flush.
     *
     * This will guarantee that it can be ready fully if the process crashes
     * before closing the stream. On the downside it will negatively affect
     * the compression ratio.
     *
     * Default: false.
     */
    public ZstdOutputStream setCloseFrameOnFlush(boolean closeOnFlush) {
        inner.setCloseFrameOnFlush(closeOnFlush);
        return this;
    }

    public ZstdOutputStream setDict(byte[] dict) throws IOException {
        inner.setDict(dict);
        return this;
    }

    public ZstdOutputStream setDict(ZstdDictCompress dict) throws IOException {
        inner.setDict(dict);
        return this;
    }

    public void write(byte[] src, int offset, int len) throws IOException {
        inner.write(src, offset, len);
    }

    public void write(int i) throws IOException {
        inner.write(i);
    }

    /**
     * Flushes the output
     */
    public void flush() throws IOException {
        inner.flush();
    }

    public void close() throws IOException {
        inner.close();
    }

}

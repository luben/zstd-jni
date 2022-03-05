package com.github.luben.zstd;

import com.github.luben.zstd.util.Native;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.nio.ByteBuffer;

public class ZstdDirectBufferCompressingStream implements Closeable, Flushable {

    static {
        Native.load();
    }

    ZstdDirectBufferCompressingStreamNoFinalizer inner;
    private boolean finalize;

    /**
     * This method should flush the buffer and either return the same buffer (but cleared) or a new buffer
     * that should be used from then on.
     * @param toFlush buffer that has to be flushed (or most cases, you want to call {@link ByteBuffer#flip()} first)
     * @return the new buffer to use, for most cases the same as the one passed in, after a call to {@link ByteBuffer#clear()}.
     */
    protected ByteBuffer flushBuffer(ByteBuffer toFlush) throws IOException {
        return toFlush;
    }

    public ZstdDirectBufferCompressingStream(ByteBuffer target, int level) throws IOException {
        inner = new ZstdDirectBufferCompressingStreamNoFinalizer(target, level) {
                @Override
                protected ByteBuffer flushBuffer(ByteBuffer toFlush) throws IOException {
                    return ZstdDirectBufferCompressingStream.this.flushBuffer(toFlush);
                }
            };
    }

    public static int recommendedOutputBufferSize() { return ZstdDirectBufferCompressingStreamNoFinalizer.recommendedOutputBufferSize(); }

    public synchronized ZstdDirectBufferCompressingStream setDict(byte[] dict) throws IOException {
        inner.setDict(dict);
        return this;
    }

    public synchronized ZstdDirectBufferCompressingStream setDict(ZstdDictCompress dict) throws IOException {
        inner.setDict(dict);
        return this;
    }

    /**
     * Enable or disable class finalizers
     *
     * If finalizers are disabled the responsibility fir calling the `close` method is on the consumer.
     *
     * @param finalize default `true` - finalizers are enabled
     */
    public void setFinalize(boolean finalize) {
        this.finalize = finalize;
    }

    public synchronized void compress(ByteBuffer source) throws IOException {
        inner.compress(source);
    }

    @Override
    public synchronized void flush() throws IOException {
        inner.flush();
    }

    @Override
    public synchronized void close() throws IOException {
        inner.close();
    }

    @Override
    protected void finalize() throws Throwable {
        if (finalize) {
            close();
        }
    }
}

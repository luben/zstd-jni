package com.github.luben.zstd;

import com.github.luben.zstd.util.Native;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;

public class ZstdBufferDecompressingStream implements Closeable {

    static {
        Native.load();
    }

    /**
     * Override this method in case the byte buffer passed to the constructor might not contain the full compressed stream
     *
     * @param toRefill current buffer
     * @return either the current buffer (but refilled and flipped if there was new content) or a new buffer.
     */
    protected ByteBuffer refill(ByteBuffer toRefill) {
        return toRefill;
    }

    private final ZstdBufferDecompressingStreamNoFinalizer inner;
    private boolean finalize = true;

    public ZstdBufferDecompressingStream(ByteBuffer source) {
        inner = new ZstdBufferDecompressingStreamNoFinalizer(source) {
            @Override
            protected ByteBuffer refill(ByteBuffer toRefill) {
                return ZstdBufferDecompressingStream.this.refill(toRefill);
            }
        };
    }

    /**
     * Enable or disable class finalizers
     * <p>
     * If finalizers are disabled the responsibility fir calling the `close` method is on the consumer.
     *
     * @param finalize default `true` - finalizers are enabled
     */
    public void setFinalize(boolean finalize) {
        this.finalize = finalize;
    }

    public synchronized boolean hasRemaining() {
        return inner.hasRemaining();
    }

    public static int recommendedTargetBufferSize() {
        return ZstdBufferDecompressingStreamNoFinalizer.recommendedTargetBufferSize();
    }

    public synchronized ZstdBufferDecompressingStream setDict(byte[] dict) throws IOException {
        inner.setDict(dict);
        return this;
    }

    public synchronized ZstdBufferDecompressingStream setDict(ZstdDictDecompress dict) throws IOException {
        inner.setDict(dict);
        return this;
    }

    public ZstdBufferDecompressingStream setLongMax(int windowLogMax) throws IOException {
        inner.setLongMax(windowLogMax);
        return this;
    }


    public synchronized int read(ByteBuffer target) throws IOException {
        return inner.read(target);
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

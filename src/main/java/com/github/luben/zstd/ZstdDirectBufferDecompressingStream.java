package com.github.luben.zstd;

import com.github.luben.zstd.util.Native;
import org.jetbrains.annotations.NotNull;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;

public class ZstdDirectBufferDecompressingStream implements Closeable {

    static {
        Native.load();
    }

    /**
     * Override this method in case the byte buffer passed to the constructor might not contain the full compressed stream
     * @param toRefill current buffer
     * @return either the current buffer (but refilled and flipped if there was new content) or a new buffer.
     */
    protected @NotNull ByteBuffer refill(@NotNull ByteBuffer toRefill) {
        return toRefill;
    }

    private ZstdDirectBufferDecompressingStreamNoFinalizer inner;
    private boolean finalize = true;

    public ZstdDirectBufferDecompressingStream(@NotNull ByteBuffer source) {
        inner = new ZstdDirectBufferDecompressingStreamNoFinalizer(source) {
                @Override
                protected @NotNull ByteBuffer refill(@NotNull ByteBuffer toRefill) {
                    return ZstdDirectBufferDecompressingStream.this.refill(toRefill);
                }
            };
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

    public synchronized boolean hasRemaining() {
        return inner.hasRemaining();
    }

    public static int recommendedTargetBufferSize() {
        return ZstdDirectBufferDecompressingStreamNoFinalizer.recommendedTargetBufferSize();
    }

    public synchronized @NotNull ZstdDirectBufferDecompressingStream setDict(byte @NotNull [] dict) throws IOException {
        inner.setDict(dict);
        return this;
    }

    public synchronized @NotNull ZstdDirectBufferDecompressingStream setDict(@NotNull ZstdDictDecompress dict) throws IOException {
        inner.setDict(dict);
        return this;
    }

    public @NotNull ZstdDirectBufferDecompressingStream setLongMax(int windowLogMax) throws IOException {
        inner.setLongMax(windowLogMax);
        return this;
    }


    public synchronized int read(@NotNull ByteBuffer target) throws IOException {
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

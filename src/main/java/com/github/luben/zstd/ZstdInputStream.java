package com.github.luben.zstd;

import org.jetbrains.annotations.NotNull;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * InputStream filter that decompresses the data provided
 * by the underlying InputStream using Zstd compression.
 *
 * It does not support mark/reset methods
 */

public class ZstdInputStream extends FilterInputStream {

    private @NotNull ZstdInputStreamNoFinalizer inner;

    /**
     * create a new decompressing InputStream
     * @param inStream the stream to wrap
     */
    public ZstdInputStream(@NotNull InputStream inStream) throws IOException {
        super(inStream);
        inner = new ZstdInputStreamNoFinalizer(inStream);
    }

    /**
     * create a new decompressing InputStream
     * @param inStream the stream to wrap
     * @param bufferPool the pool to fetch and return buffers
     */
    public ZstdInputStream(@NotNull InputStream inStream, @NotNull BufferPool bufferPool) throws IOException {
        super(inStream);
        inner = new ZstdInputStreamNoFinalizer(inStream, bufferPool);
    }

    /**
     * Enable or disable class finalizers
     *
     * @param finalize default `true` - finalizers are enabled
     *
     * @deprecated
     * If you don't rely on finalizers, use `ZstdInputStreamNoFinalizer` instead, instances of
     * `ZstdInputStream` will always try to close/release in the finalizer.
     */
    @Deprecated
    public void setFinalize(boolean finalize) {
    }

    @Override
    protected void finalize() throws Throwable {
        close();
    }

    public static long recommendedDInSize() {
        return ZstdInputStreamNoFinalizer.recommendedDInSize();
    }

    public static long recommendedDOutSize() {
        return ZstdInputStreamNoFinalizer.recommendedDOutSize();
    }

    /**
     * Don't break on unfinished frames
     *
     * Use case: decompressing files that are not yet finished writing and compressing
     */
    public @NotNull ZstdInputStream setContinuous(boolean b) {
        inner.setContinuous(b);
        return this;
    }

    public boolean getContinuous() {
        return inner.getContinuous();
    }

    public @NotNull ZstdInputStream setDict(byte @NotNull [] dict) throws IOException {
        inner.setDict(dict);
        return this;
    }
    public @NotNull ZstdInputStream setDict(@NotNull ZstdDictDecompress dict) throws IOException {
        inner.setDict(dict);
        return this;
    }

    public @NotNull ZstdInputStream setLongMax(int windowLogMax) throws IOException {
        inner.setLongMax(windowLogMax);
        return this;
    }

    /**
     * Enable or disable support for multiple dictionary references
     *
     * @param useMultiple Enables references table for DDict, so the DDict used for decompression will be
     *                    determined per the dictId in the frame, default: false
     */
    public @NotNull ZstdInputStream setRefMultipleDDicts(boolean useMultiple) throws IOException {
        inner.setRefMultipleDDicts(useMultiple);
        return this;
    }

    public int read(byte @NotNull [] dst, int offset, int len) throws IOException {
        return inner.read(dst, offset, len);
    }

    public int read() throws IOException {
        return inner.read();
    }

    public int available() throws IOException {
        return inner.available();
    }


    public long skip(long numBytes) throws IOException {
        return inner.skip(numBytes);
    }

    public boolean markSupported() {
        return inner.markSupported();
    }

    /**
     * Reset the decompression stream to read from a new {@link InputStream}.
     *
     * This reuses the existing native {@code ZSTD_DStream} context, including its
     * internal window buffer and any previously loaded dictionary. The native context
     * is reset via {@code ZSTD_DCtx_reset} with {@code ZSTD_reset_session_only},
     * which resets the session state without freeing or reallocating the window buffer
     * and without clearing loaded dictionaries or parameters.
     * If the next frame's window size fits within the existing buffer, no native memory
     * allocation occurs at all.
     *
     * Previously configured settings such as {@link #setContinuous(boolean)} and
     * loaded dictionaries are preserved across the reset.
     *
     * The previous {@link InputStream} is not closed; the caller is responsible
     * for its lifecycle.
     *
     * This is intended for pooling: instead of creating a new
     * {@code ZstdInputStream} (which calls {@code ZSTD_createDStream} +
     * {@code malloc}) for each compressed stream and then closing it (which calls
     * {@code ZSTD_freeDStream} + {@code free}), callers can keep a single instance
     * and call {@code reset()} with each new input stream.
     *
     * @param newInput the new compressed input stream to decompress
     * @return the old input stream
     * @throws IOException if the stream has been closed or the native reset fails
     */
    public synchronized InputStream resetSession(@NotNull InputStream newInput) throws IOException {
        return inner.resetSession(newInput);
    }

    public void close() throws IOException {
        inner.close();
    }
}

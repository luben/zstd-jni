package com.github.luben.zstd;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;

public abstract class BaseZstdBufferDecompressingStreamNoFinalizer implements Closeable {
    protected long stream;
    protected ByteBuffer source;
    protected boolean closed = false;
    private boolean finishedFrame = false;
    private boolean streamEnd = false;
    /**
     * This field is set by the native call to represent the number of bytes consumed from {@link #source} buffer.
     */
    private int consumed;
    /**
     * This field is set by the native call to represent the number of bytes produced into the target buffer.
     */
    private int produced;

    BaseZstdBufferDecompressingStreamNoFinalizer(ByteBuffer source) {
        this.source = source;
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

    /**
     * @return false if all data is processed and no more data is available from the {@link #source}
     */
    public boolean hasRemaining() {
        return !streamEnd && (source.hasRemaining() || !finishedFrame);
    }

    public BaseZstdBufferDecompressingStreamNoFinalizer setDict(byte[] dict) throws IOException {
        long size = Zstd.loadDictDecompress(stream, dict, dict.length);
        if (Zstd.isError(size)) {
            throw new ZstdIOException(size);
        }
        return this;
    }

    public BaseZstdBufferDecompressingStreamNoFinalizer setDict(ZstdDictDecompress dict) throws IOException {
        dict.acquireSharedLock();
        try {
            long size = Zstd.loadFastDictDecompress(stream, dict);
            if (Zstd.isError(size)) {
                throw new ZstdIOException(size);
            }
        } finally {
            dict.releaseSharedLock();
        }
        return this;
    }

    /**
     * Set the value of zstd parameter <code>ZSTD_d_windowLogMax</code>.
     *
     * @param windowLogMax window size in bytes
     * @return this instance of {@link BaseZstdBufferDecompressingStreamNoFinalizer}
     * @throws ZstdIOException if there is an error while setting the configuration natively.
     *
     * @see <a href="https://github.com/facebook/zstd/blob/0525d1cec64a8df749ff293ee476f616de79f7b0/lib/zstd.h#L606"> Zstd's ZSTD_d_windowLogMax parameter</a>
     */
    public BaseZstdBufferDecompressingStreamNoFinalizer setLongMax(int windowLogMax) throws IOException {
        long size = Zstd.setDecompressionLongMax(stream, windowLogMax);
        if (Zstd.isError(size)) {
            throw new ZstdIOException(size);
        }
        return this;
    }

    int readInternal(ByteBuffer target, boolean isDirectBufferRequired) throws IOException {
        if (closed) {
            throw new IOException("Stream closed");
        }
        if (streamEnd) {
            return 0;
        }

        long remaining = decompressStream(stream, target, target.position(), target.remaining(), source, source.position(), source.remaining());
        if (Zstd.isError(remaining)) {
            throw new ZstdIOException(remaining);
        }

        source.position(source.position() + consumed);
        target.position(target.position() + produced);

        if (!source.hasRemaining()) {
            source = refill(source);
            if (!isDirectBufferRequired && source.isDirect()) {
                throw new IllegalArgumentException("Source buffer should be a non-direct buffer");
            }
            if (isDirectBufferRequired && !source.isDirect()) {
                throw new IllegalArgumentException("Source buffer should be a direct buffer");
            }
        }

        finishedFrame = remaining == 0;
        if (finishedFrame) {
            // nothing left, so at end of the stream
            streamEnd = !source.hasRemaining();
        }

        return produced;
    }

    @Override
    public void close() {
        if (!closed) {
            try {
                freeDStream(stream);
            } finally {
                closed = true;
                source = null; // help GC with realizing the buffer can be released
            }
        }
    }

    /**
     * Reads the content of the de-compressed stream into the target buffer.
     * <p>This method will block until the chunk of compressed data stored in {@link #source} has been decompressed and
     * written into the target buffer. After each execution, this method will refill the {@link #source} buffer, using
     * {@link #refill(ByteBuffer)}.
     *<p>To read the full stream of decompressed data, this method should be called in a loop while {@link #hasRemaining()}
     * is <code>true</code>.
     *<p>The target buffer will be written starting from {@link ByteBuffer#position()}. The {@link ByteBuffer#position()}
     * of source and the target buffers will be modified to represent the data read and written respectively.
     *
     * @param target buffer to store the read bytes from uncompressed stream.
     * @return the number of bytes read into the target buffer.
     * @throws ZstdIOException if an error occurs while reading.
     * @throws IllegalArgumentException if provided source or target buffers are incorrectly configured.
     * @throws IOException if the stream is closed before reading.
     */
    public abstract int read(ByteBuffer target) throws IOException;

    abstract long createDStream();

    abstract long freeDStream(long stream);

    abstract long initDStream(long stream);

    abstract long decompressStream(long stream, ByteBuffer dst, int dstOffset, int dstSize, ByteBuffer src, int srcOffset, int srcSize);
}

package com.github.luben.zstd;

import com.github.luben.zstd.util.Native;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;

public class ZstdDirectBufferDecompressingStreamNoFinalizer implements Closeable {

    static {
        Native.load();
    }

    /**
     * Override this method in case the byte buffer passed to the constructor might not contain the full compressed stream
     * @param toRefill current buffer
     * @return either the current buffer (but refilled and flipped if there was new content) or a new buffer.
     */
    protected ByteBuffer refill(ByteBuffer toRefill) {
        return toRefill;
    }

    private ByteBuffer source;
    private final long stream;
    private boolean finishedFrame = false;
    private boolean closed = false;
    private boolean streamEnd = false;

    private static native long recommendedDOutSize();
    private static native long createDStream();
    private static native long freeDStream(long stream);
    private native long initDStream(long stream);
    private native long decompressStream(long stream, ByteBuffer dst, int dstOffset, int dstSize, ByteBuffer src, int srcOffset, int srcSize);

    public ZstdDirectBufferDecompressingStreamNoFinalizer(ByteBuffer source) {
        if (!source.isDirect()) {
            throw new IllegalArgumentException("Source buffer should be a direct buffer");
        }
        this.source = source;
        stream = createDStream();
        initDStream(stream);
    }

    public boolean hasRemaining() {
        return !streamEnd && (source.hasRemaining() || !finishedFrame);
    }

    public static int recommendedTargetBufferSize() {
        return (int) recommendedDOutSize();
    }

    public ZstdDirectBufferDecompressingStreamNoFinalizer setDict(byte[] dict) throws IOException {
        long size = Zstd.loadDictDecompress(stream, dict, dict.length);
        if (Zstd.isError(size)) {
            throw new ZstdIOException(size);
        }
        return this;
    }

    public ZstdDirectBufferDecompressingStreamNoFinalizer setDict(ZstdDictDecompress dict) throws IOException {
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

    public ZstdDirectBufferDecompressingStreamNoFinalizer setLongMax(int windowLogMax) throws IOException {
        long size = Zstd.setDecompressionLongMax(stream, windowLogMax);
        if (Zstd.isError(size)) {
            throw new ZstdIOException(size);
        }
        return this;
    }

    private int consumed;
    private int produced;
    public int read(ByteBuffer target) throws IOException {
        if (!target.isDirect()) {
            throw new IllegalArgumentException("Target buffer should be a direct buffer");
        }
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
            if (!source.isDirect()) {
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
            }
            finally {
                closed = true;
                source = null; // help GC with realizing the buffer can be released
            }
        }
    }
}

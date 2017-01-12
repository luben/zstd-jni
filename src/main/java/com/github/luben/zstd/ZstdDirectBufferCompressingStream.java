package com.github.luben.zstd;

import com.github.luben.zstd.util.Native;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.nio.ByteBuffer;

public abstract class ZstdDirectBufferCompressingStream implements Closeable, Flushable {

    static {
        Native.load();
    }

    private ByteBuffer target;
    private final long stream;

    /**
     * This method should flush the buffer and either return the same buffer (but cleared) or a new buffer
     * that should be used from then on.
     * @param toFlush buffer that has to be flushed (or most cases, you want to call {@link ByteBuffer#flip()} first)
     * @return the new buffer to use, for most cases the same as the one passed in, after a call to {@link ByteBuffer#clear()}.
     */
    protected abstract ByteBuffer flushBuffer(ByteBuffer toFlush) throws IOException;

    protected ZstdDirectBufferCompressingStream(ByteBuffer target, int level) throws IOException {
        this.target = target;
        stream = createCStream();
        int size = initCStream(stream, level);
        if (Zstd.isError(size)) {
            throw new IOException("Compression error: cannot create header: " + Zstd.getErrorName(size));
        }
    }

    public static int recommendedOutputBufferSize() { return (int)recommendedCOutSize(); }

    private int consumed = 0;
    private int produced = 0;
    private boolean closed = false;

    /* JNI methods */
    private static native long recommendedCOutSize();
    private static native long createCStream();
    private static native int  freeCStream(long ctx);
    private native int  initCStream(long ctx, int level);
    private native int  compressDirectByteBuffer(long ctx, ByteBuffer dst, int dstOffset, int dstSize, ByteBuffer src, int srcOffset, int srcSize);
    private native int  flushStream(long ctx, ByteBuffer dst, int dstOffset, int dstSize);
    private native int  endStream(long ctx, ByteBuffer dst, int dstOffset, int dstSize);


    public void compress(ByteBuffer source) throws IOException {
        if (!target.isDirect()) {
            throw new IllegalArgumentException("Target buffer should be a direct buffer");
        }
        if (closed) {
            throw new IOException("Stream closed");
        }
        while (source.hasRemaining()) {
            if (!target.hasRemaining()) {
                target = flushBuffer(target);
            }
            int processed = compressDirectByteBuffer(stream, target, target.position(), target.remaining(), source, source.position(), source.remaining());
            if (Zstd.isError(processed)) {
                throw new IOException("Compression error: " + Zstd.getErrorName(processed));
            }
            target.position(target.position() + produced);
            source.position(source.position() + consumed);
        }
    }

    @Override
    public void flush() throws IOException {
        if (!closed) {
            int needed;
            do {
                needed = flushStream(stream, target, target.position(), target.remaining());
                if (Zstd.isError(needed)) {
                    throw new IOException("Compression error: " + Zstd.getErrorName(needed));
                }
                target.position(target.position() + produced);
                target = flushBuffer(target);
            }
            while (needed > 0);
        }
    }

    @Override
    public void close() throws IOException {
        if (!closed) {
            try {
                int needed;
                do {
                    needed = endStream(stream, target, target.position(), target.remaining());
                    if (Zstd.isError(needed)) {
                        throw new IOException("Compression error: " + Zstd.getErrorName(needed));
                    }
                    target.position(target.position() + produced);
                    target = flushBuffer(target);
                } while (needed > 0);
            }
            finally {
                freeCStream(stream);
                closed = true;
            }
        }
    }

    @Override
    protected void finalize() throws Throwable {
        close();
    }
}

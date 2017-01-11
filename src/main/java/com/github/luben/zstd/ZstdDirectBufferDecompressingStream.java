package com.github.luben.zstd;

import com.github.luben.zstd.util.Native;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;

public class ZstdDirectBufferDecompressingStream implements Closeable {

    static {
        Native.load();
    }

    private final ByteBuffer source;
    private final long stream;
    private boolean finishedFrame = false;
    private boolean closed = false;

    private static native int recommendedDOutSize();
    private static native long createDStream();
    private static native int  freeDStream(long stream);
    private native int initDStream(long stream);
    private native long decompressStream(long stream, ByteBuffer dst, int dstOffset, int dstSize, ByteBuffer src, int srcOffset, int srcSize);

    public ZstdDirectBufferDecompressingStream(ByteBuffer source) {
        if (!source.isDirect()) {
            throw new IllegalArgumentException("Source buffer should be a direct buffer");
        }
        this.source = source;
        stream = createDStream();
        initDStream(stream);
    }

    public boolean hasRemaining() {
        return source.hasRemaining() || !finishedFrame;
    }

    public static int recommendedTargetBufferSize() {
        return (int) recommendedDOutSize();
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

        long remaining = decompressStream(stream, target, target.position(), target.remaining(), source, source.position(), source.remaining());
        if (Zstd.isError(remaining)) {
            throw new IOException(Zstd.getErrorName(remaining));
        }

        source.position(source.position() + consumed);
        target.position(target.position() + produced);

        finishedFrame = remaining == 0;
        if (finishedFrame && source.hasRemaining()) {
            // finished a frame and there is more stuff to read
            // so let's initialize for the next frame
            long size = initDStream(stream);
            if (Zstd.isError(size)) {
                throw new IOException("Decompression error: " + Zstd.getErrorName(size));
            }
        }

        return produced;
    }


    @Override
    public void close() throws IOException {
        if (!closed) {
            try {
                freeDStream(stream);
            }
            finally {
                closed = true;
            }
        }
    }

    @Override
    protected void finalize() throws Throwable {
        if (!closed) {
            freeDStream(stream);
        }
    }
}

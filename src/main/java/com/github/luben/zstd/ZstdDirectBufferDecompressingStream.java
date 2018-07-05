package com.github.luben.zstd;

import com.github.luben.zstd.util.Native;

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
    protected ByteBuffer refill(ByteBuffer toRefill) {
        return toRefill;
    }

    private ByteBuffer source;
    private final long stream;
    private boolean finishedFrame = false;
    private boolean closed = false;
    private boolean streamEnd = false;
    private boolean initialized = false;
    private byte[] dict = null;
    private ZstdDictDecompress fastDict = null;

    private static native int recommendedDOutSize();
    private static native long createDStream();
    private static native int  freeDStream(long stream);
    private native int initDStream(long stream);
    private native int initDStreamWithDict(long stream, byte[] dict, int dict_size);
    private native int initDStreamWithFastDict(long stream, ZstdDictDecompress dict);
    private native long decompressStream(long stream, ByteBuffer dst, int dstOffset, int dstSize, ByteBuffer src, int srcOffset, int srcSize);

    public ZstdDirectBufferDecompressingStream(ByteBuffer source) {
        if (!source.isDirect()) {
            throw new IllegalArgumentException("Source buffer should be a direct buffer");
        }
        this.source = source;
        stream = createDStream();
    }

    public boolean hasRemaining() {
        return !streamEnd && (source.hasRemaining() || !finishedFrame);
    }

    public static int recommendedTargetBufferSize() {
        return (int) recommendedDOutSize();
    }

    public ZstdDirectBufferDecompressingStream setDict(byte[] dict) throws IOException {
        if (initialized) {
            throw new IOException("Change of parameter on initialized stream");
        }
        this.dict = dict;
        this.fastDict = null;
        return this;
    }

    public ZstdDirectBufferDecompressingStream setDict(ZstdDictDecompress dict) throws IOException {
        if (initialized) {
            throw new IOException("Change of parameter on initialized stream");
        }
        this.fastDict = dict;
        this.dict = null;
        return this;
    }

    private void initStream() throws IOException {
        int result = 0;
        if (fastDict != null) {
            result = initDStreamWithFastDict(stream, fastDict);
        } else if (dict != null) {
            result = initDStreamWithDict(stream, dict, dict.length);
        } else {
            result = initDStream(stream);
        }
        if (Zstd.isError(result)) {
            throw new IOException("Decompression error: " + Zstd.getErrorName(result));
        }
        initialized = true;
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
        if (!initialized) {
            initStream();
        }

        long remaining = decompressStream(stream, target, target.position(), target.remaining(), source, source.position(), source.remaining());
        if (Zstd.isError(remaining)) {
            throw new IOException(Zstd.getErrorName(remaining));
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
            if (source.hasRemaining()) {
                // finished a frame and there is more stuff to read
                // so let's initialize for the next frame
                initStream();
            } else {
                // nothing left, so at end of the stream
                streamEnd = true;
            }
        }

        return produced;
    }


    @Override
    public void close() throws IOException {
        if (!closed) {
            try {
                if (initialized) {
                    freeDStream(stream);
                }
            }
            finally {
                closed = true;
                initialized = false;
            }
        }
    }

    @Override
    protected void finalize() throws Throwable {
        if (!closed && initialized) {
            freeDStream(stream);
        }
        source = null; // help GC with realizing the buffer can be released
    }
}

package com.github.luben.zstd;

import com.github.luben.zstd.util.Native;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.nio.ByteBuffer;

public class ZstdDirectBufferCompressingStreamNoFinalizer implements Closeable, Flushable {

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
    protected ByteBuffer flushBuffer(ByteBuffer toFlush) throws IOException {
        return toFlush;
    }

    public ZstdDirectBufferCompressingStreamNoFinalizer(ByteBuffer target, int level) throws IOException {
        if (!target.isDirect()) {
            throw new IllegalArgumentException("Target buffer should be a direct buffer");
        }
        this.target = target;
        this.level = level;
        stream = createCStream();
    }

    public static int recommendedOutputBufferSize() { return (int)recommendedCOutSize(); }

    private int consumed = 0;
    private int produced = 0;
    private boolean closed = false;
    private boolean initialized = false;
    private int level = Zstd.defaultCompressionLevel();
    private byte[] dict = null;
    private ZstdDictCompress fastDict = null;

    /* JNI methods */
    private static native long recommendedCOutSize();
    private static native long createCStream();
    private static native long  freeCStream(long ctx);
    private native long initCStream(long ctx, int level);
    private native long initCStreamWithDict(long ctx, byte[] dict, int dict_size, int level);
    private native long initCStreamWithFastDict(long ctx, ZstdDictCompress dict);
    private native long compressDirectByteBuffer(long ctx, ByteBuffer dst, int dstOffset, int dstSize, ByteBuffer src, int srcOffset, int srcSize);
    private native long flushStream(long ctx, ByteBuffer dst, int dstOffset, int dstSize);
    private native long endStream(long ctx, ByteBuffer dst, int dstOffset, int dstSize);

    public ZstdDirectBufferCompressingStreamNoFinalizer setDict(byte[] dict) {
        if (initialized) {
            throw new IllegalStateException("Change of parameter on initialized stream");
        }
        this.dict = dict;
        this.fastDict = null;
        return this;
    }

    public ZstdDirectBufferCompressingStreamNoFinalizer setDict(ZstdDictCompress dict) {
        if (initialized) {
            throw new IllegalStateException("Change of parameter on initialized stream");
        }
        this.dict = null;
        this.fastDict = dict;
        return this;
    }

    public void compress(ByteBuffer source) throws IOException {
        if (!source.isDirect()) {
            throw new IllegalArgumentException("Source buffer should be a direct buffer");
        }
        if (closed) {
            throw new IOException("Stream closed");
        }
        if (!initialized) {
            long result = 0;
            ZstdDictCompress fastDict = this.fastDict;
            if (fastDict != null) {
                fastDict.acquireSharedLock();
                try {
                    result = initCStreamWithFastDict(stream, fastDict);
                } finally {
                    fastDict.releaseSharedLock();
                }
            } else if (dict != null) {
                result = initCStreamWithDict(stream, dict, dict.length, level);
            } else {
                result = initCStream(stream, level);
            }
            if (Zstd.isError(result)) {
                throw new ZstdIOException(result);
            }
            initialized = true;
        }
        while (source.hasRemaining()) {
            if (!target.hasRemaining()) {
                target = flushBuffer(target);
                if (!target.isDirect()) {
                    throw new IllegalArgumentException("Target buffer should be a direct buffer");
                }
                if (!target.hasRemaining()) {
                    throw new IOException("The target buffer has no more space, even after flushing, and there are still bytes to compress");
                }
            }
            long result = compressDirectByteBuffer(stream, target, target.position(), target.remaining(), source, source.position(), source.remaining());
            if (Zstd.isError(result)) {
                throw new ZstdIOException(result);
            }
            target.position(target.position() + produced);
            source.position(source.position() + consumed);
        }
    }

    @Override
    public void flush() throws IOException {
        if (closed) {
            throw new IOException("Already closed");
        }
        if (initialized) {
            long needed;
            do {
                needed = flushStream(stream, target, target.position(), target.remaining());
                if (Zstd.isError(needed)) {
                    throw new ZstdIOException(needed);
                }
                target.position(target.position() + produced);
                target = flushBuffer(target);
                if (!target.isDirect()) {
                    throw new IllegalArgumentException("Target buffer should be a direct buffer");
                }
                if (needed > 0 && !target.hasRemaining()) {
                    // don't check on the first iteration of the loop
                    throw new IOException("The target buffer has no more space, even after flushing, and there are still bytes to compress");
                }
            }
            while (needed > 0);
        }
    }

    @Override
    public void close() throws IOException {
        if (!closed) {
            try {
                if (initialized) {
                    long needed;
                    do {
                        needed = endStream(stream, target, target.position(), target.remaining());
                        if (Zstd.isError(needed)) {
                            throw new ZstdIOException(needed);
                        }
                        target.position(target.position() + produced);
                        target = flushBuffer(target);
                        if (!target.isDirect()) {
                            throw new IllegalArgumentException("Target buffer should be a direct buffer");
                        }
                        if (needed > 0 && !target.hasRemaining()) {
                            throw new IOException("The target buffer has no more space, even after flushing, and there are still bytes to compress");
                        }
                    } while (needed > 0);
                }
            }
            finally {
                freeCStream(stream);
                closed = true;
                initialized = false;
                target = null; // help GC with realizing the buffer can be released
            }
        }
    }
}

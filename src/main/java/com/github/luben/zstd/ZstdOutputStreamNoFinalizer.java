package com.github.luben.zstd;

import java.io.OutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import com.github.luben.zstd.util.Native;

/**
 * OutputStream filter that compresses the data using Zstd compression.
 *
 */

public class ZstdOutputStreamNoFinalizer extends FilterOutputStream {

    static {
        Native.load();
    }

    /* Opaque pointer to Zstd context object */
    private final long stream;
    private long srcPos = 0;
    private long dstPos = 0;
    private final BufferPool bufferPool;
    private final ByteBuffer dstByteBuffer;
    private final byte[] dst;
    private boolean isClosed = false;
    private static final int dstSize = (int) recommendedCOutSize();
    private boolean closeFrameOnFlush = false;
    private boolean frameClosed = true;

    /* JNI methods */
    public static native long recommendedCOutSize();
    private static native long createCStream();
    private static native int  freeCStream(long ctx);
    private native int resetCStream(long ctx);
    private native int compressStream(long ctx, byte[] dst, int dst_size, byte[] src, int src_size);
    private native int flushStream(long ctx, byte[] dst, int dst_size);
    private native int endStream(long ctx, byte[] dst, int dst_size);


    /**
     * create a new compressing OutputStream
     * @param outStream the stream to wrap
     * @param level the compression level
     */
    public ZstdOutputStreamNoFinalizer(OutputStream outStream, int level) throws IOException {
        this(outStream, NoPool.INSTANCE);
        Zstd.setCompressionLevel(this.stream, level);
    }

    /**
     * create a new compressing OutputStream
     * @param outStream the stream to wrap
     */
    public ZstdOutputStreamNoFinalizer(OutputStream outStream) throws IOException {
        this(outStream, NoPool.INSTANCE);
    }

    /**
     * create a new compressing OutputStream
     * @param outStream the stream to wrap
     * @param bufferPool the pool to fetch and return buffers
     */
    public ZstdOutputStreamNoFinalizer(OutputStream outStream, BufferPool bufferPool, int level) throws IOException {
        this(outStream, bufferPool);
        Zstd.setCompressionLevel(this.stream, level);
    }

    /**
     * create a new compressing OutputStream
     * @param outStream the stream to wrap
     * @param bufferPool the pool to fetch and return buffers
     */
    public ZstdOutputStreamNoFinalizer(OutputStream outStream, BufferPool bufferPool) throws IOException {
        super(outStream);
        // create compression context
        this.stream = createCStream();
        this.closeFrameOnFlush = false;
        this.bufferPool = bufferPool;
        this.dstByteBuffer = bufferPool.get(dstSize);
        if (this.dstByteBuffer == null) {
            throw new IOException("Cannot get ByteBuffer of size " + dstSize + " from the BufferPool");
        }
        this.dst = Zstd.extractArray(dstByteBuffer);
    }

    /**
     * Enable checksums for the compressed stream.
     *
     * Default: false
     */
    public synchronized ZstdOutputStreamNoFinalizer setChecksum(boolean useChecksums) throws IOException {
        if (!frameClosed) {
            throw new IOException("Change of parameter on initialized stream");
        }
        int size = Zstd.setCompressionChecksums(stream, useChecksums);
        if (Zstd.isError(size)) {
            throw new IOException("Compression param: " + Zstd.getErrorName(size));
        }
        return this;
    }

    /**
     * Set the compression level.
     *
     * Default: 3
     */
    public synchronized ZstdOutputStreamNoFinalizer setLevel(int level) throws IOException {
        if (!frameClosed) {
            throw new IOException("Change of parameter on initialized stream");
        }
        int size = Zstd.setCompressionLevel(stream, level);
        if (Zstd.isError(size)) {
            throw new IOException("Compression param: " + Zstd.getErrorName(size));
        }
        return this;
    }

    /**
     * Enable Long Distance Matching and set the Window size Log.
     *
     * Setting windowLog greater than 27 will result in a stream that is not decompressable
     * by all decoders as it requires more memory.
     */
    public synchronized ZstdOutputStreamNoFinalizer setLong(int windowLog) throws IOException {
        if (!frameClosed) {
            throw new IOException("Change of parameter on initialized stream");
        }
        int size = Zstd.setCompressionLong(stream, windowLog);
        if (Zstd.isError(size)) {
            throw new IOException("Compression param: " + Zstd.getErrorName(size));
        }
        return this;
    }

    /**
     * Enable use of worker threads for parallel compression.
     *
     * Default: no worker threads.
     */
    public synchronized ZstdOutputStreamNoFinalizer setWorkers(int n) throws IOException {
        if (!frameClosed) {
            throw new IOException("Change of parameter on initialized stream");
        }
        int size = Zstd.setCompressionWorkers(stream, n);
        if (Zstd.isError(size)) {
            throw new IOException("Compression param: " + Zstd.getErrorName(size));
        }
        return this;
    }

    /**
     * Enable closing the frame on flush.
     *
     * This will guarantee that it can be ready fully if the process crashes
     * before closing the stream. On the downside it will negatively affect
     * the compression ratio.
     *
     * Default: false.
     */
    public synchronized ZstdOutputStreamNoFinalizer setCloseFrameOnFlush(boolean closeOnFlush) throws IOException {
        if (!frameClosed) {
            throw new IOException("Change of parameter on initialized stream");
        }
        this.closeFrameOnFlush = closeOnFlush;
        return this;
    }

    public synchronized ZstdOutputStreamNoFinalizer setDict(byte[] dict) throws IOException {
        if (!frameClosed) {
            throw new IOException("Change of parameter on initialized stream");
        }
        int size = Zstd.loadDictCompress(stream, dict, dict.length);
        if (Zstd.isError(size)) {
            throw new IOException("Compression param: " + Zstd.getErrorName(size));
        }
        return this;
    }

    public synchronized ZstdOutputStreamNoFinalizer setDict(ZstdDictCompress dict) throws IOException {
        if (!frameClosed) {
            throw new IOException("Change of parameter on initialized stream");
        }
        int size = Zstd.loadFastDictCompress(stream, dict);
        if (Zstd.isError(size)) {
            throw new IOException("Compression param: " + Zstd.getErrorName(size));
        }
        return this;
    }

    public synchronized void write(byte[] src, int offset, int len) throws IOException {
        if (isClosed) {
            throw new IOException("Stream closed");
        }
        if (frameClosed) {
            int size = resetCStream(this.stream);
            if (Zstd.isError(size)) {
                throw new IOException("Compression error: cannot create header: " + Zstd.getErrorName(size));
            }
            frameClosed = false;
        }
        int srcSize = offset + len;
        srcPos = offset;
        while (srcPos < srcSize) {
            int size = compressStream(stream, dst, dstSize, src, srcSize);
            if (Zstd.isError(size)) {
                throw new IOException("Compression error: " + Zstd.getErrorName(size));
            }
            if (dstPos > 0) {
                out.write(dst, 0, (int) dstPos);
            }
        }
    }

    public void write(int i) throws IOException {
        byte[] oneByte = new byte[1];
        oneByte[0] = (byte) i;
        write(oneByte, 0, 1);
    }

    /**
     * Flushes the output
     */
    public synchronized void flush() throws IOException {
        if (isClosed) {
            throw new IOException("Stream closed");
        }
        if (!frameClosed) {
            if (closeFrameOnFlush) {
                // compress the remaining output and close the frame
                int size;
                do {
                    size = endStream(stream, dst, dstSize);
                    if (Zstd.isError(size)) {
                        throw new IOException("Compression error: " + Zstd.getErrorName(size));
                    }
                    out.write(dst, 0, (int) dstPos);
                } while (size > 0);
                frameClosed = true;
            } else {
                // compress the remaining input
                int size;
                do {
                    size = flushStream(stream, dst, dstSize);
                    if (Zstd.isError(size)) {
                        throw new IOException("Compression error: " + Zstd.getErrorName(size));
                    }
                    out.write(dst, 0, (int) dstPos);
                } while (size > 0);
            }
            out.flush();
        }
    }

    public synchronized void close() throws IOException {
        if (isClosed) {
            return;
        }
        try {
            if (!frameClosed) {
                // compress the remaining input and close the frame
                int size;
                do {
                    size = endStream(stream, dst, dstSize);
                    if (Zstd.isError(size)) {
                        throw new IOException("Compression error: " + Zstd.getErrorName(size));
                    }
                    out.write(dst, 0, (int) dstPos);
                } while (size > 0);
            }
            out.close();
        } finally {
            // release the resources even if underlying stream throw an exception
            isClosed = true;
            bufferPool.release(dstByteBuffer);
            freeCStream(stream);
        }
    }
}

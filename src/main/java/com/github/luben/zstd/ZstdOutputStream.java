package com.github.luben.zstd;

import java.nio.ByteBuffer;
import java.io.OutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;

import com.github.luben.zstd.util.Native;
import com.github.luben.zstd.Zstd;

/**
 * OutputStream filter that compresses the data using Zstd compression
 *
 * Caveat: flush method with flush only the completed blocks of
 * 128KB size to the output
 *
 */
public class ZstdOutputStream extends FilterOutputStream {

    static {
        Native.load();
    }

    /* Opaque pointer to Zstd context object */
    private long stream;
    private long srcPos = 0;
    private long dstPos = 0;
    private byte[] dst = null;
    private static final int dstSize = (int) recommendedCOutSize();

    /* JNI methods */
    private static native long recommendedCOutSize();
    private static native long createCStream();
    private static native int  freeCStream(long ctx);
    private native int  initCStream(long ctx, int level);
    private native int  compressStream(long ctx, byte[] dst, int dst_size, byte[] src, int src_size);
    private native int  flushStream(long ctx, byte[] dst, int dst_size);
    private native int  endStream(long ctx, byte[] dst, int dst_size);


    /* The constuctor */
    public ZstdOutputStream(OutputStream outStream, int level) throws IOException {
        // FilterOutputStream constructor
        super(outStream);

        // create compression context
        stream = createCStream();
        dst = new byte[(int) dstSize];
        int size = initCStream(stream, level);
        if (Zstd.isError(size)) {
            throw new IOException("Compression error: cannot create header: " + Zstd.getErrorName(size));
        }
    }

    public ZstdOutputStream(OutputStream outStream) throws IOException {
        this(outStream, 1);
    }

    public void write(byte[] src, int offset, int len) throws IOException {
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
    public void flush() throws IOException {
        // compress the remaining input
        int size = flushStream(stream, dst, dstSize);
        if (Zstd.isError(size)) {
            throw new IOException("Compression error: " + Zstd.getErrorName(size));
        }
        out.write(dst, 0, (int) dstPos);
        out.flush();
    }

    public void close() throws IOException {
        // compress the remaining input and close the frame
        int size = endStream(stream, dst, dstSize);
        if (Zstd.isError(size)) {
            throw new IOException("Compression error: " + Zstd.getErrorName(size));
        }
        out.write(dst, 0, (int) dstPos);

        // release the resources
        freeCStream(stream);
        out.close();
    }
}

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
    private long ctx;
    private long srcPtr = 0;
    private long dstPtr = 0;
    private byte[] dst = null;
    private static final long dstSize = recommendedCOutSize();

    /* JNI methods */
    private static native long recommendedCOutSize();
    private static native long createCCtx();
    private static native int  freeCCtx(long ctx);
    private native int  compressInit(long ctx, int level);
    private native int  compressContinue(long ctx, byte[] dst, byte[] src, int src_offset);
    private native int  compressFlush(long ctx, byte[] dst);
    private native int  compressEnd(long ctx, byte[] dst);


    /* The constuctor */
    public ZstdOutputStream(OutputStream outStream, int level) throws IOException {
        // FilterOutputStream constructor
        super(outStream);

        // create compression context
        ctx = createCCtx();
        dst = new byte[(int) dstSize];
        int size = compressInit(ctx, level);
        if (Zstd.isError(size)) {
            throw new IOException("Compression error: cannot create header: " + Zstd.getErrorName(size));
        }
    }

    public ZstdOutputStream(OutputStream outStream) throws IOException {
        this(outStream, 1);
    }


    public void write(byte[] src, int offset, int len) throws IOException {
        while (len > 0) {
            srcPtr = (long) len;
            dstPtr = dstSize;
            long size = compressContinue(ctx, dst, src, offset);
            if (Zstd.isError(size)) {
                throw new IOException("Compression error: " + Zstd.getErrorName(size));
            }
            if (dstPtr > 0) {
                out.write(dst, 0, (int) dstPtr);
            }
            offset += srcPtr;
            len -= srcPtr;
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
        dstPtr = dstSize;
        // compress the remaining input
        int size = compressFlush(ctx, dst);
        if (Zstd.isError(size)) {
            throw new IOException("Compression error: " + Zstd.getErrorName(size));
        }
        out.write(dst, 0, (int) dstPtr);
        out.flush();
    }

    public void close() throws IOException {
        dstPtr = dstSize;
        // compress the remaining input
        int size = compressEnd(ctx, dst);
        if (Zstd.isError(size)) {
            throw new IOException("Compression error: " + Zstd.getErrorName(size));
        }
        out.write(dst, 0, (int) dstPtr);

        // release the resources
        freeCCtx(ctx);
        out.close();
    }
}

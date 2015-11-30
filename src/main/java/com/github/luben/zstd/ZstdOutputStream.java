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

    /* Some constants, there is provision for variable blocksize in the future */
    private final static int blockSize = 128*1024; //128 KB
    private static int iBuffSize = 0;
    private static int oBuffSize = 0;

    private byte[] iBuff = null;
    private byte[] oBuff = null;
    private int iPos  = 0;
    private int iEnd  = 0;

    /* JNI methods */
    private static native long createCCtx();
    private static native long freeCCtx(long ctx);
    private static native int  findIBuffSize(int level);
    private static native long compressBegin(long ctx, byte[] dst, long dstSize, int level);
    private static native long compressContinue(long ctx, byte[] dst, long dstSize, byte[] src, long srcOffset, long srcSize);
    private static native long compressEnd(long ctx, byte[] dst, long dstSize);

    /* The constuctor */
    public ZstdOutputStream(OutputStream outStream, int level) throws IOException {
        // FilterOutputStream constructor
        super(outStream);

        // create compression context
        ctx = createCCtx();

        // find buffer sizes
        iBuffSize = findIBuffSize(level);
        oBuffSize = (int) Zstd.compressBound(blockSize) + 6;

        /* allocate memory */
        iBuff = ByteBuffer.allocate(iBuffSize).array();
        oBuff = ByteBuffer.allocate(oBuffSize).array();
        if (iBuff == null || oBuff == null) {
            throw new IOException("Error allocating the buffers");
        }
        /* write header */
        long size = compressBegin(ctx, oBuff, oBuffSize, level);
        if (Zstd.isError(size)) {
            throw new IOException("Compression error: cannot create header: " + Zstd.getErrorName(size));
        }
        out.write(oBuff, 0, (int) size);
    }
    public ZstdOutputStream(OutputStream outStream) throws IOException {
        this(outStream, 1);
    }


    public void write(byte[] src, int offset, int len) throws IOException {
        while (len > 0) {
            int free = iBuffSize - iEnd;
            if (len < free) {
                System.arraycopy(src, offset, iBuff, iEnd, len);
                iEnd    += len;
                len     =  0;
            } else {
                System.arraycopy(src, offset, iBuff, iEnd, free);
                iEnd    =  iBuffSize;
                offset  += free;
                len     -= free;
            }
            while (iEnd >= iPos + blockSize) {
                long size = compressContinue(ctx, oBuff, oBuffSize, iBuff, iPos, blockSize);
                if (Zstd.isError(size)) {
                    throw new IOException("Compression error: " + Zstd.getErrorName(size));
                }
                out.write(oBuff, 0, (int) size);
                iPos += blockSize;
                if (iPos == iBuffSize) {
                    iPos = 0;
                    iEnd = 0;
                }
            }
        }
    }

    /**
     * Flushes the output
     *
     * Caveat: it will flush only the completed blocks
     */
    public void flush() throws IOException {
        // Does it support smaller blocks in the middle of the stream?
        out.flush();
    }

    public void close() throws IOException {
        long size = 0;
        // compress the remaining input
        if (iPos != iEnd) {
            // compress
            size = compressContinue(ctx, oBuff, oBuffSize, iBuff, iPos, iEnd - iPos);
            if (Zstd.isError(size))
                throw new IOException("Compression error: " + Zstd.getErrorName(size));
            out.write(oBuff, 0, (int) size);
        }
        // wrap it
        size = compressEnd(ctx, oBuff, oBuffSize);
        out.write(oBuff, 0, (int) size);
        // release the resources
        freeCCtx(ctx);
        out.close();
    }
}

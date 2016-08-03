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
    private final static int blockSize = (int) Zstd.blockSizeMax();
    private final static int oBuffSize = (int) Zstd.compressBound(blockSize) + 1;
    private int iBuffSize = 0;

    private ByteBuffer iBuff = null;
    private byte[] oBuff = null;
    private int iPos  = 0;

    /* JNI methods */
    private static native long createCCtx();
    private static native int  freeCCtx(long ctx);
    private static native int  findIBuffSize(int level);
    private static native int  compressBegin(long ctx, int level);
    private static native int  compressContinue(long ctx, byte[] dst, long dstSize, ByteBuffer src, long srcOffset, long srcSize);
    private static native int  compressEnd(long ctx, byte[] dst, long dstSize, ByteBuffer src, long srcOffset, long srcSize);

    /* The constuctor */
    public ZstdOutputStream(OutputStream outStream, int level) throws IOException {
        // FilterOutputStream constructor
        super(outStream);

        // create compression context
        ctx = createCCtx();

        // find buffer sizes
        iBuffSize = findIBuffSize(level);

        /* allocate memory */
        iBuff = ByteBuffer.allocateDirect(iBuffSize);
        oBuff = new byte[oBuffSize];
        if (iBuff == null || oBuff == null) {
            throw new IOException("Error allocating the buffers");
        }
        /* write header */
        int size = compressBegin(ctx, level);
        if (Zstd.isError(size)) {
            throw new IOException("Compression error: cannot create header: " + Zstd.getErrorName(size));
        }
        out.write(oBuff, 0, size);
    }

    public ZstdOutputStream(OutputStream outStream) throws IOException {
        this(outStream, 1);
    }


    public void write(byte[] src, int offset, int len) throws IOException {
        while (len > 0) {
            int free = iPos + blockSize - iBuff.position();
            if (len < free) {
                iBuff.put(src, offset, len);
                len     =  0;
            } else {
                iBuff.put(src, offset, free);
                offset  += free;
                len     -= free;
                // we have finished a block, now compress it
                long size = compressContinue(ctx, oBuff, oBuffSize, iBuff, iPos, blockSize);
                if (Zstd.isError(size)) {
                    throw new IOException("Compression error: " + Zstd.getErrorName(size));
                }
                out.write(oBuff, 0, (int) size);
                iPos += blockSize;
                // start from the beginning if we have reached the end
                if (iPos == iBuffSize) {
                    iPos = 0;
                    iBuff.position(0);
                }
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
     *
     * Caveat: it will flush only the completed blocks
     */
    public void flush() throws IOException {
        // Does it support smaller blocks in the middle of the stream?
        out.flush();
    }

    public void close() throws IOException {
        int size = 0;
        int iEnd = iBuff.position();
        // compress the remaining input
        if (iPos != iEnd) {
            // compress
            size = compressEnd(ctx, oBuff, oBuffSize, iBuff, iPos, iEnd - iPos);
            if (Zstd.isError(size))
                throw new IOException("Compression error: " + Zstd.getErrorName(size));
            out.write(oBuff, 0, (int) size);
        }
        // release the resources
        freeCCtx(ctx);
        out.close();
    }
}

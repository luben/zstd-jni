package com.github.luben.zstd;

import java.nio.ByteBuffer;
import java.io.OutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;

import com.github.luben.zstd.util.Native;
import com.github.luben.zstd.Zstd;

/**
 * OutputStream filter that compresses the data using Zstd compression
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
    private boolean isClosed = false;
    private static final int dstSize = (int) recommendedCOutSize();
    private boolean closeFrameOnFlush;
    private boolean useChecksum;
    private boolean frameClosed = true;
    private int level;

    /* JNI methods */
    private static native long recommendedCOutSize();
    private static native long createCStream();
    private static native int  freeCStream(long ctx);
    private native int  initCStream(long ctx, int level, int checksum);
    private native int  compressStream(long ctx, byte[] dst, int dst_size, byte[] src, int src_size);
    private native int  flushStream(long ctx, byte[] dst, int dst_size);
    private native int  endStream(long ctx, byte[] dst, int dst_size);


    /* The constuctor */
    public ZstdOutputStream(OutputStream outStream, int level, boolean closeFrameOnFlush, boolean useChecksum) throws IOException {
        // FilterOutputStream constructor
        super(outStream);
        this.closeFrameOnFlush = closeFrameOnFlush;
        this.level = level;
        this.useChecksum = useChecksum;

        // create compression context
        stream = createCStream();
        dst = new byte[(int) dstSize];
    }

    public ZstdOutputStream(OutputStream outStream, int level, boolean closeFrameOnFlush) throws IOException {
        this(outStream, level, closeFrameOnFlush, false);
    }

    public ZstdOutputStream(OutputStream outStream, int level) throws IOException {
        this(outStream, level, false);
    }

    public ZstdOutputStream(OutputStream outStream) throws IOException {
        this(outStream, 3, false);
    }

    public void write(byte[] src, int offset, int len) throws IOException {
        if (isClosed) {
            throw new IOException("Stream closed");
        }
        if (frameClosed) {
            // open the next frame
            int size = initCStream(stream, level, useChecksum ? 1 : 0);
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
    public void flush() throws IOException {
        if (isClosed) {
            throw new IOException("Stream closed");
        }
        if (!frameClosed) {
            if (closeFrameOnFlush) {
                // compress the remaining output and close the frame
                int size = endStream(stream, dst, dstSize);
                if (Zstd.isError(size)) {
                    throw new IOException("Compression error: " + Zstd.getErrorName(size));
                }
                frameClosed = true;
            } else {
                // compress the remaining input
                int size = flushStream(stream, dst, dstSize);
                if (Zstd.isError(size)) {
                    throw new IOException("Compression error: " + Zstd.getErrorName(size));
                }
            }
            out.write(dst, 0, (int) dstPos);
            out.flush();
        }
    }

    public void close() throws IOException {
        if (isClosed) {
            return;
        }
        if (!frameClosed) {
            // compress the remaining input and close the frame
            int size = endStream(stream, dst, dstSize);
            if (Zstd.isError(size)) {
                throw new IOException("Compression error: " + Zstd.getErrorName(size));
            }
            out.write(dst, 0, (int) dstPos);
        }
        // release the resources
        freeCStream(stream);
        out.close();
        isClosed = true;
    }

    @Override
    protected void finalize() throws Throwable {
        close();
    }
}

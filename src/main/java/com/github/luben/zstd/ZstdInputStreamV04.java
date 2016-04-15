package com.github.luben.zstd;

import java.nio.ByteBuffer;
import java.io.InputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.lang.IndexOutOfBoundsException;

import com.github.luben.zstd.util.Native;
import com.github.luben.zstd.Zstd;

/**
 * InputStream filter that decompresses the data provided
 * by the underlying InputStream using Zstd compression.
 *
 * It does not support mark/reset methods
 *
 */

public class ZstdInputStreamV04 extends FilterInputStream {

    static {
        Native.load();
    }

    // Opaque pointer to Zstd context object
    private long ctx;

    private final static int blockSize = 128*1024; //128 KB
    /* buffer sizes */
    private final static int iBuffSize = blockSize;
    private int oBuffSize = -1;

    // The decompression buffer
    private ByteBuffer oBuff = null;
    private int oPos   = 0;
    private int oEnd   = 0;

    // The input buffer
    private byte[] iBuff  = null;

    // JNI methods
    private static native long createDCtx();
    private static native long freeDCtx(long ctx);
    private static native int  findOBuffSize(byte[] src, long srcSize);
    private static native long nextSrcSizeToDecompress(long ctx);
    private static native long decompressContinue(long ctx, ByteBuffer dst, long dstOffset, long dstSize, byte[] src, long srcSize);

    // The main constuctor
    public ZstdInputStreamV04(InputStream inStream, byte[] header, int iPos) throws IOException {
        // FilterInputStream constructor
        super(inStream);

        // create decompression context
        ctx = createDCtx();

        /* allocate input buffer memory */
        iBuff = new byte[iBuffSize];
        if (iBuff == null) {
            throw new IOException("Error allocating the input buffer of size " + iBuffSize);
        }

        // find the size of the output buffer
        while (oBuffSize < 0) {
            oBuffSize = findOBuffSize(header, iPos);
            if (oBuffSize < 0) {
                iPos += in.read(header, iPos, -oBuffSize - iPos);
            }
        }

        // allocate the output buffer
        oBuff = ByteBuffer.allocateDirect(oBuffSize);
        if (oBuff == null) {
            throw new IOException("Error allocating the output buffers of size " + oBuffSize);
        }

        // consume the frame header that was read by the findBlockSize / findOBuffSize
        long toRead = nextSrcSizeToDecompress(ctx);
        long decoded = decompressContinue(ctx, oBuff, oPos, oBuffSize - oPos, header, toRead);
        if (Zstd.isError(decoded)) {
            throw new IOException("Decode Error: " + Zstd.getErrorName(decoded));
        }
        oEnd += decoded;
    }

    public int read(byte[] dst, int offset, int len) throws IOException {
        // guard agains buffer overflows
        if (len > dst.length - offset) {
            throw new IndexOutOfBoundsException("Requested lenght " +len  +
                " exceeds the buffer size " + dst.length + " from offset " + offset);
        }
        // the buffer is empty
        while (oEnd == oPos) {
            int iPos = 0;
            long toRead = nextSrcSizeToDecompress(ctx);

            // Reached end of stream (-1) if there is anything more to read
            if (toRead == 0) {
                return -1;
            }

            // Start from the beginning if we have reached the end of the oBuff
            if (oBuffSize - oPos < blockSize) {
                oPos = 0;
                oEnd = 0;
            }

            // in.read is not guaranteed to return the requested size in one go
            while (iPos < toRead) {
                long read = in.read(iBuff, iPos, (int) toRead - iPos);
                if (read > 0) {
                    iPos += read;
                } else {
                    throw new IOException("Read error or truncated source");
                }
            }

            // Decode
            long decoded = decompressContinue(ctx, oBuff, oPos, oBuffSize - oPos, iBuff, iPos);
            if (Zstd.isError(decoded)) {
                throw new IOException("Decode Error: " + Zstd.getErrorName(decoded));
            }
            oEnd += (int) decoded;
        }
        // return size is min(requested, available)
        int size = Math.min(len, oEnd - oPos);
        oBuff.position(oPos);
        oBuff.get(dst, offset, size);
        oPos += size;
        return size;
    }

    public int available() throws IOException {
        return oEnd - oPos;
    }

    /* we don't support mark/reset */
    public boolean markSupported() {
        return false;
    }

    /* we can skip forward only inside the buffer*/
    public long skip(long n) {
        if (n <= oEnd - oPos) {
            oPos += n;
            return n;
        } else {
            long skip = oEnd - oPos;
            oPos = oEnd;
            return skip;
        }
    }

    public void close() throws IOException {
        freeDCtx(ctx);
        in.close();
    }
}

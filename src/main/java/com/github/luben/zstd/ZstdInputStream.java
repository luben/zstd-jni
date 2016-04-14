package com.github.luben.zstd;

import java.nio.ByteBuffer;
import java.io.InputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.util.Arrays;
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

public class ZstdInputStream extends FilterInputStream {

    static {
        Native.load();
    }

    // Opaque pointer to Zstd context object
    private long ctx;

    // read from the frame header
    private int blockSize = -1;
    private int oBuffSize = -1;

    // The decompression buffer
    private ByteBuffer oBuff = null;
    private int oPos   = 0;
    private int oEnd   = 0;

    // The input buffer
    private byte[] iBuff  = null;

    private static final int MAGIC_BASE = 0xFD2FB520;
    private int version = 0;

    // JNI methods
    private static native long createDCtx();
    private static native int  decompressBegin(long ctx);
    private static native int  freeDCtx(long ctx);
    private static native int  findBlockSize(byte[] src, long srcSize);
    private static native int  findOBuffSize(byte[] src, long srcSize);
    private static native int  nextSrcSizeToDecompress(long ctx);
    private static native int  decompressContinue(long ctx, ByteBuffer dst, long dstOffset, long dstSize, byte[] src, long srcOffset, long srcSize);

    // legacy v05
    private static native long createDCtx_v05();
    private static native int  decompressBegin_v05(long ctx);
    private static native int  freeDCtx_v05(long ctx);
    private static native int  findBlockSize_v05(byte[] src, long srcSize);
    private static native int  findOBuffSize_v05(byte[] src, long srcSize);
    private static native int  nextSrcSizeToDecompress_v05(long ctx);
    private static native int  decompressContinue_v05(long ctx, ByteBuffer dst, long dstOffset, long dstSize, byte[] src, long srcOffset, long srcSize);

    // The main constuctor
    public ZstdInputStream(InputStream inStream) throws IOException {
        // FilterInputStream constructor
        super(inStream);

        // allocate input buffer with max frame header size
        byte[] header = new byte[Zstd.frameHeaderSizeMax()];
        if (header == null) {
            throw new IOException("Error allocating the frame header buffer of size " + Zstd.frameHeaderSizeMax());
        }

        // find the ZSTD version
        int iPos = 0;
        while (iPos < 4) {
            iPos += in.read(header, iPos, 4 - iPos);
        }

        byte[] magic = Arrays.copyOfRange(header,0,4);
        version = java.nio.ByteBuffer.wrap(magic).order(java.nio.ByteOrder.LITTLE_ENDIAN).getInt() - MAGIC_BASE;

        // create decompression context
        switch (version) {
            case 6: ctx = createDCtx();
                    decompressBegin(ctx);
                    break;
            case 5: ctx = createDCtx_v05();
                    decompressBegin_v05(ctx);
                    break;
            default: throw new IOException("Unsupported version of Zstd " + version);
        }

        // find the block size
        while (blockSize < 0) {
            switch (version) {
                case 6: blockSize = findBlockSize(header, iPos);
                        break;
                case 5: blockSize = findBlockSize_v05(header, iPos);
                        break;
            }
            if (blockSize < 0) {
                iPos += in.read(header, iPos, -blockSize - iPos);
            }
        }

        // allocate the input buffer
        iBuff = new byte[blockSize];
        if (iBuff == null) {
            throw new IOException("Error allocating the input buffer of size " + blockSize);
        }

        // find the size of the output buffer
        while (oBuffSize < 0) {
            switch (version) {
                case 6: oBuffSize = findOBuffSize(header, iPos);
                        break;
                case 5: oBuffSize = findOBuffSize_v05(header, iPos);
                        break;
            }
            if (oBuffSize < 0) {
                iPos += in.read(header, iPos, -oBuffSize - iPos);
            }
        }

        // allocate the output buffer
        oBuff = ByteBuffer.allocateDirect(oBuffSize);
        if (oBuff == null) {
            throw new IOException("Error allocating the output buffers of size " + oBuffSize);
        }

        // consume the frame header(s) that was read by the findBlockSize / findOBuffSize
        long consumed = 0;
        while (consumed < iPos) {
            int toRead = 0;
            int decoded = 0;

            switch (version) {
                case 6: toRead = nextSrcSizeToDecompress(ctx);
                        decoded = decompressContinue(ctx, oBuff, oPos, oBuffSize - oPos, header, consumed, toRead);
                        break;
                case 5: toRead = nextSrcSizeToDecompress_v05(ctx);
                        decoded = decompressContinue_v05(ctx, oBuff, oPos, oBuffSize - oPos, header, consumed, toRead);
                        break;
            }
            if (Zstd.isError(decoded)) {
                throw new IOException("Decode Error: " + Zstd.getErrorName(decoded));
            }
            oEnd += decoded;
            consumed += toRead;
        }
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
            int toRead = 0;
            switch (version) {
                case 6: toRead = nextSrcSizeToDecompress(ctx);
                        break;
                case 5: toRead = nextSrcSizeToDecompress_v05(ctx);
                        break;
            }

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
                int read = in.read(iBuff, iPos, toRead - iPos);
                if (read > 0) {
                    iPos += read;
                } else {
                    throw new IOException("Read error or truncated source");
                }
            }

            // Decode
            int decoded = 0;
            switch (version) {
                case 6: decoded = decompressContinue(ctx, oBuff, oPos, oBuffSize - oPos, iBuff, 0, iPos);
                        break;
                case 5: decoded = decompressContinue_v05(ctx, oBuff, oPos, oBuffSize - oPos, iBuff, 0, iPos);
                        break;
            }

            if (Zstd.isError(decoded)) {
                throw new IOException("Decode Error: " + Zstd.getErrorName(decoded));
            }
            oEnd += decoded;
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
        switch (version) {
            case 6: freeDCtx(ctx);
                    break;
            case 5: freeDCtx_v05(ctx);
                    break;
        }
        in.close();
    }
}

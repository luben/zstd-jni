package com.github.luben.zstd;

import java.nio.ByteBuffer;
import java.io.InputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.lang.IndexOutOfBoundsException;

import com.github.luben.zstd.util.Native;
import com.github.luben.zstd.Zstd;
import com.github.luben.zstd.ZstdInputStreamV05;

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
    protected long ctx;

    // read from the frame header
    protected int blockSize = -1;
    protected int oBuffSize = -1;

    // The decompression buffer
    protected ByteBuffer oBuff = null;
    protected int oPos   = 0;
    protected int oEnd   = 0;

    // The input buffer
    protected byte[] iBuff  = null;

    protected static final int MAGIC_BASE = 0xFD2FB520;
    protected ZstdLegacyInputStream legacy = null;

    // JNI methods
    protected static native long createDCtx();
    protected static native int  decompressBegin(long ctx);
    protected static native int  freeDCtx(long ctx);
    protected static native int  findBlockSize(byte[] src, long srcSize);
    protected static native int  findOBuffSize(byte[] src, long srcSize);
    protected static native int  nextSrcSizeToDecompress(long ctx);
    protected static native int  decompressContinue(long ctx, ByteBuffer dst, long dstOffset, long dstSize, byte[] src, long srcOffset, long srcSize);

    // The main constuctor / legacy version dispatcher
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
        int version = java.nio.ByteBuffer.wrap(magic).order(java.nio.ByteOrder.LITTLE_ENDIAN).getInt() - MAGIC_BASE;

        switch (version) {
            case 7: init(inStream, header, iPos);
                    break;
            case 6: legacy = new ZstdInputStreamV06(inStream, header, iPos);
                    break;
            case 5: legacy = new ZstdInputStreamV05(inStream, header, iPos);
                    break;
            case 4: legacy = new ZstdInputStreamV04(inStream, header, iPos);
                    break;
            default: throw new IOException("Legacy version " + version + " is not supported");
        }
    }

    // The main initialization logic
    private void init(InputStream inStream, byte[] header, int iPos) throws IOException {

        // create decompression context
        ctx = createDCtx();
        decompressBegin(ctx);

        // find the block size
        while (blockSize < 0) {
            blockSize = findBlockSize(header, iPos);
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

        // consume the frame header(s) that was read by the findBlockSize / findOBuffSize
        long consumed = 0;
        while (consumed < iPos) {
            int toRead = nextSrcSizeToDecompress(ctx);
            int decoded = decompressContinue(ctx, oBuff, oPos, oBuffSize - oPos, header, consumed, toRead);
            if (Zstd.isError(decoded)) {
                throw new IOException("Decode Error: " + Zstd.getErrorName(decoded));
            }
            oEnd += decoded;
            consumed += toRead;
        }
    }

    public int read(byte[] dst, int offset, int len) throws IOException {
        if (legacy != null) return legacy.read(dst, offset, len);
        // guard agains buffer overflows
        if (len > dst.length - offset) {
            throw new IndexOutOfBoundsException("Requested lenght " +len  +
                " exceeds the buffer size " + dst.length + " from offset " + offset);
        }
        // the buffer is empty
        while (oEnd == oPos) {
            int iPos = 0;
            int toRead = nextSrcSizeToDecompress(ctx);

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
            int decoded = decompressContinue(ctx, oBuff, oPos, oBuffSize - oPos, iBuff, 0, iPos);

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

    public int read() throws IOException {
        byte[] oneByte = new byte[1];
        int result = read(oneByte, 0, 1);
        if (result > 0) {
            return oneByte[0] & 0xff;
        } else {
            return result;
        }
    }

    public int available() throws IOException {
        if (legacy != null) return legacy.available();
        return oEnd - oPos;
    }

    /* we don't support mark/reset */
    public boolean markSupported() {
        return false;
    }

    /* we can skip forward only inside the buffer*/
    public long skip(long n) throws IOException {
        if (legacy != null) return legacy.skip(n);
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
        if (legacy != null) {
            legacy.close();
        } else {
            freeDCtx(ctx);
            in.close();
        }
    }
}

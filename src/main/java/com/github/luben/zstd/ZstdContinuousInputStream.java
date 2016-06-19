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

public class ZstdContinuousInputStream extends ZstdInputStream {

    static {
        Native.load();
    }

    private int toRead = 0;
    private int iPos   = 0;

    public ZstdContinuousInputStream(InputStream inStream) throws IOException {
        // ZstdInputStream constructor
        super(inStream);
    }

    public int read(byte[] dst, int offset, int len) throws IOException {
        if (legacy != null) return legacy.read_truncated(dst, offset, len);
        // guard agains buffer overflows
        if (len > dst.length - offset) {
            throw new IndexOutOfBoundsException("Requested lenght " +len  +
                " exceeds the buffer size " + dst.length + " from offset " + offset);
        }
        // the output buffer is empty
        while (oEnd == oPos) {
            if (toRead - iPos == 0) {
                iPos = 0;
                toRead = nextSrcSizeToDecompress(ctx);
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
                    return 0;
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
}

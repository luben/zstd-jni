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

public class ZstdInputStream extends FilterInputStream {

    static {
        Native.load();
    }

    // Opaque pointer to Zstd context object
    private long stream;
    private long srcPos = 0;
    private long dstPos = 0;
    private byte[] src = null;
    private long toRead = 0;
    private static final int srcSize = (int) recommendedDInSize();

    /* JNI methods */
    private static native long recommendedDInSize();
    private static native long createDStream();
    private static native int  freeDStream(long stream);
    private native int  initDStream(long stream);
    private native int  decompressStream(long stream, byte[] dst, int dst_size, byte[] src, int src_size);

    // The main constuctor / legacy version dispatcher
    public ZstdInputStream(InputStream inStream) throws IOException {
        // FilterInputStream constructor
        super(inStream);

        // allocate input buffer with max frame header size
        src = new byte[srcSize];
        if (src == null) {
            throw new IOException("Error allocating the input buffer of size " + srcSize);
        }
        stream = createDStream();
        toRead = initDStream(stream);
        if (Zstd.isError(toRead)) {
            throw new IOException("Decompression error: " + Zstd.getErrorName(toRead));
        }
    }

    public int read(byte[] dst, int offset, int len) throws IOException {
        int completed = 0;

        // guard agains buffer underflows
        if (offset < 0) {
            throw new IndexOutOfBoundsException("Requested offset is negative: " + offset);
        }
        // guard agains buffer overflows
        if (len > dst.length - offset) {
            throw new IndexOutOfBoundsException("Requested lenght " +len  +
                " exceeds the buffer size " + dst.length + " from offset " + offset);
        }
        int dstSize = offset + len;
        dstPos = offset;
        while (dstPos < dstSize) {
            long oldPos = dstPos;
            int read = in.read(src, 0, (int) toRead);
            if (read < 0) {
                throw new IOException("Read error or truncated source");
            }
            toRead = decompressStream(stream, dst, dstSize, src, read);
            if (Zstd.isError(toRead)) {
                throw new IOException("Decompression error: " + Zstd.getErrorName(toRead));
            }
            completed += dstPos - oldPos;
            // we have completed a frame
            if (toRead == 0) {
                // re-init the codec so it can start decoding next frame
                toRead = initDStream(stream);
                if (Zstd.isError(toRead)) {
                    throw new IOException("Decompression error: " + Zstd.getErrorName(toRead));
                }
                return completed;
            }
        }
        return completed;
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

    /* TODO: Talk with  Yann */
    public int available() throws IOException {
        return 0;
    }

    /* we don't support mark/reset */
    public boolean markSupported() {
        return false;
    }

    /* we can skip forward only inside the buffer*/
    public long skip(long n) throws IOException {
        long toSkip = n;
        long skipped = 0;
        while (toSkip > Integer.MAX_VALUE) {
            byte[] dst = new byte[Integer.MAX_VALUE];
            long size = read(dst, 0, Integer.MAX_VALUE);
            toSkip -= size;
            skipped += size;
        }
        byte[] dst = new byte[(int) toSkip];
        skipped += read(dst, 0, (int) toSkip);
        return skipped;
    }

    public void close() throws IOException {
        freeDStream(stream);
        in.close();
    }
}

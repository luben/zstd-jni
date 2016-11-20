package com.github.luben.zstd;

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
    private long dstPos = 0;
    private long srcPos = 0;
    private long srcSize = 0;
    private byte[] src = null;
    private static final int srcBuffSize = (int) recommendedDInSize();

    private boolean isContinuous = false;
    private boolean frameFinished = false;
    private boolean isClosed = false;

    /* JNI methods */
    private static native long recommendedDInSize();
    private static native long recommendedDOutSize();
    private static native long createDStream();
    private static native int  freeDStream(long stream);
    private native int  initDStream(long stream);
    private native int  decompressStream(long stream, byte[] dst, int dst_size, byte[] src, int src_size);

    // The main constuctor / legacy version dispatcher
    public ZstdInputStream(InputStream inStream) throws IOException {
        // FilterInputStream constructor
        super(inStream);

        // allocate input buffer with max frame header size
        src = new byte[srcBuffSize];
        if (src == null) {
            throw new IOException("Error allocating the input buffer of size " + srcBuffSize);
        }
        stream = createDStream();
        int size = initDStream(stream);
        if (Zstd.isError(size)) {
            throw new IOException("Decompression error: " + Zstd.getErrorName(size));
        }
    }

    /**
     * Don't break on unfinished frames
     *
     * Use case: decompressing files that are not
     * yet finished writing and compressing
     */
    public ZstdInputStream setContinuous(boolean b) {
        isContinuous = b;
        return this;
    }

    public boolean getContinuous() {
        return this.isContinuous;
    }

    public int read(byte[] dst, int offset, int len) throws IOException {

        if (isClosed) {
            throw new IOException("Stream closed");
        }

        // guard agains buffer overflows
        if (offset < 0 || len > dst.length - offset) {
            throw new IndexOutOfBoundsException("Requested lenght " + len
                    + " from offset " + offset + " in buffer of size " + dst.length);
        }
        int dstSize = offset + len;
        dstPos = offset;

        while (dstPos < dstSize) {
            if (srcSize - srcPos == 0) {
                srcSize = in.read(src, 0, srcBuffSize);
                srcPos = 0;
                if (srcSize < 0) {
                    srcSize = 0;
                    if (frameFinished) {
                        return -1;
                    } else if (isContinuous) {
                        return (int)(dstPos - offset);
                    } else {
                        throw new IOException("Read error or truncated source");
                    }
                }
                frameFinished = false;
            }

            int size = decompressStream(stream, dst, dstSize, src, (int) srcSize);

            if (Zstd.isError(size)) {
                throw new IOException("Decompression error: " + Zstd.getErrorName(size));
            }

            // we have completed a frame
            if (size == 0) {
                frameFinished = true;
                // re-init the codec so it can start decoding next frame
                size = initDStream(stream);
                if (Zstd.isError(size)) {
                    throw new IOException("Decompression error: " + Zstd.getErrorName(size));
                }
                return (int)(dstPos - offset);
            }
        }
        return len;
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
        if (isClosed) {
            throw new IOException("Stream closed");
        }
        if (srcSize - srcPos > 0) {
            return 1;
        } else {
            return 0;
        }
    }

    /* we don't support mark/reset */
    public boolean markSupported() {
        return false;
    }

    /* we can skip forward */
    public long skip(long toSkip) throws IOException {
        if (isClosed) {
            throw new IOException("Stream closed");
        }
        long skipped = 0;
        int dstSize = (int) recommendedDOutSize();
        byte[] dst = new byte[dstSize];
        while (toSkip > dstSize) {
            long size = read(dst, 0, dstSize);
            toSkip -= size;
            skipped += size;
        }
        skipped += read(dst, 0, (int) toSkip);
        return skipped;
    }

    public void close() throws IOException {
        if (isClosed) {
            return;
        }
        freeDStream(stream);
        in.close();
        isClosed = true;
    }
}

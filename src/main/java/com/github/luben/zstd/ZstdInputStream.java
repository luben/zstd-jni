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
    private long dstPos = 0;
    private long srcPos = 0;
    private long srcEnd = 0;
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
        toRead = initDStream(stream); // TODO: why it does not return the frame header size?
        toRead = 9; // frame header size + 1 TODO: fix it
        if (Zstd.isError(toRead)) {
            throw new IOException("Decompression error: " + Zstd.getErrorName(toRead));
        }
    }

    public int read(byte[] dst, int offset, int len) throws IOException {

        // guard agains buffer overflows
        if (offset < 0 || len > dst.length - offset) {
            throw new IndexOutOfBoundsException("Requested lenght " + len
                    + " from offset " + offset + " in buffer of size " + dst.length);
        }
        int dstSize = offset + len;
        dstPos = offset;

        int completed = 0;

        while (dstPos < dstSize) {
            if (srcEnd + toRead > srcSize) {
                int toCopy = (int) (srcEnd-srcPos);
                if (toCopy > 0) {
                    System.arraycopy(src, (int) srcPos, src, 0, (int)(srcEnd-srcPos));
                }
                srcPos = 0;
                srcEnd = toCopy;
            }
            if (toRead != 1) { // TODO: Why are they not consumed in one go?
                int reading = (int) (toRead - (srcEnd - srcPos));
                if (reading > 0) {
                    int read = in.read(src, (int) srcEnd, reading);
                    if (read < 0) {
                        throw new IOException("Read error or truncated source");
                    }
                    srcEnd += read;
                }
            }
            long oldDPos = dstPos;
            toRead = decompressStream(stream, dst, dstSize, src, (int) srcEnd);
            completed += (int)(dstPos - oldDPos);

            if (Zstd.isError(toRead)) {
                throw new IOException("Decompression error: " + Zstd.getErrorName(toRead));
            }
            // we have completed a frame
            if (toRead == 0) {
                // re-init the codec so it can start decoding next frame
                toRead = initDStream(stream);
                toRead = 9; // TODO: magic
                srcPos = 0;
                srcEnd = 0;
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

    public int available() throws IOException {
        if (toRead == 1) {
            /* TODO: Talk with  Yann */
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
    public long skip(long n) throws IOException {
        long toSkip = n;
        long skipped = 0;
        byte[] dst = new byte[128*1024];
        while (toSkip > 128*1024) {
            long size = read(dst, 0, 1024);
            toSkip -= size;
            skipped += size;
        }
        skipped += read(dst, 0, (int) toSkip);
        return skipped;
    }

    public void close() throws IOException {
        freeDStream(stream);
        in.close();
    }
}

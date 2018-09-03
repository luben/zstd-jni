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
    private boolean frameFinished = true;
    private boolean isClosed = false;
    private byte[] dict = null;
    private ZstdDictDecompress fastDict = null;

    /* JNI methods */
    private static native long recommendedDInSize();
    private static native long recommendedDOutSize();
    private static native long createDStream();
    private static native int  freeDStream(long stream);
    private native int  initDStream(long stream);
    private native int  initDStreamWithDict(long stream, byte[] dict, int dict_size);
    private native int  initDStreamWithFastDict(long stream, ZstdDictDecompress dict);
    private native int  decompressStream(long stream, byte[] dst, int dst_size, byte[] src, int src_size);

    // The main constuctor / legacy version dispatcher
    public ZstdInputStream(InputStream inStream) throws IOException {
        // FilterInputStream constructor
        super(inStream);

        // allocate input buffer with max frame header size
        src = new byte[srcBuffSize];
        stream = createDStream();
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

    public ZstdInputStream setDict(byte[] dict) throws IOException {
        if (!frameFinished) {
            throw new IOException("Change of parameter on initialized stream");
        }
        this.dict = dict;
        this.fastDict = null;
        return this;
    }

    public ZstdInputStream setDict(ZstdDictDecompress dict) throws IOException {
        if (!frameFinished) {
            throw new IOException("Change of parameter on initialized stream");
        }
        this.fastDict = dict;
        this.dict = null;
        return this;
    }


    public int read(byte[] dst, int offset, int len) throws IOException {

        if (isClosed) {
            throw new IOException("Stream closed");
        }

        if (frameFinished) {
            int size = 0;
            if (fastDict != null) {
                size = initDStreamWithFastDict(stream, fastDict);
            } else if (dict != null) {
                size = initDStreamWithDict(stream, dict, dict.length);
            } else {
                size = initDStream(stream);
            }
            if (Zstd.isError(size)) {
                throw new IOException("Decompression error: " + Zstd.getErrorName(size));
            }
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
            return (int)(srcSize - srcPos);
        } else {
            return in.available();
        }
    }

    /* we don't support mark/reset */
    public boolean markSupported() {
        return false;
    }

    /* we can skip forward */
    public long skip(long numBytes) throws IOException {
        if (isClosed) {
            throw new IOException("Stream closed");
        }
        if (numBytes <= 0) {
            return 0;
        }
        long toSkip = numBytes;
        int bufferLen = (int) Math.min(recommendedDOutSize(), toSkip);
        byte data[] = new byte[bufferLen];
        while (toSkip > 0) {
            int read = read(data, 0, (int) Math.min((long) bufferLen, toSkip));
            if (read < 0) {
                break;
            }
            toSkip -= read;
        }
        return numBytes - toSkip;
    }

    public void close() throws IOException {
        if (isClosed) {
            return;
        }
        freeDStream(stream);
        in.close();
        isClosed = true;
    }

    @Override
    protected void finalize() throws Throwable {
        close();
    }
}

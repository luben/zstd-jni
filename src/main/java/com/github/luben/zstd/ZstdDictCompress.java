package com.github.luben.zstd;

import com.github.luben.zstd.util.Native;

import java.io.Closeable;
import java.io.IOException;
import java.util.Arrays;

public class ZstdDictCompress implements Closeable {

    static {
        Native.load();
    }

    private long nativePtr = 0;

    private native void init(byte[] dict, int dict_offset, int dict_size, int level);

    private native void free();

    /**
     * Convenience constructor to create a new dictionary for use with fast compress
     *
     * @param dict  buffer containing dictionary to load/parse with exact length
     * @param level compression level
     */
    public ZstdDictCompress(byte[] dict, int level) {
        this(dict, 0, dict.length, level);
    }

    /**
     * Create a new dictionary for use with fast compress
     *
     * @param dict   buffer containing dictionary
     * @param offset the offset into the buffer to read from
     * @param length number of bytes to use from the buffer
     * @param level  compression level
     */
    public ZstdDictCompress(byte[] dict, int offset, int length, int level) {
        if (dict.length - offset < 0) {
            throw new IllegalArgumentException("Dictionary buffer is to short");
        }

        init(dict, offset, length, level);

        if (0 == nativePtr) {
            throw new IllegalStateException("ZSTD_createCDict failed");
        }
    }


    @Override
    public synchronized void close() throws IOException {
        if (nativePtr != 0) {
            free();
            nativePtr = 0;
        }
    }

    @Override
    protected void finalize() throws Throwable {
        close();
    }
}

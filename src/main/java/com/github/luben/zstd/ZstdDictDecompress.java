package com.github.luben.zstd;

import com.github.luben.zstd.util.Native;

import java.io.Closeable;
import java.io.IOException;
import java.util.Arrays;

public class ZstdDictDecompress implements Closeable {

    static {
        Native.load();
    }

    private long nativePtr = 0L;

    private native void init(byte[] dict, int dict_offset, int dict_size);

    private native void free();

    /**
     * Convenience constructor to create a new dictionary for use with fast decompress
     *
     * @param dict buffer containing dictionary to load/parse with exact length
     */
    public ZstdDictDecompress(byte[] dict) {
        this(dict, 0, dict.length);
    }

    /**
     * Create a new dictionary for use with fast decompress
     *
     * @param dict   buffer containing dictionary
     * @param offset the offset into the buffer to read from
     * @param length number of bytes to use from the buffer
     */
    public ZstdDictDecompress(byte[] dict, int offset, int length) {

        init(dict, offset, length);

        if (nativePtr == 0L) {
           throw new IllegalStateException("ZSTD_createDDict failed");
        }
    }


    @Override
    public void close() throws IOException {
        free();
        nativePtr = 0;
    }

    @Override
    protected void finalize() throws Throwable {
        close();
    }
}

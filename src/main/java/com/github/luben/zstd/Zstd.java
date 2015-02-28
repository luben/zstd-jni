package com.github.luben.zstd;

import java.nio.ByteBuffer;
import java.util.Arrays;

import com.github.luben.zstd.util.Native;

public class Zstd {

    static {
        Native.load();
    }
    /**
     * Compresses buffer 'src' into buffer 'dst'.
     *
     * Destination buffer should be sized to handle worst cases situations (input
     * data not compressible). Worst case size evaluation is provided by function
     * ZSTD_compressBound().
     *
     * @param dst the destination buffer
     * @param src the source buffer
     * @return  the number of bytes written into buffer 'dst' or an error code if
     *          it fails (which can be tested using ZSTD_isError())
     */
    public static native long compress  (byte[] dst, byte[] src);

    /**
     * Decompresses buffer 'src' into buffer 'dst'.
     *
     * Destination buffer should be sized to be larger of equal to the originalSize
     *
     * @param dst the destination buffer
     * @param src the source buffer
     * @return the number of bytes decompressed into destination buffer (originalSize)
     *          or an errorCode if it fails (which can be tested using ZSTD_isError())
     *
     */
    public static native long decompress(byte[] dst, byte[] src);

    /* Utility methods */

    /**
     * Maximum size of the compressed data
     *
     * @param srcSize the size of the data to be compressed
     * @return the maximum size of the compressed data
     */
    public static native long    compressBound(long srcSize);
    public static native boolean isError(long code);
    public static native String  getErrorName(long code);

    /* Convenience methods */

    /**
     * Compresses the data in buffer 'src'
     *
     * @param src the source buffer
     * @return byte array with the compressed data
     */
    public static byte[] compress(byte[] src) {
        long maxDstSize     = compressBound(src.length);
        if (maxDstSize > Integer.MAX_VALUE) {
            throw new RuntimeException("Max output size is greater than MAX_INT");
        }
        ByteBuffer dst_buff = ByteBuffer.allocate((int) maxDstSize);
        byte[] dst          = dst_buff.array();
        long size = compress(dst, src);
        if (isError(size)) {
            throw new RuntimeException(getErrorName(size));
        }
        return Arrays.copyOfRange(dst, 0, (int) size);
    }

    /**
     * Decompress data
     *
     * @param src the source buffer
     * @param originalSize the maximum size of the uncompressed data
     * @return byte array with the decompressed data
     */
    public static byte[] decompress(byte[] src, int originalSize) {
        ByteBuffer dst_buff = ByteBuffer.allocate(originalSize);
        byte[] dst          = dst_buff.array();
        long size = decompress(dst, src);
        if (isError(size)) {
            throw new RuntimeException(getErrorName(size));
        }
        if (size != originalSize) {
            return Arrays.copyOfRange(dst, 0, (int) size);
        } else {
            return dst;
        }
    }
}

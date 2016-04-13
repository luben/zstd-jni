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
     * @param level compression level
     * @return  the number of bytes written into buffer 'dst' or an error code if
     *          it fails (which can be tested using ZSTD_isError())
     */
    public static native long compress  (byte[] dst, byte[] src, int level);

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
    /**
     * Error handling
     *
     * @param code return code/size
     */

    public static native boolean isError(long code);
    public static native String  getErrorName(long code);


    /**
     * Constants from the zstd_static header
     */
    public static native int magicNumber();
    public static native int windowLogMin();
    public static native int windowLogMax();
    public static native int chainLogMin();
    public static native int chainLogMax();
    public static native int hashLogMin();
    public static native int hashLogMax();
    public static native int searchLogMin();
    public static native int searchLogMax();
    public static native int searchLengthMin();
    public static native int searchLengthMax();
    public static native int targetLengthMin();
    public static native int targetLengthMax();
    public static native int frameHeaderSizeMin();
    public static native int frameHeaderSizeMax();
    public static native int blockSizeMax();

    /* Convenience methods */

    /**
     * Compresses the data in buffer 'src'
     *
     * @param src the source buffer
     * @param level compression level
     * @return byte array with the compressed data
     */
    public static byte[] compress(byte[] src, int level) {
        long maxDstSize = compressBound(src.length);
        if (maxDstSize > Integer.MAX_VALUE) {
            throw new RuntimeException("Max output size is greater than MAX_INT");
        }
        ByteBuffer dst_buff = ByteBuffer.allocate((int) maxDstSize);
        byte[] dst = dst_buff.array();
        long size = compress(dst, src, level);
        if (isError(size)) {
            throw new RuntimeException(getErrorName(size));
        }
        return Arrays.copyOfRange(dst, 0, (int) size);
    }

    /**
     * Compresses the data in buffer 'src' using defaul compression level
     *
     * @param src the source buffer
     * @return byte array with the compressed data
     */
    public static byte[] compress(byte[] src) {
        return compress(src, 1);
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

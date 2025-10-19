package com.github.luben.zstd;

import com.github.luben.zstd.util.Native;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class Zstd {
    static {
        Native.load();
    }

    /**
     * Note: This enum controls features which are conditionally beneficial.
     * Zstd typically will make a final decision on whether to enable the
     * feature ({@link AUTO}), but setting the switch to {@link ENABLE} or
     * {@link DISABLE} allows for force enabling/disabling the feature.
     */
    public static enum ParamSwitch {
        /**
         * Let the library automatically determine whether the feature shall be enabled
         */
        AUTO(0),
        /**
         * Force-enable the feature
         */
        ENABLE(1),
        /**
         * Do not use the feature
         */
        DISABLE(2);

        private int val;
        ParamSwitch(int val) {
            this.val = val;
        }

        public int getValue() {
            return val;
        }
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
     * @param checksumFlag flag to enable or disable checksum
     * @return  the number of bytes written into buffer 'dst' or an error code if
     *          it fails (which can be tested using ZSTD_isError())
     */
    public static long compress(byte[] dst, byte[] src, int level, boolean checksumFlag) {
        ZstdCompressCtx ctx = new ZstdCompressCtx();
        try {
            ctx.setLevel(level);
            ctx.setChecksum(checksumFlag);
            return (long) ctx.compress(dst, src);
        } finally {
            ctx.close();
        }
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
    public static long compress(byte[] dst, byte[] src, int level) {
        return compress(dst, src, level, false);
    }

    /**
     * Compresses buffer 'src' into buffer 'dst'.
     *
     * Destination buffer should be sized to handle worst cases situations (input
     * data not compressible). Worst case size evaluation is provided by function
     * ZSTD_compressBound().
     *
     * @param dst the destination buffer
     * @param dstOffset offset from the start of the destination buffer
     * @param dstSize available space in the destination buffer after the offset
     * @param src the source buffer
     * @param srcOffset offset from the start of the source buffer
     * @param srcSize available data in the source buffer after the offset
     * @param level compression level
     * @param checksumFlag flag to enable or disable checksum
     * @return  the number of bytes written into buffer 'dst' or an error code if
     *          it fails (which can be tested using ZSTD_isError())
     */
    public static long compressByteArray(byte[] dst, int dstOffset, int dstSize, byte[] src, int srcOffset, int srcSize, int level, boolean checksumFlag) {
        ZstdCompressCtx ctx = new ZstdCompressCtx();
        try {
            ctx.setLevel(level);
            ctx.setChecksum(checksumFlag);
            return (long) ctx.compressByteArray(dst, dstOffset, dstSize, src, srcOffset, srcSize);
        } finally {
            ctx.close();
        }
    }

    /**
     * Compresses buffer 'src' into buffer 'dst'.
     *
     * Destination buffer should be sized to handle worst cases situations (input
     * data not compressible). Worst case size evaluation is provided by function
     * ZSTD_compressBound().
     *
     * @param dst the destination buffer
     * @param dstOffset offset from the start of the destination buffer
     * @param dstSize available space in the destination buffer after the offset
     * @param src the source buffer
     * @param srcOffset offset from the start of the source buffer
     * @param srcSize available data in the source buffer after the offset
     * @param level compression level
     * @return  the number of bytes written into buffer 'dst' or an error code if
     *          it fails (which can be tested using ZSTD_isError())
     */
    public static long compressByteArray(byte[] dst, int dstOffset, int dstSize, byte[] src, int srcOffset, int srcSize, int level) {
        return compressByteArray(dst, dstOffset, dstSize, src, srcOffset, srcSize, level, false);
    }

    /**
     * Compresses direct buffer 'src' into direct buffer 'dst'.
     *
     * Destination buffer should be sized to handle worst cases situations (input
     * data not compressible). Worst case size evaluation is provided by function
     * ZSTD_compressBound().
     *
     * @param dst the destination buffer
     * @param dstOffset offset from the start of the destination buffer
     * @param dstSize available space in the destination buffer after the offset
     * @param src the source buffer
     * @param srcOffset offset from the start of the source buffer
     * @param srcSize available data in the source buffer after the offset
     * @param level compression level
     * @param checksumFlag flag to enable or disable checksum
     * @return  the number of bytes written into buffer 'dst' or an error code if
     *          it fails (which can be tested using ZSTD_isError())
     */
    public static long compressDirectByteBuffer(ByteBuffer dst, int dstOffset, int dstSize, ByteBuffer src, int srcOffset, int srcSize, int level, boolean checksumFlag) {
        ZstdCompressCtx ctx = new ZstdCompressCtx();
        try {
            ctx.setLevel(level);
            ctx.setChecksum(checksumFlag);
            return (long) ctx.compressDirectByteBuffer(dst, dstOffset, dstSize, src, srcOffset, srcSize);
        } finally {
            ctx.close();
        }
    }

    /**
     * Compresses direct buffer 'src' into direct buffer 'dst'.
     *
     * Destination buffer should be sized to handle worst cases situations (input
     * data not compressible). Worst case size evaluation is provided by function
     * ZSTD_compressBound().
     *
     * @param dst the destination buffer
     * @param dstOffset offset from the start of the destination buffer
     * @param dstSize available space in the destination buffer after the offset
     * @param src the source buffer
     * @param srcOffset offset from the start of the source buffer
     * @param srcSize available data in the source buffer after the offset
     * @param level compression level
     * @return  the number of bytes written into buffer 'dst' or an error code if
     *          it fails (which can be tested using ZSTD_isError())
     */
    public static long compressDirectByteBuffer(ByteBuffer dst, int dstOffset, int dstSize, ByteBuffer src, int srcOffset, int srcSize, int level) {
        return compressDirectByteBuffer(dst, dstOffset, dstSize, src, srcOffset, srcSize, level, false);
    }


    /**
     * Compresses buffer 'src' into direct buffer 'dst'.
     *
     * Destination buffer should be sized to handle worst cases situations (input
     * data not compressible). Worst case size evaluation is provided by function
     * ZSTD_compressBound().
     *
     * @param dst pointer to the destination buffer
     * @param dstSize available space in the destination buffer
     * @param src pointer to the source buffer
     * @param srcSize available data in the source buffer
     * @param level compression level
     * @param checksumFlag flag to enable or disable checksum
     * @return  the number of bytes written into buffer 'dst' or an error code if
     *          it fails (which can be tested using ZSTD_isError())
     */
    public static native long compressUnsafe(long dst, long dstSize, long src, long srcSize, int level, boolean checksumFlag);

    /**
     * Compresses buffer 'src' into direct buffer 'dst'.
     *
     * Destination buffer should be sized to handle worst cases situations (input
     * data not compressible). Worst case size evaluation is provided by function
     * ZSTD_compressBound().
     *
     * @param dst pointer to the destination buffer
     * @param dstSize available space in the destination buffer
     * @param src pointer to the source buffer
     * @param srcSize available data in the source buffer
     * @param level compression level
     * @return  the number of bytes written into buffer 'dst' or an error code if
     *          it fails (which can be tested using ZSTD_isError())
     */
    public static long compressUnsafe(long dst, long dstSize, long src, long srcSize, int level) {
        return compressUnsafe(dst, dstSize, src, srcSize, level, false);
    }

   /**
     * Compresses buffer 'src' into buffer 'dst' with dictionary.
     *
     * Destination buffer should be sized to handle worst cases situations (input
     * data not compressible). Worst case size evaluation is provided by function
     * ZSTD_compressBound().
     *
     * @param dst the destination buffer
     * @param dstOffset the start offset of 'dst'
     * @param src the source buffer
     * @param srcOffset the start offset of 'src'
     * @param length the length of available data in 'src' after `srcOffset'
     * @param dict the dictionary buffer
     * @param level compression level
     * @return  the number of bytes written into buffer 'dst' or an error code if
     *          it fails (which can be tested using ZSTD_isError())
     */
    public static long compressUsingDict (byte[] dst, int dstOffset, byte[] src, int srcOffset, int length, byte[] dict, int level) {
        ZstdCompressCtx ctx = new ZstdCompressCtx();
        try {
            ctx.setLevel(level);
            ctx.loadDict(dict);
            return (long) ctx.compressByteArray(dst, dstOffset, dst.length - dstOffset, src, srcOffset, length);
        } finally {
            ctx.close();
        }
    }

   /**
     * Compresses buffer 'src' into buffer 'dst' with dictionary.
     *
     * Destination buffer should be sized to handle worst cases situations (input
     * data not compressible). Worst case size evaluation is provided by function
     * ZSTD_compressBound().
     *
     * @param dst the destination buffer
     * @param dstOffset the start offset of 'dst'
     * @param src the source buffer
     * @param srcOffset the start offset of 'src'
     * @param dict the dictionary buffer
     * @param level compression level
     * @return  the number of bytes written into buffer 'dst' or an error code if
     *          it fails (which can be tested using ZSTD_isError())
     */
    public static long compressUsingDict (byte[] dst, int dstOffset, byte[] src, int srcOffset, byte[] dict, int level) {
        ZstdCompressCtx ctx = new ZstdCompressCtx();
        try {
            ctx.setLevel(level);
            ctx.loadDict(dict);
            return (long) ctx.compressByteArray(dst, dstOffset, dst.length - dstOffset, src, srcOffset, src.length - srcOffset);
        } finally {
            ctx.close();
        }
    }

   /**
     * Compresses direct byte buffer 'src' into direct byte buffer 'dst' with dictionary.
     *
     * Destination buffer should be sized to handle worst cases situations (input
     * data not compressible). Worst case size evaluation is provided by function
     * ZSTD_compressBound().
     *
     * @param dst the destination buffer
     * @param dstOffset the start offset of 'dst'
     * @param dstSize size of 'dst'
     * @param src the source buffer
     * @param srcOffset the start offset of 'src'
     * @param srcSize the length of 'src'
     * @param dict the dictionary buffer
     * @param level compression level
     * @return  the number of bytes written into buffer 'dst' or an error code if
     *          it fails (which can be tested using ZSTD_isError())
     */
    public static long compressDirectByteBufferUsingDict(ByteBuffer dst, int dstOffset, int dstSize, ByteBuffer src, int srcOffset, int srcSize, byte[] dict, int level) {
        ZstdCompressCtx ctx = new ZstdCompressCtx();
        try {
            ctx.setLevel(level);
            ctx.loadDict(dict);
            return (long) ctx.compressDirectByteBuffer(dst, dstOffset, dstSize, src, srcOffset, srcSize);
        } finally {
            ctx.close();
        }
    }

    /**
     * Compresses buffer 'src' into buffer 'dst' with dictionary.
     *
     * Destination buffer should be sized to handle worst cases situations (input
     * data not compressible). Worst case size evaluation is provided by function
     * ZSTD_compressBound().
     *
     * @param dst the destination buffer
     * @param dstOffset the start offset of 'dst'
     * @param src the source buffer
     * @param srcOffset the start offset of 'src'
     * @param length the length of available data in 'src' after `srcOffset'
     * @param dict the dictionary
     * @return  the number of bytes written into buffer 'dst' or an error code if
     *          it fails (which can be tested using ZSTD_isError())
     */
    public static long compressFastDict(byte[] dst, int dstOffset, byte[] src, int srcOffset, int length, ZstdDictCompress dict) {
        ZstdCompressCtx ctx = new ZstdCompressCtx();
        try {
            ctx.loadDict(dict);
            ctx.setLevel(dict.level());
            return (long) ctx.compressByteArray(dst, dstOffset, dst.length - dstOffset, src, srcOffset, length);
        } finally {
            ctx.close();
        }
    }

    /**
     * Compresses buffer 'src' into buffer 'dst' with dictionary.
     *
     * Destination buffer should be sized to handle worst cases situations (input
     * data not compressible). Worst case size evaluation is provided by function
     * ZSTD_compressBound().
     *
     * @param dst the destination buffer
     * @param dstOffset the start offset of 'dst'
     * @param src the source buffer
     * @param srcOffset the start offset of 'src'
     * @param dict the dictionary
     * @return  the number of bytes written into buffer 'dst' or an error code if
     *          it fails (which can be tested using ZSTD_isError())
     */
    public static long compressFastDict(byte[] dst, int dstOffset, byte[] src, int srcOffset, ZstdDictCompress dict) {
        ZstdCompressCtx ctx = new ZstdCompressCtx();
        try {
            ctx.loadDict(dict);
            ctx.setLevel(dict.level());
            return (long) ctx.compressByteArray(dst, dstOffset, dst.length - dstOffset, src, srcOffset, src.length - srcOffset);
        } finally {
            ctx.close();
        }
    }

    public static long compress(byte[] dst, byte[] src, ZstdDictCompress dict) {
        ZstdCompressCtx ctx = new ZstdCompressCtx();
        try {
            ctx.loadDict(dict);
            ctx.setLevel(dict.level());
            return (long) ctx.compress(dst, src);
        } finally {
            ctx.close();
        }
    }

    /**
     * Compresses buffer 'src' into buffer 'dst' with dictionary.
     *
     * Destination buffer should be sized to handle worst cases situations (input
     * data not compressible). Worst case size evaluation is provided by function
     * ZSTD_compressBound().
     *
     * @param dst the destination buffer
     * @param dstOffset the start offset of 'dst'
     * @param dstSize the size of 'dst'
     * @param src the source buffer
     * @param srcOffset the start offset of 'src'
     * @param srcSize the length of 'src'
     * @param dict the dictionary
     * @return  the number of bytes written into buffer 'dst' or an error code if
     *          it fails (which can be tested using ZSTD_isError())
     */
    public static long compressDirectByteBufferFastDict(ByteBuffer dst, int dstOffset, int dstSize, ByteBuffer src, int srcOffset, int srcSize, ZstdDictCompress dict) {
        ZstdCompressCtx ctx = new ZstdCompressCtx();
        try {
            ctx.loadDict(dict);
            ctx.setLevel(dict.level());
            return (long) ctx.compressDirectByteBuffer(dst, dstOffset, dstSize, src, srcOffset, srcSize);
        } finally {
            ctx.close();
        }
    }

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
    public static long decompress(byte[] dst, byte[] src) {
        ZstdDecompressCtx ctx = new ZstdDecompressCtx();
        try {
            return (long) ctx.decompress(dst, src);
        } finally {
            ctx.close();
        }
    }

    public static int decompress(byte[] dst, ByteBuffer srcBuf) {
        ZstdDecompressCtx ctx = new ZstdDecompressCtx();
        try {
            return ctx.decompress(dst, srcBuf);
        } finally {
            ctx.close();
        }
    }

    /**
     * Decompresses buffer 'src' into buffer 'dst'.
     *
     * Destination buffer should be sized to be larger of equal to the originalSize
     *
     * @param dst the destination buffer
     * @param dstOffset offset from the start of the destination buffer
     * @param dstSize available space in the destination buffer after the offset
     * @param src the source buffer
     * @param srcOffset offset from the start of the source buffer
     * @param srcSize available data in the source buffer after the offset
     * @return the number of bytes decompressed into destination buffer (originalSize)
     *          or an errorCode if it fails (which can be tested using ZSTD_isError())
     *
     */
    public static long decompressByteArray(byte[] dst, int dstOffset, int dstSize, byte[] src, int srcOffset, int srcSize) {
        ZstdDecompressCtx ctx = new ZstdDecompressCtx();
        try {
            return (long) ctx.decompressByteArray(dst, dstOffset, dstSize, src, srcOffset, srcSize);
        } finally {
            ctx.close();
        }
    }

    /**
     * Decompresses direct buffer 'src' into direct buffer 'dst'.
     *
     * Destination buffer should be sized to be larger of equal to the originalSize
     *
     * @param dst the destination buffer
     * @param dstOffset offset from the start of the destination buffer
     * @param dstSize available space in the destination buffer after the offset
     * @param src the source buffer
     * @param srcOffset offset from the start of the source buffer
     * @param srcSize available data in the source buffer after the offset
     *
     * @return the number of bytes decompressed into destination buffer (originalSize)
     *          or an errorCode if it fails (which can be tested using ZSTD_isError())
     *
     */
    public static long decompressDirectByteBuffer(ByteBuffer dst, int dstOffset, int dstSize, ByteBuffer src, int srcOffset, int srcSize) {
        ZstdDecompressCtx ctx = new ZstdDecompressCtx();
        try {
            return (long) ctx.decompressDirectByteBuffer(dst, dstOffset, dstSize, src, srcOffset, srcSize);
        } finally {
            ctx.close();
        }
    }

    /**
     * Decompresses buffer 'src' into direct buffer 'dst'.
     *
     * Destination buffer should be sized to be larger of equal to the originalSize
     *
     * @param dst pointer to the destination buffer
     * @param dstSize available space in the destination buffer after the offset
     * @param src pointer the source buffer
     * @param srcSize available data in the source buffer after the offset
     *
     * @return the number of bytes decompressed into destination buffer (originalSize)
     *          or an errorCode if it fails (which can be tested using ZSTD_isError())
     *
     */
    public static native long decompressUnsafe(long dst, long dstSize, long src, long srcSize);

    /**
     * Decompresses buffer 'src' into buffer 'dst' with dictionary.
     *
     * Destination buffer should be sized to be larger of equal to the originalSize
     *
     * @param dst the destination buffer
     * @param dstOffset the start offset of 'dst'
     * @param src the source buffer
     * @param srcOffset the start offset of 'src'
     * @param length the length of 'src'
     * @param dict the dictionary buffer
     * @return the number of bytes decompressed into destination buffer (originalSize)
     *          or an errorCode if it fails (which can be tested using ZSTD_isError())
     *
     */
    public static long decompressUsingDict(byte[] dst, int dstOffset, byte[] src, int srcOffset, int length, byte[] dict) {
        ZstdDecompressCtx ctx = new ZstdDecompressCtx();
        try {
            ctx.loadDict(dict);
            return (long) ctx.decompressByteArray(dst, dstOffset, dst.length - dstOffset, src, srcOffset, length);
        } finally {
            ctx.close();
        }
    }

    /**
     * Decompresses buffer 'src' into buffer 'dst' with dictionary.
     *
     * Destination buffer should be sized to be larger of equal to the originalSize
     *
     * @param dst the destination buffer
     * @param dstOffset the start offset of 'dst'
     * @param dstSize size of 'dst'
     * @param src the source buffer
     * @param srcOffset the start offset of 'src'
     * @param srcSize the  size of 'src'
     * @param dict the dictionary buffer
     * @return the number of bytes decompressed into destination buffer (originalSize)
     *          or an errorCode if it fails (which can be tested using ZSTD_isError())
     *
     */
    public static long decompressDirectByteBufferUsingDict(ByteBuffer dst, int dstOffset, int dstSize, ByteBuffer src, int srcOffset, int srcSize, byte[] dict) {
        ZstdDecompressCtx ctx = new ZstdDecompressCtx();
        try {
            ctx.loadDict(dict);
            return (long) ctx.decompressDirectByteBuffer(dst, dstOffset, dstSize, src, srcOffset, srcSize);
        } finally {
            ctx.close();
        }
    }

    /**
     * Decompresses buffer 'src' into buffer 'dst' with dictionary.
     *
     * Destination buffer should be sized to be larger of equal to the originalSize
     *
     * @param dst the destination buffer
     * @param dstOffset the start offset of 'dst'
     * @param src the source buffer
     * @param srcOffset the start offset of 'src'
     * @param length the length of 'src'
     * @param dict the dictionary
     * @return the number of bytes decompressed into destination buffer (originalSize)
     *          or an errorCode if it fails (which can be tested using ZSTD_isError())
     *
     */
    public static long decompressFastDict(byte[] dst, int dstOffset, byte[] src, int srcOffset, int length, ZstdDictDecompress dict) {
        ZstdDecompressCtx ctx = new ZstdDecompressCtx();
        try {
            ctx.loadDict(dict);
            return (long) ctx.decompressByteArray(dst, dstOffset, dst.length - dstOffset, src, srcOffset, length);
        } finally {
            ctx.close();
        }
    }

    /**
     * Decompresses buffer 'src' into buffer 'dst' with dictionary.
     *
     * Destination buffer should be sized to be larger of equal to the originalSize
     *
     * @param dst the destination buffer
     * @param dstOffset the start offset of 'dst'
     * @param dstSize the size of 'dst'
     * @param src the source buffer
     * @param srcOffset the start offset of 'src'
     * @param srcSize the size of 'src'
     * @param dict the dictionary
     * @return the number of bytes decompressed into destination buffer (originalSize)
     *          or an errorCode if it fails (which can be tested using ZSTD_isError())
     *
     */
    public static long decompressDirectByteBufferFastDict(ByteBuffer dst, int dstOffset, int dstSize, ByteBuffer src, int srcOffset, int srcSize, ZstdDictDecompress dict) {
        ZstdDecompressCtx ctx = new ZstdDecompressCtx();
        try {
            ctx.loadDict(dict);
            return (long) ctx.decompressDirectByteBuffer(dst, dstOffset, dstSize, src, srcOffset, srcSize);
        } finally {
            ctx.close();
        }
    }

    /* Advance API */
    public static native int loadDictDecompress(long stream, byte[] dict, int dict_size);
    public static native int loadFastDictDecompress(long stream, ZstdDictDecompress dict);
    public static native int loadDictCompress(long stream, byte[] dict, int dict_size);
    public static native int loadFastDictCompress(long stream, ZstdDictCompress dict);
    public static native void registerSequenceProducer(long stream, long seqProdState, long seqProdFunction);
    static native long getBuiltinSequenceProducer(); // Used in tests
    static native void generateSequences(long stream, long outSeqs, long outSeqsSize, long src, long srcSize);
    static native long getStubSequenceProducer();    // Used in tests
    public static native int setCompressionChecksums(long stream, boolean useChecksums);
    public static native int setCompressionMagicless(long stream, boolean useMagicless);
    public static native int setCompressionLevel(long stream, int level);
    public static native int setCompressionLong(long stream, int windowLog);
    public static native int setCompressionWorkers(long stream, int workers);
    public static native int setCompressionOverlapLog(long stream, int overlapLog);
    public static native int setCompressionJobSize(long stream, int jobSize);
    public static native int setCompressionTargetLength(long stream, int targetLength);
    public static native int setCompressionMinMatch(long stream, int minMatch);
    public static native int setCompressionSearchLog(long stream, int searchLog);
    public static native int setCompressionChainLog(long stream, int chainLog);
    public static native int setCompressionHashLog(long stream, int hashLog);
    public static native int setCompressionWindowLog(long stream, int windowLog);
    public static native int setCompressionStrategy(long stream, int strategy);
    public static native int setDecompressionLongMax(long stream, int windowLogMax);
    public static native int setDecompressionMagicless(long stream, boolean useMagicless);
    public static native int setRefMultipleDDicts(long stream, boolean useMultiple);
    public static native int setValidateSequences(long stream, int validateSequences);
    public static native int setSequenceProducerFallback(long stream, boolean fallbackFlag);
    public static native int setSearchForExternalRepcodes(long stream, int searchRepcodes);
    public static native int setEnableLongDistanceMatching(long stream, int enableLDM);

    /* Utility methods */
    /**
     * Return the compressed size of a frame within a buffer.
     *
     * @param src the compressed buffer
     * @param srcPosition offset of the compressed frame inside the src buffer
     * @param srcSize length of the compressed data inside the src buffer
     * @return the number of bytes of the compressed frame
     * @throws ZstdException if there is an error decoding the frame
     */
    public static long findFrameCompressedSize(byte[] src, int srcPosition, int srcSize) {
        if (srcPosition >= src.length) {
            throw new ArrayIndexOutOfBoundsException(srcPosition);
        }
        if (srcPosition + srcSize > src.length) {
            throw new ArrayIndexOutOfBoundsException(srcPosition + srcSize);
        }

        long size = findFrameCompressedSize0(src, srcPosition, srcSize);
        if (Zstd.isError(size)) {
            throw new ZstdException(size);
        }

        return size;
    }

    private static native long findFrameCompressedSize0(byte[] src, int srcPosition, int srcSize);

    /**
     * Return the compressed size of a frame within a buffer.
     *
     * @param src the compressed buffer
     * @param srcPosition offset of the compressed frame inside the src buffer
     * @return the number of bytes of the compressed frame
     *         negative if there is an error decoding the frame header
     */
    public static long findFrameCompressedSize(byte[] src, int srcPosition) {
        return findFrameCompressedSize(src, srcPosition, src.length - srcPosition);
    }

    /**
     * Return the compressed size of a frame within a buffer.
     *
     * @param src the compressed buffer
     * @return the number of bytes of the compressed frame
     *         negative if there is an error decoding the frame header
     */
    public static long findFrameCompressedSize(byte[] src) {
        return findFrameCompressedSize(src, 0);
    }

    /**
     * Return the compressed size of a frame within a buffer.
     *
     * @param srcBuf the compressed buffer.  must be direct.  It is assumed that the position() of this buffer marks the beginning of the
     *               compressed data whose decompressed size is being queried, and that the limit() of this buffer marks its
     *               end.
     * @return the number of bytes of the compressed frame
     *         negative if there is an error decoding the frame header
     */
    public static long findFrameCompressedSize(ByteBuffer srcBuf) {
        return findDirectByteBufferFrameCompressedSize(srcBuf, srcBuf.position(), srcBuf.limit() - srcBuf.position());
    }

    /**
     * Return the compressed size of a frame within a buffer.
     *
     * @param src the compressed buffer
     * @param srcPosition offset of the compressed data inside the src buffer
     * @param srcSize length of the compressed data inside the src buffe
     * @return the number of bytes of the compressed frame
     *         negative if there is an error decoding the frame header
     */
    public static native long findDirectByteBufferFrameCompressedSize(ByteBuffer src, int srcPosition, int srcSize);

    /**
     * Return the original size of a compressed buffer (if known)
     *
     * @param src the compressed buffer
     * @param srcPosition offset of the compressed data inside the src buffer
     * @param srcSize length of the compressed data inside the src buffer
     * @param magicless whether the buffer contains a magicless frame
     * @return the number of bytes of the original buffer
     *         0 if the original size is not known,
     *         negative if there is an error decoding the frame header
     */
    public static long getFrameContentSize(byte[] src, int srcPosition, int srcSize, boolean magicless) {
        if (srcPosition >= src.length) {
            throw new ArrayIndexOutOfBoundsException(srcPosition);
        }
        if (srcPosition + srcSize > src.length) {
            throw new ArrayIndexOutOfBoundsException(srcPosition + srcSize);
        }
        return getFrameContentSize0(src, srcPosition, srcSize, magicless);
    }

    private static native long getFrameContentSize0(byte[] src, int srcPosition, int srcSize, boolean magicless);

    /**
     * Return the original size of a compressed buffer (if known)
     *
     * @param src the compressed buffer
     * @param srcPosition offset of the compressed data inside the src buffer
     * @param srcSize length of the compressed data inside the src buffer
     * @param magicless whether the buffer contains a magicless frame
     * @return the number of bytes of the original buffer
     *         0 if the original size is not known
     * @deprecated
     * Use `getFrameContentSize` to also return error codes from zstd
     */
    @Deprecated
    public static long decompressedSize(byte[] src, int srcPosition, int srcSize, boolean magicless) {
        if (srcPosition >= src.length) {
            throw new ArrayIndexOutOfBoundsException(srcPosition);
        }
        if (srcPosition + srcSize > src.length) {
            throw new ArrayIndexOutOfBoundsException(srcPosition + srcSize);
        }
        return decompressedSize0(src, srcPosition, srcSize, magicless);
    }

    private static native long decompressedSize0(byte[] src, int srcPosition, int srcSize, boolean magicless);

    /**
     * Return the original size of a compressed buffer (if known)
     *
     * @param src the compressed buffer
     * @param srcPosition offset of the compressed data inside the src buffer
     * @param srcSize length of the compressed data inside the src buffer
     * @return the number of bytes of the original buffer
     *         0 if the original size is not known,
     *         negative if there is an error decoding the frame header
     */
    public static long getFrameContentSize(byte[] src, int srcPosition, int srcSize) {
        return getFrameContentSize(src, srcPosition, srcSize, false);
    }

    /**
     * Return the original size of a compressed buffer (if known)
     *
     * @param src the compressed buffer
     * @param srcPosition offset of the compressed data inside the src buffer
     * @param srcSize length of the compressed data inside the src buffer
     * @return the number of bytes of the original buffer
     *         0 if the original size is not known
     * @deprecated
     * Use `getFrameContentSize` to also return error codes from zstd
     */
    @Deprecated
    public static long decompressedSize(byte[] src, int srcPosition, int srcSize) {
        return decompressedSize(src, srcPosition, srcSize, false);
    }

    /**
     * Return the original size of a compressed buffer (if known)
     *
     * @param src the compressed buffer
     * @param srcPosition offset of the compressed data inside the src buffer
     * @return the number of bytes of the original buffer
     *         0 if the original size is not known,
     *         negative if there is an error decoding the frame header
     */
    public static long getFrameContentSize(byte[] src, int srcPosition) {
        return getFrameContentSize(src, srcPosition, src.length - srcPosition);
    }

    /**
     * Return the original size of a compressed buffer (if known)
     *
     * @param src the compressed buffer
     * @param srcPosition offset of the compressed data inside the src buffer
     * @return the number of bytes of the original buffer
     *         0 if the original size is not known
     * @deprecated
     * Use `getFrameContentSize` to also return error codes from zstd
     */
    @Deprecated
    public static long decompressedSize(byte[] src, int srcPosition) {
        return decompressedSize(src, srcPosition, src.length - srcPosition);
    }

    /**
     * Return the original size of a compressed buffer (if known)
     *
     * @param src the compressed buffer
     * @return the number of bytes of the original buffer
     *         0 if the original size is not known,
     *         negative if there is an error decoding the frame header
     */
    public static long getFrameContentSize(byte[] src) {
        return getFrameContentSize(src, 0);
    }

    /**
     * Return the original size of a compressed buffer (if known)
     *
     * @param src the compressed buffer
     * @return the number of bytes of the original buffer
     *         0 if the original size is not known
     * @deprecated
     * Use `getFrameContentSize` to also return error codes from zstd
     */
    @Deprecated
    public static long decompressedSize(byte[] src) {
        return decompressedSize(src, 0);
    }

    /**
     * Return the original size of a compressed buffer (if known)
     *
     * @param src the compressed buffer
     * @param srcPosition offset of the compressed data inside the src buffer
     * @param srcSize length of the compressed data inside the src buffe
     * @param magicless whether the buffer contains a magicless frame
     * @return the number of bytes of the original buffer
     *         0 if the original size is not known
     * @deprecated
     * Use `getDirectByteBufferFrameContentSize` to also return error codes from zstd
     */
    @Deprecated
    public static native long decompressedDirectByteBufferSize(ByteBuffer src, int srcPosition, int srcSize, boolean magicless);

    /**
     * Return the original size of a compressed buffer (if known)
     *
     * @param src the compressed buffer
     * @param srcPosition offset of the compressed data inside the src buffer
     * @param srcSize length of the compressed data inside the src buffe
     * @param magicless whether the buffer contains a magicless frame
     * @return the number of bytes of the original buffer
     *         0 if the original size is not known
     *         negative if there is an error decoding the frame header
     */
    public static native long getDirectByteBufferFrameContentSize(ByteBuffer src, int srcPosition, int srcSize, boolean magicless);

    /**
     * Return the original size of a compressed buffer (if known)
     *
     * @param src the compressed buffer
     * @param srcPosition offset of the compressed data inside the src buffer
     * @param srcSize length of the compressed data inside the src buffe
     * @return the number of bytes of the original buffer
     *         0 if the original size is not known
     * @deprecated
     * Use `getDirectByteBufferFrameContentSize` that return also the errors
     */
    @Deprecated
    public static long decompressedDirectByteBufferSize(ByteBuffer src, int srcPosition, int srcSize) {
        return decompressedDirectByteBufferSize(src, srcPosition, srcSize, false);
    }

    /**
     * Return the original size of a compressed buffer (if known)
     *
     * @param src the compressed buffer
     * @param srcPosition offset of the compressed data inside the src buffer
     * @param srcSize length of the compressed data inside the src buffe
     * @return the number of bytes of the original buffer
     *         0 if the original size is not known
     *         negative if there is an error decoding the frame header
     */
    public static long getDirectByteBufferFrameContentSize(ByteBuffer src, int srcPosition, int srcSize) {
        return getDirectByteBufferFrameContentSize(src, srcPosition, srcSize, false);
    }

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
     * @return if the return code signals an error
     */

    public static native boolean isError(long code);
    public static native String  getErrorName(long code);
    public static native long    getErrorCode(long code);


    /* Stable constants from the zstd_errors header */
    public static native long errNoError();
    public static native long errGeneric();
    public static native long errPrefixUnknown();
    public static native long errVersionUnsupported();
    public static native long errFrameParameterUnsupported();
    public static native long errFrameParameterWindowTooLarge();
    public static native long errCorruptionDetected();
    public static native long errChecksumWrong();
    public static native long errDictionaryCorrupted();
    public static native long errDictionaryWrong();
    public static native long errDictionaryCreationFailed();
    public static native long errParameterUnsupported();
    public static native long errParameterOutOfBound();
    public static native long errTableLogTooLarge();
    public static native long errMaxSymbolValueTooLarge();
    public static native long errMaxSymbolValueTooSmall();
    public static native long errStageWrong();
    public static native long errInitMissing();
    public static native long errMemoryAllocation();
    public static native long errWorkSpaceTooSmall();
    public static native long errDstSizeTooSmall();
    public static native long errSrcSizeWrong();
    public static native long errDstBufferNull();

    /**
     * Creates a new dictionary to tune a kind of samples
     *
     * @param samples the samples buffer array
     * @param dictBuffer the new dictionary buffer
     * @param legacy  use the legacy training algorithm; otherwise cover
     * @return the number of bytes into buffer 'dictBuffer' or an error code if
     *          it fails (which can be tested using ZSTD_isError())
     */
    public static long trainFromBuffer(byte[][] samples, byte[] dictBuffer, boolean legacy) {
        return trainFromBuffer(samples, dictBuffer, legacy, defaultCompressionLevel());
    }

    /**
     * Creates a new dictionary to tune a kind of samples
     *
     * @param samples the samples buffer array
     * @param dictBuffer the new dictionary buffer
     * @param legacy  use the legacy training algorithm; otherwise cover
     * @param compressionLevel  optimal if using the same level as when compressing.
     * @return the number of bytes into buffer 'dictBuffer' or an error code if
     *          it fails (which can be tested using ZSTD_isError())
     */
    public static long trainFromBuffer(byte[][] samples, byte[] dictBuffer, boolean legacy, int compressionLevel) {
        if (samples.length <= 10) {
            throw new ZstdException(Zstd.errGeneric(), "nb of samples too low");
        }
        return trainFromBuffer0(samples, dictBuffer, legacy, compressionLevel);
    }
    private static native long trainFromBuffer0(byte[][] samples, byte[] dictBuffer, boolean legacy, int compressionLevel);

    /**
     * Creates a new dictionary to tune a kind of samples
     *
     * @param samples the samples direct byte buffer array
     * @param sampleSizes java integer array of sizes
     * @param dictBuffer the new dictionary buffer (preallocated direct byte buffer)
     * @param legacy  use the legacy training algorithm; oter
     * @return the number of bytes into buffer 'dictBuffer' or an error code if
     *          it fails (which can be tested using ZSTD_isError())
     */
    public static long trainFromBufferDirect(ByteBuffer samples, int[] sampleSizes, ByteBuffer dictBuffer, boolean legacy) {
	return trainFromBufferDirect(samples, sampleSizes, dictBuffer, legacy, defaultCompressionLevel());
    }

    /**
     * Creates a new dictionary to tune a kind of samples
     *
     * @param samples the samples direct byte buffer array
     * @param sampleSizes java integer array of sizes
     * @param dictBuffer the new dictionary buffer (preallocated direct byte buffer)
     * @param legacy  use the legacy training algorithm; oter
     * @param compressionLevel  optimal if using the same level as when compressing.
     * @return the number of bytes into buffer 'dictBuffer' or an error code if
     *          it fails (which can be tested using ZSTD_isError())
     */
    public static long trainFromBufferDirect(ByteBuffer samples, int[] sampleSizes, ByteBuffer dictBuffer, boolean legacy, int compressionLevel) {
        if (sampleSizes.length <= 10) {
            throw new ZstdException(Zstd.errGeneric(), "nb of samples too low");
        }
        return trainFromBufferDirect0(samples, sampleSizes, dictBuffer, legacy, compressionLevel);
    }


    private static native long trainFromBufferDirect0(ByteBuffer samples, int[] sampleSizes, ByteBuffer dictBuffer, boolean legacy, int compressionLevel);

    /**
     * Get DictId from a compressed frame
     *
     * @param src compressed frame
     * @return DictId or 0 if not available
     */
    public static native long getDictIdFromFrame(byte[] src);

    /**
     * Get DictId from a compressed ByteBuffer frame
     *
     * @param src compressed frame
     * @return DictId or 0 if not available
     */
    public static native long getDictIdFromFrameBuffer(ByteBuffer src);

    /**
     * Get DictId of a dictionary
     *
     * @param dict dictionary
     * @return DictId or 0 if not available
     */
    public static native long getDictIdFromDict(byte[] dict);

    private static native long getDictIdFromDictDirect(ByteBuffer dict, int offset, int length);

    /**
     * Get DictId of a dictionary
     *
     * @param dict dictionary as Direct ByteBuffer
     * @return DictId or 0 if not available
     */
    public static long getDictIdFromDictDirect(ByteBuffer dict) {
	int length = dict.limit() - dict.position();
        if (!dict.isDirect()) {
            throw new IllegalArgumentException("dict must be a direct buffer");
        }
        if (length < 0) {
            throw new IllegalArgumentException("dict cannot be empty.");
        }
	return getDictIdFromDictDirect(dict, dict.position(), length);
    }

    /* Stub methods for backward comatibility
     */

    /**
     * Creates a new dictionary to tune a kind of samples (uses Cover algorithm)
     *
     * @param samples the samples buffer array
     * @param dictBuffer the new dictionary buffer
     * @return the number of bytes into buffer 'dictBuffer' or an error code if
     *          it fails (which can be tested using ZSTD_isError())
     */
    public static long trainFromBuffer(byte[][] samples, byte[] dictBuffer) {
        return trainFromBuffer(samples, dictBuffer, false);
    }

    /**
     * Creates a new dictionary to tune a kind of samples (uses Cover algorithm)
     *
     * @param samples the samples direct byte buffer array
     * @param sampleSizes java integer array of sizes
     * @param dictBuffer the new dictionary buffer (preallocated direct byte buffer)
     * @return the number of bytes into buffer 'dictBuffer' or an error code if
     *          it fails (which can be tested using ZSTD_isError())
     */
    public static long trainFromBufferDirect(ByteBuffer samples, int[] sampleSizes, ByteBuffer dictBuffer) {
        return trainFromBufferDirect(samples, sampleSizes, dictBuffer, false);
    }

    /* Constants from the zstd_static header */
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
    public static native int blockSizeMax();
    public static native int defaultCompressionLevel();
    /* Min/max compression levels */
    public static native int minCompressionLevel();
    public static native int maxCompressionLevel();



    /* Convenience methods */

    /**
     * Compresses the data in buffer 'src' using default compression level
     *
     * @param src the source buffer
     * @return byte array with the compressed data
     */
    public static byte[] compress(byte[] src) {
        return compress(src, defaultCompressionLevel());
    }

    /**
     * Compresses the data in buffer 'src'
     *
     * @param src the source buffer
     * @param level compression level
     * @return byte array with the compressed data
     */
    public static byte[] compress(byte[] src, int level) {
        ZstdCompressCtx ctx = new ZstdCompressCtx();
        try {
            ctx.setLevel(level);
            return ctx.compress(src);
        } finally {
            ctx.close();
        }
    }

    /**
     * Compresses the data in buffer 'srcBuf' using default compression level
     *
     * @param dstBuf the destination buffer.  must be direct.  It is assumed that the position() of this buffer marks the offset
     *               at which the compressed data are to be written, and that the limit() of this buffer is the maximum
     *               compressed data size to allow.
     *               <p>
     *               When this method returns successfully, dstBuf's position() will be set to its current position() plus the
     *               compressed size of the data.
     *               </p>
     * @param srcBuf the source buffer.  must be direct.  It is assumed that the position() of this buffer marks the beginning of the
     *               uncompressed data to be compressed, and that the limit() of this buffer marks its end.
     *               <p>
     *               When this method returns successfully, srcBuf's position() will be set to its limit().
     *               </p>
     * @return the size of the compressed data
     */

    public static int compress(ByteBuffer dstBuf, ByteBuffer srcBuf) {
        return compress(dstBuf, srcBuf, defaultCompressionLevel());
    }

    /**
     * Compresses the data in buffer 'srcBuf'
     *
     * @param dstBuf the destination buffer.  must be direct.  It is assumed that the position() of this buffer marks the offset
     *               at which the compressed data are to be written, and that the limit() of this buffer is the maximum
     *               compressed data size to allow.
     *               <p>
     *               When this method returns successfully, dstBuf's position() will be set to its current position() plus the
     *               compressed size of the data.
     *               </p>
     * @param srcBuf the source buffer.  must be direct.  It is assumed that the position() of this buffer marks the beginning of the
     *               uncompressed data to be compressed, and that the limit() of this buffer marks its end.
     *               <p>
     *               When this method returns successfully, srcBuf's position() will be set to its limit().
     *               </p>
     * @param level compression level
     * @return the size of the compressed data
     */
    public static int compress(ByteBuffer dstBuf, ByteBuffer srcBuf, int level, boolean checksumFlag) {
        ZstdCompressCtx ctx = new ZstdCompressCtx();
        try {
            ctx.setLevel(level);
            ctx.setChecksum(checksumFlag);
            return ctx.compress(dstBuf, srcBuf);
        } finally {
            ctx.close();
        }

        /*

        if (!srcBuf.isDirect()) {
            throw new IllegalArgumentException("srcBuf must be a direct buffer");
        }

        if (!dstBuf.isDirect()) {
            throw new IllegalArgumentException("dstBuf must be a direct buffer");
        }

        long size = compressDirectByteBuffer(dstBuf, // compress into dstBuf
                dstBuf.position(),                   // write compressed data starting at offset position()
                dstBuf.limit() - dstBuf.position(),  // write no more than limit() - position() bytes
                srcBuf,                              // read data to compress from srcBuf
                srcBuf.position(),                   // start reading at position()
                srcBuf.limit() - srcBuf.position(),  // read limit() - position() bytes
                level,                               // use this compression level
                checksumFlag);                       // enable or disable checksum based on this flag
        if (isError(size)) {
            throw new ZstdException(size);
        }
        srcBuf.position(srcBuf.limit());
        dstBuf.position(dstBuf.position() + (int) size);
        return (int) size;
        */
    }

    public static int compress(ByteBuffer dstBuf, ByteBuffer srcBuf, int level) {
        return compress(dstBuf, srcBuf, level, false);
    }

    /**
     * Compresses the data in buffer 'srcBuf'
     *
     * @param srcBuf the source buffer.  must be direct.  It is assumed that the position() of this buffer marks the beginning of the
     *               uncompressed data to be compressed, and that the limit() of this buffer marks its end.
     *               <p>
     *               When this method returns successfully, srcBuf's position() will be set to its limit().
     *               </p>
     * @param level compression level
     * @return A newly allocated direct ByteBuffer containing the compressed data.
     */
    public static ByteBuffer compress(ByteBuffer srcBuf, int level) {
        ZstdCompressCtx ctx = new ZstdCompressCtx();
        try {
            ctx.setLevel(level);
            return ctx.compress(srcBuf);
        } finally {
            ctx.close();
        }
    }

    /**
     * Compresses the data in buffer 'src'
     *
     * @param src the source buffer
     * @param dict dictionary to use
     * @return byte array with the compressed data
     */
    public static byte[] compress(byte[] src, ZstdDictCompress dict) {
        ZstdCompressCtx ctx = new ZstdCompressCtx();
        try {
            ctx.loadDict(dict);
            ctx.setLevel(dict.level());
            return ctx.compress(src);
        } finally {
            ctx.close();
        }
    }

   /**
     * Compresses buffer 'src' into buffer 'dst' with dictionary.
     *
     * @deprecated
     * Use compress(dst, src, dict, level) instead
     */
    @Deprecated
    public static long compressUsingDict(byte[] dst, byte[] src, byte[] dict, int level) {
        return compressUsingDict(dst, 0, src, 0, src.length, dict, level);
    }

   /**
     * Compresses buffer 'src' with dictionary.
     *
     * @param src the source buffer
     * @param dict the dictionary buffer
     * @param level compression level
     * @return  compressed byte array
     */

    public static byte[] compressUsingDict(byte[] src, byte[] dict, int level) {
        ZstdCompressCtx ctx = new ZstdCompressCtx();
        try {
            ctx.loadDict(dict);
            ctx.setLevel(level);
            return ctx.compress(src);
        } finally {
            ctx.close();
        }
    }

   /**
     * Compresses buffer 'src' into buffer 'dst' with dictionary.
     *
     * Destination buffer should be sized to handle worst cases situations (input
     * data not compressible). Worst case size evaluation is provided by function
     * ZSTD_compressBound().
     *
     * @param dst the destination buffer
     * @param src the source buffer
     * @param dict the dictionary buffer
     * @param level compression level
     * @return  the number of bytes written into buffer 'dst' or an error code if
     *          it fails (which can be tested using ZSTD_isError())
     */
    public static long compress(byte[] dst, byte[] src, byte[] dict, int level) {
        return compressUsingDict(dst, 0, src, 0, src.length, dict, level);
    }

   /**
     * Compresses buffer 'src' into buffer 'dst' with dictionary.
     *
     * Destination buffer should be sized to handle worst cases situations (input
     * data not compressible). Worst case size evaluation is provided by function
     * ZSTD_compressBound().
     *
     * @param dstBuff the destination buffer
     * @param srcBuff the source buffer
     * @param dict the dictionary buffer
     * @param level compression level
     * @return  the number of bytes written into buffer 'dstBuff'
     */
    public static int compress(ByteBuffer dstBuff, ByteBuffer srcBuff, byte[] dict, int level) {
        ZstdCompressCtx ctx = new ZstdCompressCtx();
        try {
            ctx.loadDict(dict);
            ctx.setLevel(level);
            return ctx.compress(dstBuff, srcBuff);
        } finally {
            ctx.close();
        }
    }

   /**
     * Compresses buffer 'src' into buffer 'dst' with dictionary.
     *
     * Destination buffer should be sized to handle worst cases situations (input
     * data not compressible). Worst case size evaluation is provided by function
     * ZSTD_compressBound().
     *
     * @param srcBuff the source buffer
     * @param dict the dictionary buffer
     * @param level compression level
     * @return  compressed direct byte buffer
     */
    public static ByteBuffer compress(ByteBuffer srcBuff, byte[] dict, int level) {
        ZstdCompressCtx ctx = new ZstdCompressCtx();
        try {
            ctx.loadDict(dict);
            ctx.setLevel(level);
            return ctx.compress(srcBuff);
        } finally {
            ctx.close();
        }
    }

    /**
     * Compresses buffer 'src' into buffer 'dst' with dictionary.
     *
     * Destination buffer should be sized to handle worst cases situations (input
     * data not compressible). Worst case size evaluation is provided by function
     * ZSTD_compressBound().
     *
     * @param dstBuff the destination buffer
     * @param srcBuff the source buffer
     * @param dict the dictionary buffer
     * @return  the number of bytes written into buffer 'dstBuff'
     */
    public static int compress(ByteBuffer dstBuff, ByteBuffer srcBuff, ZstdDictCompress dict) {
        ZstdCompressCtx ctx = new ZstdCompressCtx();
        try {
            ctx.loadDict(dict);
            ctx.setLevel(dict.level());
            return ctx.compress(dstBuff, srcBuff);
        } finally {
            ctx.close();
        }
    }

   /**
     * Compresses buffer 'src' into buffer 'dst' with dictionary.
     *
     * Destination buffer should be sized to handle worst cases situations (input
     * data not compressible). Worst case size evaluation is provided by function
     * ZSTD_compressBound().
     *
     * @param srcBuff the source buffer
     * @param dict the dictionary buffer
     * @return  compressed direct byte buffer
     */
    public static ByteBuffer compress(ByteBuffer srcBuff, ZstdDictCompress dict) {
        ZstdCompressCtx ctx = new ZstdCompressCtx();
        try {
            ctx.loadDict(dict);
            ctx.setLevel(dict.level());
            return ctx.compress(srcBuff);
        } finally {
            ctx.close();
        }
    }

    /**
     * Decompress data, assuming that whole buffer is a compressed data
     *
     * @param src the source buffer
     * @param originalSize the maximum size of the uncompressed data.
     *                  If originalSize is greater than the actual uncompressed size, additional memory copy going to happen.
     *                  If originalSize is smaller than the uncompressed size, {@link ZstdException} will be thrown.
     * @return byte array with the decompressed data
     */
    public static byte[] decompress(byte[] src, int originalSize) {
        ZstdDecompressCtx ctx = new ZstdDecompressCtx();
        try {
            return ctx.decompress(src, originalSize);
        } finally {
            ctx.close();
        }
    }

    /**
     * Decompress data, assuming that whole buffer is a compressed data.
     * <p>
     * Note, that file must be encoded with pledged content size, not using stream API.
     * For more, see ZSTD_c_contentSizeFlag flag description.
     * </p>
     *
     * @param src the source buffer
     * @return byte array with the decompressed data
     */
    public static byte[] decompress(byte[] src) {
        List<FrameData> frames = new ArrayList<>();

        int contentSize = calculateContentSizeAndFrames(src, frames);

        byte[] decompressedData = new byte[contentSize];

        int srcPosition = 0;
        int decompressedPosition = 0;
        for (int i = 0; i < frames.size(); i++) {
            FrameData frameInfo = frames.get(i);
            long size = decompressByteArray(decompressedData, decompressedPosition, (int) frameInfo.contentSize, src, srcPosition, (int) frameInfo.compressedSize);
            if (Zstd.isError(size)) {
                throw new ZstdException(size, String.format("error %s while decompressing %d frame", Zstd.getErrorName(size), i));
            }

            if (size != frameInfo.contentSize) {
                throw new IllegalStateException("decompressed size mismatch");
            }

            srcPosition += (int) frameInfo.compressedSize;
            decompressedPosition += (int) frameInfo.contentSize;
        }

        return decompressedData;
    }

    private static int calculateContentSizeAndFrames(byte[] src, List<FrameData> frames) {
        int contentSize = 0;

        int srcPosition = 0;

        while (srcPosition < src.length) {
            FrameData frameData = new FrameData(src, srcPosition);

            frames.add(frameData);

            srcPosition += (int) frameData.compressedSize;
            contentSize += (int) frameData.contentSize;
        }

        return contentSize;
    }

    /**
     * Decompress data, using only single frame from offset.
     *
     * @param src the source buffer
     * @param srcOffset the start offset of 'src'
     * @param srcSize the size of 'src'
     * @param originalSize the maximum size of the uncompressed data.
     *                  If originalSize is greater than the actual uncompressed size, additional memory copy going to happen.
     *                  If originalSize is smaller than the uncompressed size, {@link ZstdException} will be thrown.
     * @return byte array with the decompressed data
     */
    public static byte[] decompressFrame(byte[] src, int srcOffset, int srcSize, int originalSize) {
        ZstdDecompressCtx ctx = new ZstdDecompressCtx();
        try {
            return ctx.decompress(src, srcOffset, srcSize, originalSize);
        } finally {
            ctx.close();
        }
    }

    /**
     * Decompress data, using only single frame from offset.
     *
     * @param src the source buffer
     * @param srcOffset the start offset of 'src'
     * @return byte array with the decompressed data
     */
    public static byte[] decompressFrame(byte[] src, int srcOffset) {
        int compressedSize = (int) findFrameCompressedSize(src, srcOffset);
        long contentSize = getFrameContentSize(src, srcOffset, compressedSize);
        if (Zstd.isError(contentSize)) {
            // known error at the moment, but not for getErrorName
            if (contentSize == -1) {
                throw new ZstdException(contentSize, "Content size is unknown");
            }
            // otherwise let ZstdException get error message itself
            throw new ZstdException(contentSize);
        }

        return decompressFrame(src, srcOffset, compressedSize, (int) contentSize);
    }

    /**
     * Decompress data, using only first frame from offset.
     *
     * @param src the source buffer
     * @return byte array with the decompressed data
     */
    public static byte[] decompressFrame(byte[] src) {
        return decompressFrame(src, 0);
    }

    /**
     * Decompress data
     *
     * @param dstBuf the destination buffer.  must be direct.  It is assumed that the position() of this buffer marks the offset
     *               at which the decompressed data are to be written, and that the limit() of this buffer is the maximum
     *               decompressed data size to allow.
     *               <p>
     *               When this method returns successfully, dstBuf's position() will be set to its current position() plus the
     *               decompressed size of the data.
     *               </p>
     * @param srcBuf the source buffer.  must be direct.  It is assumed that the position() of this buffer marks the beginning of the
     *               compressed data to be decompressed, and that the limit() of this buffer marks its end.
     *               <p>
     *               When this method returns successfully, srcBuf's position() will be set to its limit().
     *               </p>
     * @return the size of the decompressed data.
     */
    public static int decompress(ByteBuffer dstBuf, ByteBuffer srcBuf) {
        ZstdDecompressCtx ctx = new ZstdDecompressCtx();
        try {
            return ctx.decompress(dstBuf, srcBuf);
        } finally {
            ctx.close();
        }
    }

    public static int decompress(ByteBuffer dstBuf, byte[] src) {
        ZstdDecompressCtx ctx = new ZstdDecompressCtx();
        try {
            return ctx.decompress(dstBuf, src);
        } finally {
            ctx.close();
        }
    }

    /**
     * Decompress data
     *
     * @param srcBuf the source buffer.  must be direct.  It is assumed that the position() of this buffer marks the beginning of the
     *               compressed data to be decompressed, and that the limit() of this buffer marks its end.
     *               <p>
     *               When this method returns successfully, srcBuf's position() will be set to its limit().
     *               </p>
     * @param originalSize the maximum size of the uncompressed data
     * @return A newly-allocated ByteBuffer containing the decompressed data.  The position() of this buffer will be 0,
     *          and the limit() will be the size of the decompressed data.  In other words the buffer is ready to be used for
     *          reading.  Note that this is different behavior from the other decompress() overload which takes as a parameter
     *          the destination ByteBuffer.
     */
    public static ByteBuffer decompress(ByteBuffer srcBuf, int originalSize) {
        ZstdDecompressCtx ctx = new ZstdDecompressCtx();
        try {
            return ctx.decompress(srcBuf, originalSize);
        } finally {
            ctx.close();
        }
    }

    /**
     * Decompress data
     *
     * @param src the source buffer
     * @param dict dictionary to use
     * @param originalSize the maximum size of the uncompressed data
     * @return byte array with the decompressed data
     */
    public static byte[] decompress(byte[] src, ZstdDictDecompress dict, int originalSize) {
        ZstdDecompressCtx ctx = new ZstdDecompressCtx();
        try {
            ctx.loadDict(dict);
            return ctx.decompress(src, originalSize);
        } finally {
            ctx.close();
        }
    }

    /**
     * Decompresses buffer 'src' into buffer 'dst' with dictionary.
     *
     * @deprecated
     * Use decompress(dst, src, dict) instead
     */
    @Deprecated
    public static long decompressUsingDict(byte[] dst, byte[] src, byte[] dict) {
        return decompressUsingDict(dst, 0, src, 0, src.length, dict);
    }

    /**
     * Decompresses buffer 'src' into buffer 'dst' with dictionary.
     *
     * Destination buffer should be sized to be larger of equal to the originalSize
     *
     * @param dst the destination buffer
     * @param src the source buffer
     * @param dict the dictionary buffer
     * @return the number of bytes decompressed into destination buffer (originalSize)
     *          or an errorCode if it fails (which can be tested using ZSTD_isError())
     */
    public static long decompress(byte[] dst, byte[] src, byte[] dict) {
        return decompressUsingDict(dst, 0, src, 0, src.length, dict);
    }

    /**
     * @param src the source buffer
     * @param dict dictionary to use
     * @param originalSize the maximum size of the uncompressed data
     * @return byte array with the decompressed data
     */
    public static byte[] decompress(byte[] src, byte[] dict, int originalSize) {
        ZstdDecompressCtx ctx = new ZstdDecompressCtx();
        try {
            ctx.loadDict(dict);
            return ctx.decompress(src, originalSize);
        } finally {
            ctx.close();
        }
    }

    /**
     * Return the original size of a compressed buffer (if known)
     *
     * @param srcBuf the compressed buffer.  must be direct.  It is assumed that the position() of this buffer marks the beginning of the
     *               compressed data whose decompressed size is being queried, and that the limit() of this buffer marks its
     *               end.
     * @return the number of bytes of the original buffer
     *         0 if the original size is not known
     * @deprecated
     * Use `getDirectByteBufferFrameContentSize` that return also the errors
     */
    @Deprecated
    public static long decompressedSize(ByteBuffer srcBuf) {
        return decompressedDirectByteBufferSize(srcBuf, srcBuf.position(), srcBuf.limit() - srcBuf.position());
    }

    /**
     * Return the original size of a compressed buffer (if known)
     *
     * @param srcBuf the compressed buffer.  must be direct.  It is assumed that the position() of this buffer marks the beginning of the
     *               compressed data whose decompressed size is being queried, and that the limit() of this buffer marks its
     *               end.
     * @return the number of bytes of the original buffer
     *         0 if the original size is not known
     *         negative if there is an error decoding the frame header
     */
    public static long getFrameContentSize(ByteBuffer srcBuf) {
        return getDirectByteBufferFrameContentSize(srcBuf, srcBuf.position(), srcBuf.limit() - srcBuf.position());
    }

    /**
     * Decompress data
     *
     * @param dstBuff the destination buffer.  must be direct.  It is assumed that the position() of this buffer marks the offset
     *               at which the decompressed data are to be written, and that the limit() of this buffer is the maximum
     *               decompressed data size to allow.
     *               <p>
     *               When this method returns successfully, dstBuff's position() will be set to its current position() plus the
     *               decompressed size of the data.
     *               </p>
     * @param srcBuff the source buffer.  must be direct.  It is assumed that the position() of this buffer marks the beginning of the
     *               compressed data to be decompressed, and that the limit() of this buffer marks its end.
     *               <p>
     *               When this method returns successfully, srcBuff's position() will be set to its limit().
     *               </p>
     * @param dict   the dictionary buffer to use for compression
     * @return the size of the decompressed data.
     */
    public static int decompress(ByteBuffer dstBuff, ByteBuffer srcBuff, byte[] dict) {
        ZstdDecompressCtx ctx = new ZstdDecompressCtx();
        try {
            ctx.loadDict(dict);
            return ctx.decompress(dstBuff, srcBuff);
        } finally {
            ctx.close();
        }
    }

    /**
     * Decompress data
     *
     * @param srcBuff the source buffer.  must be direct.  It is assumed that the position() of this buffer marks the beginning of the
     *               compressed data to be decompressed, and that the limit() of this buffer marks its end.
     *               <p>
     *               When this method returns successfully, srcBuff's position() will be set to its limit().
     *               </p>
     * @param dict   the dictionary used in the compression
     * @param originalSize the maximum size of the uncompressed data
     * @return A newly-allocated ByteBuffer containing the decompressed data.  The position() of this buffer will be 0,
     *          and the limit() will be the size of the decompressed data.  In other words the buffer is ready to be used for
     *          reading.  Note that this is different behavior from the other decompress() overload which takes as a parameter
     *          the destination ByteBuffer.
     */
    public static ByteBuffer decompress(ByteBuffer srcBuff, byte[] dict, int originalSize) {
        ZstdDecompressCtx ctx = new ZstdDecompressCtx();
        try {
            ctx.loadDict(dict);
            return ctx.decompress(srcBuff, originalSize);
        } finally {
            ctx.close();
        }
    }

    /**
     * Decompress data
     *
     * @param dstBuff the destination buffer.  must be direct.  It is assumed that the position() of this buffer marks the offset
     *               at which the decompressed data are to be written, and that the limit() of this buffer is the maximum
     *               decompressed data size to allow.
     *               <p>
     *               When this method returns successfully, dstBuff's position() will be set to its current position() plus the
     *               decompressed size of the data.
     *               </p>
     * @param srcBuff the source buffer.  must be direct.  It is assumed that the position() of this buffer marks the beginning of the
     *               compressed data to be decompressed, and that the limit() of this buffer marks its end.
     *               <p>
     *               When this method returns successfully, srcBuff's position() will be set to its limit().
     *               </p>
     * @param dict   the dictionary buffer to use for compression
     * @return the size of the decompressed data.
     */
    public static int decompress(ByteBuffer dstBuff, ByteBuffer srcBuff, ZstdDictDecompress dict) {
        ZstdDecompressCtx ctx = new ZstdDecompressCtx();
        try {
            ctx.loadDict(dict);
            return ctx.decompress(dstBuff, srcBuff);
        } finally {
            ctx.close();
        }
    }

    /**
     * Decompress data
     *
     * @param srcBuff the source buffer.  must be direct.  It is assumed that the position() of this buffer marks the beginning of the
     *               compressed data to be decompressed, and that the limit() of this buffer marks its end.
     *               <p>
     *               When this method returns successfully, srcBuff's position() will be set to its limit().
     *               </p>
     * @param dict   the dictionary used in the compression
     * @param originalSize the maximum size of the uncompressed data
     * @return A newly-allocated ByteBuffer containing the decompressed data. The position() of this buffer will be 0,
     *          and the limit() will be the size of the decompressed data. In other words the buffer is ready to be used for
     *          reading. Note that this is different behavior from the other decompress() overload which takes as a parameter
     *          the destination ByteBuffer.
     */
    public static ByteBuffer decompress(ByteBuffer srcBuff, ZstdDictDecompress dict, int originalSize) {
        ZstdDecompressCtx ctx = new ZstdDecompressCtx();
        try {
            ctx.loadDict(dict);
            return ctx.decompress(srcBuff, originalSize);
        } finally {
            ctx.close();
        }
    }

    static ByteBuffer getArrayBackedBuffer(BufferPool bufferPool, int size) throws ZstdIOException {
        ByteBuffer buffer = bufferPool.get(size);
        if (buffer == null) {
            throw new ZstdIOException(Zstd.errMemoryAllocation(), "Cannot get ByteBuffer of size " + size + " from the BufferPool");
        }
        if (!buffer.hasArray() || buffer.arrayOffset() != 0) {
            bufferPool.release(buffer);
            throw new IllegalArgumentException("provided ByteBuffer lacks array or has non-zero arrayOffset");
        }
        return buffer;
    }

    private static class FrameData {
        final long contentSize;
        final long compressedSize;

        FrameData(byte[] src, int srcPosition) {
            compressedSize = findFrameCompressedSize(src, srcPosition);
            contentSize = getFrameContentSize(src, srcPosition, (int) compressedSize);

            if (Zstd.isError(contentSize)) {
                // known error at the moment, but not for getErrorName
                if (contentSize == -1) {
                    throw new ZstdException(contentSize, "Content size is unknown");
                }
                // otherwise let ZstdException get error message itself
                throw new ZstdException(contentSize);
            }
        }
    }
}

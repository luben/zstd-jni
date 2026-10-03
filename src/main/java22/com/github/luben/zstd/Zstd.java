package com.github.luben.zstd;

import com.github.luben.zstd.util.Native;

import org.jetbrains.annotations.NotNull;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.ref.Reference;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Java 22 implementation using FFM for native operations.
 */
public class Zstd {
    private static final @NotNull String maxDecompressSizeOverride = "ZstdMaxDecompressSize";
    /**
     * Max memory size automatically allocated on decompression when no explicit content size is supplied.
     *
     * It can be controlled by the ZstdMaxDecompressSize property and defaults to 512MiB
     */
    public static final long MAX_DECOMPRESS_SIZE;

    static {
        Native.load();
        long configuredMax;
        try {
            String prop = System.getProperty(maxDecompressSizeOverride);
            configuredMax = (prop != null) ? Long.parseLong(prop) : 512L * 1024L * 1024L;
        } catch (NumberFormatException e) {
            configuredMax = 512L * 1024L * 1024L;
        }
        MAX_DECOMPRESS_SIZE = configuredMax;
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
    public static long compress(byte @NotNull [] dst, byte @NotNull [] src, int level, boolean checksumFlag) {
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
    public static long compress(byte @NotNull [] dst, byte @NotNull [] src, int level) {
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
    public static long compressByteArray(byte @NotNull [] dst, int dstOffset, int dstSize, byte @NotNull [] src, int srcOffset, int srcSize, int level, boolean checksumFlag) {
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
    public static long compressByteArray(byte @NotNull [] dst, int dstOffset, int dstSize, byte @NotNull [] src, int srcOffset, int srcSize, int level) {
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
    public static long compressDirectByteBuffer(@NotNull ByteBuffer dst, int dstOffset, int dstSize, @NotNull ByteBuffer src, int srcOffset, int srcSize, int level, boolean checksumFlag) {
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
    public static long compressDirectByteBuffer(@NotNull ByteBuffer dst, int dstOffset, int dstSize, @NotNull ByteBuffer src, int srcOffset, int srcSize, int level) {
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
    public static long compressUnsafe(long dst, long dstSize, long src, long srcSize, int level, boolean checksumFlag) {
        MemorySegment cctx = ZstdBinding.createCCtx();
        try {
            ZstdBinding.setCCtxParameter(cctx, ZstdBinding.ZSTD_C_COMPRESSION_LEVEL, level);
            ZstdBinding.setCCtxParameter(cctx, ZstdBinding.ZSTD_C_CHECKSUM_FLAG, checksumFlag ? 1 : 0);
            return ZstdBinding.compress2Native(cctx, MemorySegment.ofAddress(dst), dstSize, MemorySegment.ofAddress(src), srcSize);
        } finally {
            ZstdBinding.freeCCtx(cctx);
        }
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
    public static long compressUsingDict (byte @NotNull [] dst, int dstOffset, byte @NotNull [] src, int srcOffset, int length, byte @NotNull [] dict, int level) {
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
    public static long compressUsingDict (byte @NotNull [] dst, int dstOffset, byte @NotNull [] src, int srcOffset, byte @NotNull [] dict, int level) {
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
    public static long compressDirectByteBufferUsingDict(@NotNull ByteBuffer dst, int dstOffset, int dstSize, @NotNull ByteBuffer src, int srcOffset, int srcSize, byte @NotNull [] dict, int level) {
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
    public static long compressFastDict(byte @NotNull [] dst, int dstOffset, byte @NotNull [] src, int srcOffset, int length, @NotNull ZstdDictCompress dict) {
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
    public static long compressFastDict(byte @NotNull [] dst, int dstOffset, byte @NotNull [] src, int srcOffset, @NotNull ZstdDictCompress dict) {
        ZstdCompressCtx ctx = new ZstdCompressCtx();
        try {
            ctx.loadDict(dict);
            ctx.setLevel(dict.level());
            return (long) ctx.compressByteArray(dst, dstOffset, dst.length - dstOffset, src, srcOffset, src.length - srcOffset);
        } finally {
            ctx.close();
        }
    }

    public static long compress(byte @NotNull [] dst, byte @NotNull [] src, @NotNull ZstdDictCompress dict) {
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
    public static long compressDirectByteBufferFastDict(@NotNull ByteBuffer dst, int dstOffset, int dstSize, @NotNull ByteBuffer src, int srcOffset, int srcSize, @NotNull ZstdDictCompress dict) {
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
    public static long decompress(byte @NotNull [] dst, byte @NotNull [] src) {
        ZstdDecompressCtx ctx = new ZstdDecompressCtx();
        try {
            return (long) ctx.decompress(dst, src);
        } finally {
            ctx.close();
        }
    }

    public static int decompress(byte @NotNull [] dst, @NotNull ByteBuffer srcBuf) {
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
    public static long decompressByteArray(byte @NotNull [] dst, int dstOffset, int dstSize, byte @NotNull [] src, int srcOffset, int srcSize) {
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
    public static long decompressDirectByteBuffer(@NotNull ByteBuffer dst, int dstOffset, int dstSize, @NotNull ByteBuffer src, int srcOffset, int srcSize) {
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
    public static long decompressUnsafe(long dst, long dstSize, long src, long srcSize) {
        return ZstdBinding.decompress(MemorySegment.ofAddress(dst), dstSize, MemorySegment.ofAddress(src), srcSize);
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
     * @param dict the dictionary buffer
     * @return the number of bytes decompressed into destination buffer (originalSize)
     *          or an errorCode if it fails (which can be tested using ZSTD_isError())
     *
     */
    public static long decompressUsingDict(byte @NotNull [] dst, int dstOffset, byte @NotNull [] src, int srcOffset, int length, byte @NotNull [] dict) {
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
    public static long decompressDirectByteBufferUsingDict(@NotNull ByteBuffer dst, int dstOffset, int dstSize, @NotNull ByteBuffer src, int srcOffset, int srcSize, byte @NotNull [] dict) {
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
    public static long decompressFastDict(byte @NotNull [] dst, int dstOffset, byte @NotNull [] src, int srcOffset, int length, @NotNull ZstdDictDecompress dict) {
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
    public static long decompressDirectByteBufferFastDict(@NotNull ByteBuffer dst, int dstOffset, int dstSize, @NotNull ByteBuffer src, int srcOffset, int srcSize, @NotNull ZstdDictDecompress dict) {
        ZstdDecompressCtx ctx = new ZstdDecompressCtx();
        try {
            ctx.loadDict(dict);
            return (long) ctx.decompressDirectByteBuffer(dst, dstOffset, dstSize, src, srcOffset, srcSize);
        } finally {
            ctx.close();
        }
    }

    /* Advance API */
    public static int loadDictDecompress(long stream, byte @NotNull [] dict, int dict_size) {
        if (dict == null) {
            return -ZstdBinding.ZSTD_ERROR_DICTIONARY_WRONG;
        }
        return (int) ZstdBinding.loadDDictionary(
                MemorySegment.ofAddress(stream), MemorySegment.ofArray(dict), dict_size);
    }
    public static int loadFastDictDecompress(long stream, @NotNull ZstdDictDecompress dict) {
        if (dict == null) {
            return -ZstdBinding.ZSTD_ERROR_DICTIONARY_WRONG;
        }
        long pointer = dict.nativePtr();
        if (pointer == 0) {
            return -ZstdBinding.ZSTD_ERROR_DICTIONARY_WRONG;
        }
        try {
            return (int) ZstdBinding.refDDict(MemorySegment.ofAddress(stream), MemorySegment.ofAddress(pointer));
        } finally {
            // JNI kept its object argument alive through the native call as well.
            Reference.reachabilityFence(dict);
        }
    }
    public static int loadDictCompress(long stream, byte @NotNull [] dict, int dict_size) {
        if (dict == null) {
            return -ZstdBinding.ZSTD_ERROR_DICTIONARY_WRONG;
        }
        return (int) ZstdBinding.loadDictionary(
                MemorySegment.ofAddress(stream), MemorySegment.ofArray(dict), dict_size);
    }
    public static int loadFastDictCompress(long stream, @NotNull ZstdDictCompress dict) {
        if (dict == null) {
            return -ZstdBinding.ZSTD_ERROR_DICTIONARY_WRONG;
        }
        long pointer = dict.nativePtr();
        if (pointer == 0) {
            return -ZstdBinding.ZSTD_ERROR_DICTIONARY_WRONG;
        }
        try {
            return (int) ZstdBinding.refCDict(MemorySegment.ofAddress(stream), MemorySegment.ofAddress(pointer));
        } finally {
            // JNI kept its object argument alive through the native call as well.
            Reference.reachabilityFence(dict);
        }
    }
    public static void registerSequenceProducer(long stream, long seqProdState, long seqProdFunction) {
        ZstdBinding.registerSequenceProducer(
                MemorySegment.ofAddress(stream),
                MemorySegment.ofAddress(seqProdState),
                MemorySegment.ofAddress(seqProdFunction));
    }
    public static int setCompressionChecksums(long stream, boolean useChecksums) {
        return (int) ZstdBinding.setCCtxParameter(
                MemorySegment.ofAddress(stream),
                ZstdBinding.ZSTD_C_CHECKSUM_FLAG,
                useChecksums ? 1 : 0);
    }
    public static int setCompressionMagicless(long stream, boolean useMagicless) {
        return (int) ZstdBinding.setCCtxParameter(
                MemorySegment.ofAddress(stream),
                ZstdBinding.ZSTD_C_FORMAT,
                useMagicless ? ZstdBinding.ZSTD_F_ZSTD1_MAGICLESS : ZstdBinding.ZSTD_F_ZSTD1);
    }
    public static int setCompressionLevel(long stream, int level) {
        return (int) ZstdBinding.setCCtxParameter(
                MemorySegment.ofAddress(stream), ZstdBinding.ZSTD_C_COMPRESSION_LEVEL, level);
    }
    public static int setCompressionLong(long stream, int windowLog) {
        MemorySegment cctx = MemorySegment.ofAddress(stream);
        boolean enabled = windowLog >= ZstdBinding.ZSTD_WINDOWLOG_MIN
                && windowLog <= ZstdBinding.ZSTD_WINDOWLOG_LIMIT_DEFAULT;
        long result = ZstdBinding.setCCtxParameter(
                cctx,
                ZstdBinding.ZSTD_C_ENABLE_LONG_DISTANCE_MATCHING,
                enabled ? ZstdBinding.ZSTD_PS_ENABLE : ZstdBinding.ZSTD_PS_DISABLE);
        if (ZstdBinding.isErrorCode(result)) {
            return (int) result;
        }
        return (int) ZstdBinding.setCCtxParameter(cctx, ZstdBinding.ZSTD_C_WINDOW_LOG, enabled ? windowLog : 0);
    }
    public static int setCompressionWorkers(long stream, int workers) {
        return (int) ZstdBinding.setCCtxParameter(
                MemorySegment.ofAddress(stream), ZstdBinding.ZSTD_C_NB_WORKERS, workers);
    }
    public static int setCompressionOverlapLog(long stream, int overlapLog) {
        return (int) ZstdBinding.setCCtxParameter(
                MemorySegment.ofAddress(stream), ZstdBinding.ZSTD_C_OVERLAP_LOG, overlapLog);
    }
    public static int setCompressionJobSize(long stream, int jobSize) {
        return (int) ZstdBinding.setCCtxParameter(
                MemorySegment.ofAddress(stream), ZstdBinding.ZSTD_C_JOB_SIZE, jobSize);
    }
    public static int setCompressionTargetLength(long stream, int targetLength) {
        return (int) ZstdBinding.setCCtxParameter(
                MemorySegment.ofAddress(stream), ZstdBinding.ZSTD_C_TARGET_LENGTH, targetLength);
    }
    public static int setCompressionMinMatch(long stream, int minMatch) {
        return (int) ZstdBinding.setCCtxParameter(
                MemorySegment.ofAddress(stream), ZstdBinding.ZSTD_C_MIN_MATCH, minMatch);
    }
    public static int setCompressionSearchLog(long stream, int searchLog) {
        return (int) ZstdBinding.setCCtxParameter(
                MemorySegment.ofAddress(stream), ZstdBinding.ZSTD_C_SEARCH_LOG, searchLog);
    }
    public static int setCompressionChainLog(long stream, int chainLog) {
        return (int) ZstdBinding.setCCtxParameter(
                MemorySegment.ofAddress(stream), ZstdBinding.ZSTD_C_CHAIN_LOG, chainLog);
    }
    public static int setCompressionHashLog(long stream, int hashLog) {
        return (int) ZstdBinding.setCCtxParameter(
                MemorySegment.ofAddress(stream), ZstdBinding.ZSTD_C_HASH_LOG, hashLog);
    }
    public static int setCompressionWindowLog(long stream, int windowLog) {
        return (int) ZstdBinding.setCCtxParameter(
                MemorySegment.ofAddress(stream), ZstdBinding.ZSTD_C_WINDOW_LOG, windowLog);
    }
    public static int setCompressionStrategy(long stream, int strategy) {
        return (int) ZstdBinding.setCCtxParameter(
                MemorySegment.ofAddress(stream), ZstdBinding.ZSTD_C_STRATEGY, strategy);
    }
    public static int setDecompressionLongMax(long stream, int windowLogMax) {
        return (int) ZstdBinding.setDCtxParameter(
                MemorySegment.ofAddress(stream), ZstdBinding.ZSTD_D_WINDOW_LOG_MAX, windowLogMax);
    }
    public static int setDecompressionMagicless(long stream, boolean useMagicless) {
        return (int) ZstdBinding.setDCtxParameter(
                MemorySegment.ofAddress(stream),
                ZstdBinding.ZSTD_D_FORMAT,
                useMagicless ? ZstdBinding.ZSTD_F_ZSTD1_MAGICLESS : ZstdBinding.ZSTD_F_ZSTD1);
    }
    public static int setRefMultipleDDicts(long stream, boolean useMultiple) {
        return (int) ZstdBinding.setDCtxParameter(
                MemorySegment.ofAddress(stream),
                ZstdBinding.ZSTD_D_REF_MULTIPLE_DDICTS,
                useMultiple ? ZstdBinding.ZSTD_RMD_REF_MULTIPLE_DDICTS : ZstdBinding.ZSTD_RMD_REF_SINGLE_DDICT);
    }
    public static int setValidateSequences(long stream, int validateSequences) {
        return (int) ZstdBinding.setCCtxParameter(
                MemorySegment.ofAddress(stream), ZstdBinding.ZSTD_C_VALIDATE_SEQUENCES, validateSequences);
    }
    public static int setSequenceProducerFallback(long stream, boolean fallbackFlag) {
        return (int) ZstdBinding.setCCtxParameter(
                MemorySegment.ofAddress(stream),
                ZstdBinding.ZSTD_C_ENABLE_SEQ_PRODUCER_FALLBACK,
                fallbackFlag ? 1 : 0);
    }
    public static int setSearchForExternalRepcodes(long stream, int searchRepcodes) {
        return (int) ZstdBinding.setCCtxParameter(
                MemorySegment.ofAddress(stream), ZstdBinding.ZSTD_C_SEARCH_FOR_EXTERNAL_REPCODES, searchRepcodes);
    }
    public static int setEnableLongDistanceMatching(long stream, int enableLDM) {
        return (int) ZstdBinding.setCCtxParameter(
                MemorySegment.ofAddress(stream), ZstdBinding.ZSTD_C_ENABLE_LONG_DISTANCE_MATCHING, enableLDM);
    }

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
    public static long findFrameCompressedSize(byte @NotNull [] src, int srcPosition, int srcSize) {
        if (srcPosition < 0 || srcPosition >= src.length) {
            throw new ArrayIndexOutOfBoundsException(srcPosition);
        }
        if (srcSize < 0 || srcSize > src.length - srcPosition) {
            throw new ArrayIndexOutOfBoundsException(srcPosition + srcSize);
        }

        long size = findFrameCompressedSize0(src, srcPosition, srcSize);
        if (Zstd.isError(size)) {
            throw new ZstdException(size);
        }

        return size;
    }

    private static long findFrameCompressedSize0(byte @NotNull [] src, int srcPosition, int srcSize) {
        return ZstdBinding.findFrameCompressedSize(MemorySegment.ofArray(src).asSlice(srcPosition), srcSize);
    }

    /**
     * Return the compressed size of a frame within a buffer.
     *
     * @param src the compressed buffer
     * @param srcPosition offset of the compressed frame inside the src buffer
     * @return the number of bytes of the compressed frame
     *         negative if there is an error decoding the frame header
     */
    public static long findFrameCompressedSize(byte @NotNull [] src, int srcPosition) {
        return findFrameCompressedSize(src, srcPosition, src.length - srcPosition);
    }

    /**
     * Return the compressed size of a frame within a buffer.
     *
     * @param src the compressed buffer
     * @return the number of bytes of the compressed frame
     *         negative if there is an error decoding the frame header
     */
    public static long findFrameCompressedSize(byte @NotNull [] src) {
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
    public static long findFrameCompressedSize(@NotNull ByteBuffer srcBuf) {
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
    public static long findDirectByteBufferFrameCompressedSize(@NotNull ByteBuffer src, int srcPosition, int srcSize) {
        int capacity = src != null && src.isDirect() ? src.capacity() : -1;
        if (srcPosition < 0 || srcSize < 0 || srcPosition > capacity - srcSize) {
            return -ZstdBinding.ZSTD_ERROR_GENERIC;
        }
        MemorySegment source = fullBuffer(src);
        if (source.address() == 0) {
            return ZstdBinding.asSizeT(-ZstdBinding.ZSTD_ERROR_MEMORY_ALLOCATION);
        }
        return ZstdBinding.findFrameCompressedSizeNative(source.asSlice(srcPosition), srcSize);
    }

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
    public static long getFrameContentSize(byte @NotNull [] src, int srcPosition, int srcSize, boolean magicless) {
        if (srcPosition < 0 || srcPosition >= src.length) {
            throw new ArrayIndexOutOfBoundsException(srcPosition);
        }
        if (srcSize < 0 || srcSize > src.length - srcPosition) {
            throw new ArrayIndexOutOfBoundsException(srcPosition + srcSize);
        }
        return getFrameContentSize0(src, srcPosition, srcSize, magicless);
    }

    private static long getFrameContentSize0(byte @NotNull [] src, int srcPosition, int srcSize, boolean magicless) {
        return ZstdBinding.frameContentSize(MemorySegment.ofArray(src).asSlice(srcPosition), srcSize, magicless);
    }

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
    public static long decompressedSize(byte @NotNull [] src, int srcPosition, int srcSize, boolean magicless) {
        if (srcPosition < 0 || srcPosition >= src.length) {
            throw new ArrayIndexOutOfBoundsException(srcPosition);
        }
        if (srcSize < 0 || srcSize > src.length - srcPosition) {
            throw new ArrayIndexOutOfBoundsException(srcPosition + srcSize);
        }
        return decompressedSize0(src, srcPosition, srcSize, magicless);
    }

    private static long decompressedSize0(byte @NotNull [] src, int srcPosition, int srcSize, boolean magicless) {
        // JNI tests an unsigned size_t <= 0: only zero is clamped, not error sentinels.
        return getFrameContentSize0(src, srcPosition, srcSize, magicless);
    }

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
    public static long getFrameContentSize(byte @NotNull [] src, int srcPosition, int srcSize) {
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
    public static long decompressedSize(byte @NotNull [] src, int srcPosition, int srcSize) {
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
    public static long getFrameContentSize(byte @NotNull [] src, int srcPosition) {
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
    public static long decompressedSize(byte @NotNull [] src, int srcPosition) {
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
    public static long getFrameContentSize(byte @NotNull [] src) {
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
    public static long decompressedSize(byte @NotNull [] src) {
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
    public static long decompressedDirectByteBufferSize(@NotNull ByteBuffer src, int srcPosition, int srcSize, boolean magicless) {
        // Same unsigned-zero check as decompressedSize0 in JNI.
        return getDirectByteBufferFrameContentSize(src, srcPosition, srcSize, magicless);
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
     *         negative if there is an error decoding the frame header
     */
    public static long getDirectByteBufferFrameContentSize(@NotNull ByteBuffer src, int srcPosition, int srcSize, boolean magicless) {
        int capacity = src != null && src.isDirect() ? src.capacity() : -1;
        if (srcPosition < 0 || srcSize < 0 || srcPosition > capacity - srcSize) {
            return -ZstdBinding.ZSTD_ERROR_GENERIC;
        }
        MemorySegment source = fullBuffer(src);
        if (source.address() == 0) {
            return ZstdBinding.asSizeT(-ZstdBinding.ZSTD_ERROR_MEMORY_ALLOCATION);
        }
        return ZstdBinding.frameContentSizeNative(source.asSlice(srcPosition), srcSize, magicless);
    }

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
    public static long decompressedDirectByteBufferSize(@NotNull ByteBuffer src, int srcPosition, int srcSize) {
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
    public static long getDirectByteBufferFrameContentSize(@NotNull ByteBuffer src, int srcPosition, int srcSize) {
        return getDirectByteBufferFrameContentSize(src, srcPosition, srcSize, false);
    }

    /**
     * Maximum size of the compressed data
     *
     * @param srcSize the size of the data to be compressed
     * @return the maximum size of the compressed data
     */
    public static long    compressBound(long srcSize) {
        return ZstdBinding.compressBound(srcSize);
    }

    /**
     * Error handling
     *
     * @param code return code/size
     * @return if the return code signals an error
     */

    public static boolean isError(long code) {
        return ZstdBinding.isErrorCode(code);
    }
    public static @NotNull String  getErrorName(long code) {
        return ZstdBinding.getErrorName(code);
    }
    public static long    getErrorCode(long code) {
        return ZstdBinding.getErrorCode(code);
    }


    /* Stable constants from the zstd_errors header */
    public static long errNoError() {
        return ZstdBinding.ZSTD_ERROR_NO_ERROR;
    }
    public static long errGeneric() {
        return ZstdBinding.ZSTD_ERROR_GENERIC;
    }
    public static long errPrefixUnknown() {
        return ZstdBinding.ZSTD_ERROR_PREFIX_UNKNOWN;
    }
    public static long errVersionUnsupported() {
        return ZstdBinding.ZSTD_ERROR_VERSION_UNSUPPORTED;
    }
    public static long errFrameParameterUnsupported() {
        return ZstdBinding.ZSTD_ERROR_FRAME_PARAMETER_UNSUPPORTED;
    }
    public static long errFrameParameterWindowTooLarge() {
        return ZstdBinding.ZSTD_ERROR_FRAME_PARAMETER_WINDOW_TOO_LARGE;
    }
    public static long errCorruptionDetected() {
        return ZstdBinding.ZSTD_ERROR_CORRUPTION_DETECTED;
    }
    public static long errChecksumWrong() {
        return ZstdBinding.ZSTD_ERROR_CHECKSUM_WRONG;
    }
    public static long errDictionaryCorrupted() {
        return ZstdBinding.ZSTD_ERROR_DICTIONARY_CORRUPTED;
    }
    public static long errDictionaryWrong() {
        return ZstdBinding.ZSTD_ERROR_DICTIONARY_WRONG;
    }
    public static long errDictionaryCreationFailed() {
        return ZstdBinding.ZSTD_ERROR_DICTIONARY_CREATION_FAILED;
    }
    public static long errParameterUnsupported() {
        return ZstdBinding.ZSTD_ERROR_PARAMETER_UNSUPPORTED;
    }
    public static long errParameterOutOfBound() {
        return ZstdBinding.ZSTD_ERROR_PARAMETER_OUT_OF_BOUND;
    }
    public static long errTableLogTooLarge() {
        return ZstdBinding.ZSTD_ERROR_TABLE_LOG_TOO_LARGE;
    }
    public static long errMaxSymbolValueTooLarge() {
        return ZstdBinding.ZSTD_ERROR_MAX_SYMBOL_VALUE_TOO_LARGE;
    }
    public static long errMaxSymbolValueTooSmall() {
        return ZstdBinding.ZSTD_ERROR_MAX_SYMBOL_VALUE_TOO_SMALL;
    }
    public static long errStageWrong() {
        return ZstdBinding.ZSTD_ERROR_STAGE_WRONG;
    }
    public static long errInitMissing() {
        return ZstdBinding.ZSTD_ERROR_INIT_MISSING;
    }
    public static long errMemoryAllocation() {
        return ZstdBinding.ZSTD_ERROR_MEMORY_ALLOCATION;
    }
    public static long errWorkSpaceTooSmall() {
        return ZstdBinding.ZSTD_ERROR_WORK_SPACE_TOO_SMALL;
    }
    public static long errDstSizeTooSmall() {
        return ZstdBinding.ZSTD_ERROR_DST_SIZE_TOO_SMALL;
    }
    public static long errSrcSizeWrong() {
        return ZstdBinding.ZSTD_ERROR_SRC_SIZE_WRONG;
    }
    public static long errDstBufferNull() {
        return ZstdBinding.ZSTD_ERROR_DST_BUFFER_NULL;
    }

    /**
     * Creates a new dictionary to tune a kind of samples
     *
     * @param samples the samples buffer array
     * @param dictBuffer the new dictionary buffer
     * @param legacy  use the legacy training algorithm; otherwise cover
     * @return the number of bytes into buffer 'dictBuffer' or an error code if
     *          it fails (which can be tested using ZSTD_isError())
     */
    public static long trainFromBuffer(byte @NotNull [][] samples, byte @NotNull [] dictBuffer, boolean legacy) {
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
    public static long trainFromBuffer(byte @NotNull [][] samples, byte @NotNull [] dictBuffer, boolean legacy, int compressionLevel) {
        Objects.requireNonNull(samples, "samples");
        Objects.requireNonNull(dictBuffer, "dictBuffer");
        for (byte[] sample : samples) {
            Objects.requireNonNull(sample, "sample");
        }
        if (samples.length <= 10) {
            throw new ZstdException(Zstd.errGeneric(), "nb of samples too low");
        }
        return trainFromBuffer0(samples, dictBuffer, legacy, compressionLevel);
    }
    private static long trainFromBuffer0(byte @NotNull [][] samples, byte @NotNull [] dictBuffer, boolean legacy, int compressionLevel) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment sizes = ZstdBinding.allocateSampleSizes(arena, samples.length);
            long total = 0;
            for (int i = 0; i < samples.length; i++) {
                ZstdBinding.setSampleSize(sizes, i, samples[i].length);
                total += samples[i].length;
            }
            MemorySegment packed = ZstdBinding.allocateTrainingBuffer(arena, total);
            long cursor = 0;
            for (byte[] sample : samples) {
                MemorySegment.copy(MemorySegment.ofArray(sample), 0, packed, cursor, sample.length);
                cursor += sample.length;
            }
            MemorySegment output = ZstdBinding.allocateTrainingBuffer(arena, dictBuffer.length);
            MemorySegment heapOutput = MemorySegment.ofArray(dictBuffer);
            output.copyFrom(heapOutput);
            try {
                return ZstdBinding.trainDictionary(arena, output, packed, sizes,
                        samples.length, legacy, compressionLevel);
            } finally {
                // JNI releases with mode 0, including partially written output on errors.
                heapOutput.copyFrom(output);
            }
        }
    }

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
    public static long trainFromBufferDirect(@NotNull ByteBuffer samples, int @NotNull [] sampleSizes, @NotNull ByteBuffer dictBuffer, boolean legacy) {
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
    public static long trainFromBufferDirect(@NotNull ByteBuffer samples, int @NotNull [] sampleSizes, @NotNull ByteBuffer dictBuffer, boolean legacy, int compressionLevel) {
        Objects.requireNonNull(samples, "samples");
        Objects.requireNonNull(sampleSizes, "sampleSizes");
        Objects.requireNonNull(dictBuffer, "dictBuffer");
        if (sampleSizes.length <= 10) {
            throw new ZstdException(Zstd.errGeneric(), "nb of samples too low");
        }
        return trainFromBufferDirect0(samples, sampleSizes, dictBuffer, legacy, compressionLevel);
    }


    // JNI addresses the buffer base, ignoring position/limit without changing them.
    private static MemorySegment fullBuffer(ByteBuffer buffer) {
        return MemorySegment.ofBuffer(buffer.duplicate().clear());
    }

    private static long trainFromBufferDirect0(@NotNull ByteBuffer samples, int @NotNull [] sampleSizes, @NotNull ByteBuffer dictBuffer, boolean legacy, int compressionLevel) {
        long allocationError = ZstdBinding.asSizeT(-ZstdBinding.ZSTD_ERROR_MEMORY_ALLOCATION);
        if (!samples.isDirect() || !dictBuffer.isDirect()) {
            return allocationError;
        }
        MemorySegment packed = fullBuffer(samples);
        MemorySegment output = fullBuffer(dictBuffer);
        if (packed.address() == 0 || output.address() == 0) {
            return allocationError;
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment sizes = ZstdBinding.allocateSampleSizes(arena, sampleSizes.length);
            long total = 0;
            for (int i = 0; i < sampleSizes.length; i++) {
                if (sampleSizes[i] < 0) {
                    return allocationError;
                }
                total += sampleSizes[i];
                ZstdBinding.setSampleSize(sizes, i, sampleSizes[i]);
            }
            if (total > packed.byteSize()) {
                return allocationError;
            }
            return ZstdBinding.trainDictionary(arena, output, packed, sizes,
                    sampleSizes.length, legacy, compressionLevel);
        }
    }

    /**
     * Get DictId from a compressed frame
     *
     * @param src compressed frame
     * @return DictId or 0 if not available
     */
    public static long getDictIdFromFrame(byte @NotNull [] src) {
        return ZstdBinding.getDictIDFromFrame(MemorySegment.ofArray(src), src.length);
    }

    /**
     * Get DictId from a compressed ByteBuffer frame
     *
     * @param src compressed frame
     * @return DictId or 0 if not available
     */
    public static long getDictIdFromFrameBuffer(@NotNull ByteBuffer src) {
        if (src == null || !src.isDirect() || src.capacity() == 0) {
            return 0;
        }
        MemorySegment source = fullBuffer(src);
        return source.address() == 0 ? 0 : ZstdBinding.getDictIDFromFrameNative(source, source.byteSize());
    }

    /**
     * Get DictId of a dictionary
     *
     * @param dict dictionary
     * @return DictId or 0 if not available
     */
    public static long getDictIdFromDict(byte @NotNull [] dict) {
        return ZstdBinding.getDictIDFromDict(MemorySegment.ofArray(dict), dict.length);
    }

    private static long getDictIdFromDictDirect(@NotNull ByteBuffer dict, int offset, int length) {
        if (!dict.isDirect()) {
            return 0;
        }
        MemorySegment source = fullBuffer(dict);
        return source.address() == 0 ? 0 : ZstdBinding.getDictIDFromDict(source.asSlice(offset), length);
    }

    /**
     * Get DictId of a dictionary
     *
     * @param dict dictionary as Direct ByteBuffer
     * @return DictId or 0 if not available
     */
    public static long getDictIdFromDictDirect(@NotNull ByteBuffer dict) {
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
    public static long trainFromBuffer(byte @NotNull [][] samples, byte @NotNull [] dictBuffer) {
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
    public static long trainFromBufferDirect(@NotNull ByteBuffer samples, int @NotNull [] sampleSizes, @NotNull ByteBuffer dictBuffer) {
        return trainFromBufferDirect(samples, sampleSizes, dictBuffer, false);
    }

    /* Constants from the zstd_static header */
    public static int magicNumber() {
        return ZstdBinding.ZSTD_MAGICNUMBER;
    }
    public static int windowLogMin() {
        return ZstdBinding.ZSTD_WINDOWLOG_MIN;
    }
    public static int windowLogMax() {
        return ZstdBinding.ZSTD_WINDOWLOG_MAX;
    }
    public static int chainLogMin() {
        return ZstdBinding.ZSTD_CHAINLOG_MIN;
    }
    public static int chainLogMax() {
        return ZstdBinding.ZSTD_CHAINLOG_MAX;
    }
    public static int hashLogMin() {
        return ZstdBinding.ZSTD_HASHLOG_MIN;
    }
    public static int hashLogMax() {
        return ZstdBinding.ZSTD_HASHLOG_MAX;
    }
    public static int searchLogMin() {
        return ZstdBinding.ZSTD_SEARCHLOG_MIN;
    }
    public static int searchLogMax() {
        return ZstdBinding.ZSTD_SEARCHLOG_MAX;
    }
    public static int searchLengthMin() {
        // The base API declares this method, but has no JNI implementation.
        throw new UnsatisfiedLinkError("'int com.github.luben.zstd.Zstd.searchLengthMin()'");
    }
    public static int searchLengthMax() {
        // The base API declares this method, but has no JNI implementation.
        throw new UnsatisfiedLinkError("'int com.github.luben.zstd.Zstd.searchLengthMax()'");
    }
    public static int blockSizeMax() {
        return ZstdBinding.ZSTD_BLOCKSIZE_MAX;
    }
    public static int defaultCompressionLevel() {
        return ZstdBinding.ZSTD_CLEVEL_DEFAULT;
    }
    /* Min/max compression levels */
    public static int minCompressionLevel() {
        return ZstdBinding.minCLevel();
    }
    public static int maxCompressionLevel() {
        return ZstdBinding.maxCLevel();
    }



    /* Convenience methods */

    /**
     * Compresses the data in buffer 'src' using default compression level
     *
     * @param src the source buffer
     * @return byte array with the compressed data
     */
    public static byte @NotNull [] compress(byte @NotNull [] src) {
        return compress(src, defaultCompressionLevel());
    }

    /**
     * Compresses the data in buffer 'src'
     *
     * @param src the source buffer
     * @param level compression level
     * @return byte array with the compressed data
     */
    public static byte @NotNull [] compress(byte @NotNull [] src, int level) {
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

    public static int compress(@NotNull ByteBuffer dstBuf, @NotNull ByteBuffer srcBuf) {
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
    public static int compress(@NotNull ByteBuffer dstBuf, @NotNull ByteBuffer srcBuf, int level, boolean checksumFlag) {
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

    public static int compress(@NotNull ByteBuffer dstBuf, @NotNull ByteBuffer srcBuf, int level) {
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
    public static @NotNull ByteBuffer compress(@NotNull ByteBuffer srcBuf, int level) {
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
    public static byte @NotNull [] compress(byte @NotNull [] src, @NotNull ZstdDictCompress dict) {
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
    public static long compressUsingDict(byte @NotNull [] dst, byte @NotNull [] src, byte @NotNull [] dict, int level) {
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

    public static byte @NotNull [] compressUsingDict(byte @NotNull [] src, byte @NotNull [] dict, int level) {
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
    public static long compress(byte @NotNull [] dst, byte @NotNull [] src, byte @NotNull [] dict, int level) {
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
    public static int compress(@NotNull ByteBuffer dstBuff, @NotNull ByteBuffer srcBuff, byte @NotNull [] dict, int level) {
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
    public static @NotNull ByteBuffer compress(@NotNull ByteBuffer srcBuff, byte @NotNull [] dict, int level) {
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
    public static int compress(@NotNull ByteBuffer dstBuff, @NotNull ByteBuffer srcBuff, @NotNull ZstdDictCompress dict) {
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
    public static @NotNull ByteBuffer compress(@NotNull ByteBuffer srcBuff, @NotNull ZstdDictCompress dict) {
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
    public static byte @NotNull [] decompress(byte @NotNull [] src, int originalSize) {
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
    public static byte @NotNull [] decompress(byte @NotNull [] src) {
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

    private static int calculateContentSizeAndFrames(byte @NotNull [] src, @NotNull List<FrameData> frames) {
        long contentSize = 0;

        int srcPosition = 0;

        while (srcPosition < src.length) {
            FrameData frameData = new FrameData(src, srcPosition);

            frames.add(frameData);
            if (frameData.compressedSize > src.length - srcPosition) {
                throw new RuntimeException("Invalid compressed size");
            }
            if (frameData.contentSize < 0) {
                throw new RuntimeException("Frame content size is invalid");
            }
            if (frameData.contentSize > MAX_DECOMPRESS_SIZE) {
                throw new RuntimeException("Frame content size is too large");
            }

            srcPosition += (int) frameData.compressedSize;
            contentSize += frameData.contentSize;
            if (contentSize > MAX_DECOMPRESS_SIZE) {
                throw new RuntimeException("Content size too large");
            }
        }
        return (int) contentSize;
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
    public static byte @NotNull [] decompressFrame(byte @NotNull [] src, int srcOffset, int srcSize, int originalSize) {
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
    public static byte @NotNull [] decompressFrame(byte @NotNull [] src, int srcOffset) {
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
        if (contentSize < 0) {
            throw new RuntimeException("Frame content size is invalid");
        }
        if (contentSize > MAX_DECOMPRESS_SIZE) {
            throw new RuntimeException("Frame content size is too large");
        }

        return decompressFrame(src, srcOffset, compressedSize, (int) contentSize);
    }

    /**
     * Decompress data, using only first frame from offset.
     *
     * @param src the source buffer
     * @return byte array with the decompressed data
     */
    public static byte @NotNull [] decompressFrame(byte @NotNull [] src) {
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
    public static int decompress(@NotNull ByteBuffer dstBuf, @NotNull ByteBuffer srcBuf) {
        ZstdDecompressCtx ctx = new ZstdDecompressCtx();
        try {
            return ctx.decompress(dstBuf, srcBuf);
        } finally {
            ctx.close();
        }
    }

    public static int decompress(@NotNull ByteBuffer dstBuf, byte @NotNull [] src) {
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
    public static @NotNull ByteBuffer decompress(@NotNull ByteBuffer srcBuf, int originalSize) {
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
    public static byte @NotNull [] decompress(byte @NotNull [] src, @NotNull ZstdDictDecompress dict, int originalSize) {
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
    public static long decompressUsingDict(byte @NotNull [] dst, byte @NotNull [] src, byte @NotNull [] dict) {
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
    public static long decompress(byte @NotNull [] dst, byte @NotNull [] src, byte @NotNull [] dict) {
        return decompressUsingDict(dst, 0, src, 0, src.length, dict);
    }

    /**
     * @param src the source buffer
     * @param dict dictionary to use
     * @param originalSize the maximum size of the uncompressed data
     * @return byte array with the decompressed data
     */
    public static byte @NotNull [] decompress(byte @NotNull [] src, byte @NotNull [] dict, int originalSize) {
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
    public static long decompressedSize(@NotNull ByteBuffer srcBuf) {
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
    public static long getFrameContentSize(@NotNull ByteBuffer srcBuf) {
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
    public static int decompress(@NotNull ByteBuffer dstBuff, @NotNull ByteBuffer srcBuff, byte @NotNull [] dict) {
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
    public static @NotNull ByteBuffer decompress(@NotNull ByteBuffer srcBuff, byte @NotNull [] dict, int originalSize) {
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
    public static int decompress(@NotNull ByteBuffer dstBuff, @NotNull ByteBuffer srcBuff, @NotNull ZstdDictDecompress dict) {
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
    public static @NotNull ByteBuffer decompress(@NotNull ByteBuffer srcBuff, @NotNull ZstdDictDecompress dict, int originalSize) {
        ZstdDecompressCtx ctx = new ZstdDecompressCtx();
        try {
            ctx.loadDict(dict);
            return ctx.decompress(srcBuff, originalSize);
        } finally {
            ctx.close();
        }
    }

    static @NotNull ByteBuffer getArrayBackedBuffer(@NotNull BufferPool bufferPool, int size) throws ZstdIOException {
        ByteBuffer buffer = bufferPool.get(size);
        // defensive: BufferPool is a public interface, a third-party implementation could violate the @NotNull contract
        //noinspection ConstantValue
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

        FrameData(byte @NotNull [] src, int srcPosition) {
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

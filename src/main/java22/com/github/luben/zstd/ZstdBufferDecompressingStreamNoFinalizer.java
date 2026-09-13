package com.github.luben.zstd;

import com.github.luben.zstd.util.Native;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;

public class ZstdBufferDecompressingStreamNoFinalizer extends BaseZstdBufferDecompressingStreamNoFinalizer {
    static {
        Native.load();
    }

    /* libzstd's size_t* out-params. Java cannot take the address of a field, so each
     * value lives in a one-element heap array - a legal pointer argument under
     * Linker.Option.critical, so this stream needs no Arena. They replace the C's
     * SetIntField on consumed/produced, which this class copies across after the call. */
    private final @NotNull ZstdBinding.SizeTRef dstPos = ZstdBinding.newSizeTRef();
    private final @NotNull ZstdBinding.SizeTRef srcPos = ZstdBinding.newSizeTRef();

    /* The ZSTD_DCtx as a downcall argument, assigned by createDStream(). The same
     * pointer is in the inherited `stream` field as a long, which setDict and
     * setLongMax hand to the Zstd.* natives still on JNI. */
    private @NotNull MemorySegment dstream;

    public ZstdBufferDecompressingStreamNoFinalizer(@NotNull ByteBuffer source) {
        super(source);
        if (source.isDirect()) {
            throw new IllegalArgumentException("Source buffer should be a non-direct buffer");
        }
        stream = createDStream();
        initDStream(stream);
    }

    @Override
    public int read(@NotNull ByteBuffer target) throws IOException {
        if (target.isDirect()) {
            throw new IllegalArgumentException("Target buffer should be a non-direct buffer");
        }
        return readInternal(target, false);
    }

    /* createDStream() also keeps the context as a MemorySegment, so the two calls
     * below ignore the long they are handed. */
    @Override
    long createDStream() {
        dstream = ZstdBinding.createDStream();
        return dstream.address();
    }

    @Override
    long freeDStream(long stream) {
        return ZstdBinding.freeDCtx(dstream);
    }

    /* Kept, unlike in ZstdInputStreamNoFinalizer: this class's C really does call
     * ZSTD_initDStream, not just cache jfieldIDs. */
    @Override
    long initDStream(long stream) {
        return ZstdBinding.initDStream(dstream);
    }

    /* The whole array is passed, with dstCapacity/srcSize as absolute end offsets and
     * the position slots pre-set - the same span the C reaches as ptr + offset with a
     * length, and the convention ZstdInputStreamNoFinalizer uses. One wrapper per
     * buffer; a slice would need two. */
    @Override
    long decompressStream(long stream, @NotNull ByteBuffer dst, int dstBufPos, int dstSize,
                          @NotNull ByteBuffer src, int srcBufPos, int srcSize) {
        if (!src.hasArray()) {
            throw new IllegalArgumentException("provided source ByteBuffer lacks array");
        }
        if (!dst.hasArray()) {
            throw new IllegalArgumentException("provided destination ByteBuffer lacks array");
        }

        byte[] targetArr = dst.array();
        byte[] sourceArr = src.array();

        // We are interested in array data corresponding to the pos represented by the ByteBuffer view.
        // A ByteBuffer may share an underlying array with other ByteBuffers. In such scenario, we need to adjust the
        // index of the array by adding an offset using arrayOffset().
        int dstOffset = dstBufPos + dst.arrayOffset();
        int srcOffset = srcBufPos + src.arrayOffset();

        /* jni_bufferdecompress_zstd.c:61-68, re-hosted. The order decides which code
         * wins when both sides are invalid, so it is kept. Its two NULL checks are
         * dropped: the hasArray() calls above already throw on a null argument. The
         * direct class checks less, because its own C always did. */
        if (dstOffset < 0) {
            return -ZstdBinding.ZSTD_ERROR_DST_SIZE_TOO_SMALL;
        }
        if (srcOffset < 0) {
            return -ZstdBinding.ZSTD_ERROR_SRC_SIZE_WRONG;
        }
        if (srcSize < 0) {
            return -ZstdBinding.ZSTD_ERROR_SRC_SIZE_WRONG;
        }
        if (dstSize < 0) {
            return -ZstdBinding.ZSTD_ERROR_DST_SIZE_TOO_SMALL;
        }
        if (srcOffset + srcSize > sourceArr.length) {
            return -ZstdBinding.ZSTD_ERROR_SRC_SIZE_WRONG;
        }
        if (dstOffset + dstSize > targetArr.length) {
            return -ZstdBinding.ZSTD_ERROR_DST_SIZE_TOO_SMALL;
        }

        dstPos.set(dstOffset);
        srcPos.set(srcOffset);
        long result = ZstdBinding.decompressStream(
                dstream,
                MemorySegment.ofArray(targetArr), dstOffset + dstSize, dstPos.segment,
                MemorySegment.ofArray(sourceArr), srcOffset + srcSize, srcPos.segment);
        consumed = (int) (srcPos.get() - srcOffset);
        produced = (int) (dstPos.get() - dstOffset);
        return result;
    }

    public static int recommendedTargetBufferSize() {
        return (int) ZstdBinding.dStreamOutSize();
    }
}

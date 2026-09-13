package com.github.luben.zstd;

import com.github.luben.zstd.util.Native;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;

public class ZstdDirectBufferDecompressingStreamNoFinalizer extends BaseZstdBufferDecompressingStreamNoFinalizer {
    static {
        Native.load();
    }

    /* libzstd's size_t* out-params. Java cannot take the address of a field, so each
     * value lives in a one-element heap array - legal as a pointer argument under
     * Linker.Option.critical even though the data buffers here are direct, so the
     * position slots need no Arena. They replace the C's SetIntField on
     * consumed/produced, which this class copies across after the call. */
    private final @NotNull ZstdBinding.SizeTRef dstPos = ZstdBinding.newSizeTRef();
    private final @NotNull ZstdBinding.SizeTRef srcPos = ZstdBinding.newSizeTRef();

    /* The ZSTD_DCtx as a downcall argument, assigned by createDStream(). The same
     * pointer is in the inherited `stream` field as a long, which setDict and
     * setLongMax hand to the Zstd.* natives still on JNI. */
    private @NotNull MemorySegment dstream;

    public ZstdDirectBufferDecompressingStreamNoFinalizer(@NotNull ByteBuffer source) {
        super(source);
        if (!source.isDirect()) {
            throw new IllegalArgumentException("Source buffer should be a direct buffer");
        }
        this.source = source;
        stream = createDStream();
        initDStream(stream);
    }

    @Override
    public int read(@NotNull ByteBuffer target) throws IOException {
        if (!target.isDirect()) {
            throw new IllegalArgumentException("Target buffer should be a direct buffer");
        }
        return readInternal(target, true);
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

    /* The whole buffer is passed, with dstCapacity/srcSize as absolute end offsets and
     * the position slots pre-set - the same span the C reaches as ptr + offset with a
     * length, and the convention ZstdInputStreamNoFinalizer uses. One wrapper per
     * buffer; a slice would need two. */
    @Override
    long decompressStream(long stream, @NotNull ByteBuffer dst, int dstOffset, int dstSize,
                          @NotNull ByteBuffer src, int srcOffset, int srcSize) {
        /* jni_directbufferdecompress_zstd.c:62-65, re-hosted. These two are all of it:
         * that function never rejected a negative offset or size, where
         * jni_bufferdecompress_zstd.c does. The difference predates this port and is
         * kept rather than reconciled. Its GetDirectBufferAddress NULL checks have no
         * analogue - they guarded against a non-direct buffer, which read() rules out. */
        if (dstOffset + dstSize > dst.capacity()) {
            return -ZstdBinding.ZSTD_ERROR_DST_SIZE_TOO_SMALL;
        }
        if (srcOffset + srcSize > src.capacity()) {
            return -ZstdBinding.ZSTD_ERROR_SRC_SIZE_WRONG;
        }

        dstPos.set(dstOffset);
        srcPos.set(srcOffset);
        long result = ZstdBinding.decompressStream(
                dstream,
                wholeBufferSegment(dst), dstOffset + dstSize, dstPos.segment,
                wholeBufferSegment(src), srcOffset + srcSize, srcPos.segment);
        consumed = (int) (srcPos.get() - srcOffset);
        produced = (int) (dstPos.get() - dstOffset);
        return result;
    }

    /* ofBuffer covers [position, limit); the duplicate() widens that to the full
     * capacity without touching the caller's positions - the span
     * GetDirectBufferAddress / GetDirectBufferCapacity give the C. */
    private static @NotNull MemorySegment wholeBufferSegment(@NotNull ByteBuffer buffer) {
        return MemorySegment.ofBuffer(buffer.duplicate().clear());
    }

    public static int recommendedTargetBufferSize() {
        return (int) ZstdBinding.dStreamOutSize();
    }
}

package com.github.luben.zstd;

import com.github.luben.zstd.util.Native;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;

public class ZstdDirectBufferCompressingStreamNoFinalizer implements Closeable, Flushable {

    static {
        Native.load();
    }

    private @Nullable ByteBuffer target;

    /* The ZSTD_CStream as a downcall argument. No `long stream` beside it, unlike the
     * other FFM classes: nothing here passes the pointer to a native still on JNI. */
    private final @NotNull MemorySegment cstream;

    /**
     * This method should flush the buffer and either return the same buffer (but cleared) or a new buffer
     * that should be used from then on.
     * @param toFlush buffer that has to be flushed (or most cases, you want to call {@link ByteBuffer#flip()} first)
     * @return the new buffer to use, for most cases the same as the one passed in, after a call to {@link ByteBuffer#clear()}.
     */
    protected @NotNull ByteBuffer flushBuffer(@NotNull ByteBuffer toFlush) throws IOException {
        return toFlush;
    }

    public ZstdDirectBufferCompressingStreamNoFinalizer(@NotNull ByteBuffer target, int level) throws IOException {
        if (!target.isDirect()) {
            throw new IllegalArgumentException("Target buffer should be a direct buffer");
        }
        this.target = target;
        this.level = level;
        cstream = ZstdBinding.createCStream();
    }

    public static int recommendedOutputBufferSize() { return (int) ZstdBinding.cStreamOutSize(); }

    private int consumed = 0;
    private int produced = 0;
    private boolean closed = false;
    private boolean initialized = false;
    private int level = Zstd.defaultCompressionLevel();
    private byte @Nullable [] dict = null;
    private @Nullable ZstdDictCompress fastDict = null;

    /* libzstd's size_t* out-params, replacing the C's SetIntField on consumed/produced.
     * One-element heap arrays: legal pointer arguments under Linker.Option.critical
     * even though the data buffers here are direct, so no Arena. */
    private final @NotNull ZstdBinding.SizeTRef dstPos = ZstdBinding.newSizeTRef();
    private final @NotNull ZstdBinding.SizeTRef srcPos = ZstdBinding.newSizeTRef();

    /* The target is a field that flushBuffer normally hands back unchanged, and a caller
     * compressing in chunks normally reuses one source buffer - so wrapping either fresh
     * on every call builds a MemorySegment per call for an identical result. Keep the
     * last wrapper for each and reuse it while the buffer is the same object, as
     * ZstdInputStreamNoFinalizer does for the caller's destination array. Two slots, not
     * one: target and source alternate within a single call.
     *
     * Cleared in close(), so neither buffer is pinned past it. */
    private @Nullable ByteBuffer lastTarget;
    private @Nullable MemorySegment lastTargetSegment;
    private @Nullable ByteBuffer lastSource;
    private @Nullable MemorySegment lastSourceSegment;

    /* A cached segment has to outlive the positions it was built from, so it spans the
     * whole capacity - duplicate().clear() on a miss, which is what the call sites then
     * address with absolute end offsets, the span C gets from GetDirectBufferAddress and
     * GetDirectBufferCapacity. Keyed on identity: a segment belongs to one buffer object. */
    private @NotNull MemorySegment targetSegment(@NotNull ByteBuffer dst) {
        MemorySegment cached = lastTargetSegment;
        if (dst != lastTarget || cached == null) {
            cached = MemorySegment.ofBuffer(dst.duplicate().clear());
            lastTargetSegment = cached;
            lastTarget = dst;
        }
        return cached;
    }

    private @NotNull MemorySegment sourceSegment(@NotNull ByteBuffer src) {
        MemorySegment cached = lastSourceSegment;
        if (src != lastSource || cached == null) {
            cached = MemorySegment.ofBuffer(src.duplicate().clear());
            lastSourceSegment = cached;
            lastSource = src;
        }
        return cached;
    }

    /* Minus the jfieldID caching: consumed/produced are plain field assignments here. */
    private long initCStream(int level) {
        return ZstdBinding.initCStream(cstream, level);
    }

    /* The C ignores what the reset and the parameter set return; kept as is. */
    private long initCStreamWithDict(byte @NotNull [] dict, int dictSize, int level) {
        ZstdBinding.resetCCtx(cstream, ZstdBinding.ZSTD_RESET_SESSION_ONLY);
        ZstdBinding.setCCtxParameter(cstream, ZstdBinding.ZSTD_C_COMPRESSION_LEVEL, level);
        return ZstdBinding.loadDictionary(cstream, MemorySegment.ofArray(dict), dictSize);
    }

    /* nativePtr() replaces the C's GetLongField on the dict; a closed one is null and
     * is rejected here rather than reaching libzstd, as in the C. */
    private long initCStreamWithFastDict(@NotNull ZstdDictCompress dict) {
        long cdict = dict.nativePtr();
        if (cdict == 0) {
            return -ZstdBinding.ZSTD_ERROR_DICTIONARY_WRONG;
        }
        ZstdBinding.resetCCtx(cstream, ZstdBinding.ZSTD_RESET_SESSION_ONLY);
        return ZstdBinding.refCDict(cstream, MemorySegment.ofAddress(cdict));
    }

    /* The C's ZSTD_compressStream / ZSTD_flushStream / ZSTD_endStream take
     * ZSTD_outBuffer* / ZSTD_inBuffer*, and a struct has to live off-heap - an Arena
     * per stream for what is otherwise a copy-free class. Each is one
     * ZSTD_compressStream2 call with ZSTD_e_continue / _flush / _end, which the
     * _simpleArgs variant reaches with the buffers as plain arguments. Two of them
     * post-process the return value, and neither difference is visible here:
     * ZSTD_compressStream returns a next-input hint rather than the bytes remaining to
     * flush, which compress() only tests with Zstd.isError; and ZSTD_endStream adds the
     * unwritten block header and checksum, a sharper estimate that is zero on exactly
     * the same calls, since libzstd returns 0 for ZSTD_e_end iff the frame is complete.
     *
     * dstEnd / srcEnd are absolute end offsets rather than lengths, because the cached
     * segments span the whole capacity - the same convention as the C's ptr + offset
     * with a length, and the one the other FFM classes use. */
    private long compressDirectByteBuffer(@NotNull ByteBuffer dst, int dstOffset, int dstSize,
                                          @NotNull ByteBuffer src, int srcOffset, int srcSize) {
        /* jni_directbuffercompress_zstd.c:101-104. Its GetDirectBufferAddress NULL
         * checks have no analogue - they guarded against a non-direct buffer, which the
         * isDirect() checks below rule out. */
        int dstEnd = dstOffset + dstSize;
        if (dstEnd > dst.capacity()) {
            return -ZstdBinding.ZSTD_ERROR_DST_SIZE_TOO_SMALL;
        }
        int srcEnd = srcOffset + srcSize;
        if (srcEnd > src.capacity()) {
            return -ZstdBinding.ZSTD_ERROR_SRC_SIZE_WRONG;
        }

        dstPos.set(dstOffset);
        srcPos.set(srcOffset);
        long result = ZstdBinding.compressStream2(
                cstream,
                targetSegment(dst), dstEnd, dstPos.segment,
                sourceSegment(src), srcEnd, srcPos.segment,
                ZstdBinding.ZSTD_E_CONTINUE);
        consumed = (int) srcPos.get() - srcOffset;
        produced = (int) dstPos.get() - dstOffset;
        return result;
    }

    /* The C's flushStream and endStream, which differ only in the directive: an empty
     * input, so libzstd drains what it already buffered. Neither sets `consumed`. */
    private long drainStream(@NotNull ByteBuffer dst, int dstOffset, int dstSize, int endOp) {
        int dstEnd = dstOffset + dstSize;
        if (dstEnd > dst.capacity()) {
            return -ZstdBinding.ZSTD_ERROR_DST_SIZE_TOO_SMALL;
        }

        dstPos.set(dstOffset);
        srcPos.set(0);
        long result = ZstdBinding.compressStream2(
                cstream,
                targetSegment(dst), dstEnd, dstPos.segment,
                MemorySegment.NULL, 0L, srcPos.segment,
                endOp);
        produced = (int) dstPos.get() - dstOffset;
        return result;
    }

    public @NotNull ZstdDirectBufferCompressingStreamNoFinalizer setDict(byte @NotNull [] dict) {
        if (initialized) {
            throw new IllegalStateException("Change of parameter on initialized stream");
        }
        this.dict = dict;
        this.fastDict = null;
        return this;
    }

    public @NotNull ZstdDirectBufferCompressingStreamNoFinalizer setDict(@NotNull ZstdDictCompress dict) {
        if (initialized) {
            throw new IllegalStateException("Change of parameter on initialized stream");
        }
        this.dict = null;
        dict.acquireSharedLock();
        if (this.fastDict != null) {
            this.fastDict.releaseSharedLock();
        }
        this.fastDict = dict;
        return this;
    }

    public void compress(@NotNull ByteBuffer source) throws IOException {
        if (!source.isDirect()) {
            throw new IllegalArgumentException("Source buffer should be a direct buffer");
        }
        if (closed) {
            throw new IOException("Stream closed");
        }
        if (!initialized) {
            long result = 0;
            ZstdDictCompress fastDict = this.fastDict;
            if (fastDict != null) {
                result = initCStreamWithFastDict(fastDict);
            } else if (dict != null) {
                result = initCStreamWithDict(dict, dict.length, level);
            } else {
                result = initCStream(level);
            }
            if (Zstd.isError(result)) {
                throw new ZstdIOException(result);
            }
            initialized = true;
        }
        while (source.hasRemaining()) {
            ByteBuffer target = this.target;
            if (target == null) {
                throw new IOException("Stream closed");
            }
            if (!target.hasRemaining()) {
                target = flushBuffer(target);
                this.target = target;
                if (!target.isDirect()) {
                    throw new IllegalArgumentException("Target buffer should be a direct buffer");
                }
                if (!target.hasRemaining()) {
                    throw new IOException("The target buffer has no more space, even after flushing, and there are still bytes to compress");
                }
            }
            long result = compressDirectByteBuffer(target, target.position(), target.remaining(), source, source.position(), source.remaining());
            if (Zstd.isError(result)) {
                throw new ZstdIOException(result);
            }
            target.position(target.position() + produced);
            source.position(source.position() + consumed);
        }
    }

    @Override
    public void flush() throws IOException {
        if (closed) {
            throw new IOException("Already closed");
        }
        if (initialized) {
            long needed;
            do {
                ByteBuffer target = this.target;
                if (target == null) {
                    throw new IOException("Stream closed");
                }
                needed = drainStream(target, target.position(), target.remaining(), ZstdBinding.ZSTD_E_FLUSH);
                if (Zstd.isError(needed)) {
                    throw new ZstdIOException(needed);
                }
                target.position(target.position() + produced);
                target = flushBuffer(target);
                this.target = target;
                if (!target.isDirect()) {
                    throw new IllegalArgumentException("Target buffer should be a direct buffer");
                }
                if (needed > 0 && !target.hasRemaining()) {
                    // don't check on the first iteration of the loop
                    throw new IOException("The target buffer has no more space, even after flushing, and there are still bytes to compress");
                }
            }
            while (needed > 0);
        }
    }

    @Override
    public void close() throws IOException {
        if (!closed) {
            try {
                if (initialized) {
                    long needed;
                    do {
                        ByteBuffer target = this.target;
                        if (target == null) {
                            throw new IOException("Stream closed");
                        }
                        needed = drainStream(target, target.position(), target.remaining(), ZstdBinding.ZSTD_E_END);
                        if (Zstd.isError(needed)) {
                            throw new ZstdIOException(needed);
                        }
                        target.position(target.position() + produced);
                        target = flushBuffer(target);
                        this.target = target;
                        if (!target.isDirect()) {
                            throw new IllegalArgumentException("Target buffer should be a direct buffer");
                        }
                        if (needed > 0 && !target.hasRemaining()) {
                            throw new IOException("The target buffer has no more space, even after flushing, and there are still bytes to compress");
                        }
                    } while (needed > 0);
                }
            }
            finally {
                ZstdBinding.freeCStream(cstream);
                closed = true;
                initialized = false;
                target = null; // help GC with realizing the buffer can be released
                // and do not let the segment cache keep either buffer alive past close()
                lastTarget = null;
                lastTargetSegment = null;
                lastSource = null;
                lastSourceSegment = null;
                if (fastDict != null) {
                    fastDict.releaseSharedLock();
                }
                fastDict = null;
                dict = null;
            }
        }
    }
}

package com.github.luben.zstd;

import com.github.luben.zstd.util.Native;

import org.jetbrains.annotations.NotNull;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * libzstd downcall bindings shared by the FFM implementations.
 * <p>
 * Package-private on purpose: this type exists only under
 * META-INF/versions/22, so making it public would give a JDK 22+ consumer an
 * API that no other runtime has.
 */
final class ZstdBinding {

    static {
        Native.load();
    }

    private ZstdBinding() {
    }

    private static final Linker LINKER = nativeLinker();

    private static final SymbolLookup LOOKUP = SymbolLookup.loaderLookup();

    /* libzstd deals in size_t, which FFM has no layout for - the linker knows the
     * platform's. Every specialized ABI linker is 64-bit, but a platform without
     * one falls back to the libffi FallbackLinker, whose size_t is the platform's:
     * 4 bytes on a 32-bit JDK (Debian ships such builds for i386 and armhf). Java
     * has no 32-bit-wide `long`, so the signatures below stay 64-bit and the width
     * difference is absorbed in `adapt` instead. */
    private static final ValueLayout C_SIZE_T = cSizeT();

    private static final boolean SIZE_T_IS_64_BIT = C_SIZE_T.byteSize() == 8;

    /* `unsigned long long` is 8 bytes everywhere, but its ABI alignment is not: the
     * System V i386 ABI aligns it to 4. Taken from the linker rather than written as
     * ValueLayout.JAVA_LONG so the struct below and the pledged-size argument carry
     * the platform's alignment. */
    private static final ValueLayout.OfLong C_LONG_LONG =
            (ValueLayout.OfLong) LINKER.canonicalLayouts().get("long long");

    /* Declared ahead of every downcall handle below: `adapt` reads it while they
     * initialize, and static initializers run in source order. */
    private static final MethodHandle INT_TO_UNSIGNED_LONG = intToUnsignedLong();

    private static MethodHandle intToUnsignedLong() {
        try {
            return MethodHandles.lookup().findStatic(
                    Integer.class, "toUnsignedLong", MethodType.methodType(long.class, int.class));
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Integer.toUnsignedLong is missing", e);
        }
    }

    private static Linker nativeLinker() {
        try {
            return Linker.nativeLinker();
        } catch (UnsupportedOperationException e) {
            /* Multi-Release dispatch is by JDK feature version alone, so a JDK 22+
             * platform that cannot do FFM still gets these classes rather than the
             * JNI ones. The property is what puts it back on the JNI
             * implementation; it is read once at startup and applies to every jar
             * in the JVM. Reached on JDKs built without libffi and without a
             * specialized linker - BellSoft's 32-bit builds, for instance. */
            throw new UnsupportedOperationException(
                    "The zstd-jni FFM implementation needs an FFM Linker, which this platform does not"
                            + " have. Start the JVM with -Djdk.util.jar.enableMultiRelease=false to use"
                            + " the JNI implementation instead.", e);
        }
    }

    private static ValueLayout cSizeT() {
        MemoryLayout sizeT = LINKER.canonicalLayouts().get("size_t");
        if (sizeT instanceof ValueLayout.OfLong || sizeT instanceof ValueLayout.OfInt) {
            return (ValueLayout) sizeT;
        }
        throw new UnsupportedOperationException("Unexpected size_t layout " + sizeT);
    }

    /**
     * Makes a downcall handle match {@code javaType}, which types every size_t as
     * {@code long}. A no-op where size_t is 64-bit. Where it is 32-bit the handle
     * really takes and returns {@code int}, and two steps, in this order, fix that:
     * <ol>
     * <li><b>The return value</b> is widened by {@code filterReturnValue}, using
     *     {@code Integer.toUnsignedLong}. Unsigned because that is what the JNI
     *     build's {@code (jlong) size_t} produces, so {@code Zstd.isError} sees the
     *     same value in both builds. This has to happen first, because - despite
     *     the name - {@code explicitCastArguments} converts the return value as
     *     well, and it would use the plain {@code (long) anInt} widening, which
     *     sign-extends.</li>
     * <li><b>The arguments</b> are narrowed to {@code int} by
     *     {@code explicitCastArguments}, which by then finds the return type
     *     already right and leaves it alone. Narrowing is safe here because every
     *     size_t argument is a length or an offset. It is also why the call is not
     *     {@code asType}, which refuses a narrowing primitive cast.</li>
     * </ol>
     */
    private static MethodHandle adapt(@NotNull MethodHandle handle, @NotNull MethodType javaType) {
        MethodHandle adapted = handle;
        if (adapted.type().returnType() == int.class && javaType.returnType() == long.class) {
            adapted = MethodHandles.filterReturnValue(adapted, INT_TO_UNSIGNED_LONG);
        }
        return MethodHandles.explicitCastArguments(adapted, javaType);
    }

    /* ZSTD_EndDirective */
    static final int ZSTD_E_CONTINUE = 0;
    static final int ZSTD_E_FLUSH    = 1;
    static final int ZSTD_E_END      = 2;

    /* ZSTD_ResetDirective */
    static final int ZSTD_RESET_SESSION_ONLY           = 1;
    static final int ZSTD_RESET_SESSION_AND_PARAMETERS = 3;

    /* ZSTD_cParameter */
    static final int ZSTD_C_COMPRESSION_LEVEL = 100;
    static final int ZSTD_C_CONTENT_SIZE_FLAG = 200;
    static final int ZSTD_C_CHECKSUM_FLAG     = 201;
    static final int ZSTD_C_DICT_ID_FLAG      = 202;

    /* ZSTD_ErrorCode */
    static final int ZSTD_ERROR_DICTIONARY_WRONG   = 32;
    static final int ZSTD_ERROR_DST_SIZE_TOO_SMALL = 70;
    static final int ZSTD_ERROR_SRC_SIZE_WRONG     = 72;

    private static final MethodHandle ZSTD_CStreamOutSize =
            downcall(
                    "ZSTD_CStreamOutSize",
                    FunctionDescriptor.of(C_SIZE_T),
                    MethodType.methodType(long.class));
    private static final MethodHandle ZSTD_createCStream =
            downcall(
                    "ZSTD_createCStream",
                    FunctionDescriptor.of(ValueLayout.ADDRESS),
                    MethodType.methodType(MemorySegment.class));
    private static final MethodHandle ZSTD_freeCStream =
            downcall(
                    "ZSTD_freeCStream",
                    FunctionDescriptor.of(C_SIZE_T, ValueLayout.ADDRESS),
                    MethodType.methodType(long.class, MemorySegment.class));
    private static final MethodHandle ZSTD_CCtx_reset =
            downcall(
                    "ZSTD_CCtx_reset",
                    FunctionDescriptor.of(C_SIZE_T, ValueLayout.ADDRESS, ValueLayout.JAVA_INT),
                    MethodType.methodType(long.class, MemorySegment.class, int.class));
    private static final MethodHandle ZSTD_initCStream =
            downcall(
                    "ZSTD_initCStream",
                    FunctionDescriptor.of(C_SIZE_T, ValueLayout.ADDRESS, ValueLayout.JAVA_INT),
                    MethodType.methodType(long.class, MemorySegment.class, int.class));
    private static final MethodHandle ZSTD_CCtx_setParameter =
            downcall(
                    "ZSTD_CCtx_setParameter",
                    FunctionDescriptor.of(
                            C_SIZE_T,
                            ValueLayout.ADDRESS,                                       // ZSTD_CCtx* cctx
                            ValueLayout.JAVA_INT,                                      // ZSTD_cParameter param
                            ValueLayout.JAVA_INT),                                     // int value
                    MethodType.methodType(long.class, MemorySegment.class, int.class, int.class));
    /* critical: the dictionary is a caller-supplied heap byte[]. libzstd copies it
     * (ZSTD_dlm_byCopy is the default), so nothing retains the pointer past the call. */
    private static final MethodHandle ZSTD_CCtx_loadDictionary =
            downcallCritical(
                    "ZSTD_CCtx_loadDictionary",
                    FunctionDescriptor.of(C_SIZE_T, ValueLayout.ADDRESS, ValueLayout.ADDRESS, C_SIZE_T),
                    MethodType.methodType(long.class, MemorySegment.class, MemorySegment.class, long.class));
    private static final MethodHandle ZSTD_CCtx_refCDict =
            downcall(
                    "ZSTD_CCtx_refCDict",
                    FunctionDescriptor.of(C_SIZE_T, ValueLayout.ADDRESS, ValueLayout.ADDRESS),
                    MethodType.methodType(long.class, MemorySegment.class, MemorySegment.class));
    private static final MethodHandle ZSTD_createCCtx =
            downcall(
                    "ZSTD_createCCtx",
                    FunctionDescriptor.of(ValueLayout.ADDRESS),
                    MethodType.methodType(MemorySegment.class));
    private static final MethodHandle ZSTD_freeCCtx =
            downcall(
                    "ZSTD_freeCCtx",
                    FunctionDescriptor.of(C_SIZE_T, ValueLayout.ADDRESS),
                    MethodType.methodType(long.class, MemorySegment.class));
    private static final MethodHandle ZSTD_CCtx_setPledgedSrcSize =
            downcall(
                    "ZSTD_CCtx_setPledgedSrcSize",
                    FunctionDescriptor.of(C_SIZE_T, ValueLayout.ADDRESS, C_LONG_LONG),
                    MethodType.methodType(long.class, MemorySegment.class, long.class));
    /* critical: dst and src may both be heap byte[]s, and the one-shot entry point
     * takes them as plain pointer arguments, so neither needs staging. The JNI
     * implementation pins them the same way (GetPrimitiveArrayCritical). */
    private static final MethodHandle ZSTD_compress2 =
            downcallCritical(
                    "ZSTD_compress2",
                    FunctionDescriptor.of(
                            C_SIZE_T,
                            ValueLayout.ADDRESS,                                       // ZSTD_CCtx* cctx
                            ValueLayout.ADDRESS, C_SIZE_T,                             // dst, dstCapacity
                            ValueLayout.ADDRESS, C_SIZE_T),                            // src, srcSize
                    MethodType.methodType(long.class,
                            MemorySegment.class,
                            MemorySegment.class, long.class,
                            MemorySegment.class, long.class));

    /* ZSTD_frameProgression, returned by value. C_LONG_LONG rather than
     * ValueLayout.JAVA_LONG for the reason C_SIZE_T exists: a descriptor has to describe
     * the platform's C type, and while `long long` is 8 bytes everywhere, the i386 ABI
     * aligns it to 4 where JAVA_LONG would claim 8. JAVA_INT needs no such care - `int`
     * is 4 bytes, 4-aligned, everywhere. Offsets come out 0/8/16/24/32/36 and the size
     * 40 either way. */
    private static final MemoryLayout ZSTD_frameProgression = MemoryLayout.structLayout(
            C_LONG_LONG.withName("ingested"),
            C_LONG_LONG.withName("consumed"),
            C_LONG_LONG.withName("produced"),
            C_LONG_LONG.withName("flushed"),
            ValueLayout.JAVA_INT.withName("currentJobID"),
            ValueLayout.JAVA_INT.withName("nbActiveWorkers"));

    private static long offsetIn(@NotNull MemoryLayout layout, @NotNull String field) {
        return layout.byteOffset(MemoryLayout.PathElement.groupElement(field));
    }

    private static final long FP_INGESTED          = offsetIn(ZSTD_frameProgression, "ingested");
    private static final long FP_CONSUMED          = offsetIn(ZSTD_frameProgression, "consumed");
    private static final long FP_PRODUCED          = offsetIn(ZSTD_frameProgression, "produced");
    private static final long FP_FLUSHED           = offsetIn(ZSTD_frameProgression, "flushed");
    private static final long FP_CURRENT_JOB_ID    = offsetIn(ZSTD_frameProgression, "currentJobID");
    private static final long FP_NB_ACTIVE_WORKERS = offsetIn(ZSTD_frameProgression, "nbActiveWorkers");

    /* Deliberately not critical(true), which would let the result buffer below be a heap
     * array: Linker.Option.critical only promises that heap segments may be passed "as
     * addresses", i.e. as pointer arguments. A struct returned by value goes to a buffer
     * the linker takes from a SegmentAllocator, and nothing says that may be on the heap.
     * It happens to work on linux/x86-64 and does not elsewhere - macos/aarch64 throws,
     * and the i386 FallbackLinker silently writes the struct somewhere else and leaves
     * the array zeroed.
     */
    private static final MethodHandle ZSTD_getFrameProgression =
            downcall(
                    "ZSTD_getFrameProgression",
                    FunctionDescriptor.of(ZSTD_frameProgression, ValueLayout.ADDRESS),
                    MethodType.methodType(MemorySegment.class, SegmentAllocator.class, MemorySegment.class));

    /* Not plain ZSTD_compressStream2, which takes ZSTD_inBuffer / ZSTD_outBuffer:
     * under Linker.Option.critical - the FFM analogue of the JNI implementation's
     * GetPrimitiveArrayCritical - a heap byte[] can be passed as a pointer
     * argument but never stored into an off-heap struct, so the structs would
     * force a staging buffer and a copy of every byte both ways. zstd offers this
     * variant to "binders from dynamic languages which have troubles handling
     * structures containing memory pointers".
     */
    private static final MethodHandle ZSTD_compressStream2_simpleArgs =
            downcallCritical(
                    "ZSTD_compressStream2_simpleArgs",
                    FunctionDescriptor.of(
                            C_SIZE_T,
                            ValueLayout.ADDRESS,                                       // ZSTD_CCtx* cctx
                            ValueLayout.ADDRESS, C_SIZE_T, ValueLayout.ADDRESS,        // dst, dstCapacity, dstPos
                            ValueLayout.ADDRESS, C_SIZE_T, ValueLayout.ADDRESS,        // src, srcSize, srcPos
                            ValueLayout.JAVA_INT),                                     // ZSTD_EndDirective endOp
                    MethodType.methodType(long.class,
                            MemorySegment.class,
                            MemorySegment.class, long.class, MemorySegment.class,
                            MemorySegment.class, long.class, MemorySegment.class,
                            int.class));

    private static final MethodHandle ZSTD_DStreamInSize =
            downcall(
                    "ZSTD_DStreamInSize",
                    FunctionDescriptor.of(C_SIZE_T),
                    MethodType.methodType(long.class));
    private static final MethodHandle ZSTD_DStreamOutSize =
            downcall(
                    "ZSTD_DStreamOutSize",
                    FunctionDescriptor.of(C_SIZE_T),
                    MethodType.methodType(long.class));
    private static final MethodHandle ZSTD_createDStream =
            downcall(
                    "ZSTD_createDStream",
                    FunctionDescriptor.of(ValueLayout.ADDRESS),
                    MethodType.methodType(MemorySegment.class));
    private static final MethodHandle ZSTD_initDStream =
            downcall(
                    "ZSTD_initDStream",
                    FunctionDescriptor.of(C_SIZE_T, ValueLayout.ADDRESS),
                    MethodType.methodType(long.class, MemorySegment.class));
    /* ZSTD_freeDStream's counterpart, and the one the JNI implementation calls:
     * a ZSTD_DStream is a ZSTD_DCtx, and both functions free it the same way. */
    private static final MethodHandle ZSTD_freeDCtx =
            downcall(
                    "ZSTD_freeDCtx",
                    FunctionDescriptor.of(C_SIZE_T, ValueLayout.ADDRESS),
                    MethodType.methodType(long.class, MemorySegment.class));

    /* The decompression twin of ZSTD_compressStream2_simpleArgs, chosen for the
     * same reason: no ZSTD_inBuffer / ZSTD_outBuffer means every pointer argument
     * can be a heap byte[] under Linker.Option.critical. */
    private static final MethodHandle ZSTD_decompressStream_simpleArgs =
            downcallCritical(
                    "ZSTD_decompressStream_simpleArgs",
                    FunctionDescriptor.of(
                            C_SIZE_T,
                            ValueLayout.ADDRESS,                                       // ZSTD_DCtx* dctx
                            ValueLayout.ADDRESS, C_SIZE_T, ValueLayout.ADDRESS,        // dst, dstCapacity, dstPos
                            ValueLayout.ADDRESS, C_SIZE_T, ValueLayout.ADDRESS),       // src, srcSize, srcPos
                    MethodType.methodType(long.class,
                            MemorySegment.class,
                            MemorySegment.class, long.class, MemorySegment.class,
                            MemorySegment.class, long.class, MemorySegment.class));

    private static MethodHandle downcall(@NotNull String name,
                                         @NotNull FunctionDescriptor descriptor,
                                         @NotNull MethodType javaType) {
        return adapt(LINKER.downcallHandle(symbol(name), descriptor), javaType);
    }

    private static MethodHandle downcallCritical(@NotNull String name,
                                                 @NotNull FunctionDescriptor descriptor,
                                                 @NotNull MethodType javaType) {
        return adapt(LINKER.downcallHandle(symbol(name), descriptor, Linker.Option.critical(true)), javaType);
    }

    private static @NotNull MemorySegment symbol(@NotNull String name) {
        return LOOKUP.find(name)
                .orElseThrow(() -> new UnsatisfiedLinkError("Cannot find the symbol " + name));
    }

    /**
     * A {@code size_t*} in/out parameter: a one-element array as wide as the
     * platform's size_t, because that is how many bytes libzstd writes into it.
     * The width stays here - callers only ever see {@code long}.
     */
    abstract static class SizeTRef {

        /** The array, as a pointer argument. Built once; the array is final. */
        final @NotNull MemorySegment segment;

        private SizeTRef(@NotNull MemorySegment segment) {
            this.segment = segment;
        }

        abstract long get();

        abstract void set(long value);

        private static final class Wide extends SizeTRef {
            private final long[] cell;

            private Wide(long[] cell) {
                super(MemorySegment.ofArray(cell));
                this.cell = cell;
            }

            long get() {
                return cell[0];
            }

            void set(long value) {
                cell[0] = value;
            }
        }

        private static final class Narrow extends SizeTRef {
            private final int[] cell;

            private Narrow(int[] cell) {
                super(MemorySegment.ofArray(cell));
                this.cell = cell;
            }

            /* size_t is unsigned, so widen it as unsigned - as `adapt` does with
             * the return values. */
            long get() {
                return Integer.toUnsignedLong(cell[0]);
            }

            void set(long value) {
                cell[0] = (int) value;
            }
        }
    }

    /**
     * {@code ZSTD_isError}, without the JNI transition {@link Zstd#isError} costs. Meant
     * for tests the FFM classes make on every call, where the JNI classes make none: the
     * C did this test in C and packed the answer into its return value.
     * <p>
     * libzstd reports an error as {@code (size_t) (0 - code)}, so every error return has
     * the top bit of the size_t set, and a success return is a byte count that cannot
     * approach it. The real predicate is narrower - {@code code > (size_t) -ZSTD_error_maxCode}
     * (error_private.h:52), the top 120 values only - so the two disagree solely on
     * returns that would have to be at least 2^63 bytes to arise. `maxCode` itself is
     * deliberately not transcribed: zstd_errors.h:97 warns it changes between versions,
     * and a stale copy would misclassify new error codes silently instead of failing to
     * link.
     * <p>
     * The width has to be branched on because {@code adapt} widens a 32-bit size_t return
     * with {@code Integer.toUnsignedLong}, which leaves an error as a *positive* long
     * with only its low word set. {@code SIZE_T_IS_64_BIT} is a constant, so the branch
     * folds away. Codes this class returns itself, such as
     * {@code -ZSTD_ERROR_DST_SIZE_TOO_SMALL}, are negative Java longs and satisfy either
     * arm.
     * <p>
     * Only tests a port introduces should use this. Lines carried over from a base class
     * keep {@link Zstd#isError}, so that the two copies of a method stay diffable and the
     * versioned one does not quietly change behaviour the port does not own.
     */
    static boolean isError(long result) {
        return SIZE_T_IS_64_BIT ? result < 0 : (int) result < 0;
    }

    static @NotNull SizeTRef newSizeTRef() {
        return SIZE_T_IS_64_BIT
                ? new SizeTRef.Wide(new long[1])
                : new SizeTRef.Narrow(new int[1]);
    }

    static long cStreamOutSize() {
        try {
            return (long) ZSTD_CStreamOutSize.invokeExact();
        } catch (Throwable t) {
            throw new AssertionError("Call to ZSTD_CStreamOutSize failed", t);
        }
    }

    static @NotNull MemorySegment createCStream() {
        try {
            return (MemorySegment) ZSTD_createCStream.invokeExact();
        } catch (Throwable t) {
            throw new AssertionError("Call to ZSTD_createCStream failed", t);
        }
    }

    static long freeCStream(@NotNull MemorySegment cctx) {
        try {
            return (long) ZSTD_freeCStream.invokeExact(cctx);
        } catch (Throwable t) {
            throw new AssertionError("Call to ZSTD_freeCStream failed", t);
        }
    }

    /** @param directive one of the ZSTD_RESET_* values */
    static long resetCCtx(@NotNull MemorySegment cctx, int directive) {
        try {
            return (long) ZSTD_CCtx_reset.invokeExact(cctx, directive);
        } catch (Throwable t) {
            throw new AssertionError("Call to ZSTD_CCtx_reset failed", t);
        }
    }

    static long initCStream(@NotNull MemorySegment cctx, int level) {
        try {
            return (long) ZSTD_initCStream.invokeExact(cctx, level);
        } catch (Throwable t) {
            throw new AssertionError("Call to ZSTD_initCStream failed", t);
        }
    }

    /** @param param one of the ZSTD_C_* values */
    static long setCCtxParameter(@NotNull MemorySegment cctx, int param, int value) {
        try {
            return (long) ZSTD_CCtx_setParameter.invokeExact(cctx, param, value);
        } catch (Throwable t) {
            throw new AssertionError("Call to ZSTD_CCtx_setParameter failed", t);
        }
    }

    /** `dictSize` is a plain length: the dictionary always starts at the segment's base. */
    static long loadDictionary(@NotNull MemorySegment cctx, @NotNull MemorySegment dict, long dictSize) {
        try {
            return (long) ZSTD_CCtx_loadDictionary.invokeExact(cctx, dict, dictSize);
        } catch (Throwable t) {
            throw new AssertionError("Call to ZSTD_CCtx_loadDictionary failed", t);
        }
    }

    static long refCDict(@NotNull MemorySegment cctx, @NotNull MemorySegment cdict) {
        try {
            return (long) ZSTD_CCtx_refCDict.invokeExact(cctx, cdict);
        } catch (Throwable t) {
            throw new AssertionError("Call to ZSTD_CCtx_refCDict failed", t);
        }
    }

    static @NotNull MemorySegment createCCtx() {
        try {
            return (MemorySegment) ZSTD_createCCtx.invokeExact();
        } catch (Throwable t) {
            throw new AssertionError("Call to ZSTD_createCCtx failed", t);
        }
    }

    static long freeCCtx(@NotNull MemorySegment cctx) {
        try {
            return (long) ZSTD_freeCCtx.invokeExact(cctx);
        } catch (Throwable t) {
            throw new AssertionError("Call to ZSTD_freeCCtx failed", t);
        }
    }

    static long setPledgedSrcSize(@NotNull MemorySegment cctx, long pledgedSrcSize) {
        try {
            return (long) ZSTD_CCtx_setPledgedSrcSize.invokeExact(cctx, pledgedSrcSize);
        } catch (Throwable t) {
            throw new AssertionError("Call to ZSTD_CCtx_setPledgedSrcSize failed", t);
        }
    }

    /**
     * The one-shot counterpart of {@link #compressStream2}: no position slots, so
     * `dstCapacity` and `srcSize` are plain lengths and each segment starts where
     * libzstd does.
     */
    static long compress2(@NotNull MemorySegment cctx,
                          @NotNull MemorySegment dst, long dstCapacity,
                          @NotNull MemorySegment src, long srcSize) {
        try {
            return (long) ZSTD_compress2.invokeExact(cctx, dst, dstCapacity, src, srcSize);
        } catch (Throwable t) {
            throw new AssertionError("Call to ZSTD_compress2 failed", t);
        }
    }

    /**
     * The buffer {@code ZSTD_getFrameProgression} writes its result into.
     * <p>
     * A struct returned by value goes through a hidden pointer to memory the caller owns,
     * which FFM asks for as a {@link SegmentAllocator} first parameter on the handle: the
     * linker calls it once per downcall, for one buffer of the struct's size. Nothing else
     * calls it and the buffer is dead once the fields are copied out, so both arguments
     * are ignored and the same cell is returned every time.
     * <p>
     * The cell has to be off-heap - see the comment on the handle - but it does not have
     * to be new each time, which is the whole cost an {@code Arena} carries here: a
     * {@code malloc}/{@code free} pair per call. One automatic arena per buffer instead,
     * so there is nothing to close, no shared-arena thread handshake, and no confinement
     * to the thread that happened to ask first; the memory goes when this object does.
     */
    static final class FrameProgressionBuffer implements SegmentAllocator {
        private final @NotNull MemorySegment cell = Arena.ofAuto().allocate(ZSTD_frameProgression);

        @Override
        public @NotNull MemorySegment allocate(long byteSize, long byteAlignment) {
            // Always the layout's own size and alignment, and asked for once per call.
            return cell;
        }
    }

    /**
     * Builds the {@link ZstdFrameProgression} here rather than returning the struct,
     * which is what the JNI implementation's {@code NewObject} does at the same point -
     * minus its {@code FindClass} and {@code GetMethodID}, which the C repeats on every
     * call. The fields are read through the layout, which carries the platform's byte
     * order: the two {@code unsigned} members share one slot, so separating them by hand
     * would be right only on a little-endian machine.
     *
     * @param into the caller's reusable cell, so that nothing is allocated per call
     */
    static @NotNull ZstdFrameProgression frameProgression(@NotNull MemorySegment cctx,
                                                          @NotNull FrameProgressionBuffer into) {
        MemorySegment progression;
        try {
            progression = (MemorySegment) ZSTD_getFrameProgression.invokeExact((SegmentAllocator) into, cctx);
        } catch (Throwable t) {
            throw new AssertionError("Call to ZSTD_getFrameProgression failed", t);
        }
        return new ZstdFrameProgression(
                progression.get(C_LONG_LONG, FP_INGESTED),
                progression.get(C_LONG_LONG, FP_CONSUMED),
                progression.get(C_LONG_LONG, FP_PRODUCED),
                progression.get(C_LONG_LONG, FP_FLUSHED),
                progression.get(ValueLayout.JAVA_INT, FP_CURRENT_JOB_ID),
                progression.get(ValueLayout.JAVA_INT, FP_NB_ACTIVE_WORKERS));
    }

    /**
     * `dstCapacity` and `srcSize` are measured from the start of the segments handed
     * over, not from where libzstd begins at `dstPos` / `srcPos` - so a caller that
     * passes a whole array with a non-zero start position passes an absolute end
     * offset rather than a length, as the JNI implementation does. Both position
     * segments are in/out.
     *
     * @param endOp one of the ZSTD_E_* values
     */
    static long compressStream2(@NotNull MemorySegment cctx,
                                @NotNull MemorySegment dst, long dstCapacity, @NotNull MemorySegment dstPos,
                                @NotNull MemorySegment src, long srcSize, @NotNull MemorySegment srcPos,
                                int endOp) {
        try {
            return (long) ZSTD_compressStream2_simpleArgs.invokeExact(
                    cctx,
                    dst, dstCapacity, dstPos,
                    src, srcSize, srcPos,
                    endOp);
        } catch (Throwable t) {
            throw new AssertionError("Call to ZSTD_compressStream2_simpleArgs failed", t);
        }
    }

    static long dStreamInSize() {
        try {
            return (long) ZSTD_DStreamInSize.invokeExact();
        } catch (Throwable t) {
            throw new AssertionError("Call to ZSTD_DStreamInSize failed", t);
        }
    }

    static long dStreamOutSize() {
        try {
            return (long) ZSTD_DStreamOutSize.invokeExact();
        } catch (Throwable t) {
            throw new AssertionError("Call to ZSTD_DStreamOutSize failed", t);
        }
    }

    static @NotNull MemorySegment createDStream() {
        try {
            return (MemorySegment) ZSTD_createDStream.invokeExact();
        } catch (Throwable t) {
            throw new AssertionError("Call to ZSTD_createDStream failed", t);
        }
    }

    static long initDStream(@NotNull MemorySegment dctx) {
        try {
            return (long) ZSTD_initDStream.invokeExact(dctx);
        } catch (Throwable t) {
            throw new AssertionError("Call to ZSTD_initDStream failed", t);
        }
    }

    static long freeDCtx(@NotNull MemorySegment dctx) {
        try {
            return (long) ZSTD_freeDCtx.invokeExact(dctx);
        } catch (Throwable t) {
            throw new AssertionError("Call to ZSTD_freeDCtx failed", t);
        }
    }

    /**
     * `dstCapacity` and `srcSize` bound the segment from its start, not from where
     * libzstd begins at `dstPos`/`srcPos`, so every caller hands over a whole array or
     * buffer and an absolute end offset - a length only when the position starts at 0.
     * Both position segments are in/out. The names are libzstd's (zstd.h:2614-2615).
     *
     * <p>The convention is ZstdInputStreamNoFinalizer's C (jni_inputstream_zstd.c:83-84).
     * The two buffer-decompressing streams' C instead shifts the pointer and passes a
     * length with `pos = 0`, reaching the same span the other way round.
     */
    static long decompressStream(@NotNull MemorySegment dctx,
                                 @NotNull MemorySegment dst, long dstCapacity, @NotNull MemorySegment dstPos,
                                 @NotNull MemorySegment src, long srcSize, @NotNull MemorySegment srcPos) {
        try {
            return (long) ZSTD_decompressStream_simpleArgs.invokeExact(
                    dctx,
                    dst, dstCapacity, dstPos,
                    src, srcSize, srcPos);
        } catch (Throwable t) {
            throw new AssertionError("Call to ZSTD_decompressStream_simpleArgs failed", t);
        }
    }
}

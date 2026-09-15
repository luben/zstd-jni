package com.github.luben.zstd;

import com.github.luben.zstd.util.Native;

import org.jetbrains.annotations.NotNull;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
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
    static final int ZSTD_RESET_SESSION_ONLY = 1;

    /* ZSTD_cParameter */
    static final int ZSTD_C_COMPRESSION_LEVEL = 100;

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

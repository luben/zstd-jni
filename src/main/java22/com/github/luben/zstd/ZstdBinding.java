package com.github.luben.zstd;

import com.github.luben.zstd.util.Native;

import org.jetbrains.annotations.NotNull;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/**
 * libzstd downcall bindings shared by the FFM implementations.
 *
 * Hand-written, verified against `jextract` output for zstd.h. Everything here
 * is a thin, stateless mapping of a libzstd entry point: no buffering, no
 * caching and no stream state, so that each versioned class keeps its own
 * behaviour and only the linkage lives in one place.
 *
 * Package-private on purpose: this type exists only under
 * META-INF/versions/22, so making it public would give a JDK 22+ consumer an
 * API that no other runtime has.
 */
final class ZstdBinding {

    /* The lookup below resolves against the JNI library, so it has to be loaded
     * first. Static initialisers run in textual order, so this block must stay
     * above the fields. */
    static {
        Native.load();
    }

    private ZstdBinding() {
    }

    private static final Linker LINKER = Linker.nativeLinker();

    /* Native.load() above has already loaded the JNI library, which exports the
     * ZSTD_* symbols, so the loader lookup finds them. defaultLookup() covers a
     * libzstd that was linked into the process some other way. */
    private static final SymbolLookup LOOKUP =
            SymbolLookup.loaderLookup().or(LINKER.defaultLookup());

    /* size_t, not C `long`: the two differ on Windows. Every platform that has
     * an FFM Linker at all is 64-bit, so this is always an OfLong. */
    private static final ValueLayout.OfLong C_SIZE_T =
            (ValueLayout.OfLong) LINKER.canonicalLayouts().get("size_t");

    /* ZSTD_EndDirective */
    static final int ZSTD_E_CONTINUE = 0;
    static final int ZSTD_E_FLUSH    = 1;
    static final int ZSTD_E_END      = 2;

    /* ZSTD_ResetDirective */
    static final int ZSTD_RESET_SESSION_ONLY = 1;

    private static final MethodHandle ZSTD_CStreamOutSize =
            downcall("ZSTD_CStreamOutSize", FunctionDescriptor.of(C_SIZE_T));
    private static final MethodHandle ZSTD_createCStream =
            downcall("ZSTD_createCStream", FunctionDescriptor.of(ValueLayout.ADDRESS));
    private static final MethodHandle ZSTD_freeCStream =
            downcall("ZSTD_freeCStream", FunctionDescriptor.of(C_SIZE_T, ValueLayout.ADDRESS));
    private static final MethodHandle ZSTD_CCtx_reset =
            downcall("ZSTD_CCtx_reset", FunctionDescriptor.of(C_SIZE_T, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

    /* The _simpleArgs variant of ZSTD_compressStream2 takes the buffers as plain
     * arguments instead of through ZSTD_inBuffer / ZSTD_outBuffer. That matters:
     * a heap byte[] can be handed to a pointer *argument* under
     * Linker.Option.critical - the FFM analogue of GetPrimitiveArrayCritical,
     * which is exactly what the JNI implementation uses - but its address can
     * never be stored into an off-heap struct. Going through the structs would
     * force a persistent off-heap staging buffer and a copy of every byte in
     * each direction. zstd documents this entry point as being for exactly this
     * purpose: "helpful for binders from dynamic languages which have troubles
     * handling structures containing memory pointers".
     *
     * It is ZSTDLIB_STATIC_API, as are ZSTD_getFrameProgression and
     * ZSTD_getDictID_* which jni_fast_zstd.c and jni_zstd.c already call. libzstd
     * is vendored in src/main/native, so the symbol cannot drift underneath us.
     */
    private static final MethodHandle ZSTD_compressStream2_simpleArgs =
            downcallCritical("ZSTD_compressStream2_simpleArgs", FunctionDescriptor.of(C_SIZE_T,
                    ValueLayout.ADDRESS,                                       // ZSTD_CCtx* cctx
                    ValueLayout.ADDRESS, C_SIZE_T, ValueLayout.ADDRESS,        // dst, dstCapacity, dstPos
                    ValueLayout.ADDRESS, C_SIZE_T, ValueLayout.ADDRESS,        // src, srcSize, srcPos
                    ValueLayout.JAVA_INT));                                    // ZSTD_EndDirective endOp

    private static MethodHandle downcall(@NotNull String name, @NotNull FunctionDescriptor descriptor) {
        return LINKER.downcallHandle(symbol(name), descriptor);
    }

    private static MethodHandle downcallCritical(@NotNull String name, @NotNull FunctionDescriptor descriptor) {
        return LINKER.downcallHandle(symbol(name), descriptor, Linker.Option.critical(true));
    }

    private static @NotNull MemorySegment symbol(@NotNull String name) {
        return LOOKUP.find(name)
                .orElseThrow(() -> new UnsatisfiedLinkError("Cannot find the symbol " + name));
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

    /**
     * `srcSize` is an absolute end offset rather than a length, matching the way
     * the JNI implementation calls this: libzstd is handed the whole array and
     * reads from `srcPos` up to `srcSize`. Both position segments are in/out.
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
}

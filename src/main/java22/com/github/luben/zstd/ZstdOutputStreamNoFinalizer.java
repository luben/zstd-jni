package com.github.luben.zstd;

import com.github.luben.zstd.util.Native;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.ByteBuffer;

/**
 * OutputStream filter that compresses the data using Zstd compression.
 *
 */

public class ZstdOutputStreamNoFinalizer extends FilterOutputStream {

    static {
        Native.load();
    }

    /* ---------------------------------------------------------------------
     * libzstd bindings
     *
     * Hand-written, verified against `jextract` output for zstd.h. They are
     * fields of this class rather than of a shared binding class because this
     * is currently the only FFM implementation; they move out when a second
     * class needs them.
     */

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
    private static final int ZSTD_E_CONTINUE = 0;
    private static final int ZSTD_E_FLUSH    = 1;
    private static final int ZSTD_E_END      = 2;

    /* ZSTD_ResetDirective */
    private static final int ZSTD_RESET_SESSION_ONLY = 1;

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

    /* Opaque pointer to Zstd context object */
    private final long stream;
    /* The same pointer, as a downcall argument */
    private final @NotNull MemorySegment cstream;
    private final @NotNull BufferPool bufferPool;
    private final @NotNull ByteBuffer dstByteBuffer;
    private final byte @NotNull [] dst;
    private boolean isClosed = false;
    private static final int dstSize = (int) recommendedCOutSize();
    private boolean closeFrameOnFlush = false;
    private boolean frameClosed = true;
    private boolean frameStarted = false;
    // keep the active dict from GC
    private @Nullable ZstdDictCompress active_dict;

    /* `dst` as a pointer argument. The array is final, so this is built once. */
    private final @NotNull MemorySegment dstSegment;

    /* The two size_t* in/out parameters. Heap arrays rather than off-heap slots:
     * under Linker.Option.critical a heap segment is a legal pointer argument, so
     * this stream needs no native memory and therefore no Arena at all. Reading
     * and writing the positions through the arrays is also cheaper than going
     * through the segments. */
    private final long[] dstPosArray = new long[1];
    private final long[] srcPosArray = new long[1];
    private final @NotNull MemorySegment dstPos = MemorySegment.ofArray(dstPosArray);
    private final @NotNull MemorySegment srcPos = MemorySegment.ofArray(srcPosArray);

    /* MemorySegment.ofArray allocates, and write() is called once per chunk - at
     * a 1-byte chunk size that is one wrapper per byte. Streams are almost always
     * fed from the same array repeatedly, so cache the last one. */
    private byte @Nullable [] lastSrcArray;
    private @Nullable MemorySegment lastSrcSegment;

    public static long recommendedCOutSize() {
        try {
            return (long) ZSTD_CStreamOutSize.invokeExact();
        } catch (Throwable t) {
            throw new AssertionError("Call to ZSTD_CStreamOutSize failed", t);
        }
    }

    private static @NotNull MemorySegment createCStream() {
        try {
            return (MemorySegment) ZSTD_createCStream.invokeExact();
        } catch (Throwable t) {
            throw new AssertionError("Call to ZSTD_createCStream failed", t);
        }
    }

    private static long freeCStream(@NotNull MemorySegment ctx) {
        try {
            return (long) ZSTD_freeCStream.invokeExact(ctx);
        } catch (Throwable t) {
            throw new AssertionError("Call to ZSTD_freeCStream failed", t);
        }
    }

    private long resetCStream() {
        try {
            return (long) ZSTD_CCtx_reset.invokeExact(cstream, ZSTD_RESET_SESSION_ONLY);
        } catch (Throwable t) {
            throw new AssertionError("Call to ZSTD_CCtx_reset failed", t);
        }
    }

    /* The output always starts at 0 and spans the whole `dst` array, as it does
     * on every call in the JNI build. On return dstPosArray[0] is how many bytes
     * libzstd produced and srcPosArray[0] how far it got through the input. */
    private long compressStream2(@NotNull MemorySegment src, long srcSize, long srcPosition, int endOp) {
        dstPosArray[0] = 0;
        srcPosArray[0] = srcPosition;
        try {
            return (long) ZSTD_compressStream2_simpleArgs.invokeExact(
                    cstream,
                    dstSegment, (long) dstSize, dstPos,
                    src, srcSize, srcPos,
                    endOp);
        } catch (Throwable t) {
            throw new AssertionError("Call to ZSTD_compressStream2_simpleArgs failed", t);
        }
    }

    /* endStream / flushStream in the JNI build: an empty input, so libzstd only
     * drains what it already buffered. */
    private long drainStream(int endOp) {
        return compressStream2(MemorySegment.NULL, 0L, 0L, endOp);
    }

    private @NotNull MemorySegment segmentOf(byte @NotNull [] src) {
        MemorySegment cached = lastSrcSegment;
        if (src != lastSrcArray || cached == null) {
            cached = MemorySegment.ofArray(src);
            lastSrcSegment = cached;
            lastSrcArray = src;
        }
        return cached;
    }

    /**
     * create a new compressing OutputStream
     * @param outStream the stream to wrap
     * @param level the compression level
     */
    public ZstdOutputStreamNoFinalizer(@NotNull OutputStream outStream, int level) throws IOException {
        this(outStream, NoPool.INSTANCE);
        Zstd.setCompressionLevel(this.stream, level);
    }

    /**
     * create a new compressing OutputStream
     * @param outStream the stream to wrap
     */
    public ZstdOutputStreamNoFinalizer(@NotNull OutputStream outStream) throws IOException {
        this(outStream, NoPool.INSTANCE);
    }

    /**
     * create a new compressing OutputStream
     * @param outStream the stream to wrap
     * @param bufferPool the pool to fetch and return buffers
     */
    public ZstdOutputStreamNoFinalizer(@NotNull OutputStream outStream, @NotNull BufferPool bufferPool, int level) throws IOException {
        this(outStream, bufferPool);
        Zstd.setCompressionLevel(this.stream, level);
    }

    /**
     * create a new compressing OutputStream
     * @param outStream the stream to wrap
     * @param bufferPool the pool to fetch and return buffers
     */
    public ZstdOutputStreamNoFinalizer(@NotNull OutputStream outStream, @NotNull BufferPool bufferPool) throws IOException {
        super(outStream);
        // create compression context
        this.cstream = createCStream();
        this.stream = cstream.address();
        this.bufferPool = bufferPool;
        this.dstByteBuffer = Zstd.getArrayBackedBuffer(bufferPool, dstSize);
        this.dst = dstByteBuffer.array();
        this.dstSegment = MemorySegment.ofArray(dst);
    }

    /**
     * Enable checksums for the compressed stream.
     *
     * Default: false
     */
    public synchronized @NotNull ZstdOutputStreamNoFinalizer setChecksum(boolean useChecksums) throws IOException {
        if (isClosed) {
            throw new IOException("StreamClosed");
        }
        if (!frameClosed) {
            throw new IllegalStateException("Change of parameter on initialized stream");
        }
        int size = Zstd.setCompressionChecksums(stream, useChecksums);
        if (Zstd.isError(size)) {
            throw new ZstdIOException(size);
        }
        return this;
    }

    /**
     * Set the compression level.
     *
     * Default: {@link Zstd#defaultCompressionLevel()}
     */
    public synchronized @NotNull ZstdOutputStreamNoFinalizer setLevel(int level) throws IOException {
        if (isClosed) {
            throw new IOException("StreamClosed");
        }
        if (!frameClosed) {
            throw new IllegalStateException("Change of parameter on initialized stream");
        }
        int size = Zstd.setCompressionLevel(stream, level);
        if (Zstd.isError(size)) {
            throw new ZstdIOException(size);
        }
        return this;
    }

    /**
     * Enable Long Distance Matching and set the Window size Log.
     *
     * Values for windowLog outside the range 10-27 will disable and reset LDM
     */
    public synchronized @NotNull ZstdOutputStreamNoFinalizer setLong(int windowLog) throws IOException {
        if (isClosed) {
            throw new IOException("StreamClosed");
        }
        if (!frameClosed) {
            throw new IllegalStateException("Change of parameter on initialized stream");
        }
        int size = Zstd.setCompressionLong(stream, windowLog);
        if (Zstd.isError(size)) {
            throw new ZstdIOException(size);
        }
        return this;
    }

    /**
     * Enable use of worker threads for parallel compression.
     *
     * Default: no worker threads.
     */
    public synchronized @NotNull ZstdOutputStreamNoFinalizer setWorkers(int n) throws IOException {
        if (isClosed) {
            throw new IOException("StreamClosed");
        }
        if (!frameClosed) {
            throw new IllegalStateException("Change of parameter on initialized stream");
        }
        int size = Zstd.setCompressionWorkers(stream, n);
        if (Zstd.isError(size)) {
            throw new ZstdIOException(size);
        }
        return this;
    }

    /**
     * Advanced Compression Option: Set the amount of data reloaded from the
     * previous job.
     *
     * See https://facebook.github.io/zstd/zstd_manual.html#Chapter5 for more information.
     */
    public synchronized @NotNull ZstdOutputStreamNoFinalizer setOverlapLog(int overlapLog) throws IOException {
        if (isClosed) {
            throw new IOException("StreamClosed");
        }
        if (!frameClosed) {
            throw new IllegalStateException("Change of parameter on initialized stream");
        }
        int size = Zstd.setCompressionOverlapLog(stream, overlapLog);
        if (Zstd.isError(size)) {
            throw new ZstdIOException(size);
        }
        return this;
    }

    /**
     * Advanced Compression Option: Set the size of each compression job. Only applies when multi
     * threaded compression is enabled.
     *
     * See https://facebook.github.io/zstd/zstd_manual.html#Chapter5 for more information.
     */
    public synchronized @NotNull ZstdOutputStreamNoFinalizer setJobSize(int jobSize) throws IOException {
        if (isClosed) {
            throw new IOException("StreamClosed");
        }
        if (!frameClosed) {
            throw new IllegalStateException("Change of parameter on initialized stream");
        }
        int size = Zstd.setCompressionJobSize(stream, jobSize);
        if (Zstd.isError(size)) {
            throw new ZstdIOException(size);
        }
        return this;
    }

    /**
     * Advanced Compression Option: Set the target match length.
     *
     * See https://facebook.github.io/zstd/zstd_manual.html#Chapter5 for more information.
     */
    public synchronized @NotNull ZstdOutputStreamNoFinalizer setTargetLength(int targetLength) throws IOException {
        if (isClosed) {
            throw new IOException("StreamClosed");
        }
        if (!frameClosed) {
            throw new IllegalStateException("Change of parameter on initialized stream");
        }
        int size = Zstd.setCompressionTargetLength(stream, targetLength);
        if (Zstd.isError(size)) {
            throw new ZstdIOException(size);
        }
        return this;
    }

    /**
     * Advanced Compression Option: Set the minimum match length.
     *
     * See https://facebook.github.io/zstd/zstd_manual.html#Chapter5 for more information.
     */
    public synchronized @NotNull ZstdOutputStreamNoFinalizer setMinMatch(int minMatch) throws IOException {
        if (isClosed) {
            throw new IOException("StreamClosed");
        }
        if (!frameClosed) {
            throw new IllegalStateException("Change of parameter on initialized stream");
        }
        int size = Zstd.setCompressionMinMatch(stream, minMatch);
        if (Zstd.isError(size)) {
            throw new ZstdIOException(size);
        }
        return this;
    }

    /**
     * Advanced Compression Option: Set the maximum number of searches in a hash chain or a binary
     * tree using logarithmic scale.
     *
     * See https://facebook.github.io/zstd/zstd_manual.html#Chapter5 for more information.
     */
    public synchronized @NotNull ZstdOutputStreamNoFinalizer setSearchLog(int searchLog) throws IOException {
        if (isClosed) {
            throw new IOException("StreamClosed");
        }
        if (!frameClosed) {
            throw new IllegalStateException("Change of parameter on initialized stream");
        }
        int size = Zstd.setCompressionSearchLog(stream, searchLog);
        if (Zstd.isError(size)) {
            throw new ZstdIOException(size);
        }
        return this;
    }

    /**
     * Advanced Compression Option: Set the maximum number of bits for the secondary search
     * structure.
     *
     * See https://facebook.github.io/zstd/zstd_manual.html#Chapter5 for more information.
     */
    public synchronized @NotNull ZstdOutputStreamNoFinalizer setChainLog(int chainLog) throws IOException {
        if (isClosed) {
            throw new IOException("StreamClosed");
        }
        if (!frameClosed) {
            throw new IllegalStateException("Change of parameter on initialized stream");
        }
        int size = Zstd.setCompressionChainLog(stream, chainLog);
        if (Zstd.isError(size)) {
            throw new ZstdIOException(size);
        }
        return this;
    }

    /**
     * Advanced Compression Option: Set the maximum number of bits for a hash table.
     *
     * See https://facebook.github.io/zstd/zstd_manual.html#Chapter5 for more information.
     */
    public synchronized @NotNull ZstdOutputStreamNoFinalizer setHashLog(int hashLog) throws IOException {
        if (isClosed) {
            throw new IOException("StreamClosed");
        }
        if (!frameClosed) {
            throw new IllegalStateException("Change of parameter on initialized stream");
        }
        int size = Zstd.setCompressionHashLog(stream, hashLog);
        if (Zstd.isError(size)) {
            throw new ZstdIOException(size);
        }
        return this;
    }

    /**
     * Advanced Compression Option: Set the maximum number of bits for a match distance.
     *
     * See https://facebook.github.io/zstd/zstd_manual.html#Chapter5 for more information.
     */
    public synchronized @NotNull ZstdOutputStreamNoFinalizer setWindowLog(int windowLog) throws IOException {
        if (isClosed) {
            throw new IOException("StreamClosed");
        }
        if (!frameClosed) {
            throw new IllegalStateException("Change of parameter on initialized stream");
        }
        int size = Zstd.setCompressionWindowLog(stream, windowLog);
        if (Zstd.isError(size)) {
            throw new ZstdIOException(size);
        }
        return this;
    }

    /**
     * Advanced Compression Option: Set the strategy used by a match finder.
     *
     * See https://facebook.github.io/zstd/zstd_manual.html#Chapter5 for more information.
     */
    public synchronized @NotNull ZstdOutputStreamNoFinalizer setStrategy(int strategy) throws IOException {
        if (isClosed) {
            throw new IOException("StreamClosed");
        }
        if (!frameClosed) {
            throw new IllegalStateException("Change of parameter on initialized stream");
        }
        int size = Zstd.setCompressionStrategy(stream, strategy);
        if (Zstd.isError(size)) {
            throw new ZstdIOException(size);
        }
        return this;
    }

    /**
     * Enable closing the frame on flush.
     *
     * This will guarantee that it can be ready fully if the process crashes
     * before closing the stream. On the downside it will negatively affect
     * the compression ratio.
     *
     * Default: false.
     */
    public synchronized @NotNull ZstdOutputStreamNoFinalizer setCloseFrameOnFlush(boolean closeOnFlush) {
        if (!frameClosed) {
            throw new IllegalStateException("Change of parameter on initialized stream");
        }
        this.closeFrameOnFlush = closeOnFlush;
        return this;
    }

    public synchronized @NotNull ZstdOutputStreamNoFinalizer setDict(byte @NotNull [] dict) throws IOException {
        if (isClosed) {
            throw new IOException("StreamClosed");
        }
        if (!frameClosed) {
            throw new IllegalStateException("Change of parameter on initialized stream");
        }
        int size = Zstd.loadDictCompress(stream, dict, dict.length);
        if (Zstd.isError(size)) {
            throw new ZstdIOException(size);
        }
        return this;
    }

    public synchronized @NotNull ZstdOutputStreamNoFinalizer setDict(@NotNull ZstdDictCompress dict) throws IOException {
        if (isClosed) {
            throw new IOException("StreamClosed");
        }
        if (!frameClosed) {
            throw new IllegalStateException("Change of parameter on initialized stream");
        }
        if (dict != null) {
            dict.acquireSharedLock();
        }
        int size = Zstd.loadFastDictCompress(stream, dict);
        if (Zstd.isError(size)) {
            if (dict != null) {
                dict.releaseSharedLock();
            }
            throw new ZstdIOException(size);
        }
        // release the shared lock on the previously used dict (if any)
        if (active_dict != null) {
            active_dict.releaseSharedLock();
        }
        // keep the dict alive so it's not garbage collected
        active_dict = dict;
        return this;
    }

    public synchronized void write(byte @NotNull [] src, int offset, int len) throws IOException {
        if (offset < 0 || len < 0 || len > src.length - offset) {
           throw new IndexOutOfBoundsException("Requested length " + len
                      + " from offset " + offset + " in buffer of size " + src.length);
        }
        if (isClosed) {
            throw new IOException("StreamClosed");
        }
        if (frameClosed) {
            long size = resetCStream();
            if (Zstd.isError(size)) {
                throw new ZstdIOException(size);
            }
            frameClosed = false;
            frameStarted = true;
        }
        // srcSize is an absolute end offset, not a length: libzstd is handed the
        // whole array, with srcSize = offset + len and srcPos = offset.
        int srcSize = offset + len;
        MemorySegment srcSegment = segmentOf(src);
        long srcPosition = offset;
        while (srcPosition < srcSize) {
            long size = compressStream2(srcSegment, srcSize, srcPosition, ZSTD_E_CONTINUE);
            if (Zstd.isError(size)) {
                throw new ZstdIOException(size);
            }
            srcPosition = srcPosArray[0];
            long dstPosition = dstPosArray[0];
            if (dstPosition > 0) {
                out.write(dst, 0, (int) dstPosition);
            }
        }
    }

    public void write(int i) throws IOException {
        byte[] oneByte = new byte[1];
        oneByte[0] = (byte) i;
        write(oneByte, 0, 1);
    }

    /**
     * Flushes the output
     */
    public synchronized void flush() throws IOException {
        if (isClosed) {
            throw new IOException("StreamClosed");
        }
        if (!frameClosed) {
            if (closeFrameOnFlush) {
                // compress the remaining output and close the frame
                long size;
                do {
                    size = drainStream(ZSTD_E_END);
                    if (Zstd.isError(size)) {
                        throw new ZstdIOException(size);
                    }
                    out.write(dst, 0, (int) dstPosArray[0]);
                } while (size > 0);
                frameClosed = true;
            } else {
                // compress the remaining input
                long size;
                do {
                    size = drainStream(ZSTD_E_FLUSH);
                    if (Zstd.isError(size)) {
                        throw new ZstdIOException(size);
                    }
                    out.write(dst, 0, (int) dstPosArray[0]);
                } while (size > 0);
            }
            out.flush();
        }
    }


    public synchronized void close() throws IOException {
        close(true);
    }

    public synchronized void closeWithoutClosingParentStream() throws IOException {
        close(false);
    }


    private void close(boolean closeParentStream) throws IOException {
        if (isClosed) {
            return;
        }
        try {
            long size;
            // Closing the stream withouth before writing anything
            // should still produce valid zstd frame. So reset the
            // stream to start a frame if no frame was ever started.
            if (!frameStarted) {
                size = resetCStream();
                if (Zstd.isError(size)) {
                    throw new ZstdIOException(size);
                }
                frameClosed = false;
            }
            // compress the remaining input and close the frame
            if (!frameClosed) {
                do {
                    size = drainStream(ZSTD_E_END);
                    if (Zstd.isError(size)) {
                        throw new ZstdIOException(size);
                    }
                    out.write(dst, 0, (int) dstPosArray[0]);
                } while (size > 0);
            }
            if (closeParentStream) {
                out.close();
            }
        } finally {
            // release the resources even if underlying stream throw an exception
            if (active_dict != null) {
                active_dict.releaseSharedLock();
                active_dict = null;
            }
            isClosed = true;
            bufferPool.release(dstByteBuffer);
            freeCStream(cstream);
            // do not keep the caller's last source array alive past close()
            lastSrcArray = null;
            lastSrcSegment = null;
        }
    }
}

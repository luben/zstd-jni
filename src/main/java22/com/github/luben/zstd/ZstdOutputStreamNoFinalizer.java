package com.github.luben.zstd;

import com.github.luben.zstd.util.Native;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;

/**
 * OutputStream filter that compresses the data using Zstd compression.
 *
 */

public class ZstdOutputStreamNoFinalizer extends FilterOutputStream {

    static {
        Native.load();
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
        return ZstdBinding.cStreamOutSize();
    }

    private long resetCStream() {
        return ZstdBinding.resetCCtx(cstream, ZstdBinding.ZSTD_RESET_SESSION_ONLY);
    }

    /* The output always starts at 0 and spans the whole `dst` array, as it does
     * on every call in the JNI build. On return dstPosArray[0] is how many bytes
     * libzstd produced and srcPosArray[0] how far it got through the input. */
    private long compressStream2(@NotNull MemorySegment src, long srcSize, long srcPosition, int endOp) {
        dstPosArray[0] = 0;
        srcPosArray[0] = srcPosition;
        return ZstdBinding.compressStream2(
                cstream,
                dstSegment, dstSize, dstPos,
                src, srcSize, srcPos,
                endOp);
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
        this.cstream = ZstdBinding.createCStream();
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
            long size = compressStream2(srcSegment, srcSize, srcPosition, ZstdBinding.ZSTD_E_CONTINUE);
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
                    size = drainStream(ZstdBinding.ZSTD_E_END);
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
                    size = drainStream(ZstdBinding.ZSTD_E_FLUSH);
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
                    size = drainStream(ZstdBinding.ZSTD_E_END);
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
            ZstdBinding.freeCStream(cstream);
            // do not keep the caller's last source array alive past close()
            lastSrcArray = null;
            lastSrcSegment = null;
        }
    }
}

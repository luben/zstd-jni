package com.github.luben.zstd;

import com.github.luben.zstd.util.Native;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;

/**
 * InputStream filter that decompresses the data provided
 * by the underlying InputStream using Zstd compression.
 *
 * It does not support mark/reset methods. It also does not have finalizer,
 * so if you rely on finalizers to clean the native memory and release
 * buffers use `ZstdInputStream` instead.
 */
public class ZstdInputStreamNoFinalizer extends FilterInputStream {

    static {
        Native.load();
    }

    /* Opaque pointer to Zstd context object. Kept only for the Zstd.* natives,
     * which are still JNI and take a long. */
    private final long stream;
    /* The same pointer, as a downcall argument */
    private final @NotNull MemorySegment dstream;

    /* The two size_t* in/out parameters: libzstd writes back through them how much
     * it produced and how far it got through the input. C passes `&dstPos`, and Java
     * cannot take the address of a field, so each value lives in a one-element array -
     * an object whose single element has an address the downcall can hand over and
     * libzstd can write through. Heap arrays rather than off-heap slots: under
     * Linker.Option.critical a heap segment is a legal pointer argument, so this
     * stream needs no native memory and therefore no Arena at all. */
    private final @NotNull ZstdBinding.SizeTRef dstPos = ZstdBinding.newSizeTRef();
    private final @NotNull ZstdBinding.SizeTRef srcPos = ZstdBinding.newSizeTRef();

    private long srcSize = 0;
    private boolean needRead = true;
    private final @NotNull BufferPool bufferPool;
    private final @NotNull ByteBuffer srcByteBuffer;
    private final byte @NotNull [] src;
    /* libzstd takes `src` as a plain pointer, and a downcall can only pass a byte[]
     * as one by wrapping it in a MemorySegment. The wrapper is an allocation (see
     * segmentOf below), but `src` never changes, so it is built once in the
     * constructor. */
    private final @NotNull MemorySegment srcSegment;
    private static final int srcBuffSize = (int) recommendedDInSize();

    private boolean isContinuous = false;
    private boolean frameFinished = true;
    private boolean isClosed = false;
    // keep the active dict alive
    private @Nullable ZstdDictDecompress active_dict;

    /* The destination is the caller's array, and a reader that asks for small
     * chunks hands over the same one on every call. Wrapping it fresh each time
     * would allocate a MemorySegment per call for an identical result, so keep the
     * last wrapper and reuse it while the array is unchanged. */
    private byte @Nullable [] lastDstArray;
    private @Nullable MemorySegment lastDstSegment;

    public static long recommendedDInSize() {
        return ZstdBinding.dStreamInSize();
    }

    public static long recommendedDOutSize() {
        return ZstdBinding.dStreamOutSize();
    }

    /* Keyed on array identity, not equality: a segment is bound to one specific
     * array object. A hit is a field load and a reference compare; a miss costs
     * what wrapping without a cache would have cost anyway.
     *
     * ZstdOutputStreamNoFinalizer does the same for the caller's source array.
     * Kept duplicated on purpose: the cache is per-stream state, so sharing it
     * would add an indirection to every call. */
    private @NotNull MemorySegment segmentOf(byte @NotNull [] dst) {
        MemorySegment cached = lastDstSegment;
        if (dst != lastDstArray || cached == null) {
            cached = MemorySegment.ofArray(dst);
            lastDstSegment = cached;
            lastDstArray = dst;
        }
        return cached;
    }

    /* dstSize is an absolute end offset, not a length: libzstd is handed the whole
     * destination array and writes from dstPos up to dstSize, as it does in the JNI
     * implementation. The source is always the pooled buffer, filled from its start,
     * so srcLength really is a length - it is what the last upstream read returned. */
    private long decompressStream(@NotNull MemorySegment dstSegment, long dstSize, long srcLength) {
        return ZstdBinding.decompressStream(
                dstream,
                dstSegment, dstSize, dstPos.segment,
                srcSegment, srcLength, srcPos.segment);
    }

    /**
     * create a new decompressing InputStream
     * @param inStream the stream to wrap
     */
    public ZstdInputStreamNoFinalizer(@NotNull InputStream inStream) throws IOException {
        this(inStream, NoPool.INSTANCE);
    }

    /**
     * create a new decompressing InputStream
     * @param inStream the stream to wrap
     * @param bufferPool the pool to fetch and return buffers
     */
    public ZstdInputStreamNoFinalizer(@NotNull InputStream inStream, @NotNull BufferPool bufferPool) throws IOException {
        super(inStream);
        this.bufferPool = bufferPool;
        this.srcByteBuffer = Zstd.getArrayBackedBuffer(bufferPool, srcBuffSize);
        this.src = srcByteBuffer.array();
        this.srcSegment = MemorySegment.ofArray(src);
        // memory barrier
        synchronized(this) {
            this.dstream = ZstdBinding.createDStream();
            this.stream = dstream.address();
        }
        /* The JNI implementation calls initDStream here. It runs no libzstd code
         * at all - it only caches the jfieldIDs of srcPos and dstPos, which those
         * fields exist for in the first place - so there is nothing to call. */
    }

    /**
     * Don't break on unfinished frames
     *
     * Use case: decompressing files that are not yet finished writing and compressing
     */
    public synchronized @NotNull ZstdInputStreamNoFinalizer setContinuous(boolean b) {
        isContinuous = b;
        return this;
    }

    public synchronized boolean getContinuous() {
        return this.isContinuous;
    }

    public synchronized @NotNull ZstdInputStreamNoFinalizer setDict(byte @NotNull [] dict) throws IOException {
        if (isClosed) {
            throw new IOException("Stream closed");
        }
        int size = Zstd.loadDictDecompress(stream, dict, dict.length);
        if (Zstd.isError(size)) {
            throw new ZstdIOException(size);
        }
        return this;
    }

    public synchronized @NotNull ZstdInputStreamNoFinalizer setDict(@NotNull ZstdDictDecompress dict) throws IOException {
        if (isClosed) {
            throw new IOException("Stream closed");
        }
        if (dict != null) {
            dict.acquireSharedLock();
        }
        int size = Zstd.loadFastDictDecompress(stream, dict);
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

    public synchronized @NotNull ZstdInputStreamNoFinalizer setLongMax(int windowLogMax) throws IOException {
        if (isClosed) {
            throw new IOException("Stream closed");
        }
        int size = Zstd.setDecompressionLongMax(stream, windowLogMax);
        if (Zstd.isError(size)) {
            throw new ZstdIOException(size);
        }
        return this;
    }

    public synchronized @NotNull ZstdInputStreamNoFinalizer setRefMultipleDDicts(boolean useMultiple) throws IOException {
        if (isClosed) {
            throw new IOException("Stream closed");
        }
        int size = Zstd.setRefMultipleDDicts(stream, useMultiple);
        if (Zstd.isError(size)) {
            throw new ZstdIOException(size);
        }
        return this;
    }

    public synchronized int read(byte @NotNull [] dst, int offset, int len) throws IOException {
        // guard agains buffer overflows
        if (offset < 0 || len < 0 || len > dst.length - offset) {
            throw new IndexOutOfBoundsException("Requested length " + len
                    + " from offset " + offset + " in buffer of size " + dst.length);
        }
        if (len == 0) {
            return 0;
        } else {
            int result = 0;
            while (result == 0) {
                result = readInternal(dst, offset, len);
            }
            return result;
        }
    }

    int readInternal(byte @NotNull [] dst, int offset, int len) throws IOException {
        if (isClosed) {
            throw new IOException("Stream closed");
        }

        // guard against buffer overflows
        if (offset < 0 || len < 0 || len > dst.length - offset) {
            throw new IndexOutOfBoundsException("Requested length " + len
                    + " from offset " + offset + " in buffer of size " + dst.length);
        }
        int dstSize = offset + len;
        dstPos.set(offset);
        /* Mirrors dstPos, which libzstd writes through on every call: read it back
         * once per iteration instead of on each use. */
        long dstPosition = offset;
        long lastDstPos = -1;
        MemorySegment dstSegment = segmentOf(dst);

        while (dstPosition < dstSize && lastDstPos < dstPosition) {
            // we will read only if data from the upstream is available OR
            // we have not yet produced any output
            if (needRead && (in.available() > 0 || dstPosition == offset)) {
                srcSize = in.read(src, 0, srcBuffSize);
                srcPos.set(0);
                if (srcSize < 0) {
                    srcSize = 0;
                    if (frameFinished) {
                        return -1;
                    } else if (isContinuous) {
                        srcSize = (int)(dstPosition - offset);
                        if (srcSize > 0) {
                            return (int) srcSize;
                        }
                        return -1;
                    } else {
                        throw new ZstdIOException(Zstd.errCorruptionDetected(), "Truncated source");
                    }
                } else if (srcSize == 0) {
                    continue;
                } else {
                    frameFinished = false;
                }
            }

            lastDstPos = dstPosition;
            long size = decompressStream(dstSegment, dstSize, srcSize);

            if (Zstd.isError(size)) {
                throw new ZstdIOException(size);
            }
            dstPosition = dstPos.get();

            // we have completed a frame
            if (size == 0) {
                frameFinished = true;
                // we need to read from the upstream only if we have not consumed
                // fully the source buffer
                needRead = srcPos.get() == srcSize;
                return (int)(dstPosition - offset);
            } else {
                // size > 0, so more input is required but there is data left in
                // the decompressor buffers if we have not filled the dst buffer
                needRead = dstPosition < dstSize;
            }
        }
        return (int)(dstPosition - offset);
    }

    public synchronized int read() throws IOException {
        byte[] oneByte = new byte[1];
        int result = 0;
        while (result == 0) {
            result = readInternal(oneByte, 0, 1);
        }
        if (result == 1) {
            return oneByte[0] & 0xff;
        } else {
            return -1;
        }
    }

    public synchronized int available() throws IOException {
        if (isClosed) {
            throw new IOException("Stream closed");
        }
        if (!needRead) {
            return 1;
        } else {
            return in.available();
        }
    }

    /* we don't support mark/reset */
    public boolean markSupported() {
        return false;
    }

    /* we can skip forward */
    public synchronized long skip(long numBytes) throws IOException {
        if (isClosed) {
            throw new IOException("Stream closed");
        }
        if (numBytes <= 0) {
            return 0;
        }
        int bufferLen = (int) recommendedDOutSize();
        if (bufferLen > numBytes) {
            bufferLen = (int) numBytes;
        }
        ByteBuffer buf = Zstd.getArrayBackedBuffer(bufferPool, bufferLen);
        long toSkip = numBytes;
        try {
            byte data[] = buf.array();
            while (toSkip > 0) {
                int read = read(data, 0, (int) Math.min((long) bufferLen, toSkip));
                if (read < 0) {
                    break;
                }
                toSkip -= read;
            }
        } finally {
            bufferPool.release(buf);
        }
        return numBytes - toSkip;
    }

    public synchronized void close() throws IOException {
        if (isClosed) {
            return;
        }
        if (active_dict != null) {
            active_dict.releaseSharedLock();
            active_dict = null;
        }
        isClosed = true;
        bufferPool.release(srcByteBuffer);
        ZstdBinding.freeDCtx(dstream);
        // do not keep the caller's last destination array alive past close()
        lastDstArray = null;
        lastDstSegment = null;
        in.close();
    }
}

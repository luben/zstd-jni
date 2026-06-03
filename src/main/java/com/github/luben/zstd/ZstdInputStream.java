package com.github.luben.zstd;

import java.io.InputStream;
import java.io.FilterInputStream;
import java.io.IOException;

/**
 * InputStream filter that decompresses the data provided
 * by the underlying InputStream using Zstd compression.
 *
 * It does not support mark/reset methods
 */

public class ZstdInputStream extends FilterInputStream {

    private ZstdInputStreamNoFinalizer inner;

    /**
     * create a new decompressing InputStream
     * @param inStream the stream to wrap
     */
    public ZstdInputStream(InputStream inStream) throws IOException {
        super(inStream);
        inner = new ZstdInputStreamNoFinalizer(inStream);
    }

    /**
     * create a new decompressing InputStream
     * @param inStream the stream to wrap
     * @param bufferPool the pool to fetch and return buffers
     */
    public ZstdInputStream(InputStream inStream, BufferPool bufferPool) throws IOException {
        super(inStream);
        inner = new ZstdInputStreamNoFinalizer(inStream, bufferPool);
    }

    /**
     * Enable or disable class finalizers
     *
     * @param finalize default `true` - finalizers are enabled
     *
     * @deprecated
     * If you don't rely on finalizers, use `ZstdInputStreamNoFinalizer` instead, instances of
     * `ZstdInputStream` will always try to close/release in the finalizer.
     */
    @Deprecated
    public void setFinalize(boolean finalize) {
    }

    @Override
    protected void finalize() throws Throwable {
        close();
    }

    public static long recommendedDInSize() {
        return ZstdInputStreamNoFinalizer.recommendedDInSize();
    }

    public static long recommendedDOutSize() {
        return ZstdInputStreamNoFinalizer.recommendedDOutSize();
    }

    /**
     * Don't break on unfinished frames
     *
     * Use case: decompressing files that are not yet finished writing and compressing
     */
    public ZstdInputStream setContinuous(boolean b) {
        inner.setContinuous(b);
        return this;
    }

    public boolean getContinuous() {
        return inner.getContinuous();
    }

    public ZstdInputStream setDict(byte[] dict) throws IOException {
        inner.setDict(dict);
        return this;
    }
    public ZstdInputStream setDict(ZstdDictDecompress dict) throws IOException {
        inner.setDict(dict);
        return this;
    }

    /**
     * Set the maximum back-reference window the decoder may use, as a base-2 log
     * (ZSTD_d_windowLogMax). The default is 27, i.e. a 128&nbsp;MiB window.
     *
     * <p>The window size is taken from the (untrusted) frame header and a native buffer of that
     * size is allocated while the header is parsed &mdash; before any output is produced &mdash; so
     * a hostile frame can force an allocation of up to {@code 1 << windowLogMax} from only a few
     * bytes of input. When decoding untrusted data, set this <i>lower</i> than the default (to the
     * largest window you legitimately expect) to bound that allocation; raise it only when you must
     * accept large / long-distance-matching windows.
     *
     * @param windowLogMax base-2 log of the maximum allowed window size
     * @return this stream
     * @throws IOException if the parameter is rejected by the decoder
     */
    public ZstdInputStream setLongMax(int windowLogMax) throws IOException {
        inner.setLongMax(windowLogMax);
        return this;
    }

    /**
     * Enable or disable support for multiple dictionary references
     *
     * @param useMultiple Enables references table for DDict, so the DDict used for decompression will be
     *                    determined per the dictId in the frame, default: false
     */
    public ZstdInputStream setRefMultipleDDicts(boolean useMultiple) throws IOException {
        inner.setRefMultipleDDicts(useMultiple);
        return this;
    }

    public int read(byte[] dst, int offset, int len) throws IOException {
        return inner.read(dst, offset, len);
    }

    public int read() throws IOException {
        return inner.read();
    }

    public int available() throws IOException {
        return inner.available();
    }


    public long skip(long numBytes) throws IOException {
        return inner.skip(numBytes);
    }

    public boolean markSupported() {
        return inner.markSupported();
    }


    public void close() throws IOException {
        inner.close();
    }
}

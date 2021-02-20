package com.github.luben.zstd;

import java.io.InputStream;
import java.io.IOException;

/**
 * InputStream filter that decompresses the data provided
 * by the underlying InputStream using Zstd compression.
 *
 * It does not support mark/reset methods
 */

public class ZstdInputStream extends ZstdInputStreamBase<ZstdInputStream> {

    /**
     * create a new decompressing InputStream
     * @param inStream the stream to wrap
     */
    public ZstdInputStream(InputStream inStream) throws IOException {
        super(inStream);
    }

    /**
     * create a new decompressing InputStream
     * @param inStream the stream to wrap
     * @param bufferPool the pool to fetch and return buffers
     */
    public ZstdInputStream(InputStream inStream, BufferPool bufferPool) throws IOException {
        super(inStream, bufferPool);
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
    public void setFinalize(boolean finalize) {
    }

    @Override
    protected void finalize() throws Throwable {
        close();
    }
}

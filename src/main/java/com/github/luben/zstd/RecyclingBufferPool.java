package com.github.luben.zstd;

import java.lang.ref.SoftReference;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;

/**
 * An pool of buffers which uses a simple reference queue to recycle buffers.
 *
 * Do not use it as generic buffer pool - it is optimized and supports only
 * buffer sizes used by the Zstd classes.
 */
public class RecyclingBufferPool implements BufferPool {
    public static final BufferPool INSTANCE = new RecyclingBufferPool();

    private static final int buffSize = Math.max(Math.max(
            (int) ZstdOutputStreamNoFinalizer.recommendedCOutSize(),
            (int) ZstdInputStreamNoFinalizer.recommendedDInSize()),
            (int) ZstdInputStreamNoFinalizer.recommendedDOutSize());

    private final ArrayDeque<SoftReference<ByteBuffer>> pool;

    private RecyclingBufferPool() {
        this.pool = new ArrayDeque<SoftReference<ByteBuffer>>();
    }

    @Override
    public synchronized ByteBuffer get(int capacity) {
        if (capacity > buffSize) {
            throw new RuntimeException(
                    "Unsupported buffer size: " + capacity +
                    ". Supported buffer sizes: " + buffSize + " or smaller."
                );
        }
        while(true) {
            SoftReference<ByteBuffer> sbuf = pool.pollFirst();
            if (sbuf == null) {
                return ByteBuffer.allocate(buffSize);
            }
            ByteBuffer buf = sbuf.get();
            if (buf != null) {
                return buf;
            }
        }
    }

    @Override
    public synchronized void release(ByteBuffer buffer) {
        if (buffer.capacity() >= buffSize) {
            // the same as `buffer.clear()` but that method was moved from
            // `Buffer` to `ByteBuffer` and that leads to bytecode incompatibility
            // with previous JVMs when compiled with Jvm11
            buffer.limit(buffer.capacity());
            buffer.position(0);
            pool.addFirst(new SoftReference<ByteBuffer>(buffer));
        }
    }
}

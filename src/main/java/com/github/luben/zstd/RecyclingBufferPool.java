package com.github.luben.zstd;

import java.lang.ref.SoftReference;
import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * A pool of buffers which uses a simple reference queue to recycle buffers.
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

    private final ConcurrentLinkedQueue<SoftReference<ByteBuffer>> pool;

    private RecyclingBufferPool() {
        this.pool = new ConcurrentLinkedQueue<>();
    }

    @Override
    public ByteBuffer get(int capacity) {
        if (capacity > buffSize) {
            throw new RuntimeException(
                    "Unsupported buffer size: " + capacity +
                            ". Supported buffer sizes: " + buffSize + " or smaller."
            );
        }
        while(true) {
            // This if statement introduces a possible race condition of allocating a buffer while we're trying to
            // release one. However, the extra allocation should be considered insignificant in terms of cost.
            // Particularly with respect to throughput.
            SoftReference<ByteBuffer> sbuf = pool.poll();

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
    public void release(ByteBuffer buffer) {
        if (buffer.capacity() >= buffSize) {
            buffer.clear();
            pool.add(new SoftReference<>(buffer));
        }
    }
}

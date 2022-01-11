package com.github.luben.zstd;

import java.lang.ref.SoftReference;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Deque;

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

    private final Deque<SoftReference<ByteBuffer>> pool;

    private RecyclingBufferPool() {
        // TODO: With Java 7 support, migrate this to a ConcurrentLinkedQueue and remove the 'synchronization' of it.
        this.pool = new ArrayDeque<SoftReference<ByteBuffer>>();
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
            SoftReference<ByteBuffer> sbuf = null;

            // This if statement introduces a possible race condition of allocating a buffer while we're trying to
            // release one. However, the extra allocation should be considered insignificant in terms of cost.
            // Particularly with respect to throughput.
            if (!pool.isEmpty()) {
                synchronized (pool) {
                    sbuf = pool.pollFirst();
                }
            }

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
            synchronized (pool) {
                pool.addLast(new SoftReference<ByteBuffer>(buffer));
            }
        }
    }
}

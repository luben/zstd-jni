package com.github.luben.zstd;

import java.lang.ref.SoftReference;
import java.util.Map;
import java.util.HashMap;
import java.util.Deque;
import java.util.ArrayDeque;

/**
 * An pool of buffers which uses a simple reference queue to recycle old buffers.
 */
class BufferPool {
    private static final Map<Integer, SoftReference<BufferPool>> pools = new HashMap<Integer, SoftReference<BufferPool>>();

    static BufferPool get(int length) {
        synchronized (pools) {
            SoftReference<BufferPool> poolReference = pools.get(length);
            BufferPool pool;
            if (poolReference == null || (pool = poolReference.get()) == null) {
                pool = new BufferPool(length);
                poolReference = new SoftReference<BufferPool>(pool);
                pools.put(length, poolReference);
            }
            return pool;
        }
    }

    private final int length;
    private final Deque<byte[]> queue;

    BufferPool(int length) {
        this.length = length;
        this.queue = new ArrayDeque<byte[]>();
    }

    synchronized byte[] checkOut() {
        byte[] buffer = queue.pollFirst();
        if (buffer == null) {
            buffer = new byte[length];
        }
        return buffer;
    }

    synchronized void checkIn(byte[] buffer) {
        if (length != buffer.length) {
            throw new IllegalStateException("buffer size mismatch");
        }
        queue.addLast(buffer);
    }
}
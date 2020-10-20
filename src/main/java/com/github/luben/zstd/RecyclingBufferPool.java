package com.github.luben.zstd;

import java.lang.ref.SoftReference;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.HashMap;
import java.util.Deque;
import java.util.ArrayDeque;

/**
 * An pool of buffers which uses a simple reference queue to recycle old buffers.
 */
public class RecyclingBufferPool implements BufferPool {
    public static final BufferPool INSTANCE = new RecyclingBufferPool();

    private final Map<Integer, SoftReference<Deque<ByteBuffer>>> pools;
    
    private RecyclingBufferPool() {
        this.pools = new HashMap<Integer, SoftReference<Deque<ByteBuffer>>>();
    }

    private Deque<ByteBuffer> getDeque(int capacity) {
        SoftReference<Deque<ByteBuffer>> dequeReference = pools.get(capacity);
        Deque<ByteBuffer> deque;
        if (dequeReference == null || (deque = dequeReference.get()) == null) {
            deque = new ArrayDeque<ByteBuffer>();
            dequeReference = new SoftReference<Deque<ByteBuffer>>(deque);
            pools.put(capacity, dequeReference);
        }
        return deque;
    }

    @Override
    public synchronized ByteBuffer get(int capacity) {
        ByteBuffer buffer = getDeque(capacity).pollFirst();
        if (buffer == null) {
            buffer = ByteBuffer.allocate(capacity);
        }
        return buffer;
    }

    @Override
    public synchronized void release(ByteBuffer buffer) {
        getDeque(buffer.capacity()).addLast(buffer);
    }
}
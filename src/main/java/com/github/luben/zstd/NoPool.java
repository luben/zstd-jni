package com.github.luben.zstd;

import java.nio.ByteBuffer;

/**
 * Implementation of `BufferPool` that does not recycle buffers.
 */
public class NoPool implements BufferPool {
    public static final BufferPool INSTANCE = new NoPool();

    private NoPool() {
    }

    @Override
    public synchronized ByteBuffer get(int capacity) {
       return ByteBuffer.allocate(capacity);
    }

    @Override
    public synchronized void release(ByteBuffer buffer) {
    }
}

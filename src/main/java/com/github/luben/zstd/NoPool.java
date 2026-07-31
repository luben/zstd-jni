package com.github.luben.zstd;

import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;

/**
 * Implementation of `BufferPool` that does not recycle buffers.
 */
public class NoPool implements BufferPool {
    @NotNull
    public static final BufferPool INSTANCE = new NoPool();

    private NoPool() {
    }

    @Override
    @NotNull
    public ByteBuffer get(int capacity) {
       return ByteBuffer.allocate(capacity);
    }

    @Override
    public void release(@NotNull ByteBuffer buffer) {
    }
}

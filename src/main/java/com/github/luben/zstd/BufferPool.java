package com.github.luben.zstd;

import java.nio.ByteBuffer;

/**
 * An interface that allows users to customize how buffers are recycled.
 */
public interface BufferPool {

    /**
     * Fetch a buffer from the pool.
     * @param capacity the desired size of the buffer
     * @return a heap buffer with size at least the `capacity` and arrayOffset of 0
     */
    ByteBuffer get(int capacity);

    /**
     * Return a buffer to the pool.
     * @param buffer the buffer to return
     */
    void release(ByteBuffer buffer);

}

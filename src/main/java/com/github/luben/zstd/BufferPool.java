package com.github.luben.zstd;

import java.util.Map;
import java.util.HashMap;
import java.util.Deque;
import java.util.ArrayDeque;

/**
 * An pool of buffers which uses a simple reference queue to recycle old buffers.
 */
class BufferPool {
    private static final Map<Integer, Deque<byte[]>> queues = new HashMap<Integer, Deque<byte[]>>();

    private static synchronized Deque<byte[]> getQueue(int length) {
        Deque<byte[]> queue = queues.get(length);
        if (queue == null) {
            queue = new ArrayDeque<byte[]>();
            queues.put(length, queue);
        }
        return queue;
    }

    static synchronized byte[] checkOut(int length) {
        byte[] buffer = getQueue(length).pollFirst();
        if (buffer == null) {
            buffer = new byte[length];
        }
        return buffer;
    }

    static synchronized void checkIn(byte[] buffer) {
        getQueue(buffer.length).addLast(buffer);
    }
}
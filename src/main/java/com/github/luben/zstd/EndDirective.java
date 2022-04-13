package com.github.luben.zstd;

/** Enum that expresses desired flushing for a streaming compression call.
 *
 * @see ZstdCompressCtx#compressDirectByteBufferStream
 */
public enum EndDirective {
    CONTINUE(0),
    FLUSH(1),
    END(2);

    private final int value;
    private EndDirective(int value) {
        this.value = value;
    }

    int value() {
        return value;
    }
}

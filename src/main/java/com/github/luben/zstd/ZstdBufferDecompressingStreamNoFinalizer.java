package com.github.luben.zstd;

import com.github.luben.zstd.util.Native;

import java.io.IOException;
import java.nio.ByteBuffer;

public class ZstdBufferDecompressingStreamNoFinalizer extends BaseZstdBufferDecompressingStreamNoFinalizer {
    static {
        Native.load();
    }

    public ZstdBufferDecompressingStreamNoFinalizer(ByteBuffer source) {
        super(source);
        if (source.isDirect()) {
            throw new IllegalArgumentException("Source buffer should be a non-direct buffer");
        }
        stream = createDStreamNative();
        initDStreamNative(stream);
    }

    @Override
    public int read(ByteBuffer target) throws IOException {
        if (target.isDirect()) {
            throw new IllegalArgumentException("Target buffer should be a non-direct buffer");
        }
        return readInternal(target, false);
    }

    @Override
    long createDStream() {
        return createDStreamNative();
    }

    @Override
    long freeDStream(long stream) {
        return freeDStreamNative(stream);
    }

    @Override
    long initDStream(long stream) {
        return initDStreamNative(stream);
    }

    @Override
    long decompressStream(long stream, ByteBuffer dst, int dstOffset, int dstSize, ByteBuffer src, int srcOffset, int srcSize) {
        byte[] targetArr = Zstd.extractArray(dst);
        byte[] sourceArr = Zstd.extractArray(source);

        return decompressStreamNative(stream, targetArr, dstOffset, dstSize, sourceArr, srcOffset, srcSize);
    }

    public static int recommendedTargetBufferSize() {
        return (int) recommendedDOutSizeNative();
    }

    private native long createDStreamNative();

    private native long freeDStreamNative(long stream);

    private native long initDStreamNative(long stream);

    private native long decompressStreamNative(long stream, byte[] dst, int dstOffset, int dstSize, byte[] src, int srcOffset, int srcSize);

    private static native long recommendedDOutSizeNative();
}

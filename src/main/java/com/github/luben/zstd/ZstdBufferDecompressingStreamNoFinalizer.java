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
        stream = createDStream();
        initDStream(stream);
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
    long decompressStream(long stream, ByteBuffer dst, int dstBufPos, int dstSize, ByteBuffer src, int srcBufPos, int srcSize) {
        if (!src.hasArray()) {
            throw new IllegalArgumentException("provided source ByteBuffer lacks array");
        }
        if (!dst.hasArray()) {
            throw new IllegalArgumentException("provided destination ByteBuffer lacks array");
        }
        byte[] targetArr = dst.array();
        byte[] sourceArr = src.array();

        // We are interested in array data corresponding to the pos represented by the ByteBuffer view.
        // A ByteBuffer may share an underlying array with other ByteBuffers. In such scenario, we need to adjust the
        // index of the array by adding an offset using arrayOffset().
        return decompressStreamNative(stream, targetArr, dstBufPos + dst.arrayOffset(), dstSize, sourceArr, srcBufPos + src.arrayOffset(), srcSize);
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

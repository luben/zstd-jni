package com.github.luben.zstd;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class ZstdDictTrainer {
    private final int allocatedSize;
    private final ByteBuffer trainingSamples;
    private final List<Integer> sampleSizes;
    private final int dictSize;
    private long filledSize;

    public ZstdDictTrainer(int sampleSize, int dictSize) {
        trainingSamples = ByteBuffer.allocateDirect(sampleSize);
        sampleSizes =  new ArrayList<>();
        this.allocatedSize = sampleSize;
        this.dictSize = dictSize;
    }

    public boolean addSample(byte[] sample) {
        if (filledSize + sample.length > allocatedSize) {
            return false;
        }
        trainingSamples.put(sample);
        sampleSizes.add(sample.length);
        filledSize += sample.length;
        return true;
    }

    public ByteBuffer trainSamplesDirect() {
        ByteBuffer dictBuffer = ByteBuffer.allocateDirect(dictSize);
        long l = Zstd.trainFromBufferDirect(trainingSamples, copyToIntArray(sampleSizes), dictBuffer);
        if (Zstd.isError(l)) {
            dictBuffer.limit(0);
            // TODO: throw exception here?
            return null;
        }
        dictBuffer.limit(Long.valueOf(l).intValue());
        return dictBuffer;
    }

    public byte[] trainSamples() {
        ByteBuffer byteBuffer = trainSamplesDirect();
        byte[] bytes = new byte[byteBuffer.remaining()];
        byteBuffer.get(bytes);
        return bytes;
    }

    private int[] copyToIntArray(List<Integer> list) {
        int[] ints = new int[list.size()];
        int idx = 0;
        for (Integer i: list) {
            ints[idx] = i;
            idx++;
        }
        return ints;
    }


}

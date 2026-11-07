package com.github.luben.zstd;

/**
 * How the bytes of a dictionary are interpreted, mirroring libzstd's {@code ZSTD_dictContentType_e}.
 *
 * @see ZstdCompressCtx#loadDict(byte[], ZstdDictContentType)
 * @see ZstdDecompressCtx#loadDict(byte[], ZstdDictContentType)
 * @see ZstdDictCompress#ZstdDictCompress(byte[], int, int, int, ZstdDictContentType)
 * @see ZstdDictDecompress#ZstdDictDecompress(byte[], int, int, ZstdDictContentType)
 */
public enum ZstdDictContentType {
    /**
     * The dictionary is a full Zstandard dictionary when it starts with the dictionary magic number
     * ({@code 0xEC30A437}), and raw content otherwise. This is the default.
     */
    AUTO(0),
    /**
     * The dictionary is always raw content, even when it starts with the dictionary magic number.
     * Use this when the dictionary is arbitrary data, such as a previously served resource
     * (for example the {@code dcz} content coding of RFC 9842).
     */
    RAW_CONTENT(1),
    /**
     * The dictionary must be a full Zstandard dictionary, starting with the dictionary magic number.
     * Anything else is rejected.
     */
    FULL_DICT(2);

    private final int value;

    ZstdDictContentType(int value) {
        this.value = value;
    }

    int value() {
        return value;
    }
}

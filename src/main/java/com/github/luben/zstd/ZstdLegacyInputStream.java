package com.github.luben.zstd;

import java.io.InputStream;
import java.io.FilterInputStream;
import java.io.IOException;

abstract class ZstdLegacyInputStream extends FilterInputStream {

    protected int toRead = 0;
    protected int iPos   = 0;

    public ZstdLegacyInputStream(InputStream inStream) throws IOException {
        super(inStream);
    }

    abstract int read_truncated(byte[] dst, int offset, int len) throws IOException;

}

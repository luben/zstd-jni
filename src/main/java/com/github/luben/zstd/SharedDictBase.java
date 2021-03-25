package com.github.luben.zstd;

abstract class SharedDictBase extends AutoCloseBase {

    @Override
    protected void finalize() {
        close();
    }
}

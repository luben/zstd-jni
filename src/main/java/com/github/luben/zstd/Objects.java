package com.github.luben.zstd;

final class Objects {

    /**
     * Checks constraints, that the fromIndex, size, length is not negative, and fromIndex + size is not greater than the size. 
     */
    static void checkFromIndexSize(int fromIndex, int size, int length) {
        if ((length | fromIndex | size) < 0 || size > length - fromIndex) {
            throw new IndexOutOfBoundsException(String.format("Range [%s, %<s + %s) out of bounds for length %s", fromIndex, size, length));
        }
    }
}

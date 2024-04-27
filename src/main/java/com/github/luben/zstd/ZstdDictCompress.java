package com.github.luben.zstd;

import java.nio.ByteBuffer;
import com.github.luben.zstd.util.Native;

public class ZstdDictCompress extends SharedDictBase {

    static {
        Native.load();
    }

    private long nativePtr = 0;

    private ByteBuffer sharedDict = null;

    private int level = Zstd.defaultCompressionLevel();

    private native void init(byte[] dict, int dict_offset, int dict_size, int level);

    private native void initDirect(ByteBuffer dict, int dict_offset, int dict_size, int level, int byReference);

    private native void free();

    /**
     * Get the byte buffer that backs this dict, if any, or null if not backed by a byte buffer.
     */
    public ByteBuffer getByReferenceBuffer() {
	return sharedDict;
    }

    /**
     * Convenience constructor to create a new dictionary for use with fast compress
     *
     * @param dict  buffer containing dictionary to load/parse with exact length
     * @param level compression level
     */
    public ZstdDictCompress(byte[] dict, int level) {
        this(dict, 0, dict.length, level);
    }

    /**
     * Create a new dictionary for use with fast compress
     *
     * @param dict   buffer containing dictionary
     * @param offset the offset into the buffer to read from
     * @param length number of bytes to use from the buffer
     * @param level  compression level
     */
    public ZstdDictCompress(byte[] dict, int offset, int length, int level) {
        this.level = level;
        if (dict.length - offset < 0) {
            throw new IllegalArgumentException("Dictionary buffer is to short");
        }

        init(dict, offset, length, level);

        if (0 == nativePtr) {
            throw new IllegalStateException("ZSTD_createCDict failed");
        }
        // Ensures that even if ZstdDictCompress is created and published through a race, no thread could observe
        // nativePtr == 0.
        storeFence();
    }

    /**
     * Create a new dictionary for use with fast compress. The provided bytebuffer is available for reuse when the method returns.
     *
     * @param dict   Direct ByteBuffer containing dictionary using position and limit to define range in buffer.
     * @param level  compression level
     */
    public ZstdDictCompress(ByteBuffer dict, int level) {
	this(dict, level, false);
    }

    /**
     * Create a new dictionary for use with fast compress.
     * If byReference is true, then the native code does not copy the data but keeps a reference to the byte buffer, which must then not be modified before this context has been closed.
     *
     * @param dict   Direct ByteBuffer containing dictionary using position and limit to define range in buffer.
     * @param level  compression level
     * @param byReference tell the native part to use the byte buffer directly and not copy the data when true.
     */
    public ZstdDictCompress(ByteBuffer dict, int level, boolean byReference) {
	this.level = level;
	int length = dict.limit() - dict.position();
        if (!dict.isDirect()) {
            throw new IllegalArgumentException("dict must be a direct buffer");
        }
        if (length < 0) {
            throw new IllegalArgumentException("dict cannot be empty.");
        }
	initDirect(dict, dict.position(), length, level, byReference ? 1 : 0);

        if (nativePtr == 0L) {
           throw new IllegalStateException("ZSTD_createCDict failed");
        }
	if (byReference) {
	    sharedDict = dict; // ensures the dict is not garbage collected while this object remains.
	}
        // Ensures that even if ZstdDictCompress is created and published through a race, no thread could observe
        // nativePtr == 0.
        storeFence();
    }


    int level() {
        return level;
    }

    @Override
    void  doClose() {
        if (nativePtr != 0) {
            free();
            nativePtr = 0;
            sharedDict = null;
        }
    }
}

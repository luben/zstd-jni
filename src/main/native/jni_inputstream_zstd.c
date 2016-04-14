#include <jni.h>
#include <zstd_static.h>
#include <error_public.h>

# define MIN(_a, _b) (((_a) < (_b)) ? (_a) : (_b))

/*
 * Class:     com_github_luben_zstd_ZstdInputStream
 * Method:    findBlockSize
 * Signature: ([BJ)I
 */
JNIEXPORT jint JNICALL Java_com_github_luben_zstd_ZstdInputStream_findBlockSize
  (JNIEnv *env, jclass obj, jbyteArray src, jlong src_size) {
    ZSTD_frameParams params;
    void *src_buff = (*env)->GetPrimitiveArrayCritical(env, src, NULL);
    size_t size = ZSTD_getFrameParams(&params, src_buff, (size_t) src_size);
    (*env)->ReleasePrimitiveArrayCritical(env, src, src_buff, JNI_ABORT);
    if (size == 0) {
        return (jint) MIN(1 << params.windowLog, ZSTD_BLOCKSIZE_MAX);
    } else {
        return (jint) -size;
    }
}

/*
 * Class:     com_github_luben_zstd_ZstdInputStream
 * Method:    findOBuffSize
 * Signature: ([BJ)I
 */
JNIEXPORT jint JNICALL Java_com_github_luben_zstd_ZstdInputStream_findOBuffSize
  (JNIEnv *env, jclass obj, jbyteArray src, jlong src_size) {
    ZSTD_frameParams params;
    void *src_buff = (*env)->GetPrimitiveArrayCritical(env, src, NULL);
    size_t size = ZSTD_getFrameParams(&params, src_buff, (size_t) src_size);
    (*env)->ReleasePrimitiveArrayCritical(env, src, src_buff, JNI_ABORT);
    if (size == 0) {
        size_t block_size = MIN(1 << params.windowLog, ZSTD_BLOCKSIZE_MAX);
        return (jint) (1 << params.windowLog) + block_size;
    } else {
        return (jint) -size;
    }
}

/*
 * Class:     com_github_luben_zstd_ZstdInputStream
 * Method:    nextSrcSizeToDecompress
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_com_github_luben_zstd_ZstdInputStream_nextSrcSizeToDecompress
  (JNIEnv *env, jclass obj, jlong ctx) {
    return (jint) ZSTD_nextSrcSizeToDecompress((ZSTD_DCtx*)(size_t) ctx);
}

/*
 * Class:     com_github_luben_zstd_ZstdInputStream
 * Method:    createDCtx
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_ZstdInputStream_createDCtx
  (JNIEnv *env, jclass obj) {
    return (jlong)(size_t) ZSTD_createDCtx();
}

/*
 * Class:     com_github_luben_zstd_ZstdInputStream
 * Method:    decompressBegin
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_com_github_luben_zstd_ZstdInputStream_decompressBegin
  (JNIEnv *env, jclass obj, jlong ctx) {
    return ZSTD_decompressBegin((ZSTD_DCtx*)(size_t)ctx);
}

/*
 * Class:     com_github_luben_zstd_ZstdInputStream
 * Method:    freeDCtx
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_com_github_luben_zstd_ZstdInputStream_freeDCtx
  (JNIEnv *env, jclass obj, jlong ctx) {
    return ZSTD_freeDCtx((ZSTD_DCtx*)(size_t)ctx);
}

/*
 * Class:     com_github_luben_zstd_ZstdInputStream
 * Method:    decompressContinue
 * Signature: (JLjava/nio/ByteBuffer;JJ[BJJ)I
 */
JNIEXPORT jint JNICALL Java_com_github_luben_zstd_ZstdInputStream_decompressContinue
  (JNIEnv *env, jclass obj, jlong ctx, jobject dst, jlong dst_offset, jlong dst_size, jbyteArray src, jlong src_offset, jlong src_size) {
    size_t size = (size_t)(0 - ZSTD_error_memory_allocation);
    void *dst_buff = (*env)->GetDirectBufferAddress(env, dst);
    if (dst_buff == NULL) goto E1;
    void *src_buff = (*env)->GetPrimitiveArrayCritical(env, src, NULL);
    if (src_buff == NULL) goto E1;

    size = ZSTD_decompressContinue(
            (ZSTD_DCtx*)(size_t) ctx,
            dst_buff + dst_offset, (size_t) dst_size,
            src_buff + src_offset, (size_t) src_size
        );

    (*env)->ReleasePrimitiveArrayCritical(env, src, src_buff, JNI_ABORT);
E1: return (jint) size;
}

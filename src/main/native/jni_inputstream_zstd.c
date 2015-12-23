#include <string.h>
#include <stdlib.h>

#include <jni.h>
#include <zstd_static.h>
#include <error.h>
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
 * Method:    freeDCtx
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_ZstdInputStream_freeDCtx
  (JNIEnv *env, jclass obj, jlong ctx) {
    return ZSTD_freeDCtx((ZSTD_DCtx*)(size_t)ctx);
}

/*
 * Class:     com_github_luben_zstd_ZstdInputStream
 * Method:    findOBuffSize
 * Signature: ([BJ)I
 */
JNIEXPORT jint JNICALL Java_com_github_luben_zstd_ZstdInputStream_findOBuffSize
  (JNIEnv *env, jclass obj, jbyteArray src, jlong src_size) {
    ZSTD_parameters params;
    void *src_buff = (*env)->GetPrimitiveArrayCritical(env, src, NULL);
    size_t size = ZSTD_getFrameParams(&params, src_buff, (size_t) src_size);
    (*env)->ReleasePrimitiveArrayCritical(env, src, src_buff, 0);
    if (size == 0) {
        return (int) (1 << params.windowLog);
    } else {
        return -size;
    }
}

/*
 * Class:     com_github_luben_zstd_ZstdInputStream
 * Method:    nextSrcSizeToDecompress
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_ZstdInputStream_nextSrcSizeToDecompress
  (JNIEnv *env, jclass obj, jlong ctx) {
    return ZSTD_nextSrcSizeToDecompress((ZSTD_DCtx*)(size_t) ctx);
}

/*
 * Class:     com_github_luben_zstd_ZstdInputStream
 * Method:    decompressContinue
 * Signature: (J[BJJ[BJ)J
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_ZstdInputStream_decompressContinue
  (JNIEnv *env, jclass obj, jlong ctx, jbyteArray dst, jlong dst_offset, jlong dst_size, jbyteArray src, jlong src_size) {
    size_t size = ERROR(memory_allocation);
    void *src_buff = (*env)->GetPrimitiveArrayCritical(env, src, NULL);
    if (src_buff == NULL) goto E1;
    void *dst_buff = (*env)->GetPrimitiveArrayCritical(env, dst, NULL);
    if (dst_buff == NULL) goto E2;
    size = ZSTD_decompressContinue(
            (ZSTD_DCtx*)(size_t) ctx,
            dst_buff + dst_offset, (size_t) dst_size,
            src_buff,              (size_t) src_size
        );
    (*env)->ReleasePrimitiveArrayCritical(env, dst, dst_buff, 0);
E2: (*env)->ReleasePrimitiveArrayCritical(env, src, src_buff, 0);
E1: return size;
}

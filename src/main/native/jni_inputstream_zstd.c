#include <string.h>
#include <stdlib.h>

#include <jni.h>
#include <zstd_static.h>

/*
 * Class:     com_github_luben_zstd_ZstdInputStream
 * Method:    createDCtx
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_ZstdInputStream_createDCtx
  (JNIEnv *env, jclass obj) {
    return (long) ZSTD_createDCtx();
}

/*
 * Class:     com_github_luben_zstd_ZstdInputStream
 * Method:    freeDCtx
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_ZstdInputStream_freeDCtx
  (JNIEnv *env, jclass obj, jlong ctx) {
    return ZSTD_freeDCtx((ZSTD_Dctx*) ctx);
}

/*
 * Class:     com_github_luben_zstd_ZstdInputStream
 * Method:    nextSrcSizeToDecompress
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_ZstdInputStream_nextSrcSizeToDecompress
  (JNIEnv *env, jclass obj, jlong ctx) {
    return ZSTD_nextSrcSizeToDecompress((ZSTD_Dctx*) ctx);
}

/*
 * Class:     com_github_luben_zstd_ZstdInputStream
 * Method:    decompressContinue
 * Signature: (J[BJJ[BJ)J
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_ZstdInputStream_decompressContinue
  (JNIEnv *env, jclass obj, jlong ctx, jbyteArray dst, jlong dst_offset, jlong dst_size, jbyteArray src, jlong src_size) {
    void *dst_buff = NULL;
    void *src_buff = NULL;
    if (dst) {
        dst_buff = (*env)->GetPrimitiveArrayCritical(env, dst, NULL);
    } else {
        dst_size = 0;
        dst_size = 0;
    }
    if (src) {
        src_buff = (*env)->GetPrimitiveArrayCritical(env, src, NULL);
    } else {
        src_size = 0;
    }
    size_t size = ZSTD_decompressContinue((ZSTD_Dctx*) ctx, dst_buff + dst_offset, (size_t) dst_size, src_buff, (size_t) src_size);
    if (src_buff) (*env)->ReleasePrimitiveArrayCritical(env, src, src_buff, 0);
    if (dst_buff) (*env)->ReleasePrimitiveArrayCritical(env, dst, dst_buff, 0);
    return size;
}

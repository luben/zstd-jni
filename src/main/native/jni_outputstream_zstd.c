#include <jni.h>
#include <zstdhc_static.h>

/*
 * Class:     com_github_luben_zstd_ZstdOutputStream
 * Method:    createCCtx
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_ZstdOutputStream_createCCtx
  (JNIEnv *env, jclass obj) {
    return (long) ZSTD_HC_createCCtx();
}

/*
 * Class:     com_github_luben_zstd_ZstdOutputStream
 * Method:    freeCCtx
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_ZstdOutputStream_freeCCtx
  (JNIEnv *env, jclass obj, jlong ctx) {
    return ZSTD_HC_freeCCtx((ZSTD_HC_CCtx*) ctx);
}

/*
 * Class:     com_github_luben_zstd_ZstdOutputStream
 * Method:    comperssBegin
 * Signature: (J[BJI)J
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_ZstdOutputStream_compressBegin
  (JNIEnv *env, jclass obj, jlong ctx, jbyteArray dst, jlong dst_size, jint level) {
    void *dst_buff = (*env)->GetPrimitiveArrayCritical(env, dst, NULL);
    size_t size = ZSTD_HC_compressBegin((ZSTD_HC_CCtx*) ctx, dst_buff, dst_size, level, 0);
    (*env)->ReleasePrimitiveArrayCritical(env, dst, dst_buff, 0);
    return size;
}

/*
 * Class:     com_github_luben_zstd_ZstdOutputStream
 * Method:    compressContinue
 * Signature: (J[BJ[BJJ)J
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_ZstdOutputStream_compressContinue
  (JNIEnv *env, jclass obj, jlong ctx, jbyteArray dst, jlong dst_size, jbyteArray src, jlong src_offset, jlong src_size) {
    void *dst_buff = (*env)->GetPrimitiveArrayCritical(env, dst, NULL);
    void *src_buff = (*env)->GetPrimitiveArrayCritical(env, src, NULL);
    size_t size = ZSTD_HC_compressContinue((ZSTD_HC_CCtx*) ctx, dst_buff, dst_size, src_buff + src_offset, src_size);
    (*env)->ReleasePrimitiveArrayCritical(env, src, src_buff, 0);
    (*env)->ReleasePrimitiveArrayCritical(env, dst, dst_buff, 0);
    return size;
}

/*
 * Class:     com_github_luben_zstd_ZstdOutputStream
 * Method:    compressEnd
 * Signature: (J[BJ)J
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_ZstdOutputStream_compressEnd
  (JNIEnv *env, jclass obj, jlong ctx, jbyteArray dst, jlong dst_size) {
    void *dst_buff = (*env)->GetPrimitiveArrayCritical(env, dst, NULL);
    size_t size = ZSTD_HC_compressEnd((ZSTD_HC_CCtx*) ctx, dst_buff, dst_size);
    (*env)->ReleasePrimitiveArrayCritical(env, dst, dst_buff, 0);
    return size;
}

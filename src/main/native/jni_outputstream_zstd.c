#include <jni.h>
#include <zstd_static.h>

/*
 * Class:     com_github_luben_zstd_ZstdOutputStream
 * Method:    createCCtx
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_ZstdOutputStream_createCCtx
  (JNIEnv *env, jclass obj) {
    return (long) ZSTD_createCCtx();
}

/*
 * Class:     com_github_luben_zstd_ZstdOutputStream
 * Method:    freeCCtx
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_ZstdOutputStream_freeCCtx
  (JNIEnv *env, jclass obj, jlong ctx) {
    return ZSTD_freeCCtx((ZSTD_Cctx*) ctx);
}

/*
 * Class:     com_github_luben_zstd_ZstdOutputStream
 * Method:    comperssBegin
 * Signature: (J[BJ)J
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_ZstdOutputStream_compressBegin
  (JNIEnv *env, jclass obj, jlong ctx, jbyteArray dst, jlong dst_size) {
    void *dst_buff = (*env)->GetPrimitiveArrayCritical(env, dst, NULL);
    size_t size = ZSTD_compressBegin((ZSTD_Cctx*) ctx, dst_buff, dst_size);
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
    size_t size = ZSTD_compressContinue((ZSTD_Cctx*) ctx, dst_buff, dst_size, src_buff + src_offset, src_size);
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
    size_t size = ZSTD_compressEnd((ZSTD_Cctx*) ctx, dst_buff, dst_size);
    (*env)->ReleasePrimitiveArrayCritical(env, dst, dst_buff, 0);
    return size;
}

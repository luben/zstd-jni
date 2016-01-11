#include <jni.h>
#include <zstd_static.h>
#include <error.h>

/*
 * Class:     com_github_luben_zstd_ZstdOutputStream
 * Method:    findIBuffSize
 * Signature: (I)I
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_ZstdOutputStream_findIBuffSize
  (JNIEnv *env, jclass obj, jint level) {
    return (jlong) (1 << ZSTD_defaultParameters[0][level].windowLog);
}

/*
 * Class:     com_github_luben_zstd_ZstdOutputStream
 * Method:    createCCtx
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_ZstdOutputStream_createCCtx
  (JNIEnv *env, jclass obj) {
    return (jlong)(size_t) ZSTD_createCCtx();
}

/*
 * Class:     com_github_luben_zstd_ZstdOutputStream
 * Method:    freeCCtx
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_ZstdOutputStream_freeCCtx
  (JNIEnv *env, jclass obj, jlong ctx) {
    return (jlong) ZSTD_freeCCtx((ZSTD_CCtx*)(size_t) ctx);
}

/*
 * Class:     com_github_luben_zstd_ZstdOutputStream
 * Method:    comperssBegin
 * Signature: (JI)J
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_ZstdOutputStream_compressBegin
  (JNIEnv *env, jclass obj, jlong ctx, jint level) {
    return (jlong) ZSTD_compressBegin((ZSTD_CCtx*)(size_t) ctx, level);
}

/*
 * Class:     com_github_luben_zstd_ZstdOutputStream
 * Method:    compressContinue
 * Signature: (J[BJ[BJJ)J
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_ZstdOutputStream_compressContinue
  (JNIEnv *env, jclass obj, jlong ctx, jbyteArray dst, jlong dst_size, jbyteArray src, jlong src_offset, jlong src_size) {
    size_t size = ERROR(memory_allocation);
    void *dst_buff = (*env)->GetPrimitiveArrayCritical(env, dst, NULL);
    if (dst_buff == NULL) goto E1;
    void *src_buff = (*env)->GetPrimitiveArrayCritical(env, src, NULL);
    if (src_buff == NULL) goto E2;
    size = ZSTD_compressContinue(
                (ZSTD_CCtx*)(size_t) ctx,
                dst_buff, dst_size,
                src_buff + src_offset, src_size
            );
    (*env)->ReleasePrimitiveArrayCritical(env, src, src_buff, 0);
E2: (*env)->ReleasePrimitiveArrayCritical(env, dst, dst_buff, 0);
E1: return size;
}

/*
 * Class:     com_github_luben_zstd_ZstdOutputStream
 * Method:    compressEnd
 * Signature: (J[BJ)J
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_ZstdOutputStream_compressEnd
  (JNIEnv *env, jclass obj, jlong ctx, jbyteArray dst, jlong dst_size) {
    void *dst_buff = (*env)->GetPrimitiveArrayCritical(env, dst, NULL);
    if (dst_buff == NULL) return ERROR(memory_allocation);
    size_t size = ZSTD_compressEnd((ZSTD_CCtx*)(size_t) ctx, dst_buff, dst_size);
    (*env)->ReleasePrimitiveArrayCritical(env, dst, dst_buff, 0);
    return size;
}

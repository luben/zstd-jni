#include <jni.h>
#include <zstd_internal.h>
#include <error_public.h>

/*
 * Class:     com_github_luben_zstd_ZstdOutputStream
 * Method:    findIBuffSize
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_com_github_luben_zstd_ZstdOutputStream_findIBuffSize
  (JNIEnv *env, jclass obj, jint level) {
    return 1 << ZSTD_getCParams(level, 0, 0).windowLog;
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
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_com_github_luben_zstd_ZstdOutputStream_freeCCtx
  (JNIEnv *env, jclass obj, jlong ctx) {
    return ZSTD_freeCCtx((ZSTD_CCtx*)(size_t) ctx);
}

/*
 * Class:     com_github_luben_zstd_ZstdOutputStream
 * Method:    comperssBegin
 * Signature: (JI)I
 */
JNIEXPORT jint JNICALL Java_com_github_luben_zstd_ZstdOutputStream_compressBegin
  (JNIEnv *env, jclass obj, jlong ctx, jint level) {
    return ZSTD_compressBegin((ZSTD_CCtx*)(size_t) ctx, level);
}

/*
 * Class:     com_github_luben_zstd_ZstdOutputStream
 * Method:    compressContinue
 * Signature: (J[BJLjava/nio/ByteBuffer;JJ)J
 */
JNIEXPORT jint JNICALL Java_com_github_luben_zstd_ZstdOutputStream_compressContinue
  (JNIEnv *env, jclass obj, jlong ctx, jbyteArray dst, jlong dst_size, jobject src, jlong src_offset, jlong src_size) {
    size_t size = (size_t)(0-ZSTD_error_memory_allocation);
    void *dst_buff = (*env)->GetPrimitiveArrayCritical(env, dst, NULL);
    if (dst_buff == NULL) goto E1;
    void *src_buff = (*env)->GetDirectBufferAddress(env, src);
    if (src_buff == NULL) goto E2;
    size = ZSTD_compressContinue(
                (ZSTD_CCtx*)(size_t) ctx,
                dst_buff, dst_size,
                src_buff + src_offset, src_size
            );
E2: (*env)->ReleasePrimitiveArrayCritical(env, dst, dst_buff, 0);
E1: return (jint) size;
}

/*
 * Class:     com_github_luben_zstd_ZstdOutputStream
 * Method:    compressEnd
 * Signature: (J[BJ)I
 */
JNIEXPORT jint JNICALL Java_com_github_luben_zstd_ZstdOutputStream_compressEnd
  (JNIEnv *env, jclass obj, jlong ctx, jbyteArray dst, jlong dst_size) {
    void *dst_buff = (*env)->GetPrimitiveArrayCritical(env, dst, NULL);
    if (dst_buff == NULL)
        return 0-ZSTD_error_memory_allocation;
    size_t size = ZSTD_compressEnd((ZSTD_CCtx*)(size_t) ctx, dst_buff, dst_size);
    (*env)->ReleasePrimitiveArrayCritical(env, dst, dst_buff, 0);
    return (jint) size;
}

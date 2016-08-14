#include <jni.h>
#include <zstd_internal.h>
#include <zbuff.h>
#include <error_public.h>


/*

ZSTDLIB_API ZBUFF_CCtx* ZBUFF_createCCtx(void);
ZSTDLIB_API size_t      ZBUFF_freeCCtx(ZBUFF_CCtx* cctx);
ZSTDLIB_API size_t ZBUFF_compressInit(ZBUFF_CCtx* cctx, int compressionLevel);
ZSTDLIB_API size_t ZBUFF_compressInitDictionary(ZBUFF_CCtx* cctx, const void* dict, size_t dictSize, int compressionLevel);
ZSTDLIB_API size_t ZBUFF_compressContinue(ZBUFF_CCtx* cctx, void* dst, size_t* dstCapacityPtr, const void* src, size_t* srcSizePtr);
ZSTDLIB_API size_t ZBUFF_compressFlush(ZBUFF_CCtx* cctx, void* dst, size_t* dstCapacityPtr);
ZSTDLIB_API size_t ZBUFF_compressEnd(ZBUFF_CCtx* cctx, void* dst, size_t* dstCapacityPtr);
*/


static jfieldID src_ptr_id;
static jfieldID dst_ptr_id;

/*
 * Class:     com_github_luben_zstd_ZstdOutputStream
 * Method:    createCCtx
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_ZstdOutputStream_createCCtx
  (JNIEnv *env, jclass obj) {
    return (jlong)(size_t) ZBUFF_createCCtx();
}

/*
 * Class:     com_github_luben_zstd_ZstdOutputStream
 * Method:    freeCCtx
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_com_github_luben_zstd_ZstdOutputStream_freeCCtx
  (JNIEnv *env, jclass obj, jlong ctx) {
    return ZBUFF_freeCCtx((ZBUFF_CCtx*)(size_t) ctx);
}

/*
 * Class:     com_github_luben_zstd_ZstdOutputStream
 * Method:    comperssInit
 * Signature: (JI)I
 */
JNIEXPORT jint JNICALL Java_com_github_luben_zstd_ZstdOutputStream_compressInit
  (JNIEnv *env, jclass obj, jlong ctx, jint level) {
    jclass clazz = (*env)->GetObjectClass(env, obj);
    src_ptr_id = (*env)->GetFieldID(env, clazz, "srcPtr", "J");
    dst_ptr_id = (*env)->GetFieldID(env, clazz, "dstPtr", "J");
    return ZBUFF_compressInit((ZBUFF_CCtx*)(size_t) ctx, level);
}

/*
 * Class:     com_github_luben_zstd_ZstdOutputStream
 * Method:    compressContinue
 * Signature: (J[B[BJ)I
 */
JNIEXPORT jint JNICALL Java_com_github_luben_zstd_ZstdOutputStream_compressContinue
  (JNIEnv *env, jclass obj, jlong ctx, jbyteArray dst, jbyteArray src, jlong src_offset) {

    size_t src_ptr = (size_t) (*env)->GetLongField(env, obj, src_ptr_id);
    size_t dst_ptr = (size_t) (*env)->GetLongField(env, obj, dst_ptr_id);

    size_t size = (size_t)(0-ZSTD_error_memory_allocation);
    void *dst_buff = (*env)->GetPrimitiveArrayCritical(env, dst, NULL);
    if (dst_buff == NULL) goto E1;

    void *src_buff = (*env)->GetPrimitiveArrayCritical(env, src, NULL);
    if (src_buff == NULL) goto E2;


    size = ZBUFF_compressContinue(
                (ZBUFF_CCtx*)(size_t) ctx,
                dst_buff, &dst_ptr,
                src_buff + src_offset, &src_ptr
            );
    (*env)->ReleasePrimitiveArrayCritical(env, src, src_buff, 0);
E2: (*env)->ReleasePrimitiveArrayCritical(env, dst, dst_buff, 0);

    (*env)->SetLongField(env, obj, src_ptr_id, src_ptr);
    (*env)->SetLongField(env, obj, dst_ptr_id, dst_ptr);
E1: return (jint) size;
}

/*
 * Class:     com_github_luben_zstd_ZstdOutputStream
 * Method:    compressEnd
 * Signature: (J[B)I
 */
JNIEXPORT jint JNICALL Java_com_github_luben_zstd_ZstdOutputStream_compressEnd
  (JNIEnv *env, jclass obj, jlong ctx, jbyteArray dst) {
    size_t dst_ptr = (size_t) (*env)->GetLongField(env, obj, dst_ptr_id);
    size_t size = (size_t)(0-ZSTD_error_memory_allocation);
    void *dst_buff = (*env)->GetPrimitiveArrayCritical(env, dst, NULL);
    if (dst_buff == NULL) goto E1;
    size = ZBUFF_compressEnd((ZBUFF_CCtx*)(size_t) ctx, dst_buff, &dst_ptr);
    (*env)->ReleasePrimitiveArrayCritical(env, dst, dst_buff, 0);
    (*env)->SetLongField(env, obj, dst_ptr_id, dst_ptr);
E1: return (jint) size;
}

/*
 * Class:     com_github_luben_zstd_ZstdOutputStream
 * Method:    compressFlush
 * Signature: (J[B)I
 */
JNIEXPORT jint JNICALL Java_com_github_luben_zstd_ZstdOutputStream_compressFlush
  (JNIEnv *env, jclass obj, jlong ctx, jbyteArray dst) {
    size_t dst_ptr = (size_t) (*env)->GetLongField(env, obj, dst_ptr_id);
    size_t size = (size_t)(0-ZSTD_error_memory_allocation);
    void *dst_buff = (*env)->GetPrimitiveArrayCritical(env, dst, NULL);
    if (dst_buff == NULL) goto E1;
    size = ZBUFF_compressFlush((ZBUFF_CCtx*)(size_t) ctx, dst_buff, &dst_ptr);
    (*env)->ReleasePrimitiveArrayCritical(env, dst, dst_buff, 0);
    (*env)->SetLongField(env, obj, dst_ptr_id, dst_ptr);
E1: return (jint) size;
}

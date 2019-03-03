#include <jni.h>
#include <zstd_internal.h>
#include <zstd_errors.h>
#include <stdint.h>

// They can't change in the same VM
static jfieldID compress_dict = 0;
static jfieldID decompress_dict = 0;

/*
 * Class:     com_github_luben_zstd_ZstdDictCompress
 * Method:    init
 * Signature: ([BI)V
 */
JNIEXPORT void JNICALL Java_com_github_luben_zstd_ZstdDictCompress_init
  (JNIEnv *env, jobject obj, jbyteArray dict, jint dict_offset, jint dict_size, jint level)
{
    jclass clazz = (*env)->GetObjectClass(env, obj);
    compress_dict = (*env)->GetFieldID(env, clazz, "nativePtr", "J");
    if (NULL == dict) return;
    void *dict_buff = (*env)->GetPrimitiveArrayCritical(env, dict, NULL);
    if (NULL == dict_buff) return;
    ZSTD_CDict* cdict = ZSTD_createCDict(dict_buff + dict_offset, dict_size, level);
    (*env)->ReleasePrimitiveArrayCritical(env, dict, dict_buff, JNI_ABORT);
    if (NULL == cdict) return;
    (*env)->SetLongField(env, obj, compress_dict, (jlong)(intptr_t) cdict);
}

/*
 * Class:     com_github_luben_zstd_ZstdDictCompress
 * Method:    free
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_com_github_luben_zstd_ZstdDictCompress_free
  (JNIEnv *env, jobject obj)
{
    if (compress_dict == 0) return;
    ZSTD_CDict* cdict = (ZSTD_CDict*)(intptr_t)(*env)->GetLongField(env, obj, compress_dict);
    if (NULL == cdict) return;
    ZSTD_freeCDict(cdict);
}

/*
 * Class:     com_github_luben_zstd_ZstdDictDecompress
 * Method:    init
 * Signature: ([B)V
 */
JNIEXPORT void JNICALL Java_com_github_luben_zstd_ZstdDictDecompress_init
  (JNIEnv *env, jobject obj, jbyteArray dict, jint dict_offset, jint dict_size)
{
    jclass clazz = (*env)->GetObjectClass(env, obj);
    decompress_dict = (*env)->GetFieldID(env, clazz, "nativePtr", "J");
    if (NULL == dict) return;
    void *dict_buff = (*env)->GetPrimitiveArrayCritical(env, dict, NULL);
    if (NULL == dict_buff) return;

    ZSTD_DDict* ddict = ZSTD_createDDict(dict_buff + dict_offset, dict_size);

    (*env)->ReleasePrimitiveArrayCritical(env, dict, dict_buff, JNI_ABORT);
    if (NULL == ddict) return;
    (*env)->SetLongField(env, obj, decompress_dict, (jlong)(intptr_t) ddict);
}

/*
 * Class:     com_github_luben_zstd_ZstdDictDecompress
 * Method:    free
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_com_github_luben_zstd_ZstdDictDecompress_free
  (JNIEnv *env, jobject obj)
{
    if (decompress_dict == 0) return;
    ZSTD_DDict* ddict = (ZSTD_DDict*)(intptr_t)(*env)->GetLongField(env, obj, decompress_dict);
    if (NULL == ddict) return;
    ZSTD_freeDDict(ddict);
}

/*
 * Class:     com_github_luben_zstd_Zstd
 * Method:    decompressFastDict0
 * Signature: ([BI[BIILcom/github/luben/zstd/ZstdDictDecompress;)J
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_Zstd_decompressFastDict0
  (JNIEnv *env, jclass obj, jbyteArray dst, jint dst_offset, jbyteArray src, jint src_offset, jint src_length, jobject dict)
{
    if (NULL == dict) return ZSTD_error_dictionary_wrong;
    ZSTD_DDict* ddict = (ZSTD_DDict*)(intptr_t)(*env)->GetLongField(env, dict, decompress_dict);
    if (NULL == ddict) return ZSTD_error_dictionary_wrong;
    if (NULL == dst) return ZSTD_error_dstSize_tooSmall;
    if (NULL == src) return ZSTD_error_srcSize_wrong;
    if (0 > dst_offset) return ZSTD_error_dstSize_tooSmall;
    if (0 > src_offset) return ZSTD_error_srcSize_wrong;
    if (0 > src_length) return ZSTD_error_srcSize_wrong;

    size_t size = (size_t)(0-ZSTD_error_memory_allocation);
    jsize dst_size = (*env)->GetArrayLength(env, dst);
    jsize src_size = (*env)->GetArrayLength(env, src);
    if (dst_offset > dst_size) return ZSTD_error_dstSize_tooSmall;
    if (src_size < (src_offset + src_length)) return ZSTD_error_srcSize_wrong;
    dst_size -= dst_offset;
    void *dst_buff = (*env)->GetPrimitiveArrayCritical(env, dst, NULL);
    if (dst_buff == NULL) goto E1;
    void *src_buff = (*env)->GetPrimitiveArrayCritical(env, src, NULL);
    if (src_buff == NULL) goto E2;
    ZSTD_DCtx* dctx = ZSTD_createDCtx();
    size = ZSTD_decompress_usingDDict(dctx, dst_buff + dst_offset, (size_t) dst_size, src_buff + src_offset, (size_t) src_length, ddict);
    ZSTD_freeDCtx(dctx);
    (*env)->ReleasePrimitiveArrayCritical(env, src, src_buff, JNI_ABORT);
E2: (*env)->ReleasePrimitiveArrayCritical(env, dst, dst_buff, 0);
E1: return size;
}

/*
 * Class:     com_github_luben_zstd_Zstd
 * Method:    compressFastDict0
 * Signature: ([BI[BIILcom/github/luben/zstd/ZstdDictCompress;)J
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_Zstd_compressFastDict0
  (JNIEnv *env, jclass obj, jbyteArray dst, jint dst_offset, jbyteArray src, jint src_offset, jint src_length, jobject dict) {
    if (NULL == dict) return ZSTD_error_dictionary_wrong;
    ZSTD_CDict* cdict = (ZSTD_CDict*)(intptr_t)(*env)->GetLongField(env, dict, compress_dict);
    if (NULL == cdict) return ZSTD_error_dictionary_wrong;
    if (NULL == dst) return ZSTD_error_dstSize_tooSmall;
    if (NULL == src) return ZSTD_error_srcSize_wrong;
    if (0 > dst_offset) return ZSTD_error_dstSize_tooSmall;
    if (0 > src_offset) return ZSTD_error_srcSize_wrong;
    if (0 > src_length) return ZSTD_error_srcSize_wrong;


    size_t size = (size_t)(0-ZSTD_error_memory_allocation);
    jsize dst_size = (*env)->GetArrayLength(env, dst);
    jsize src_size = (*env)->GetArrayLength(env, src);
    if (dst_offset > dst_size) return ZSTD_error_dstSize_tooSmall;
    if (src_size < (src_offset + src_length)) return ZSTD_error_srcSize_wrong;
    dst_size -= dst_offset;
    void *dst_buff = (*env)->GetPrimitiveArrayCritical(env, dst, NULL);
    if (dst_buff == NULL) goto E1;
    void *src_buff = (*env)->GetPrimitiveArrayCritical(env, src, NULL);
    if (src_buff == NULL) goto E2;
    ZSTD_CCtx* ctx = ZSTD_createCCtx();
    size = ZSTD_compress_usingCDict(ctx, dst_buff + dst_offset, (size_t) dst_size, src_buff + src_offset, (size_t) src_length, cdict);
    ZSTD_freeCCtx(ctx);
    (*env)->ReleasePrimitiveArrayCritical(env, src, src_buff, JNI_ABORT);
E2: (*env)->ReleasePrimitiveArrayCritical(env, dst, dst_buff, 0);
E1: return size;
}

/*
 * Class:     com_github_luben_zstd_Zstd
 * Method:    compressDirectByteBufferFastDict0
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_Zstd_compressDirectByteBufferFastDict0
  (JNIEnv *env, jclass obj, jobject dst, jint dst_offset, jint dst_size, jobject src, jint src_offset, jint src_size, jobject dict) {
    if (NULL == dict) return ZSTD_error_dictionary_wrong;
    ZSTD_CDict* cdict = (ZSTD_CDict*)(intptr_t)(*env)->GetLongField(env, dict, compress_dict);
    if (NULL == cdict) return ZSTD_error_dictionary_wrong;
    if (NULL == dst) return ZSTD_error_dstSize_tooSmall;
    if (NULL == src) return ZSTD_error_srcSize_wrong;
    if (0 > dst_offset) return ZSTD_error_dstSize_tooSmall;
    if (0 > src_offset) return ZSTD_error_srcSize_wrong;
    if (0 > src_size) return ZSTD_error_srcSize_wrong;
    size_t size = (size_t)(0-ZSTD_error_memory_allocation);
    char *dst_buff = (char*)(*env)->GetDirectBufferAddress(env, dst);
    char *src_buff = (char*)(*env)->GetDirectBufferAddress(env, src);
    ZSTD_CCtx* ctx = ZSTD_createCCtx();
    size = ZSTD_compress_usingCDict(ctx, dst_buff + dst_offset, (size_t) dst_size, src_buff + src_offset, (size_t) src_size, cdict);
    ZSTD_freeCCtx(ctx);
    return size;
}

/*
 * Class:     com_github_luben_zstd_Zstd
 * Method:    decompressDirectByteBufferFastDict0
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_Zstd_decompressDirectByteBufferFastDict0
  (JNIEnv *env, jclass obj, jobject dst, jint dst_offset, jint dst_size, jobject src, jint src_offset, jint src_size, jobject dict)
{
    if (NULL == dict) return ZSTD_error_dictionary_wrong;
    ZSTD_DDict* ddict = (ZSTD_DDict*)(intptr_t)(*env)->GetLongField(env, dict, decompress_dict);
    if (NULL == ddict) return ZSTD_error_dictionary_wrong;
    if (NULL == dst) return ZSTD_error_dstSize_tooSmall;
    if (NULL == src) return ZSTD_error_srcSize_wrong;
    if (0 > dst_offset) return ZSTD_error_dstSize_tooSmall;
    if (0 > src_offset) return ZSTD_error_srcSize_wrong;
    if (0 > src_size) return ZSTD_error_srcSize_wrong;

    size_t size = (size_t)(0-ZSTD_error_memory_allocation);
    char *dst_buff = (char*)(*env)->GetDirectBufferAddress(env, dst);
    char *src_buff = (char*)(*env)->GetDirectBufferAddress(env, src);
    ZSTD_DCtx* dctx = ZSTD_createDCtx();
    size = ZSTD_decompress_usingDDict(dctx, dst_buff + dst_offset, (size_t) dst_size, src_buff + src_offset, (size_t) src_size, ddict);
    ZSTD_freeDCtx(dctx);
    return size;
}

#include <jni.h>
#include <zstd_internal.h>
#include <error_public.h>

static jfieldID nativePtrField(JNIEnv *env, jobject obj)
{
    jclass c = (*env)->GetObjectClass(env, obj);
    jfieldID f = (*env)->GetFieldID(env, c, "nativePtr", "J");
    (*env)->DeleteLocalRef(env, c);
    return f;
}

static void* getNativePtr(JNIEnv *env, jobject obj)
{
    jlong handle = (*env)->GetLongField(env, obj, nativePtrField(env, obj));
    return (void*)handle;
}

/*
 * Class:     com_github_luben_zstd_ZstdDictDecompress
 * Method:    init
 * Signature: ([B)V
 */
JNIEXPORT void JNICALL Java_com_github_luben_zstd_ZstdDictDecompress_init
  (JNIEnv *env, jobject obj, jbyteArray dict)
{
    if (NULL == dict) return;
    jsize dict_size = (*env)->GetArrayLength(env, dict);
    void *dict_buff = (*env)->GetPrimitiveArrayCritical(env, dict, NULL);
    if (NULL == dict_buff) return;
    ZSTD_DDict* ddict = ZSTD_createDDict(dict_buff, dict_size);
    (*env)->ReleasePrimitiveArrayCritical(env, dict, dict_buff, JNI_ABORT);
    if (NULL == ddict) return;
    (*env)->SetLongField(env, obj, nativePtrField(env, obj), (jlong)ddict);
}

/*
 * Class:     com_github_luben_zstd_ZstdDictDecompress
 * Method:    free
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_com_github_luben_zstd_ZstdDictDecompress_free
  (JNIEnv *env, jobject obj)
{
    ZSTD_DDict* ddict = getNativePtr(env, obj);
    if (NULL == ddict) return;
    ZSTD_freeDDict(ddict);
}

/*
 * Class:     com_github_luben_zstd_ZstdDictCompress
 * Method:    init
 * Signature: ([BI)V
 */
JNIEXPORT void JNICALL Java_com_github_luben_zstd_ZstdDictCompress_init
  (JNIEnv *env, jobject obj, jbyteArray dict, jint level)
{
    if (NULL == dict) return;
    jsize dict_size = (*env)->GetArrayLength(env, dict);
    void *dict_buff = (*env)->GetPrimitiveArrayCritical(env, dict, NULL);
    if (NULL == dict_buff) return;
    ZSTD_CDict* cdict = ZSTD_createCDict(dict_buff, dict_size, level);
    (*env)->ReleasePrimitiveArrayCritical(env, dict, dict_buff, JNI_ABORT);
    if (NULL == cdict) return;
    (*env)->SetLongField(env, obj, nativePtrField(env, obj), (jlong)cdict);
}

/*
 * Class:     com_github_luben_zstd_ZstdDictCompress
 * Method:    free
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_com_github_luben_zstd_ZstdDictCompress_free
  (JNIEnv *env, jobject obj)
{
    ZSTD_CDict* cdict = getNativePtr(env, obj);
    if (NULL == cdict) return;
    ZSTD_freeCDict(cdict);
}

/*
 * Class:     com_github_luben_zstd_Zstd
 * Method:    decompressFast
 * Signature: ([BI[BIILcom/github/luben/zstd/ZstdDictDecompress;)J
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_Zstd_decompressFastDict
  (JNIEnv *env, jclass obj, jbyteArray dst, jint dst_offset, jbyteArray src, jint src_offset, jint src_length, jobject dict)
{
    if (NULL == dict) return ZSTD_error_dictionary_wrong;
    ZSTD_DDict* ddict = getNativePtr(env, dict);
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
 * Method:    compressFast
 * Signature: ([BI[BIILcom/github/luben/zstd/ZstdDictCompress;)J
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_Zstd_compressFastDict
  (JNIEnv *env, jclass obj, jbyteArray dst, jint dst_offset, jbyteArray src, jint src_offset, jint src_length, jobject dict) {
    if (NULL == dict) return ZSTD_error_dictionary_wrong;
    ZSTD_CDict* cdict = getNativePtr(env, dict);
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

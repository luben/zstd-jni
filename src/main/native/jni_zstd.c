#include <jni.h>
#include <zstd_internal.h>
#include <error_public.h>

#define ZSTD_BLOCKSIZE_MAX (128 * 1024)   /* define, for static allocation */

/*
 * Class:     com_github_luben_zstd_Zstd
 * Method:    compress
 * Signature: ([B[BI)J
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_Zstd_compress
  (JNIEnv *env, jclass obj, jbyteArray dst, jbyteArray src, jint level) {
    size_t size = (size_t)(0-ZSTD_error_memory_allocation);
    jsize dst_size = (*env)->GetArrayLength(env, dst);
    jsize src_size = (*env)->GetArrayLength(env, src);
    void *dst_buff = (*env)->GetPrimitiveArrayCritical(env, dst, NULL);
    if (dst_buff == NULL) goto E1;
    void *src_buff = (*env)->GetPrimitiveArrayCritical(env, src, NULL);
    if (src_buff == NULL) goto E2;
    size = ZSTD_compress(dst_buff, (size_t) dst_size, src_buff, (size_t) src_size, (int) level);
    (*env)->ReleasePrimitiveArrayCritical(env, src, src_buff, 0);
E2: (*env)->ReleasePrimitiveArrayCritical(env, dst, dst_buff, 0);
E1: return size;
}

/*
 * Class:     com_github_luben_zstd_Zstd
 * Method:    decompress
 * Signature: ([B[B)J
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_Zstd_decompress
  (JNIEnv *env, jclass obj, jbyteArray dst, jbyteArray src) {
    size_t size = (size_t)(0-ZSTD_error_memory_allocation);
    jsize dst_size = (*env)->GetArrayLength(env, dst);
    jsize src_size = (*env)->GetArrayLength(env, src);
    void *dst_buff = (*env)->GetPrimitiveArrayCritical(env, dst, NULL);
    if (dst_buff == NULL) goto E1;
    void *src_buff = (*env)->GetPrimitiveArrayCritical(env, src, NULL);
    if (src_buff == NULL) goto E2;
    size = ZSTD_decompress(dst_buff, (size_t) dst_size, src_buff, (size_t) src_size);
    (*env)->ReleasePrimitiveArrayCritical(env, src, src_buff, 0);
E2: (*env)->ReleasePrimitiveArrayCritical(env, dst, dst_buff, 0);
E1: return size;
}

/*
 * Class:     com_github_luben_zstd_Zstd
 * Method:    decompressedSize
 * Signature: ([B)J
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_Zstd_decompressedSize
  (JNIEnv *env, jclass obj, jbyteArray src) {
    size_t size = (size_t)(0-ZSTD_error_memory_allocation);
    jsize src_size = (*env)->GetArrayLength(env, src);
    void *src_buff = (*env)->GetPrimitiveArrayCritical(env, src, NULL);
    if (src_buff == NULL) goto E1;
    size = ZSTD_getDecompressedSize(src_buff, (size_t) src_size);
    (*env)->ReleasePrimitiveArrayCritical(env, src, src_buff, 0);
E1: return size;
}


JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_Zstd_compressUsingDict
  (JNIEnv *env, jclass obj, jbyteArray dst, jint dst_offset, jbyteArray src, jint src_offset, jint src_length, jbyteArray dict, jint level) {
    size_t size = (size_t)(0-ZSTD_error_memory_allocation);
    jsize dst_size = (*env)->GetArrayLength(env, dst) - dst_offset;
    jsize dict_size = (*env)->GetArrayLength(env, dict);
    void *dst_buff = (*env)->GetPrimitiveArrayCritical(env, dst, NULL);
    if (dst_buff == NULL) goto E1;
    void *src_buff = (*env)->GetPrimitiveArrayCritical(env, src, NULL);
    if (src_buff == NULL) goto E2;
    void *dict_buff = (*env)->GetPrimitiveArrayCritical(env, dict, NULL);
    if (dict_buff == NULL) goto E3;
    ZSTD_CCtx* ctx = ZSTD_createCCtx();
    size = ZSTD_compress_usingDict(ctx, dst_buff + dst_offset, (size_t) dst_size, src_buff + src_offset, (size_t) src_length, dict_buff, (size_t) dict_size, (int) level);
    ZSTD_freeCCtx(ctx);
    (*env)->ReleasePrimitiveArrayCritical(env, dict, dict_buff, JNI_ABORT);
E3: (*env)->ReleasePrimitiveArrayCritical(env, src, src_buff, JNI_ABORT);
E2: (*env)->ReleasePrimitiveArrayCritical(env, dst, dst_buff, 0);
E1: return size;
}

JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_Zstd_decompressUsingDict
  (JNIEnv *env, jclass obj, jbyteArray dst, jint dst_offset, jbyteArray src, jint src_offset, jint src_length, jbyteArray dict) {
    size_t size = (size_t)(0-ZSTD_error_memory_allocation);
    jsize dst_size = (*env)->GetArrayLength(env, dst) - dst_offset;
    jsize dict_size = (*env)->GetArrayLength(env, dict);
    void *dst_buff = (*env)->GetPrimitiveArrayCritical(env, dst, NULL);
    if (dst_buff == NULL) goto E1;
    void *src_buff = (*env)->GetPrimitiveArrayCritical(env, src, NULL);
    if (src_buff == NULL) goto E2;
    void *dict_buff = (*env)->GetPrimitiveArrayCritical(env, dict, NULL);
    if (dict_buff == NULL) goto E3;
    ZSTD_DCtx* dctx = ZSTD_createDCtx();
    size = ZSTD_decompress_usingDict(dctx, dst_buff + dst_offset, (size_t) dst_size, src_buff + src_offset, (size_t) src_length, dict_buff, (size_t) dict_size);
    ZSTD_freeDCtx(dctx);
    (*env)->ReleasePrimitiveArrayCritical(env, dict, dict_buff, JNI_ABORT);
E3: (*env)->ReleasePrimitiveArrayCritical(env, src, src_buff, JNI_ABORT);
E2: (*env)->ReleasePrimitiveArrayCritical(env, dst, dst_buff, 0);
E1: return size;
}

/*
 * Class:     com_github_luben_zstd_Zstd
 * Method:    compressBound
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_Zstd_compressBound
  (JNIEnv *env, jclass obj, jlong size) {
    return ZSTD_compressBound((size_t) size);
}

/*
 * Class:     com_github_luben_zstd_Zstd
 * Method:    isError
 * Signature: (J)I
 */
JNIEXPORT jboolean JNICALL Java_com_github_luben_zstd_Zstd_isError
  (JNIEnv *env, jclass obj, jlong code) {
    return ZSTD_isError((size_t) code) != 0;
}

/*
 * Class:     com_github_luben_zstd_Zstd
 * Method:    getErrorName
 * Signature: (J)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_com_github_luben_zstd_Zstd_getErrorName
  (JNIEnv *env, jclass obj, jlong code) {
    const char *msg = ZSTD_getErrorName(code);
    return (*env)->NewStringUTF(env, msg);
}

/*
 * Class:     com_github_luben_zstd_Zstd
 * Methods:   header constants access
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_com_github_luben_zstd_Zstd_windowLogMin
  (JNIEnv *env, jclass obj) {
    return ZSTD_WINDOWLOG_MIN;
}

JNIEXPORT jint JNICALL Java_com_github_luben_zstd_Zstd_windowLogMax
  (JNIEnv *env, jclass obj) {
    return ZSTD_WINDOWLOG_MAX;
}

JNIEXPORT jint JNICALL Java_com_github_luben_zstd_Zstd_chainLogMin
  (JNIEnv *env, jclass obj) {
    return ZSTD_CHAINLOG_MIN;
}

JNIEXPORT jint JNICALL Java_com_github_luben_zstd_Zstd_chainLogMax
  (JNIEnv *env, jclass obj) {
    return ZSTD_CHAINLOG_MAX;
}

JNIEXPORT jint JNICALL Java_com_github_luben_zstd_Zstd_hashLogMin
  (JNIEnv *env, jclass obj) {
    return ZSTD_HASHLOG_MIN;
}

JNIEXPORT jint JNICALL Java_com_github_luben_zstd_Zstd_hashLogMax
  (JNIEnv *env, jclass obj) {
    return ZSTD_HASHLOG_MAX;
}

JNIEXPORT jint JNICALL Java_com_github_luben_zstd_Zstd_searchLogMin
  (JNIEnv *env, jclass obj) {
    return ZSTD_SEARCHLOG_MIN;
}

JNIEXPORT jint JNICALL Java_com_github_luben_zstd_Zstd_searchLogMax
  (JNIEnv *env, jclass obj) {
    return ZSTD_SEARCHLOG_MAX;
}

JNIEXPORT jint JNICALL Java_com_github_luben_zstd_Zstd_searchLengthMin
  (JNIEnv *env, jclass obj) {
    return ZSTD_SEARCHLENGTH_MIN;
}

JNIEXPORT jint JNICALL Java_com_github_luben_zstd_Zstd_searchLengthMax
  (JNIEnv *env, jclass obj) {
    return ZSTD_SEARCHLENGTH_MAX;
}

JNIEXPORT jint JNICALL Java_com_github_luben_zstd_Zstd_targetLengthMin
  (JNIEnv *env, jclass obj) {
    return ZSTD_TARGETLENGTH_MIN;
}

JNIEXPORT jint JNICALL Java_com_github_luben_zstd_Zstd_targetLengthMax
  (JNIEnv *env, jclass obj) {
    return ZSTD_TARGETLENGTH_MAX;
}

JNIEXPORT jint JNICALL Java_com_github_luben_zstd_Zstd_magicNumber
  (JNIEnv *env, jclass obj) {
    return ZSTD_MAGICNUMBER;
}

JNIEXPORT jint JNICALL Java_com_github_luben_zstd_Zstd_frameHeaderSizeMin
  (JNIEnv *env, jclass obj) {
    return ZSTD_frameHeaderSize_min;
}

JNIEXPORT jint JNICALL Java_com_github_luben_zstd_Zstd_frameHeaderSizeMax
  (JNIEnv *env, jclass obj) {
    return ZSTD_frameHeaderSize_max;
}

JNIEXPORT jint JNICALL Java_com_github_luben_zstd_Zstd_blockSizeMax
  (JNIEnv *env, jclass obj) {
    return ZSTD_BLOCKSIZE_MAX;
}

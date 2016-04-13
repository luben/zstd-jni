#include <jni.h>
#include <zstd_static.h>
#include <error_public.h>

/*
 * Class:     com_github_luben_zstd_Zstd
 * Method:    compress
 * Signature: ([BJ[BJI)J
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
 * Signature: ([BJ[BJ)J
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

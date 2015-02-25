#include <jni.h>
#include <zstd_static.h>

/*
 * Class:     com_github_luben_zstd_Zstd
 * Method:    ZSTD_compress
 * Signature: ([BJ[BJ)J
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_Zstd_compress
  (JNIEnv *env, jclass obj, jbyteArray dst, jbyteArray src) {
    void *dst_buff = (*env)->GetPrimitiveArrayCritical(env, dst, NULL);
    jsize dst_size = (*env)->GetArrayLength(env, dst);
    void *src_buff = (*env)->GetPrimitiveArrayCritical(env, src, NULL);
    jsize src_size = (*env)->GetArrayLength(env, src);
    size_t size = ZSTD_compress(dst_buff, (size_t) dst_size, src_buff, (size_t) src_size);
    (*env)->ReleasePrimitiveArrayCritical(env, src, src_buff, 0);
    (*env)->ReleasePrimitiveArrayCritical(env, dst, dst_buff, 0);
    return size;
}

/*
 * Class:     com_github_luben_zstd_Zstd
 * Method:    ZSTD_decompress
 * Signature: ([BJ[BJ)J
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_Zstd_decompress
  (JNIEnv *env, jclass obj, jbyteArray dst, jbyteArray src) {
    void *dst_buff = (*env)->GetPrimitiveArrayCritical(env, dst, NULL);
    jsize dst_size = (*env)->GetArrayLength(env, dst);
    void *src_buff = (*env)->GetPrimitiveArrayCritical(env, src, NULL);
    jsize src_size = (*env)->GetArrayLength(env, src);
    size_t size = ZSTD_decompress(dst_buff, (size_t) dst_size, src_buff, (size_t) src_size);
    (*env)->ReleasePrimitiveArrayCritical(env, src, src_buff, 0);
    (*env)->ReleasePrimitiveArrayCritical(env, dst, dst_buff, 0);
    return size;
}

/*
 * Class:     com_github_luben_zstd_Zstd
 * Method:    ZSTD_compressBound
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_Zstd_compressBound
  (JNIEnv *env, jclass obj, jlong size) {
    return ZSTD_compressBound((size_t) size);
}

/*
 * Class:     com_github_luben_zstd_Zstd
 * Method:    ZSTD_isError
 * Signature: (J)I
 */
JNIEXPORT jboolean JNICALL Java_com_github_luben_zstd_Zstd_isError
  (JNIEnv *env, jclass obj, jlong code) {
    return ZSTD_isError((size_t) code) != 0;
}

/*
 * Class:     com_github_luben_zstd_Zstd
 * Method:    ZSTD_getErrorName
 * Signature: (J)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_com_github_luben_zstd_Zstd_getErrorName
  (JNIEnv *env, jclass obj, jlong code) {
    const char *msg = ZSTD_getErrorName(code);
    return (*env)->NewStringUTF(env, msg);
}

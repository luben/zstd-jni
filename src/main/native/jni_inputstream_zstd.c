#include <jni.h>
#include <zstd_internal.h>
#include <error_public.h>
#include <stdlib.h>

/* field IDs can't change in the same VM */
static jfieldID src_pos_id;
static jfieldID dst_pos_id;

/*
 * Class:     com_github_luben_zstd_ZstdInputStream
 * Method:    recommendedDInSize
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_com_github_luben_zstd_ZstdInputStream_recommendedDInSize
  (JNIEnv *env, jclass obj) {
    return (jint) ZSTD_DStreamInSize();
}

/*
 * Class:     com_github_luben_zstd_ZstdInputStream
 * Method:    createDStream
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_ZstdInputStream_createDStream
  (JNIEnv *env, jclass obj) {
    return (jlong) ZSTD_createDStream();
}

/*
 * Class:     com_github_luben_zstd_ZstdInputStream
 * Method:    freeDStream
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_com_github_luben_zstd_ZstdInputStream_freeDStream
  (JNIEnv *env, jclass obj, jlong stream) {
    return ZSTD_freeDStream((ZSTD_DStream *) stream);
}

/*
 * Class:     com_github_luben_zstd_ZstdInputStream
 * Method:    initDStream
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_com_github_luben_zstd_ZstdInputStream_initDStream
  (JNIEnv *env, jclass obj, jlong stream) {
    jclass clazz = (*env)->GetObjectClass(env, obj);
    src_pos_id = (*env)->GetFieldID(env, clazz, "srcPos", "J");
    dst_pos_id = (*env)->GetFieldID(env, clazz, "dstPos", "J");
    return ZSTD_initDStream((ZSTD_DStream *) stream);
}

/*
 * Class:     com_github_luben_zstd_ZstdInputStream
 * Method:    decompressStream
 * Signature: (J[BI[BI)I
 */
JNIEXPORT jint JNICALL Java_com_github_luben_zstd_ZstdInputStream_decompressStream
  (JNIEnv *env, jclass obj, jlong stream, jbyteArray dst, jint dst_size, jbyteArray src, jint src_size) {

    size_t size = (size_t)(0-ZSTD_error_memory_allocation);

    size_t dst_pos = (size_t) (*env)->GetLongField(env, obj, dst_pos_id);

    void *dst_buff = (*env)->GetPrimitiveArrayCritical(env, dst, NULL);
    if (dst_buff == NULL) goto E1;
    void *src_buff = (*env)->GetPrimitiveArrayCritical(env, src, NULL);
    if (src_buff == NULL) goto E2;

    ZSTD_outBuffer output = { dst_buff, dst_size, dst_pos };
    ZSTD_inBuffer input = { src_buff, src_size, 0 };

    size = ZSTD_decompressStream((ZSTD_DStream *) stream, &output, &input);

    (*env)->ReleasePrimitiveArrayCritical(env, src, src_buff, 0);
E2: (*env)->ReleasePrimitiveArrayCritical(env, dst, dst_buff, 0);
    (*env)->SetLongField(env, obj, src_pos_id, input.pos);
    (*env)->SetLongField(env, obj, dst_pos_id, output.pos);
E1: return (jint) size;
}

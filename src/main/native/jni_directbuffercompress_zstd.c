#include <jni.h>
#include <zstd.h>
#include <zstd_errors.h>
#include <stdlib.h>
#include <stdint.h>

/* field IDs can't change in the same VM */
static jfieldID consumed_id;
static jfieldID produced_id;

/*
 * Class:     com_github_luben_zstd_ZstdDirectBufferCompressingStreamNoFinalizer
 * Method:    recommendedCOutSize
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_ZstdDirectBufferCompressingStreamNoFinalizer_recommendedCOutSize
  (JNIEnv *env, jclass obj) {
    return ZSTD_CStreamOutSize();
}

/*
 * Class:     com_github_luben_zstd_ZstdDirectBufferCompressingStreamNoFinalizer
 * Method:    createCStream
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_ZstdDirectBufferCompressingStreamNoFinalizer_createCStream
  (JNIEnv *env, jclass obj) {
    return (jlong)(intptr_t) ZSTD_createCStream();
}

/*
 * Class:     com_github_luben_zstd_ZstdDirectBufferCompressingStreamNoFinalizer
 * Method:    freeCStream
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_ZstdDirectBufferCompressingStreamNoFinalizer_freeCStream
  (JNIEnv *env, jclass obj, jlong stream) {
    return ZSTD_freeCStream((ZSTD_CStream *)(intptr_t) stream);
}

/*
 * Class:     com_github_luben_zstd_ZstdDirectBufferCompressingStreamNoFinalizer
 * Method:    initCStream
 * Signature: (JI)J
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_ZstdDirectBufferCompressingStreamNoFinalizer_initCStream
  (JNIEnv *env, jclass obj, jlong stream, jint level) {
    jclass clazz = (*env)->GetObjectClass(env, obj);
    consumed_id = (*env)->GetFieldID(env, clazz, "consumed", "I");
    produced_id = (*env)->GetFieldID(env, clazz, "produced", "I");
    return ZSTD_initCStream((ZSTD_CStream *)(intptr_t) stream, level);
}

/*
 * Class:     com_github_luben_zstd_ZstdDirectBufferCompressingStreamNoFinalizer
 * Method:    initCStreamWithDict
 * Signature: (J[BII)J
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_ZstdDirectBufferCompressingStreamNoFinalizer_initCStreamWithDict
  (JNIEnv *env, jclass obj, jlong stream, jbyteArray dict, jint dict_size, jint level) {
    size_t result = -ZSTD_error_memory_allocation;
    jclass clazz = (*env)->GetObjectClass(env, obj);
    consumed_id = (*env)->GetFieldID(env, clazz, "consumed", "I");
    produced_id = (*env)->GetFieldID(env, clazz, "produced", "I");
    void *dict_buff = (*env)->GetPrimitiveArrayCritical(env, dict, NULL);
    if (dict_buff == NULL) goto E1;
    ZSTD_CCtx_reset((ZSTD_CStream *)(intptr_t) stream, ZSTD_reset_session_only);
    ZSTD_CCtx_setParameter((ZSTD_CStream *)(intptr_t) stream, ZSTD_c_compressionLevel, level);
    result = ZSTD_CCtx_loadDictionary((ZSTD_CStream *)(intptr_t) stream, dict_buff, dict_size);
    (*env)->ReleasePrimitiveArrayCritical(env, dict, dict_buff, JNI_ABORT);
E1:
    return result;
}

/*
 * Class:     com_github_luben_zstd_ZstdDirectBufferCompressingStreamNoFinalizer
 * Method:    initCStreamWithFastDict
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_ZstdDirectBufferCompressingStreamNoFinalizer_initCStreamWithFastDict
  (JNIEnv *env, jclass obj, jlong stream, jobject dict) {
    jclass clazz = (*env)->GetObjectClass(env, obj);
    consumed_id = (*env)->GetFieldID(env, clazz, "consumed", "I");
    produced_id = (*env)->GetFieldID(env, clazz, "produced", "I");
    jclass dict_clazz = (*env)->GetObjectClass(env, dict);
    jfieldID compress_dict = (*env)->GetFieldID(env, dict_clazz, "nativePtr", "J");
    ZSTD_CDict* cdict = (ZSTD_CDict*)(intptr_t)(*env)->GetLongField(env, dict, compress_dict);
    if (cdict == NULL) return -ZSTD_error_dictionary_wrong;
    ZSTD_CCtx_reset((ZSTD_CStream *)(intptr_t) stream, ZSTD_reset_session_only);
    return ZSTD_CCtx_refCDict((ZSTD_CStream *)(intptr_t) stream, cdict);
}

/*
 * Class:     com_github_luben_zstd_ZstdDirectBufferCompressingStreamNoFinalizer
 * Method:    compressDirectByteBuffer
 * Signature: (JLjava/nio/ByteBuffer;IILjava/nio/ByteBuffer;II)J
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_ZstdDirectBufferCompressingStreamNoFinalizer_compressDirectByteBuffer
  (JNIEnv *env, jclass obj, jlong stream, jobject dst_buf, jint dst_offset, jint dst_size, jobject src_buf, jint src_offset, jint src_size) {

    size_t size = -ZSTD_error_memory_allocation;
    jsize dst_cap = (*env)->GetDirectBufferCapacity(env, dst_buf);
    if (dst_offset + dst_size > dst_cap) return -ZSTD_error_dstSize_tooSmall;
    jsize src_cap = (*env)->GetDirectBufferCapacity(env, src_buf);
    if (src_offset + src_size > src_cap) return -ZSTD_error_srcSize_wrong;
    char *dst_buf_ptr = (char*)(*env)->GetDirectBufferAddress(env, dst_buf);
    if (dst_buf_ptr == NULL) goto E1;
    char *src_buf_ptr = (char*)(*env)->GetDirectBufferAddress(env, src_buf);
    if (src_buf_ptr == NULL) goto E1;

    ZSTD_outBuffer output = { dst_buf_ptr + dst_offset, dst_size, 0 };
    ZSTD_inBuffer input = { src_buf_ptr + src_offset, src_size, 0 };

    size = ZSTD_compressStream((ZSTD_CStream *)(intptr_t) stream, &output, &input);

    (*env)->SetIntField(env, obj, consumed_id, input.pos);
    (*env)->SetIntField(env, obj, produced_id, output.pos);
E1: return size;
}

/*
 * Class:     com_github_luben_zstd_ZstdDirectBufferCompressingStreamNoFinalizer
 * Method:    endStream
 * Signature: (JLjava/nio/ByteBuffer;II)J
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_ZstdDirectBufferCompressingStreamNoFinalizer_endStream
  (JNIEnv *env, jclass obj, jlong stream, jobject dst_buf, jint dst_offset, jint dst_size) {

    size_t size = -ZSTD_error_memory_allocation;

    jsize dst_cap = (*env)->GetDirectBufferCapacity(env, dst_buf);
    if (dst_offset + dst_size > dst_cap) return -ZSTD_error_dstSize_tooSmall;
    char *dst_buf_ptr = (char*)(*env)->GetDirectBufferAddress(env, dst_buf);

    if (dst_buf_ptr != NULL) {
        ZSTD_outBuffer output = { dst_buf_ptr + dst_offset, dst_size, 0 };
        size = ZSTD_endStream((ZSTD_CStream *)(intptr_t) stream, &output);
        (*env)->SetIntField(env, obj, produced_id, output.pos);
    }
    return size;
}

/*
 * Class:     com_github_luben_zstd_ZstdDirectBufferCompressingStreamNoFinalizer
 * Method:    flushStream
 * Signature: (JLjava/nio/ByteBuffer;II)J
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_ZstdDirectBufferCompressingStreamNoFinalizer_flushStream
  (JNIEnv *env, jclass obj, jlong stream, jobject dst_buf, jint dst_offset, jint dst_size) {

    size_t size = -ZSTD_error_memory_allocation;

    jsize dst_cap = (*env)->GetDirectBufferCapacity(env, dst_buf);
    if (dst_offset + dst_size > dst_cap) return -ZSTD_error_dstSize_tooSmall;
    char *dst_buf_ptr = (char*)(*env)->GetDirectBufferAddress(env, dst_buf);
    if (dst_buf_ptr != NULL) {
        ZSTD_outBuffer output = { dst_buf_ptr + dst_offset, dst_size, 0 };
        size = ZSTD_flushStream((ZSTD_CStream *)(intptr_t) stream, &output);
        (*env)->SetIntField(env, obj, produced_id, output.pos);
    }
    return size;
}

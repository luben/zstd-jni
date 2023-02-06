#include <jni.h>
#include <zstd.h>
#include <zstd_errors.h>
#include <stdlib.h>
#include <stdint.h>

/* field IDs can't change in the same VM */
static jfieldID consumed_id;
static jfieldID produced_id;

/*
 * Class:     com_github_luben_zstd_ZstdBufferDecompressingStreamNoFinalizer
 * Method:    recommendedDOutSizeNative
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_ZstdBufferDecompressingStreamNoFinalizer_recommendedDOutSizeNative
  (JNIEnv *env, jclass obj) {
    return ZSTD_DStreamOutSize();
}

/*
 * Class:     com_github_luben_zstd_ZstdBufferDecompressingStreamNoFinalizer
 * Method:    createDStreamNative
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_ZstdBufferDecompressingStreamNoFinalizer_createDStreamNative
  (JNIEnv *env, jclass obj) {
    return (jlong)(intptr_t) ZSTD_createDStream();
}

/*
 * Class:     com_github_luben_zstd_ZstdBufferDecompressingStreamNoFinalizer
 * Method:    freeDStreamNative
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_ZstdBufferDecompressingStreamNoFinalizer_freeDStreamNative
  (JNIEnv *env, jclass obj, jlong stream) {
    return ZSTD_freeDCtx((ZSTD_DCtx *)(intptr_t) stream);
}

/*
 * Class:     com_github_luben_zstd_ZstdBufferDecompressingStreamNoFinalizer
 * Method:    initDStreamNative
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_ZstdBufferDecompressingStreamNoFinalizer_initDStreamNative
  (JNIEnv *env, jclass obj, jlong stream) {
    jclass clazz = (*env)->GetObjectClass(env, obj);
    consumed_id = (*env)->GetFieldID(env, clazz, "consumed", "I");
    produced_id = (*env)->GetFieldID(env, clazz, "produced", "I");
    return ZSTD_initDStream((ZSTD_DCtx *)(intptr_t) stream);
}

/*
 * Class:     com_github_luben_zstd_ZstdBufferDecompressingStreamNoFinalizer
 * Method:    decompressStreamNative
 * Signature: (JLjava/nio/ByteBuffer;IILjava/nio/ByteBuffer;II)I
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_ZstdBufferDecompressingStreamNoFinalizer_decompressStreamNative
  (JNIEnv *env, jclass obj, jlong stream, jbyteArray dst, jint dst_offset, jint dst_size, jbyteArray src, jint src_offset, jint src_size) {
    // input validation
    if (NULL == dst) return -ZSTD_error_dstSize_tooSmall;
    if (NULL == src) return -ZSTD_error_srcSize_wrong;
    if (0 > dst_offset) return -ZSTD_error_dstSize_tooSmall;
    if (0 > src_offset) return -ZSTD_error_srcSize_wrong;
    if (0 > src_size) return -ZSTD_error_srcSize_wrong;
    if (0 > dst_size) return -ZSTD_error_dstSize_tooSmall;
    if (src_offset + src_size > (*env)->GetArrayLength(env, src)) return -ZSTD_error_srcSize_wrong;
    if (dst_offset + dst_size > (*env)->GetArrayLength(env, dst)) return -ZSTD_error_dstSize_tooSmall;

    size_t size = -ZSTD_error_memory_allocation;
    char *dst_buf_ptr = (char*)(*env)->GetPrimitiveArrayCritical(env, dst, NULL);
    if (dst_buf_ptr == NULL) goto E1;
    char *src_buf_ptr = (char*)(*env)->GetPrimitiveArrayCritical(env, src, NULL);
    if (src_buf_ptr == NULL) goto E2;

    ZSTD_outBuffer output = { dst_buf_ptr + dst_offset, dst_size, 0};
    ZSTD_inBuffer input = { src_buf_ptr + src_offset, src_size, 0};

    size = ZSTD_decompressStream((ZSTD_DCtx *)(intptr_t) stream, &output, &input);

    (*env)->ReleasePrimitiveArrayCritical(env, src, src_buf_ptr, JNI_ABORT);
E2: (*env)->ReleasePrimitiveArrayCritical(env, dst, dst_buf_ptr, 0);
    (*env)->SetIntField(env, obj, consumed_id, input.pos);
    (*env)->SetIntField(env, obj, produced_id, output.pos);
E1: return size;
}

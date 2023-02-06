#include <jni.h>
#include <zstd.h>
#include <zstd_errors.h>
#include <stdlib.h>
#include <stdint.h>

/* field IDs can't change in the same VM */
static jfieldID consumed_id;
static jfieldID produced_id;

/*
 * Class:     com_github_luben_zstd_ZstdDirectBufferDecompressingStreamNoFinalizer
 * Method:    recommendedDOutSizeNative
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_ZstdDirectBufferDecompressingStreamNoFinalizer_recommendedDOutSizeNative
  (JNIEnv *env, jclass obj) {
    return ZSTD_DStreamOutSize();
}

/*
 * Class:     com_github_luben_zstd_ZstdDirectBufferDecompressingStreamNoFinalizer
 * Method:    createDStreamNative
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_ZstdDirectBufferDecompressingStreamNoFinalizer_createDStreamNative
  (JNIEnv *env, jclass obj) {
    return (jlong)(intptr_t) ZSTD_createDStream();
}

/*
 * Class:     com_github_luben_zstd_ZstdDirectBufferDecompressingStreamNoFinalizer
 * Method:    freeDStreamNative
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_ZstdDirectBufferDecompressingStreamNoFinalizer_freeDStreamNative
  (JNIEnv *env, jclass obj, jlong stream) {
    return ZSTD_freeDCtx((ZSTD_DCtx *)(intptr_t) stream);
}

/*
 * Class:     com_github_luben_zstd_ZstdDirectBufferDecompressingStreamNoFinalizer
 * Method:    initDStreamNative
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_ZstdDirectBufferDecompressingStreamNoFinalizer_initDStreamNative
  (JNIEnv *env, jclass obj, jlong stream) {
    jclass clazz = (*env)->GetObjectClass(env, obj);
    consumed_id = (*env)->GetFieldID(env, clazz, "consumed", "I");
    produced_id = (*env)->GetFieldID(env, clazz, "produced", "I");
    return ZSTD_initDStream((ZSTD_DCtx *)(intptr_t) stream);
}

/*
 * Class:     com_github_luben_zstd_ZstdDirectBufferDecompressingStreamNoFinalizer
 * Method:    decompressStreamNative
 * Signature: (JLjava/nio/ByteBuffer;IILjava/nio/ByteBuffer;II)I
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_ZstdDirectBufferDecompressingStreamNoFinalizer_decompressStreamNative
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

    ZSTD_outBuffer output = { dst_buf_ptr + dst_offset, dst_size, 0};
    ZSTD_inBuffer input = { src_buf_ptr + src_offset, src_size, 0 };

    size = ZSTD_decompressStream((ZSTD_DCtx *)(intptr_t) stream, &output, &input);

    (*env)->SetIntField(env, obj, consumed_id, input.pos);
    (*env)->SetIntField(env, obj, produced_id, output.pos);
E1: return size;
}

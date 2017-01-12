#include <jni.h>
#include <zstd_internal.h>
#include <zstd_errors.h>
#include <stdlib.h>
#include <stdint.h>

/* field IDs can't change in the same VM */
static jfieldID consumed_id;
static jfieldID produced_id;

/*
 * Class:     com_github_luben_zstd_ZstdDirectBufferCompressingStream
 * Method:    recommendedCOutSize
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_com_github_luben_zstd_ZstdDirectBufferCompressingStream_recommendedCOutSize
  (JNIEnv *env, jclass obj) {
    return (jint) ZSTD_CStreamOutSize();
}

/*
 * Class:     com_github_luben_zstd_ZstdDirectBufferCompressingStream
 * Method:    createCStream
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_ZstdDirectBufferCompressingStream_createCStream
  (JNIEnv *env, jclass obj) {
    return (jlong)(intptr_t) ZSTD_createCStream();
}

/*
 * Class:     com_github_luben_zstd_ZstdDirectBufferCompressingStream
 * Method:    freeCStream
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_com_github_luben_zstd_ZstdDirectBufferCompressingStream_freeCStream
  (JNIEnv *env, jclass obj, jlong stream) {
    return ZSTD_freeCStream((ZSTD_CStream *)(intptr_t) stream);
}

/*
 * Class:     com_github_luben_zstd_ZstdDirectBufferCompressingStream
 * Method:    initCStream
 * Signature: (JI)I
 */
JNIEXPORT jint JNICALL Java_com_github_luben_zstd_ZstdDirectBufferCompressingStream_initCStream
  (JNIEnv *env, jclass obj, jlong stream, jint level) {
    jclass clazz = (*env)->GetObjectClass(env, obj);
    consumed_id = (*env)->GetFieldID(env, clazz, "consumed", "I");
    produced_id = (*env)->GetFieldID(env, clazz, "produced", "I");
    return ZSTD_initCStream((ZSTD_CStream *)(intptr_t) stream, level);
}

/*
 * Class:     com_github_luben_zstd_ZstdDirectBufferCompressingStream
 * Method:    compressDirectByteBuffer
 * Signature: (JLjava/nio/ByteBuffer;IILjava/nio/ByteBuffer;II)J
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_ZstdDirectBufferCompressingStream_compressDirectByteBuffer
  (JNIEnv *env, jclass obj, jlong stream, jobject dst_buf, jint dst_offset, jint dst_size, jobject src_buf, jint src_offset, jint src_size) {

    size_t size = (size_t)ERROR(memory_allocation);
    jsize dst_cap = (*env)->GetDirectBufferCapacity(env, dst_buf);
    if (dst_offset + dst_size > dst_cap) return ERROR(dstSize_tooSmall);
    jsize src_cap = (*env)->GetDirectBufferCapacity(env, src_buf);
    if (src_offset + src_size > src_cap) return ERROR(srcSize_wrong);
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
 * Class:     com_github_luben_zstd_ZstdDirectBufferCompressingStream
 * Method:    endStream
 * Signature: (JLjava/nio/ByteBuffer;II)I
 */
JNIEXPORT jint JNICALL Java_com_github_luben_zstd_ZstdDirectBufferCompressingStream_endStream
  (JNIEnv *env, jclass obj, jlong stream, jobject dst_buf, jint dst_offset, jint dst_size) {

    size_t size = (size_t)(0-ZSTD_error_memory_allocation);

    jsize dst_cap = (*env)->GetDirectBufferCapacity(env, dst_buf);
    if (dst_offset + dst_size > dst_cap) return (jint) ERROR(dstSize_tooSmall);
    char *dst_buf_ptr = (char*)(*env)->GetDirectBufferAddress(env, dst_buf);

    if (dst_buf_ptr != NULL) {
        ZSTD_outBuffer output = { dst_buf_ptr + dst_offset, dst_size, 0 };
        size = ZSTD_endStream((ZSTD_CStream *)(intptr_t) stream, &output);
        (*env)->SetIntField(env, obj, produced_id, output.pos);
    }
    return (jint) size;
}

/*
 * Class:     com_github_luben_zstd_ZstdDirectBufferCompressingStream
 * Method:    flushStream
 * Signature: (JLjava/nio/ByteBuffer;II)I
 */
JNIEXPORT jint JNICALL Java_com_github_luben_zstd_ZstdDirectBufferCompressingStream_flushStream
  (JNIEnv *env, jclass obj, jlong stream, jobject dst_buf, jint dst_offset, jint dst_size) {

    size_t size = (size_t)(0-ZSTD_error_memory_allocation);

    jsize dst_cap = (*env)->GetDirectBufferCapacity(env, dst_buf);
    if (dst_offset + dst_size > dst_cap) return (jint)ERROR(dstSize_tooSmall);
    char *dst_buf_ptr = (char*)(*env)->GetDirectBufferAddress(env, dst_buf);
    if (dst_buf_ptr != NULL) {
        ZSTD_outBuffer output = { dst_buf_ptr + dst_offset, dst_size, 0 };
        size = ZSTD_flushStream((ZSTD_CStream *)(intptr_t) stream, &output);
        (*env)->SetIntField(env, obj, produced_id, output.pos);
    }
    return (jint) size;
}

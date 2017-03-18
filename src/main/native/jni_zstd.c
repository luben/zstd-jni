#include <jni.h>
#include <zstd_internal.h>
#include <zstd_errors.h>

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
 * Method:    compressDirectByteBuffer
 * Signature: (Ljava/nio/ByteBuffer;IILjava/nio/ByteBuffer;III)J
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_Zstd_compressDirectByteBuffer
  (JNIEnv *env, jclass obj, jobject dst_buf, jint dst_offset, jint dst_size, jobject src_buf, jint src_offset, jint src_size, jint level) {
    size_t size = (size_t)ERROR(memory_allocation);
    jsize dst_cap = (*env)->GetDirectBufferCapacity(env, dst_buf);
    if (dst_offset + dst_size > dst_cap) return ERROR(dstSize_tooSmall);
    jsize src_cap = (*env)->GetDirectBufferCapacity(env, src_buf);
    if (src_offset + src_size > src_cap) return ERROR(srcSize_wrong);
    char *dst_buf_ptr = (char*)(*env)->GetDirectBufferAddress(env, dst_buf);
    if (dst_buf_ptr == NULL) return size;
    char *src_buf_ptr = (char*)(*env)->GetDirectBufferAddress(env, src_buf);
    if (src_buf_ptr == NULL) return size;

    size = ZSTD_compress(dst_buf_ptr + dst_offset, (size_t) dst_size, src_buf_ptr + src_offset, (size_t) src_size, (int) level);

    return size;
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

JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_Zstd_compressDirectByteBufferUsingDict
  (JNIEnv *env, jclass obj, jobject dst_buf, jint dst_offset, jint dst_size, jobject src_buf, jint src_offset, jint src_size, jbyteArray dict, jint level) {
    size_t size = (size_t)ERROR(memory_allocation);
    jsize dst_cap = (*env)->GetDirectBufferCapacity(env, dst_buf);
    if (dst_offset + dst_size > dst_cap) return ERROR(dstSize_tooSmall);
    jsize src_cap = (*env)->GetDirectBufferCapacity(env, src_buf);
    if (src_offset + src_size > src_cap) return ERROR(srcSize_wrong);
    char *dst_buff_ptr = (char*)(*env)->GetDirectBufferAddress(env, dst_buf);
    if (dst_buff_ptr == NULL) return size;
    char *src_buff_ptr = (char*)(*env)->GetDirectBufferAddress(env, src_buf);
    if (src_buff_ptr == NULL) return size;
    jsize dict_size = (*env)->GetArrayLength(env, dict);
    void *dict_buff = (*env)->GetPrimitiveArrayCritical(env, dict, NULL);
    if (dict_buff == NULL) return size;

    ZSTD_CCtx* ctx = ZSTD_createCCtx();
    size = ZSTD_compress_usingDict(ctx, dst_buff_ptr + dst_offset, (size_t) dst_size, src_buff_ptr + src_offset, (size_t) src_size, dict_buff, (size_t) dict_size, (int) level);
    ZSTD_freeCCtx(ctx);
    return size;
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
 * Method:    decompressDirectByteBuffer
 * Signature: (Ljava/nio/ByteBuffer;IILjava/nio/ByteBuffer;II)J
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_Zstd_decompressDirectByteBuffer
  (JNIEnv *env, jclass obj, jobject dst_buf, jint dst_offset, jint dst_size, jobject src_buf, jint src_offset, jint src_size) {
    size_t size = (size_t)ERROR(memory_allocation);

    jsize dst_cap = (*env)->GetDirectBufferCapacity(env, dst_buf);
    if (dst_offset + dst_size > dst_cap) return ERROR(dstSize_tooSmall);
    jsize src_cap = (*env)->GetDirectBufferCapacity(env, src_buf);
    if (src_offset + src_size > src_cap) return ERROR(srcSize_wrong);
    char *dst_buf_ptr = (char*)(*env)->GetDirectBufferAddress(env, dst_buf);
    if (dst_buf_ptr == NULL) return size;
    char *src_buf_ptr = (char*)(*env)->GetDirectBufferAddress(env, src_buf);
    if (src_buf_ptr == NULL) return size;

    size = ZSTD_decompress(dst_buf_ptr + dst_offset, (size_t) dst_size, src_buf_ptr + src_offset, (size_t) src_size);

    return size;
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

JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_Zstd_decompressDirectByteBufferUsingDict
  (JNIEnv *env, jclass obj, jobject dst_buff, jint dst_offset, jint dst_size, jbyteArray src_buff, jint src_offset, jint src_size, jbyteArray dict) {
    size_t size = (size_t)(0-ZSTD_error_memory_allocation);

    jsize dst_cap = (*env)->GetDirectBufferCapacity(env, dst_buff);
    if (dst_offset + dst_size > dst_cap) return ERROR(dstSize_tooSmall);
    jsize src_cap = (*env)->GetDirectBufferCapacity(env, src_buff);
    if (src_offset + src_size > src_cap) return ERROR(srcSize_wrong);
    char *dst_buff_ptr = (char*)(*env)->GetDirectBufferAddress(env, dst_buff);
    if (dst_buff_ptr == NULL) return size;
    char *src_buff_ptr = (char*)(*env)->GetDirectBufferAddress(env, src_buff);
    if (src_buff_ptr == NULL) return size;
    jsize dict_size = (*env)->GetArrayLength(env, dict);
    void *dict_buff = (*env)->GetPrimitiveArrayCritical(env, dict, NULL);
    if (dict_buff == NULL) return size;
    ZSTD_DCtx* dctx = ZSTD_createDCtx();
    size = ZSTD_decompress_usingDict(dctx, dst_buff_ptr + dst_offset, (size_t) dst_size, src_buff_ptr + src_offset, (size_t) src_size, dict_buff, (size_t) dict_size);
    ZSTD_freeDCtx(dctx);
    (*env)->ReleasePrimitiveArrayCritical(env, dict, dict_buff, JNI_ABORT);
    return size;
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

/*
 * Class:     com_github_luben_zstd_Zstd
 * Method:    decompressedDirectByteBufferSize
 * Signature: (Ljava/nio/ByteBuffer;II)J
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_Zstd_decompressedDirectByteBufferSize
  (JNIEnv *env, jclass obj, jobject src_buf, jint src_offset, jint src_size) {
    size_t size = (size_t)(0-ZSTD_error_memory_allocation);
    jsize src_cap = (*env)->GetDirectBufferCapacity(env, src_buf);
    if (src_offset + src_size > src_cap) return ZSTD_error_GENERIC;
    char *src_buf_ptr = (char*)(*env)->GetDirectBufferAddress(env, src_buf);
    if (src_buf_ptr == NULL) goto E1;
    size = ZSTD_getDecompressedSize(src_buf_ptr + src_offset, (size_t) src_size);
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

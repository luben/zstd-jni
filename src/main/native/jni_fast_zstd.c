#ifndef ZSTD_STATIC_LINKING_ONLY
#define ZSTD_STATIC_LINKING_ONLY
#endif
#include <jni.h>
#include <zstd.h>
#include <zstd_errors.h>
#include <stdint.h>

// They can't change in the same VM
static jfieldID compress_dict = 0;
static jfieldID decompress_dict = 0;

/*
 * Class:     com_github_luben_zstd_ZstdDictCompress
 * Method:    init
 * Signature: ([BIII)V
 */
JNIEXPORT void JNICALL Java_com_github_luben_zstd_ZstdDictCompress_init
  (JNIEnv *env, jobject obj, jbyteArray dict, jint dict_offset, jint dict_size, jint level)
{
    jclass clazz = (*env)->GetObjectClass(env, obj);
    compress_dict = (*env)->GetFieldID(env, clazz, "nativePtr", "J");
    if (NULL == dict) return;
    void *dict_buff = (*env)->GetPrimitiveArrayCritical(env, dict, NULL);
    if (NULL == dict_buff) return;
    ZSTD_CDict* cdict = ZSTD_createCDict(((char *)dict_buff) + dict_offset, dict_size, level);
    (*env)->ReleasePrimitiveArrayCritical(env, dict, dict_buff, JNI_ABORT);
    if (NULL == cdict) return;
    (*env)->SetLongField(env, obj, compress_dict, (jlong)(intptr_t) cdict);
}

/*
 * Class:     com_github_luben_zstd_ZstdDictCompress
 * Method:    init
 * Signature: (Ljava/nio/ByteBuffer;III)V
 */
JNIEXPORT void JNICALL Java_com_github_luben_zstd_ZstdDictCompress_initDirect
  (JNIEnv *env, jobject obj, jobject dict, jint dict_offset, jint dict_size, jint level)
{
    jclass clazz = (*env)->GetObjectClass(env, obj);
    compress_dict = (*env)->GetFieldID(env, clazz, "nativePtr", "J");
    if (NULL == dict) return;
    void *dict_buff = (*env)->GetDirectBufferAddress(env, dict);
    if (NULL == dict_buff) return;
    ZSTD_CDict* cdict = ZSTD_createCDict(((char *)dict_buff) + dict_offset, dict_size, level);
    if (NULL == cdict) return;
    (*env)->SetLongField(env, obj, compress_dict, (jlong)(intptr_t) cdict);
}

/*
 * Class:     com_github_luben_zstd_ZstdDictCompress
 * Method:    free
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_com_github_luben_zstd_ZstdDictCompress_free
  (JNIEnv *env, jobject obj)
{
    if (compress_dict == 0) return;
    ZSTD_CDict* cdict = (ZSTD_CDict*)(intptr_t)(*env)->GetLongField(env, obj, compress_dict);
    if (NULL == cdict) return;
    ZSTD_freeCDict(cdict);
}

/*
 * Class:     com_github_luben_zstd_ZstdDictDecompress
 * Method:    init
 * Signature: ([BII)V
 */
JNIEXPORT void JNICALL Java_com_github_luben_zstd_ZstdDictDecompress_init
  (JNIEnv *env, jobject obj, jbyteArray dict, jint dict_offset, jint dict_size)
{
    jclass clazz = (*env)->GetObjectClass(env, obj);
    decompress_dict = (*env)->GetFieldID(env, clazz, "nativePtr", "J");
    if (NULL == dict) return;
    void *dict_buff = (*env)->GetPrimitiveArrayCritical(env, dict, NULL);
    if (NULL == dict_buff) return;

    ZSTD_DDict* ddict = ZSTD_createDDict(((char *)dict_buff) + dict_offset, dict_size);

    (*env)->ReleasePrimitiveArrayCritical(env, dict, dict_buff, JNI_ABORT);
    if (NULL == ddict) return;
    (*env)->SetLongField(env, obj, decompress_dict, (jlong)(intptr_t) ddict);
}

/*
 * Class:     com_github_luben_zstd_ZstdDictDecompress
 * Method:    initDirect
 * Signature: (Ljava/nio/ByteBuffer;II)V
 */
JNIEXPORT void JNICALL Java_com_github_luben_zstd_ZstdDictDecompress_initDirect
  (JNIEnv *env, jobject obj, jobject dict, jint dict_offset, jint dict_size)
{
    jclass clazz = (*env)->GetObjectClass(env, obj);
    decompress_dict = (*env)->GetFieldID(env, clazz, "nativePtr", "J");
    if (NULL == dict) return;
    void *dict_buff = (*env)->GetDirectBufferAddress(env, dict);

    ZSTD_DDict* ddict = ZSTD_createDDict(((char *)dict_buff) + dict_offset, dict_size);

    if (NULL == ddict) return;
    (*env)->SetLongField(env, obj, decompress_dict, (jlong)(intptr_t) ddict);
}

/*
 * Class:     com_github_luben_zstd_ZstdDictDecompress
 * Method:    free
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_com_github_luben_zstd_ZstdDictDecompress_free
  (JNIEnv *env, jobject obj)
{
    if (decompress_dict == 0) return;
    ZSTD_DDict* ddict = (ZSTD_DDict*)(intptr_t)(*env)->GetLongField(env, obj, decompress_dict);
    if (NULL == ddict) return;
    ZSTD_freeDDict(ddict);
}

/*
 * Class:     com_github_luben_zstd_Zstd
 * Method:    decompressFastDict0
 * Signature: ([BI[BIILcom/github/luben/zstd/ZstdDictDecompress;)J
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_Zstd_decompressFastDict0
  (JNIEnv *env, jclass obj, jbyteArray dst, jint dst_offset, jbyteArray src, jint src_offset, jint src_length, jobject dict)
{
    if (NULL == dict) return -ZSTD_error_dictionary_wrong;
    ZSTD_DDict* ddict = (ZSTD_DDict*)(intptr_t)(*env)->GetLongField(env, dict, decompress_dict);
    if (NULL == ddict) return -ZSTD_error_dictionary_wrong;
    if (NULL == dst) return -ZSTD_error_dstSize_tooSmall;
    if (NULL == src) return -ZSTD_error_srcSize_wrong;
    if (0 > dst_offset) return -ZSTD_error_dstSize_tooSmall;
    if (0 > src_offset) return -ZSTD_error_srcSize_wrong;
    if (0 > src_length) return -ZSTD_error_srcSize_wrong;

    size_t size = -ZSTD_error_memory_allocation;
    jsize dst_size = (*env)->GetArrayLength(env, dst);
    jsize src_size = (*env)->GetArrayLength(env, src);
    if (dst_offset > dst_size) return -ZSTD_error_dstSize_tooSmall;
    if (src_size < (src_offset + src_length)) return -ZSTD_error_srcSize_wrong;
    dst_size -= dst_offset;
    void *dst_buff = (*env)->GetPrimitiveArrayCritical(env, dst, NULL);
    if (dst_buff == NULL) goto E1;
    void *src_buff = (*env)->GetPrimitiveArrayCritical(env, src, NULL);
    if (src_buff == NULL) goto E2;
    ZSTD_DCtx* dctx = ZSTD_createDCtx();
    size = ZSTD_decompress_usingDDict(dctx, ((char *)dst_buff) + dst_offset, (size_t) dst_size, ((char *)src_buff) + src_offset, (size_t) src_length, ddict);
    ZSTD_freeDCtx(dctx);
    (*env)->ReleasePrimitiveArrayCritical(env, src, src_buff, JNI_ABORT);
E2: (*env)->ReleasePrimitiveArrayCritical(env, dst, dst_buff, 0);
E1: return size;
}

/*
 * Class:     com_github_luben_zstd_Zstd
 * Method:    compressFastDict0
 * Signature: ([BI[BIILcom/github/luben/zstd/ZstdDictCompress;)J
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_Zstd_compressFastDict0
  (JNIEnv *env, jclass obj, jbyteArray dst, jint dst_offset, jbyteArray src, jint src_offset, jint src_length, jobject dict) {
    if (NULL == dict) return -ZSTD_error_dictionary_wrong;
    ZSTD_CDict* cdict = (ZSTD_CDict*)(intptr_t)(*env)->GetLongField(env, dict, compress_dict);
    if (NULL == cdict) return -ZSTD_error_dictionary_wrong;
    if (NULL == dst) return -ZSTD_error_dstSize_tooSmall;
    if (NULL == src) return -ZSTD_error_srcSize_wrong;
    if (0 > dst_offset) return -ZSTD_error_dstSize_tooSmall;
    if (0 > src_offset) return -ZSTD_error_srcSize_wrong;
    if (0 > src_length) return -ZSTD_error_srcSize_wrong;


    size_t size = -ZSTD_error_memory_allocation;
    jsize dst_size = (*env)->GetArrayLength(env, dst);
    jsize src_size = (*env)->GetArrayLength(env, src);
    if (dst_offset > dst_size) return -ZSTD_error_dstSize_tooSmall;
    if (src_size < (src_offset + src_length)) return -ZSTD_error_srcSize_wrong;
    dst_size -= dst_offset;
    void *dst_buff = (*env)->GetPrimitiveArrayCritical(env, dst, NULL);
    if (dst_buff == NULL) goto E1;
    void *src_buff = (*env)->GetPrimitiveArrayCritical(env, src, NULL);
    if (src_buff == NULL) goto E2;
    ZSTD_CCtx* ctx = ZSTD_createCCtx();
    size = ZSTD_compress_usingCDict(ctx, ((char *)dst_buff) + dst_offset, (size_t) dst_size, ((char *)src_buff) + src_offset, (size_t) src_length, cdict);
    ZSTD_freeCCtx(ctx);
    (*env)->ReleasePrimitiveArrayCritical(env, src, src_buff, JNI_ABORT);
E2: (*env)->ReleasePrimitiveArrayCritical(env, dst, dst_buff, 0);
E1: return size;
}

/*
 * Class:     com_github_luben_zstd_Zstd
 * Method:    compressDirectByteBufferFastDict0
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_Zstd_compressDirectByteBufferFastDict0
  (JNIEnv *env, jclass obj, jobject dst, jint dst_offset, jint dst_size, jobject src, jint src_offset, jint src_size, jobject dict) {
    if (NULL == dict) return -ZSTD_error_dictionary_wrong;
    ZSTD_CDict* cdict = (ZSTD_CDict*)(intptr_t)(*env)->GetLongField(env, dict, compress_dict);
    if (NULL == cdict) return -ZSTD_error_dictionary_wrong;
    if (NULL == dst) return -ZSTD_error_dstSize_tooSmall;
    if (NULL == src) return -ZSTD_error_srcSize_wrong;
    if (0 > dst_offset) return -ZSTD_error_dstSize_tooSmall;
    if (0 > src_offset) return -ZSTD_error_srcSize_wrong;
    if (0 > src_size) return -ZSTD_error_srcSize_wrong;
    size_t size = -ZSTD_error_memory_allocation;
    char *dst_buff = (char*)(*env)->GetDirectBufferAddress(env, dst);
    char *src_buff = (char*)(*env)->GetDirectBufferAddress(env, src);
    ZSTD_CCtx* ctx = ZSTD_createCCtx();
    size = ZSTD_compress_usingCDict(ctx, dst_buff + dst_offset, (size_t) dst_size, src_buff + src_offset, (size_t) src_size, cdict);
    ZSTD_freeCCtx(ctx);
    return size;
}

/*
 * Class:     com_github_luben_zstd_Zstd
 * Method:    decompressDirectByteBufferFastDict0
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_Zstd_decompressDirectByteBufferFastDict0
  (JNIEnv *env, jclass obj, jobject dst, jint dst_offset, jint dst_size, jobject src, jint src_offset, jint src_size, jobject dict)
{
    if (NULL == dict) return -ZSTD_error_dictionary_wrong;
    ZSTD_DDict* ddict = (ZSTD_DDict*)(intptr_t)(*env)->GetLongField(env, dict, decompress_dict);
    if (NULL == ddict) return -ZSTD_error_dictionary_wrong;
    if (NULL == dst) return -ZSTD_error_dstSize_tooSmall;
    if (NULL == src) return -ZSTD_error_srcSize_wrong;
    if (0 > dst_offset) return -ZSTD_error_dstSize_tooSmall;
    if (0 > src_offset) return -ZSTD_error_srcSize_wrong;
    if (0 > src_size) return -ZSTD_error_srcSize_wrong;

    size_t size = -ZSTD_error_memory_allocation;
    char *dst_buff = (char*)(*env)->GetDirectBufferAddress(env, dst);
    char *src_buff = (char*)(*env)->GetDirectBufferAddress(env, src);
    ZSTD_DCtx* dctx = ZSTD_createDCtx();
    size = ZSTD_decompress_usingDDict(dctx, dst_buff + dst_offset, (size_t) dst_size, src_buff + src_offset, (size_t) src_size, ddict);
    ZSTD_freeDCtx(dctx);
    return size;
}

/* ================ ZstCompressCtx ============================ */

/*
 * Class:     com_github_luben_zstd_ZstdCompressCtx
 * Method:    init
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_ZstdCompressCtx_init
  (JNIEnv *env, jclass clazz)
{
    ZSTD_CCtx* cctx = ZSTD_createCCtx();
    return (jlong)(intptr_t) cctx;
}

/*
 * Class:     com_github_luben_zstd_ZstdCompressCtx
 * Method:    free
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_com_github_luben_zstd_ZstdCompressCtx_free
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    ZSTD_CCtx* cctx = (ZSTD_CCtx*)(intptr_t) ptr;
    ZSTD_freeCCtx(cctx);
}

/*
 * Class:     com_github_luben_zstd_ZstdCompressCtx
 * Method:    setLevel0
 * Signature: (JI)V
 */
JNIEXPORT void JNICALL Java_com_github_luben_zstd_ZstdCompressCtx_setLevel0
  (JNIEnv *env, jclass clazz, jlong ptr, jint level)
{
    ZSTD_CCtx* cctx = (ZSTD_CCtx*)(intptr_t) ptr;
    ZSTD_CCtx_setParameter(cctx, ZSTD_c_compressionLevel, level);
}

/*
 * Class:     com_github_luben_zstd_ZstdCompressCtx
 * Method:    setChecksum0
 * Signature: (JI)V
 */
JNIEXPORT void JNICALL Java_com_github_luben_zstd_ZstdCompressCtx_setChecksum0
  (JNIEnv *env, jclass clazz, jlong ptr, jboolean checksumFlag)
{
    ZSTD_CCtx* cctx = (ZSTD_CCtx*)(intptr_t) ptr;
    ZSTD_CCtx_setParameter(cctx, ZSTD_c_checksumFlag, (checksumFlag == JNI_TRUE));
}

/*
 * Class:     com_github_luben_zstd_ZstdCompressCtx
 * Method:    setContentSize0
 * Signature: (JI)V
 */
JNIEXPORT void JNICALL Java_com_github_luben_zstd_ZstdCompressCtx_setContentSize0
  (JNIEnv *env, jclass clazz, jlong ptr, jboolean contentSizeFlag)
{
    ZSTD_CCtx* cctx = (ZSTD_CCtx*)(intptr_t) ptr;
    ZSTD_CCtx_setParameter(cctx, ZSTD_c_contentSizeFlag, (contentSizeFlag == JNI_TRUE));
}

/*
 * Class:     com_github_luben_zstd_ZstdCompressCtx
 * Method:    setDictID0
 * Signature: (JI)V
 */
JNIEXPORT void JNICALL Java_com_github_luben_zstd_ZstdCompressCtx_setDictID0
  (JNIEnv *env, jclass clazz, jlong ptr, jboolean dictIDFlag)
{
    ZSTD_CCtx* cctx = (ZSTD_CCtx*)(intptr_t) ptr;
    ZSTD_CCtx_setParameter(cctx, ZSTD_c_dictIDFlag, (dictIDFlag == JNI_TRUE));
}

/*
 * Class:     com_github_luben_zstd_ZstdCompressCtx
 * Method:    loadCDictFast0
 * Signature: (JLcom/github/luben/zstd/ZstdDictCompress)J
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_ZstdCompressCtx_loadCDictFast0
  (JNIEnv *env, jclass clazz, jlong ptr, jobject dict)
{
    ZSTD_CCtx* cctx = (ZSTD_CCtx*)(intptr_t) ptr;
    if (dict == NULL) {
        // remove dictionary
        return ZSTD_CCtx_refCDict(cctx, NULL);
    }
    ZSTD_CDict* cdict = (ZSTD_CDict*)(intptr_t)(*env)->GetLongField(env, dict, compress_dict);
    if (NULL == cdict) return -ZSTD_error_dictionary_wrong;
    return ZSTD_CCtx_refCDict(cctx, cdict);
}

/*
 * Class:     com_github_luben_zstd_ZstdCompressCtx
 * Method:    loadCDict0
 * Signature: (J[B)J
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_ZstdCompressCtx_loadCDict0
  (JNIEnv *env, jclass clazz, jlong ptr, jbyteArray dict)
{
    ZSTD_CCtx* cctx = (ZSTD_CCtx*)(intptr_t) ptr;
    if (dict == NULL) {
        // remove dictionary
        return ZSTD_CCtx_loadDictionary(cctx, NULL, 0);
    }
    jsize dict_size = (*env)->GetArrayLength(env, dict);
    void *dict_buff = (*env)->GetPrimitiveArrayCritical(env, dict, NULL);
    if (dict_buff == NULL) return -ZSTD_error_memory_allocation;
    size_t result = ZSTD_CCtx_loadDictionary(cctx, dict_buff, dict_size);
    (*env)->ReleasePrimitiveArrayCritical(env, dict, dict_buff, JNI_ABORT);
    return result;
}

/*
 * Class:     com_github_luben_zstd_ZstdCompressCtx
 * Method:    reset0
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_ZstdCompressCtx_reset0
  (JNIEnv *env, jclass jctx, jlong ptr) {
    ZSTD_CCtx* cctx = (ZSTD_CCtx*)(intptr_t) ptr;
    return ZSTD_CCtx_reset(cctx, ZSTD_reset_session_and_parameters);
}

JNIEXPORT jobject JNICALL Java_com_github_luben_zstd_ZstdCompressCtx_getFrameProgression0
  (JNIEnv *env, jclass jctx, jlong ptr) {
    ZSTD_CCtx* cctx = (ZSTD_CCtx*)(intptr_t) ptr;
    ZSTD_frameProgression native_progression = ZSTD_getFrameProgression(cctx);

    jclass frame_progression_class = (*env)->FindClass(env, "com/github/luben/zstd/ZstdFrameProgression");
    jmethodID frame_progression_constructor = (*env)->GetMethodID(env, frame_progression_class, "<init>", "(JJJJII)V");
    return (*env)->NewObject(
            env, frame_progression_class, frame_progression_constructor, native_progression.ingested,
            native_progression.consumed, native_progression.produced, native_progression.flushed,
            native_progression.currentJobID, native_progression.nbActiveWorkers);
}

JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_ZstdCompressCtx_setPledgedSrcSize0
  (JNIEnv *env, jclass jctx, jlong ptr, jlong src_size) {
    if (src_size < 0) {
        return -ZSTD_error_srcSize_wrong;
    }
    ZSTD_CCtx* cctx = (ZSTD_CCtx*)(intptr_t) ptr;
    return ZSTD_CCtx_setPledgedSrcSize(cctx, (unsigned long long)src_size);
}

static size_t compress_direct_buffer_stream
  (JNIEnv *env, jclass jctx, jlong ptr, jobject dst, jint *dst_offset, jint dst_size, jobject src, jint *src_offset, jint src_size, jint end_op) {
    if (NULL == dst) return -ZSTD_error_dstSize_tooSmall;
    if (NULL == src) return -ZSTD_error_srcSize_wrong;
    if (0 > *dst_offset) return -ZSTD_error_dstSize_tooSmall;
    if (0 > *src_offset) return -ZSTD_error_srcSize_wrong;
    if (0 > src_size) return -ZSTD_error_srcSize_wrong;

    jsize dst_cap = (*env)->GetDirectBufferCapacity(env, dst);
    if (dst_size > dst_cap) return -ZSTD_error_dstSize_tooSmall;
    jsize src_cap = (*env)->GetDirectBufferCapacity(env, src);
    if (src_size > src_cap) return -ZSTD_error_srcSize_wrong;
    ZSTD_CCtx* cctx = (ZSTD_CCtx*)(intptr_t) ptr;

    ZSTD_outBuffer out;
    out.pos = *dst_offset;
    out.size = dst_size;
    out.dst = (*env)->GetDirectBufferAddress(env, dst);
    if (out.dst == NULL) return -ZSTD_error_memory_allocation;
    ZSTD_inBuffer in;
    in.pos = *src_offset;
    in.size = src_size;
    in.src = (*env)->GetDirectBufferAddress(env, src);
    if (in.src == NULL) return -ZSTD_error_memory_allocation;

    size_t result = ZSTD_compressStream2(cctx, &out, &in, end_op);
    *dst_offset = out.pos;
    *src_offset = in.pos;
    return result;
}

/*
 * Class:     com_github_luben_zstd_ZstdCompressCtx
 * Method:    compressDirectByteBufferStream0
 * Signature: (JLjava/nio/ByteBuffer;IILjava/nio/ByteBuffer;III)J
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_ZstdCompressCtx_compressDirectByteBufferStream0
  (JNIEnv *env, jclass jctx, jlong ptr, jobject dst, jint dst_offset, jint dst_size, jobject src, jint src_offset, jint src_size, jint end_op) {
    size_t result = compress_direct_buffer_stream(env, jctx, ptr, dst, &dst_offset, dst_size, src, &src_offset, src_size, end_op);
    if (ZSTD_isError(result)) {
        return (1ULL << 31) | ZSTD_getErrorCode(result);
    }
    jlong encoded_result = ((jlong)dst_offset << 32) | src_offset;
    if (result == 0) {
        encoded_result |= 1ULL << 63;
    }
    return encoded_result;
}

/*
 * Class:     com_github_luben_zstd_ZstdCompressCtx
 * Method:    compressDirectByteBuffer0
 * Signature: (JLjava/nio/ByteBuffer;IILjava/nio/ByteBuffer;II)J
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_ZstdCompressCtx_compressDirectByteBuffer0
  (JNIEnv *env, jclass jctx, jlong ptr, jobject dst, jint dst_offset, jint dst_size, jobject src, jint src_offset, jint src_size) {
    if (NULL == dst) return -ZSTD_error_dstSize_tooSmall;
    if (NULL == src) return -ZSTD_error_srcSize_wrong;
    if (0 > dst_offset) return -ZSTD_error_dstSize_tooSmall;
    if (0 > src_offset) return -ZSTD_error_srcSize_wrong;
    if (0 > src_size) return -ZSTD_error_srcSize_wrong;

    jsize dst_cap = (*env)->GetDirectBufferCapacity(env, dst);
    if (dst_offset + dst_size > dst_cap) return -ZSTD_error_dstSize_tooSmall;
    jsize src_cap = (*env)->GetDirectBufferCapacity(env, src);
    if (src_offset + src_size > src_cap) return -ZSTD_error_srcSize_wrong;

    ZSTD_CCtx* cctx = (ZSTD_CCtx*)(intptr_t) ptr;

    char *dst_buff = (char*)(*env)->GetDirectBufferAddress(env, dst);
    if (dst_buff == NULL) return -ZSTD_error_memory_allocation;
    char *src_buff = (char*)(*env)->GetDirectBufferAddress(env, src);
    if (src_buff == NULL) return -ZSTD_error_memory_allocation;

    ZSTD_CCtx_reset(cctx, ZSTD_reset_session_only);
    return ZSTD_compress2(cctx, dst_buff + dst_offset, (size_t) dst_size, src_buff + src_offset, (size_t) src_size);
}

/*
 * Class:     com_github_luben_zstd_ZstdCompressCtx
 * Method:    compressByteArray0
 * Signature: (JB[IIB[II)J
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_ZstdCompressCtx_compressByteArray0
  (JNIEnv *env, jclass jctx, jlong ptr, jbyteArray dst, jint dst_offset, jint dst_size, jbyteArray src, jint src_offset, jint src_size) {
    size_t size = -ZSTD_error_memory_allocation;

    if (0 > dst_offset) return -ZSTD_error_dstSize_tooSmall;
    if (0 > src_offset) return -ZSTD_error_srcSize_wrong;
    if (0 > src_size) return -ZSTD_error_srcSize_wrong;

    if (src_offset + src_size > (*env)->GetArrayLength(env, src)) return -ZSTD_error_srcSize_wrong;
    if (dst_offset + dst_size > (*env)->GetArrayLength(env, dst)) return -ZSTD_error_dstSize_tooSmall;

    ZSTD_CCtx* cctx = (ZSTD_CCtx*)(intptr_t) ptr;

    void *dst_buff = (*env)->GetPrimitiveArrayCritical(env, dst, NULL);
    if (dst_buff == NULL) goto E1;
    void *src_buff = (*env)->GetPrimitiveArrayCritical(env, src, NULL);
    if (src_buff == NULL) goto E2;

    ZSTD_CCtx_reset(cctx, ZSTD_reset_session_only);

    size = ZSTD_compress2(cctx, ((char *)dst_buff) + dst_offset, (size_t) dst_size, ((char *)src_buff) + src_offset, (size_t) src_size);
    (*env)->ReleasePrimitiveArrayCritical(env, src, src_buff, JNI_ABORT);
E2: (*env)->ReleasePrimitiveArrayCritical(env, dst, dst_buff, 0);
E1: return size;
}

/* ================ ZstdDecompressCtx ============================ */

/*
 * Class:     com_github_luben_zstd_ZstdDecompressCtx
 * Method:    init
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_ZstdDecompressCtx_init
  (JNIEnv *env, jclass clazz)
{
    ZSTD_DCtx* dctx = ZSTD_createDCtx();
    return (jlong)(intptr_t) dctx;
}

/*
 * Class:     com_github_luben_zstd_ZstdDecompressCtx
 * Method:    free
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_com_github_luben_zstd_ZstdDecompressCtx_free
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    ZSTD_DCtx* dctx = (ZSTD_DCtx*)(intptr_t)ptr;
    if (NULL == dctx) return;
    ZSTD_freeDCtx(dctx);
}

/*
 * Class:     com_github_luben_zstd_ZstdDecompressCtx
 * Method:    loadDDictFast0
 * Signature: (JLcom/github/luben/zstd/ZstdDictDecompress;)J
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_ZstdDecompressCtx_loadDDictFast0
  (JNIEnv *env, jclass clazz, jlong ptr, jobject dict)
{
    ZSTD_DCtx* dctx = (ZSTD_DCtx*)(intptr_t)ptr;
    if (dict == NULL) {
        // remove dictionary
        return ZSTD_DCtx_refDDict(dctx, NULL);
    }
    ZSTD_DDict* ddict = (ZSTD_DDict*)(intptr_t)(*env)->GetLongField(env, dict, decompress_dict);
    if (NULL == ddict) return -ZSTD_error_dictionary_wrong;
    return ZSTD_DCtx_refDDict(dctx, ddict);
}

/*
 * Class:     com_github_luben_zstd_ZstdDecompressCtx
 * Method:    loadDDict0
 * Signature: (J[B)J
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_ZstdDecompressCtx_loadDDict0
  (JNIEnv *env, jclass clazz, jlong ptr, jbyteArray dict)
{
    ZSTD_DCtx* dctx = (ZSTD_DCtx*)(intptr_t)ptr;
    if (dict == NULL) {
        // remove dictionary
        return ZSTD_DCtx_loadDictionary(dctx, NULL, 0);
    }
    jsize dict_size = (*env)->GetArrayLength(env, dict);
    void *dict_buff = (*env)->GetPrimitiveArrayCritical(env, dict, NULL);
    if (dict_buff == NULL) return -ZSTD_error_memory_allocation;
    size_t result = ZSTD_DCtx_loadDictionary(dctx, dict_buff, dict_size);
    (*env)->ReleasePrimitiveArrayCritical(env, dict, dict_buff, JNI_ABORT);
    return result;
}

/*
 * Class:     com_github_luben_zstd_ZstdDecompressCtx
 * Method:    reset0
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_ZstdDecompressCtx_reset0
  (JNIEnv *env, jclass clazz, jlong ptr) {
    ZSTD_DCtx* dctx = (ZSTD_DCtx*)(intptr_t)ptr;
    return ZSTD_DCtx_reset(dctx, ZSTD_reset_session_and_parameters);
}

static size_t decompress_direct_buffer_stream
  (JNIEnv *env, jlong ptr, jobject dst, jint *dst_offset, jint dst_size, jobject src, jint *src_offset, jint src_size)
{
    if (NULL == dst) return -ZSTD_error_dstSize_tooSmall;
    if (NULL == src) return -ZSTD_error_srcSize_wrong;
    if (0 > *dst_offset) return -ZSTD_error_dstSize_tooSmall;
    if (0 > *src_offset) return -ZSTD_error_srcSize_wrong;
    if (0 > dst_size) return -ZSTD_error_dstSize_tooSmall;
    if (0 > src_size) return -ZSTD_error_srcSize_wrong;

    jsize dst_cap = (*env)->GetDirectBufferCapacity(env, dst);
    if (dst_size > dst_cap) return -ZSTD_error_dstSize_tooSmall;
    jsize src_cap = (*env)->GetDirectBufferCapacity(env, src);
    if (src_size > src_cap) return -ZSTD_error_srcSize_wrong;

    ZSTD_DCtx* dctx = (ZSTD_DCtx*)(intptr_t)ptr;

    ZSTD_outBuffer out;
    out.pos = *dst_offset;
    out.size = dst_size;
    out.dst = (*env)->GetDirectBufferAddress(env, dst);
    if (out.dst == NULL) return -ZSTD_error_memory_allocation;
    ZSTD_inBuffer in;
    in.pos = *src_offset;
    in.size = src_size;
    in.src = (*env)->GetDirectBufferAddress(env, src);
    if (in.src == NULL) return -ZSTD_error_memory_allocation;

    size_t result = ZSTD_decompressStream(dctx, &out, &in);
    *dst_offset = out.pos;
    *src_offset = in.pos;
    return result;
}

/*
 * Class:     com_github_luben_zstd_ZstdDecompressCtx
 * Method:    decompressDirectByteBufferStream0
 * Signature: (JLjava/nio/ByteBuffer;IILjava/nio/ByteBuffer;II)J
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_ZstdDecompressCtx_decompressDirectByteBufferStream0
  (JNIEnv *env, jclass jclazz, jlong ptr, jobject dst, jint dst_offset, jint dst_size, jobject src, jint src_offset, jint src_size)
{
    size_t result = decompress_direct_buffer_stream(env, ptr, dst, &dst_offset, dst_size, src, &src_offset, src_size);
    if (ZSTD_isError(result)) {
        return (1ULL << 31) | ZSTD_getErrorCode(result);
    }
    jlong encoded_result = ((jlong)dst_offset << 32) | src_offset;
    if (result == 0) {
        encoded_result |= 1ULL << 63;
    }
    return encoded_result;
}


/*
 * Class:     com_github_luben_zstd_ZstdDecompressCtx
 * Method:    decompressDirectByteBuffer0
 * Signature: (JLjava/nio/ByteBuffer;IILjava/nio/ByteBuffer;II)J
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_ZstdDecompressCtx_decompressDirectByteBuffer0
(JNIEnv *env, jclass jclazz, jlong ptr, jobject dst, jint dst_offset, jint dst_size, jobject src, jint src_offset, jint src_size)
{
    if (NULL == dst) return -ZSTD_error_dstSize_tooSmall;
    if (NULL == src) return -ZSTD_error_srcSize_wrong;
    if (0 > dst_offset) return -ZSTD_error_dstSize_tooSmall;
    if (0 > src_offset) return -ZSTD_error_srcSize_wrong;
    if (0 > src_size) return -ZSTD_error_srcSize_wrong;

    jsize dst_cap = (*env)->GetDirectBufferCapacity(env, dst);
    if (dst_offset + dst_size > dst_cap) return -ZSTD_error_dstSize_tooSmall;
    jsize src_cap = (*env)->GetDirectBufferCapacity(env, src);
    if (src_offset + src_size > src_cap) return -ZSTD_error_srcSize_wrong;

    ZSTD_DCtx* dctx = (ZSTD_DCtx*)(intptr_t)ptr;

    char *dst_buff = (char*)(*env)->GetDirectBufferAddress(env, dst);
    if (dst_buff == NULL) return -ZSTD_error_memory_allocation;
    char *src_buff = (char*)(*env)->GetDirectBufferAddress(env, src);
    if (src_buff == NULL) return -ZSTD_error_memory_allocation;

    ZSTD_DCtx_reset(dctx, ZSTD_reset_session_only);
    return ZSTD_decompressDCtx(dctx, dst_buff + dst_offset, (size_t) dst_size, src_buff + src_offset, (size_t) src_size);
}

/*
 * Class:     com_github_luben_zstd_ZstdDecompressCtx
 * Method:    decompressByteArray0
 * Signature: (B[IIB[II)J
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_ZstdDecompressCtx_decompressByteArray0
  (JNIEnv *env, jclass jclazz, jlong ptr, jbyteArray dst, jint dst_offset, jint dst_size, jbyteArray src, jint src_offset, jint src_size) {
    size_t size = -ZSTD_error_memory_allocation;

    if (0 > dst_offset) return -ZSTD_error_dstSize_tooSmall;
    if (0 > src_offset) return -ZSTD_error_srcSize_wrong;
    if (0 > src_size) return -ZSTD_error_srcSize_wrong;

    if (src_offset + src_size > (*env)->GetArrayLength(env, src)) return -ZSTD_error_srcSize_wrong;
    if (dst_offset + dst_size > (*env)->GetArrayLength(env, dst)) return -ZSTD_error_dstSize_tooSmall;

    ZSTD_DCtx* dctx = (ZSTD_DCtx*)(intptr_t)ptr;

    void *dst_buff = (*env)->GetPrimitiveArrayCritical(env, dst, NULL);
    if (dst_buff == NULL) goto E1;
    void *src_buff = (*env)->GetPrimitiveArrayCritical(env, src, NULL);
    if (src_buff == NULL) goto E2;

    ZSTD_DCtx_reset(dctx, ZSTD_reset_session_only);
    size = ZSTD_decompressDCtx(dctx, ((char *)dst_buff) + dst_offset, (size_t) dst_size, ((char *)src_buff) + src_offset, (size_t) src_size);

    (*env)->ReleasePrimitiveArrayCritical(env, src, src_buff, JNI_ABORT);
E2: (*env)->ReleasePrimitiveArrayCritical(env, dst, dst_buff, 0);
E1: return size;
}

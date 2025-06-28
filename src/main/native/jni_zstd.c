#ifndef ZSTD_STATIC_LINKING_ONLY
#define ZSTD_STATIC_LINKING_ONLY
#endif
#include <jni.h>
#include <zstd.h>
#include <zstd_errors.h>
#include <stdint.h>


/*
 * Private shim for JNI <-> ZSTD
 */
static size_t JNI_ZSTD_compress(void* dst, size_t dstCapacity,
                          const void* src, size_t srcSize,
                                int compressionLevel,
                                jboolean checksumFlag) {

    ZSTD_CCtx* const cctx = ZSTD_createCCtx();

    ZSTD_CCtx_setParameter(cctx, ZSTD_c_compressionLevel, compressionLevel);
    ZSTD_CCtx_setParameter(cctx, ZSTD_c_checksumFlag, (checksumFlag == JNI_TRUE));

    size_t const size = ZSTD_compress2(cctx, dst, dstCapacity, src, srcSize);

    ZSTD_freeCCtx(cctx);
    return size;
}

/*
 * Helper for determining decompressed size
 */
static size_t JNI_ZSTD_decompressedSize(const void* buf, size_t bufSize, jboolean magicless) {
    if (magicless) {
        ZSTD_frameHeader frameHeader;
        if (ZSTD_getFrameHeader_advanced(&frameHeader, buf, bufSize, ZSTD_f_zstd1_magicless) != 0) {
            return 0;
        }
        // note that skippable frames must have a magic number, so we don't need to consider that here
        return frameHeader.frameContentSize;
    }

    return ZSTD_getFrameContentSize(buf, bufSize);
}

/*
 * Class:     com_github_luben_zstd_Zstd
 * Method:    compressUnsafe
 * Signature: (JJJJI)J
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_Zstd_compressUnsafe
  (JNIEnv *env, jclass obj, jlong dst_buf_ptr, jlong dst_size, jlong src_buf_ptr, jlong src_size, jint level, jboolean checksumFlag) {
    return JNI_ZSTD_compress((void *)(intptr_t) dst_buf_ptr, (size_t) dst_size, (void *)(intptr_t) src_buf_ptr, (size_t) src_size, (int) level, checksumFlag);
}

/*
 * Class:     com_github_luben_zstd_Zstd
 * Method:    decompressUnsafe
 * Signature: (JJJJ)J
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_Zstd_decompressUnsafe
  (JNIEnv *env, jclass obj, jlong dst_buf_ptr, jlong dst_size, jlong src_buf_ptr, jlong src_size) {
    return ZSTD_decompress((void *)(intptr_t) dst_buf_ptr, (size_t) dst_size, (void *)(intptr_t) src_buf_ptr, (size_t) src_size);
}

/*
 * Class:     com_github_luben_zstd_Zstd
 * Method:    decompressedSize0
 * Signature: ([B)JIIZ
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_Zstd_decompressedSize0
  (JNIEnv *env, jclass obj, jbyteArray src, jint offset, jint limit, jboolean magicless) {
    size_t size = -ZSTD_error_memory_allocation;
    void *src_buff = (*env)->GetPrimitiveArrayCritical(env, src, NULL);
    if (src_buff == NULL) goto E1;
    size = JNI_ZSTD_decompressedSize(((char *) src_buff) + offset, (size_t) limit, magicless);
    (*env)->ReleasePrimitiveArrayCritical(env, src, src_buff, JNI_ABORT);
    if (size <= 0) return 0;
E1: return size;
}

/*
 * Class:     com_github_luben_zstd_Zstd
 * Method:    getFrameContentSize0
 * Signature: ([B)JIIZ
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_Zstd_getFrameContentSize0
  (JNIEnv *env, jclass obj, jbyteArray src, jint offset, jint limit, jboolean magicless) {
    size_t size = -ZSTD_error_memory_allocation;
    void *src_buff = (*env)->GetPrimitiveArrayCritical(env, src, NULL);
    if (src_buff == NULL) goto E1;
    size = JNI_ZSTD_decompressedSize(((char *) src_buff) + offset, (size_t) limit, magicless);
    (*env)->ReleasePrimitiveArrayCritical(env, src, src_buff, JNI_ABORT);
E1: return size;
}

/*
 * Class:     com_github_luben_zstd_Zstd
 * Method:    findFrameCompressedSize0
 * Signature: ([B)JII
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_Zstd_findFrameCompressedSize0
  (JNIEnv *env, jclass obj, jbyteArray src, jint offset, jint limit) {
    size_t size = -ZSTD_error_memory_allocation;
    void *src_buff = (*env)->GetPrimitiveArrayCritical(env, src, NULL);
    if (src_buff == NULL) goto E1;
    size = ZSTD_findFrameCompressedSize(((char *) src_buff) + offset, (size_t) limit);
    (*env)->ReleasePrimitiveArrayCritical(env, src, src_buff, JNI_ABORT);
E1: return size;
}

/*
 * Class:     com_github_luben_zstd_Zstd
 * Method:    findDirectByteBufferFrameCompressedSize
 * Signature: (Ljava/nio/ByteBuffer;II)J
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_Zstd_findDirectByteBufferFrameCompressedSize
  (JNIEnv *env, jclass obj, jobject src_buf, jint src_offset, jint src_size) {
    size_t size = -ZSTD_error_memory_allocation;
    jsize src_cap = (*env)->GetDirectBufferCapacity(env, src_buf);
    if (src_offset + src_size > src_cap) return -ZSTD_error_GENERIC;
    char *src_buf_ptr = (char*)(*env)->GetDirectBufferAddress(env, src_buf);
    if (src_buf_ptr == NULL) goto E1;
    size = ZSTD_findFrameCompressedSize(src_buf_ptr + src_offset, (size_t) src_size);
E1: return size;
}

/*
 * Class:     com_github_luben_zstd_Zstd
 * Method:    getDictIdFromFrame
 * Signature: ([B)J
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_Zstd_getDictIdFromFrame
  (JNIEnv *env, jclass obj, jbyteArray src) {
    unsigned dict_id = 0;
    jsize src_size = (*env)->GetArrayLength(env, src);
    void *src_buff = (*env)->GetPrimitiveArrayCritical(env, src, NULL);
    if (src_buff == NULL) goto E1;
    dict_id = ZSTD_getDictID_fromFrame(src_buff, (size_t) src_size);
    (*env)->ReleasePrimitiveArrayCritical(env, src, src_buff, JNI_ABORT);
E1: return (jlong) dict_id;
}

/*
 * Class:     com_github_luben_zstd_Zstd
 * Method:    getDictIdFromFrameBuffer
 * Signature: ([B)J
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_Zstd_getDictIdFromFrameBuffer
  (JNIEnv *env, jclass obj, jobject src) {
    unsigned dict_id = 0;
    jsize src_size = (*env)->GetDirectBufferCapacity(env, src);
    if (src_size == 0) goto E1;
    char *src_buff = (char*)(*env)->GetDirectBufferAddress(env, src);
    if (src_buff == NULL) goto E1;
    dict_id = ZSTD_getDictID_fromFrame(src_buff, (size_t) src_size);
E1: return (jlong) dict_id;
}

/*
 * Class:     com_github_luben_zstd_Zstd
 * Method:    getDictIdFromDict
 * Signature: ([B)J
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_Zstd_getDictIdFromDict
  (JNIEnv *env, jclass obj, jbyteArray src) {
    unsigned dict_id = 0;
    jsize src_size = (*env)->GetArrayLength(env, src);
    void *src_buff = (*env)->GetPrimitiveArrayCritical(env, src, NULL);
    if (src_buff == NULL) goto E1;
    dict_id = ZSTD_getDictID_fromDict(src_buff, (size_t) src_size);
    (*env)->ReleasePrimitiveArrayCritical(env, src, src_buff, JNI_ABORT);
E1: return (jlong) dict_id;
}

/*
 * Class:     com_github_luben_zstd_Zstd
 * Method:    getDictIdFromDictDirect
 * Signature: (Ljava/nio/ByteBuffer;II)J
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_Zstd_getDictIdFromDictDirect
(JNIEnv *env, jclass obj, jobject src, jint offset, jint src_size) {
    unsigned dict_id = 0;
    char *src_buff = (char*)(*env)->GetDirectBufferAddress(env, src);
    if (src_buff == NULL) goto E1;
    dict_id = ZSTD_getDictID_fromDict(src_buff + offset, (size_t) src_size);
E1: return (jlong) dict_id;
}

/*
 * Class:     com_github_luben_zstd_Zstd
 * Method:    decompressedDirectByteBufferSize
 * Signature: (Ljava/nio/ByteBuffer;II)J
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_Zstd_decompressedDirectByteBufferSize
  (JNIEnv *env, jclass obj, jobject src_buf, jint src_offset, jint src_size, jboolean magicless) {
    size_t size = -ZSTD_error_memory_allocation;
    jsize src_cap = (*env)->GetDirectBufferCapacity(env, src_buf);
    if (src_offset + src_size > src_cap) return -ZSTD_error_GENERIC;
    char *src_buf_ptr = (char*)(*env)->GetDirectBufferAddress(env, src_buf);
    if (src_buf_ptr == NULL) goto E1;
    size = JNI_ZSTD_decompressedSize(src_buf_ptr + src_offset, (size_t) src_size, magicless);
    if (size <= 0) return 0;
E1: return size;
}

/*
 * Class:     com_github_luben_zstd_Zstd
 * Method:    getDirectByteBufferFrameContentSize
 * Signature: (Ljava/nio/ByteBuffer;II)J
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_Zstd_getDirectByteBufferFrameContentSize
  (JNIEnv *env, jclass obj, jobject src_buf, jint src_offset, jint src_size, jboolean magicless) {
    size_t size = -ZSTD_error_memory_allocation;
    jsize src_cap = (*env)->GetDirectBufferCapacity(env, src_buf);
    if (src_offset + src_size > src_cap) return -ZSTD_error_GENERIC;
    char *src_buf_ptr = (char*)(*env)->GetDirectBufferAddress(env, src_buf);
    if (src_buf_ptr == NULL) goto E1;
    size = JNI_ZSTD_decompressedSize(src_buf_ptr + src_offset, (size_t) src_size, magicless);
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
 * Method:    getErrorCode
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_Zstd_getErrorCode
  (JNIEnv *env, jclass obj, jlong code) {
    return ZSTD_getErrorCode((size_t) code);
}

/*
 * Class:     com_github_luben_zstd_Zstd
 * Method:    loadDictDecompress
 * Signature: (J[BI)I
 */
JNIEXPORT jint JNICALL Java_com_github_luben_zstd_Zstd_loadDictDecompress
  (JNIEnv *env, jclass obj, jlong stream, jbyteArray dict, jint dict_size) {
    if (dict == NULL) return -ZSTD_error_dictionary_wrong;
    size_t size = -ZSTD_error_memory_allocation;
    void *dict_buff = (*env)->GetPrimitiveArrayCritical(env, dict, NULL);
    if (dict_buff == NULL) goto E1;

    size = ZSTD_DCtx_loadDictionary((ZSTD_DCtx *)(intptr_t) stream, dict_buff, dict_size);
E1:
    (*env)->ReleasePrimitiveArrayCritical(env, dict, dict_buff, JNI_ABORT);
    return size;
}

/*
 * Class:     com_github_luben_zstd_Zstd
 * Method:    loadFastDictDecompress
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_com_github_luben_zstd_Zstd_loadFastDictDecompress
  (JNIEnv *env, jclass obj, jlong stream, jobject dict) {
    if (dict == NULL) return -ZSTD_error_dictionary_wrong;
    jclass dict_clazz = (*env)->GetObjectClass(env, dict);
    jfieldID decompress_dict = (*env)->GetFieldID(env, dict_clazz, "nativePtr", "J");
    ZSTD_DDict* ddict = (ZSTD_DDict*)(intptr_t)(*env)->GetLongField(env, dict, decompress_dict);
    if (ddict == NULL) return -ZSTD_error_dictionary_wrong;
    return ZSTD_DCtx_refDDict((ZSTD_DCtx *)(intptr_t) stream, ddict);
}


/*
 * Class:     com_github_luben_zstd_Zstd
 * Method:    loadDictCompress
 * Signature: (J[BI)I
 */
JNIEXPORT jint JNICALL Java_com_github_luben_zstd_Zstd_loadDictCompress
  (JNIEnv *env, jclass obj, jlong stream, jbyteArray dict, jint dict_size) {
    if (dict == NULL) return -ZSTD_error_dictionary_wrong;
    size_t size = -ZSTD_error_memory_allocation;
    void *dict_buff = (*env)->GetPrimitiveArrayCritical(env, dict, NULL);
    if (dict_buff == NULL) goto E1;

    size = ZSTD_CCtx_loadDictionary((ZSTD_CCtx *)(intptr_t) stream, dict_buff, dict_size);
E1:
    (*env)->ReleasePrimitiveArrayCritical(env, dict, dict_buff, JNI_ABORT);
    return size;
}

/*
 * Class:     com_github_luben_zstd_Zstd
 * Method:    loadFastDictCompress
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_com_github_luben_zstd_Zstd_loadFastDictCompress
  (JNIEnv *env, jclass obj, jlong stream, jobject dict) {
    if (dict == NULL) return -ZSTD_error_dictionary_wrong;
    jclass dict_clazz = (*env)->GetObjectClass(env, dict);
    jfieldID compress_dict = (*env)->GetFieldID(env, dict_clazz, "nativePtr", "J");
    ZSTD_CDict* cdict = (ZSTD_CDict*)(intptr_t)(*env)->GetLongField(env, dict, compress_dict);
    if (cdict == NULL) return -ZSTD_error_dictionary_wrong;
    return ZSTD_CCtx_refCDict((ZSTD_CCtx *)(intptr_t) stream, cdict);
}

size_t builtinSequenceProducer(
  void* sequenceProducerState,
  ZSTD_Sequence* outSeqs, size_t outSeqsCapacity,
  const void* src, size_t srcSize,
  const void* dict, size_t dictSize,
  int compressionLevel,
  size_t windowSize
) {
  ZSTD_CCtx *zc = (ZSTD_CCtx *)sequenceProducerState;
  int windowLog = 0;
  while (windowSize > 1) {
    windowLog++;
    windowSize >>= 1;
  }
  ZSTD_CCtx_setParameter(zc, ZSTD_c_compressionLevel, compressionLevel);
  ZSTD_CCtx_setParameter(zc, ZSTD_c_windowLog, windowSize);
  size_t numSeqs = ZSTD_generateSequences((ZSTD_CCtx *)sequenceProducerState, outSeqs, outSeqsCapacity, src, srcSize);
  return ZSTD_isError(numSeqs) ? ZSTD_SEQUENCE_PRODUCER_ERROR : numSeqs;
}

size_t stubSequenceProducer(
  void* sequenceProducerState,
  ZSTD_Sequence* outSeqs, size_t outSeqsCapacity,
  const void* src, size_t srcSize,
  const void* dict, size_t dictSize,
  int compressionLevel,
  size_t windowSize
) {
  return ZSTD_SEQUENCE_PRODUCER_ERROR;
}

/*
 * Class:     com_github_luben_zstd_Zstd
 * Method:    getBuiltinSequenceProducer
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_Zstd_getBuiltinSequenceProducer
  (JNIEnv *env, jclass obj) {
    return (jlong)(intptr_t)&builtinSequenceProducer;
}

/*
 * Class:     com_github_luben_zstd_Zstd
 * Method:    getBuiltinSequenceProducer
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_Zstd_getStubSequenceProducer
  (JNIEnv *env, jclass obj) {
    return (jlong)(intptr_t)&stubSequenceProducer;
}

/*
 * Class:     com_github_luben_zstd_Zstd
 * Method:    registerSequenceProducer
 * Signature: (JJJ)V
 */
JNIEXPORT void JNICALL Java_com_github_luben_zstd_Zstd_registerSequenceProducer
  (JNIEnv *env, jclass obj, jlong stream, jlong seqProdState, jlong seqProdFunction) {
    ZSTD_registerSequenceProducer((ZSTD_CCtx *)(intptr_t) stream,
                                  (void *)(intptr_t) seqProdState,
                                  (ZSTD_sequenceProducer_F)(intptr_t) seqProdFunction);
}

/*
 * Class:     com_github_luben_zstd_Zstd
 * Method:    setCompressionChecksums
 * Signature: (JZ)I
 */
JNIEXPORT jint JNICALL Java_com_github_luben_zstd_Zstd_setCompressionChecksums
  (JNIEnv *env, jclass obj, jlong stream, jboolean enabled) {
    int checksum = enabled ? 1 : 0;
    return ZSTD_CCtx_setParameter((ZSTD_CCtx *)(intptr_t) stream, ZSTD_c_checksumFlag, checksum);
}

/*
 * Class:     com_github_luben_zstd_Zstd
 * Method:    setCompressionMagicless
 * Signature: (JZ)I
 */
JNIEXPORT jint JNICALL Java_com_github_luben_zstd_Zstd_setCompressionMagicless
  (JNIEnv *env, jclass obj, jlong stream, jboolean enabled) {
    ZSTD_format_e format = enabled ? ZSTD_f_zstd1_magicless : ZSTD_f_zstd1;
    return ZSTD_CCtx_setParameter((ZSTD_CCtx *)(intptr_t) stream, ZSTD_c_format, format);
}

/*
 * Class:     com_github_luben_zstd_Zstd
 * Method:    setCompressionLevel
 * Signature: (JI)I
 */
JNIEXPORT jint JNICALL Java_com_github_luben_zstd_Zstd_setCompressionLevel
  (JNIEnv *env, jclass obj, jlong stream, jint level) {
    return ZSTD_CCtx_setParameter((ZSTD_CCtx *)(intptr_t) stream, ZSTD_c_compressionLevel, level);
}

/*
 * Class:     com_github_luben_zstd_Zstd
 * Method:    setCompressionLong
 * Signature: (JI)I
 */
JNIEXPORT jint JNICALL Java_com_github_luben_zstd_Zstd_setCompressionLong
  (JNIEnv *env, jclass obj, jlong stream, jint windowLog) {
    ZSTD_CCtx* cctx = (ZSTD_CCtx*)(intptr_t) stream;
    if (windowLog < ZSTD_WINDOWLOG_MIN || windowLog > ZSTD_WINDOWLOG_LIMIT_DEFAULT) {
      // disable long matching and reset to default windowLog size
      ZSTD_CCtx_setParameter(cctx, ZSTD_c_enableLongDistanceMatching, ZSTD_ps_disable);
      ZSTD_CCtx_setParameter(cctx, ZSTD_c_windowLog, 0);
    } else {
      ZSTD_CCtx_setParameter(cctx, ZSTD_c_enableLongDistanceMatching, ZSTD_ps_enable);
      ZSTD_CCtx_setParameter(cctx, ZSTD_c_windowLog, windowLog);
    }
    return 0;
}

/*
 * Class:     com_github_luben_zstd_Zstd
 * Method:    setDecompressionLongMax
 * Signature: (JI)I
 */
JNIEXPORT jint JNICALL Java_com_github_luben_zstd_Zstd_setDecompressionLongMax
  (JNIEnv *env, jclass obj, jlong stream, jint windowLogMax) {
    ZSTD_DCtx* dctx = (ZSTD_DCtx*)(intptr_t) stream;
    return ZSTD_DCtx_setParameter(dctx, ZSTD_d_windowLogMax, windowLogMax);
}

/*
 * Class:     com_github_luben_zstd_Zstd
 * Method:    setDecompressionMagicless
 * Signature: (JZ)I
 */
JNIEXPORT jint JNICALL Java_com_github_luben_zstd_Zstd_setDecompressionMagicless
  (JNIEnv *env, jclass obj, jlong stream, jboolean enabled) {
    ZSTD_format_e format = enabled ? ZSTD_f_zstd1_magicless : ZSTD_f_zstd1;
    return ZSTD_DCtx_setParameter((ZSTD_DCtx *)(intptr_t) stream, ZSTD_d_format, format);
}

/*
 * Class:     com_github_luben_zstd_Zstd
 * Method:    setCompressionWorkers
 * Signature: (JI)I
 */
JNIEXPORT jint JNICALL Java_com_github_luben_zstd_Zstd_setCompressionWorkers
  (JNIEnv *env, jclass obj, jlong stream, jint workers) {
    return ZSTD_CCtx_setParameter((ZSTD_CCtx *)(intptr_t) stream, ZSTD_c_nbWorkers, workers);
}

/*
 * Class:     com_github_luben_zstd_Zstd
 * Method:    setCompressionJobSize
 * Signature: (JI)I
 */
JNIEXPORT jint JNICALL Java_com_github_luben_zstd_Zstd_setCompressionJobSize
  (JNIEnv *env, jclass obj, jlong stream, jint jobSize) {
    return ZSTD_CCtx_setParameter((ZSTD_CCtx *)(intptr_t) stream, ZSTD_c_jobSize, jobSize);
}

/*
 * Class:     com_github_luben_zstd_Zstd
 * Method:    setCompressionOverlapLog
 * Signature: (JI)I
 */
JNIEXPORT jint JNICALL Java_com_github_luben_zstd_Zstd_setCompressionOverlapLog
  (JNIEnv *env, jclass obj, jlong stream, jint overlapLog) {
    return ZSTD_CCtx_setParameter((ZSTD_CCtx *)(intptr_t) stream, ZSTD_c_overlapLog, overlapLog);
}

/*
 * Class:     com_github_luben_zstd_Zstd
 * Method:    setCompressionWindowLog
 * Signature: (JI)I
 */
JNIEXPORT jint JNICALL Java_com_github_luben_zstd_Zstd_setCompressionWindowLog
  (JNIEnv *env, jclass obj, jlong stream, jint windowLog) {
    return ZSTD_CCtx_setParameter((ZSTD_CCtx *)(intptr_t) stream, ZSTD_c_windowLog, windowLog);
}

/*
 * Class:     com_github_luben_zstd_Zstd
 * Method:    setCompressionHashLog
 * Signature: (JI)I
 */
JNIEXPORT jint JNICALL Java_com_github_luben_zstd_Zstd_setCompressionHashLog
  (JNIEnv *env, jclass obj, jlong stream, jint hashLog) {
    return ZSTD_CCtx_setParameter((ZSTD_CCtx *)(intptr_t) stream, ZSTD_c_hashLog, hashLog);
}

/*
 * Class:     com_github_luben_zstd_Zstd
 * Method:    setCompressionChainLog
 * Signature: (JI)I
 */
JNIEXPORT jint JNICALL Java_com_github_luben_zstd_Zstd_setCompressionChainLog
  (JNIEnv *env, jclass obj, jlong stream, jint chainLog) {
    return ZSTD_CCtx_setParameter((ZSTD_CCtx *)(intptr_t) stream, ZSTD_c_chainLog, chainLog);
}

/*
 * Class:     com_github_luben_zstd_Zstd
 * Method:    setCompressionSearchLog
 * Signature: (JI)I
 */
JNIEXPORT jint JNICALL Java_com_github_luben_zstd_Zstd_setCompressionSearchLog
  (JNIEnv *env, jclass obj, jlong stream, jint searchLog) {
    return ZSTD_CCtx_setParameter((ZSTD_CCtx *)(intptr_t) stream, ZSTD_c_searchLog, searchLog);
}

/*
 * Class:     com_github_luben_zstd_Zstd
 * Method:    setCompressionMinMatch
 * Signature: (JI)I
 */
JNIEXPORT jint JNICALL Java_com_github_luben_zstd_Zstd_setCompressionMinMatch
  (JNIEnv *env, jclass obj, jlong stream, jint minMatch) {
    return ZSTD_CCtx_setParameter((ZSTD_CCtx *)(intptr_t) stream, ZSTD_c_minMatch, minMatch);
}

/*
 * Class:     com_github_luben_zstd_Zstd
 * Method:    setCompressionTargetLength
 * Signature: (JI)I
 */
JNIEXPORT jint JNICALL Java_com_github_luben_zstd_Zstd_setCompressionTargetLength
  (JNIEnv *env, jclass obj, jlong stream, jint targetLength) {
    return ZSTD_CCtx_setParameter((ZSTD_CCtx *)(intptr_t) stream, ZSTD_c_targetLength, targetLength);
}

/*
 * Class:     com_github_luben_zstd_Zstd
 * Method:    setCompressionStrategy
 * Signature: (JI)I
 */
JNIEXPORT jint JNICALL Java_com_github_luben_zstd_Zstd_setCompressionStrategy
  (JNIEnv *env, jclass obj, jlong stream, jint strategy) {
    return ZSTD_CCtx_setParameter((ZSTD_CCtx *)(intptr_t) stream, ZSTD_c_strategy, strategy);
}

/*
 * Class:     com_github_luben_zstd_Zstd
 * Method:    setRefMultipleDDicts
 * Signature: (JZ)I
 */
JNIEXPORT jint JNICALL Java_com_github_luben_zstd_Zstd_setRefMultipleDDicts
  (JNIEnv *env, jclass obj, jlong stream, jboolean enabled) {
    ZSTD_refMultipleDDicts_e value = enabled ? ZSTD_rmd_refMultipleDDicts : ZSTD_rmd_refSingleDDict;
    return ZSTD_DCtx_setParameter((ZSTD_DCtx *)(intptr_t) stream, ZSTD_d_refMultipleDDicts, value);
}

/*
 * Class:     com_github_luben_zstd_Zstd
 * Method:    setValidateSequences
 * Signature: (JI)I
 */
JNIEXPORT jint JNICALL Java_com_github_luben_zstd_Zstd_setValidateSequences
  (JNIEnv *env, jclass obj, jlong stream, jint validateSequences) {
    return ZSTD_CCtx_setParameter((ZSTD_CCtx *)(intptr_t) stream, ZSTD_c_validateSequences, validateSequences);
}

/*
 * Class:     com_github_luben_zstd_Zstd
 * Method:    setSequenceProducerFallback
 * Signature: (JZ)I
 */
JNIEXPORT jint JNICALL Java_com_github_luben_zstd_Zstd_setSequenceProducerFallback
  (JNIEnv *env, jclass obj, jlong stream, jboolean fallbackFlag) {
    return ZSTD_CCtx_setParameter((ZSTD_CCtx*)(intptr_t) stream, ZSTD_c_enableSeqProducerFallback, (fallbackFlag == JNI_TRUE));
}

/*
 * Class:     com_github_luben_zstd_Zstd
 * Method:    setSearchForExternalRepcodes
 * Signature: (JZ)I
 */
JNIEXPORT jint JNICALL Java_com_github_luben_zstd_Zstd_setSearchForExternalRepcodes
  (JNIEnv *env, jclass obj, jlong stream, jint searchRepcodes) {
    return ZSTD_CCtx_setParameter((ZSTD_CCtx*)(intptr_t) stream, ZSTD_c_searchForExternalRepcodes, searchRepcodes);
}

/*
 * Class:     com_github_luben_zstd_Zstd
 * Method:    setEnableLongDistanceMatching
 * Signature: (JZ)I
 */
JNIEXPORT jint JNICALL Java_com_github_luben_zstd_Zstd_setEnableLongDistanceMatching
  (JNIEnv *env, jclass obj, jlong stream, jint enableLDM) {
    return ZSTD_CCtx_setParameter((ZSTD_CCtx*)(intptr_t) stream, ZSTD_c_enableLongDistanceMatching, enableLDM);
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

JNIEXPORT jint JNICALL Java_com_github_luben_zstd_Zstd_magicNumber
  (JNIEnv *env, jclass obj) {
    return ZSTD_MAGICNUMBER;
}

JNIEXPORT jint JNICALL Java_com_github_luben_zstd_Zstd_blockSizeMax
  (JNIEnv *env, jclass obj) {
    return ZSTD_BLOCKSIZE_MAX;
}

JNIEXPORT jint JNICALL Java_com_github_luben_zstd_Zstd_defaultCompressionLevel
  (JNIEnv *env, jclass obj) {
    return ZSTD_CLEVEL_DEFAULT;
}

JNIEXPORT jint JNICALL Java_com_github_luben_zstd_Zstd_minCompressionLevel
  (JNIEnv *env, jclass obj) {
    return ZSTD_minCLevel();
}

JNIEXPORT jint JNICALL Java_com_github_luben_zstd_Zstd_maxCompressionLevel
  (JNIEnv *env, jclass obj) {
    return ZSTD_maxCLevel();
}

#define JNI_ZSTD_ERROR(err, name) \
  JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_Zstd_err##name \
    (JNIEnv *env, jclass obj) { \
      return ZSTD_error_##err; \
  }


JNI_ZSTD_ERROR(no_error,                      NoError)
JNI_ZSTD_ERROR(GENERIC,                       Generic)
JNI_ZSTD_ERROR(prefix_unknown,                PrefixUnknown)
JNI_ZSTD_ERROR(version_unsupported,           VersionUnsupported)
JNI_ZSTD_ERROR(frameParameter_unsupported,    FrameParameterUnsupported)
JNI_ZSTD_ERROR(frameParameter_windowTooLarge, FrameParameterWindowTooLarge)
JNI_ZSTD_ERROR(corruption_detected,           CorruptionDetected)
JNI_ZSTD_ERROR(checksum_wrong,                ChecksumWrong)
JNI_ZSTD_ERROR(dictionary_corrupted,          DictionaryCorrupted)
JNI_ZSTD_ERROR(dictionary_wrong,              DictionaryWrong)
JNI_ZSTD_ERROR(dictionaryCreation_failed,     DictionaryCreationFailed)
JNI_ZSTD_ERROR(parameter_unsupported,         ParameterUnsupported)
JNI_ZSTD_ERROR(parameter_outOfBound,          ParameterOutOfBound)
JNI_ZSTD_ERROR(tableLog_tooLarge,             TableLogTooLarge)
JNI_ZSTD_ERROR(maxSymbolValue_tooLarge,       MaxSymbolValueTooLarge)
JNI_ZSTD_ERROR(maxSymbolValue_tooSmall,       MaxSymbolValueTooSmall)
JNI_ZSTD_ERROR(stage_wrong,                   StageWrong)
JNI_ZSTD_ERROR(init_missing,                  InitMissing)
JNI_ZSTD_ERROR(memory_allocation,             MemoryAllocation)
JNI_ZSTD_ERROR(workSpace_tooSmall,            WorkSpaceTooSmall)
JNI_ZSTD_ERROR(dstSize_tooSmall,              DstSizeTooSmall)
JNI_ZSTD_ERROR(srcSize_wrong,                 SrcSizeWrong)
JNI_ZSTD_ERROR(dstBuffer_null,                DstBufferNull)

#include <jni.h>
#include <zstd_static.h>
#include <error_public.h>
#include <legacy/zstd_v05.h>

# define MIN(_a, _b) (((_a) < (_b)) ? (_a) : (_b))

/* =============================== v0.5 support =========================== */

typedef struct
{
    U64 srcSize;       /* optional : tells how much bytes are present in the frame. Use 0 if not known. */
    U32 windowLog;     /* largest match distance : larger == more compression, more memory needed during decompression */
    U32 contentLog;    /* full search segment : larger == more compression, slower, more memory (useless for fast) */
    U32 hashLog;       /* dispatch table : larger == faster, more memory */
    U32 searchLog;     /* nb of searches : larger == more compression, slower */
    U32 searchLength;  /* match length searched : larger == faster decompression, sometimes less compression */
    U32 targetLength;  /* acceptable match size for optimal parser (only) : larger == more compression, slower */
    ZSTD_strategy strategy;
} ZSTDv05_parameters;

size_t ZSTDv05_decompressBegin(ZSTDv05_DCtx* dctx);
size_t ZSTDv05_getFrameParams(ZSTDv05_parameters* params, const void* src, size_t srcSize);
size_t ZSTDv05_nextSrcSizeToDecompress(ZSTDv05_DCtx* dctx);
size_t ZSTDv05_decompressContinue(ZSTDv05_DCtx* dctx, void* dst, size_t dstCapacity, const void* src, size_t srcSize);

/*
 * Class:     com_github_luben_zstd_ZstdInputStream
 * Method:    findBlockSize_v05
 * Signature: ([BJ)I
 */

JNIEXPORT jint JNICALL Java_com_github_luben_zstd_ZstdInputStream_findBlockSize_1v05
  (JNIEnv *env, jclass obj, jbyteArray src, jlong src_size) {
    ZSTDv05_parameters params;
    void *src_buff = (*env)->GetPrimitiveArrayCritical(env, src, NULL);
    size_t size = ZSTDv05_getFrameParams(&params, src_buff, (size_t) src_size);
    (*env)->ReleasePrimitiveArrayCritical(env, src, src_buff, JNI_ABORT);
    if (size == 0) {
        return (jint) MIN(1 << params.windowLog, ZSTD_BLOCKSIZE_MAX);
    } else {
        return (jint) -size;
    }
}

/*
 * Class:     com_github_luben_zstd_ZstdInputStream
 * Method:    findOBuffSize_v05
 * Signature: ([BJ)I
 */
JNIEXPORT jint JNICALL Java_com_github_luben_zstd_ZstdInputStream_findOBuffSize_1v05
  (JNIEnv *env, jclass obj, jbyteArray src, jlong src_size) {
    ZSTDv05_parameters params;
    void *src_buff = (*env)->GetPrimitiveArrayCritical(env, src, NULL);
    size_t size = ZSTDv05_getFrameParams(&params, src_buff, (size_t) src_size);
    (*env)->ReleasePrimitiveArrayCritical(env, src, src_buff, JNI_ABORT);
    if (size == 0) {
        size_t block_size = MIN(1 << params.windowLog, ZSTD_BLOCKSIZE_MAX);
        return (jint) (1 << params.windowLog) + block_size;
    } else {
        return (jint) -size;
    }
}

/*
 * Class:     com_github_luben_zstd_ZstdInputStream
 * Method:    nextSrcSizeToDecompress_v05
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_com_github_luben_zstd_ZstdInputStream_nextSrcSizeToDecompress_1v05
  (JNIEnv *env, jclass obj, jlong ctx) {
    return (jint) ZSTDv05_nextSrcSizeToDecompress((ZSTDv05_DCtx*)(size_t) ctx);
}

/*
 * Class:     com_github_luben_zstd_ZstdInputStream
 * Method:    createDCtx_v05
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_ZstdInputStream_createDCtx_1v05
  (JNIEnv *env, jclass obj) {
    return (jlong)(size_t) ZSTDv05_createDCtx();
}

/*
 * Class:     com_github_luben_zstd_ZstdInputStream
 * Method:    decompressBegin_v05
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_com_github_luben_zstd_ZstdInputStream_decompressBegin_1v05
  (JNIEnv *env, jclass obj, jlong ctx) {
    return ZSTDv05_decompressBegin((ZSTDv05_DCtx*)(size_t)ctx);
}

/*
 * Class:     com_github_luben_zstd_ZstdInputStream
 * Method:    freeDCtx_v05
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_com_github_luben_zstd_ZstdInputStream_freeDCtx_1v05
  (JNIEnv *env, jclass obj, jlong ctx) {
    return ZSTDv05_freeDCtx((ZSTDv05_DCtx*)(size_t)ctx);
}

/*
 * Class:     com_github_luben_zstd_ZstdInputStream
 * Method:    decompressContinue_v05
 * Signature: (JLjava/nio/ByteBuffer;JJ[BJJ)I
 */
JNIEXPORT jint JNICALL Java_com_github_luben_zstd_ZstdInputStream_decompressContinue_1v05
  (JNIEnv *env, jclass obj, jlong ctx, jobject dst, jlong dst_offset, jlong dst_size, jbyteArray src, jlong src_offset, jlong src_size) {
    size_t size = (size_t)(0 - ZSTD_error_memory_allocation);
    void *dst_buff = (*env)->GetDirectBufferAddress(env, dst);
    if (dst_buff == NULL) goto E1;
    void *src_buff = (*env)->GetPrimitiveArrayCritical(env, src, NULL);
    if (src_buff == NULL) goto E1;

    size = ZSTDv05_decompressContinue(
            (ZSTDv05_DCtx*)(size_t) ctx,
            dst_buff + dst_offset, (size_t) dst_size,
            src_buff + src_offset, (size_t) src_size
        );

    (*env)->ReleasePrimitiveArrayCritical(env, src, src_buff, JNI_ABORT);
E1: return (jint) size;
}

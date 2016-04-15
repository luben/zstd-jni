#include <jni.h>
//#include <zstd_static.h>
#include <mem.h>
#include <error_public.h>
#include <legacy/zstd_v04.h>

typedef enum { ZSTD_fast, ZSTD_greedy, ZSTD_lazy, ZSTD_lazy2, ZSTD_btlazy2 } ZSTD_strategy;
typedef struct
{
    U64 srcSize;       /* optional : tells how much bytes are present in the frame. Use 0 if not known. */
    U32 windowLog;     /* largest match distance : larger == more compression, more memory needed during decompression */
    U32 contentLog;    /* full search segment : larger == more compression, slower, more memory (useless for fast) */
    U32 hashLog;       /* dispatch table : larger == more memory, faster */
    U32 searchLog;     /* nb of searches : larger == more compression, slower */
    U32 searchLength;  /* size of matches : larger == faster decompression, sometimes less compression */
    ZSTD_strategy strategy;
} ZSTDv04_parameters;

size_t ZSTDv04_getFrameParams(ZSTDv04_parameters* params, const void* src, size_t srcSize);
size_t ZSTDv05_nextSrcSizeToDecompress(ZSTDv04_Dctx* dctx);

/*
 * Class:     com_github_luben_zstd_ZstdInputStreamV04
 * Method:    createDCtx
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_ZstdInputStreamV04_createDCtx
  (JNIEnv *env, jclass obj) {
    return (jlong)(size_t) ZSTDv04_createDCtx();
}

/*
 * Class:     com_github_luben_zstd_ZstdInputStreamV04
 * Method:    freeDCtx
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_ZstdInputStreamV04_freeDCtx
  (JNIEnv *env, jclass obj, jlong ctx) {
    return ZSTDv04_freeDCtx((ZSTDv04_Dctx*)(size_t)ctx);
}

/*
 * Class:     com_github_luben_zstd_ZstdInputStreamV04
 * Method:    findOBuffSize
 * Signature: ([BJ)I
 */
JNIEXPORT jint JNICALL Java_com_github_luben_zstd_ZstdInputStreamV04_findOBuffSize
  (JNIEnv *env, jclass obj, jbyteArray src, jlong src_size) {
    ZSTDv04_parameters params;
    void *src_buff = (*env)->GetPrimitiveArrayCritical(env, src, NULL);
    size_t size = ZSTDv04_getFrameParams(&params, src_buff, (size_t) src_size);
    (*env)->ReleasePrimitiveArrayCritical(env, src, src_buff, JNI_ABORT);
    if (size == 0) {
        return (jint) (1 << params.windowLog);
    } else {
        return (jint) -size;
    }
}

/*
 * Class:     com_github_luben_zstd_ZstdInputStreamV04
 * Method:    nextSrcSizeToDecompress
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_ZstdInputStreamV04_nextSrcSizeToDecompress
  (JNIEnv *env, jclass obj, jlong ctx) {
    return (jlong) ZSTDv04_nextSrcSizeToDecompress((ZSTDv04_Dctx*)(size_t) ctx);
}

/*
 * Class:     com_github_luben_zstd_ZstdInputStreamV04
 * Method:    decompressContinue
 * Signature: (JLjava/nio/ByteBuffer;JJ[BJ)J
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_ZstdInputStreamV04_decompressContinue
  (JNIEnv *env, jclass obj, jlong ctx, jobject dst, jlong dst_offset, jlong dst_size, jbyteArray src, jlong src_size) {
    size_t size = (size_t)(0-ZSTD_error_memory_allocation);
    void *dst_buff = (*env)->GetDirectBufferAddress(env, dst);
    if (dst_buff == NULL) goto E1;
    void *src_buff = (*env)->GetPrimitiveArrayCritical(env, src, NULL);
    if (src_buff == NULL) goto E1;

    size = ZSTDv04_decompressContinue(
            (ZSTDv04_Dctx*)(size_t) ctx,
            dst_buff + dst_offset, (size_t) dst_size,
            src_buff,              (size_t) src_size
        );

    (*env)->ReleasePrimitiveArrayCritical(env, src, src_buff, JNI_ABORT);
E1: return (jlong) size;
}

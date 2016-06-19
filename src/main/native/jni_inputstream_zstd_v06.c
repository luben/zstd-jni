#include <jni.h>
#include <mem.h>
#include <error_public.h>
#include <legacy/zstd_v06.h>

# define MIN(_a, _b) (((_a) < (_b)) ? (_a) : (_b))
#define ZSTD_BLOCKSIZE_MAX (128 * 1024)   /* define, for static allocation */
struct ZSTDv06_frameParams_s { U64 frameContentSize; U32 windowLog; };                                                                                                                                               
ZSTDLIB_API size_t ZSTDv06_decompressBegin(ZSTDv06_DCtx* dctx);                                                                                                                                                      

/*
 * Class:     com_github_luben_zstd_ZstdInputStream
 * Method:    findBlockSize
 * Signature: ([BJ)I
 */
JNIEXPORT jint JNICALL Java_com_github_luben_zstd_ZstdInputStreamV06_findBlockSize
  (JNIEnv *env, jclass obj, jbyteArray src, jlong src_size) {
    ZSTDv06_frameParams params;
    void *src_buff = (*env)->GetPrimitiveArrayCritical(env, src, NULL);
    size_t size = ZSTDv06_getFrameParams(&params, src_buff, (size_t) src_size);
    (*env)->ReleasePrimitiveArrayCritical(env, src, src_buff, JNI_ABORT);
    if (size == 0) {
        return (jint) MIN(1 << params.windowLog, ZSTD_BLOCKSIZE_MAX);
    } else {
        return (jint) -size;
    }
}

/*
 * Class:     com_github_luben_zstd_ZstdInputStream
 * Method:    findOBuffSize
 * Signature: ([BJ)I
 */
JNIEXPORT jint JNICALL Java_com_github_luben_zstd_ZstdInputStreamV06_findOBuffSize
  (JNIEnv *env, jclass obj, jbyteArray src, jlong src_size) {
    ZSTDv06_frameParams params;
    void *src_buff = (*env)->GetPrimitiveArrayCritical(env, src, NULL);
    size_t size = ZSTDv06_getFrameParams(&params, src_buff, (size_t) src_size);
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
 * Method:    nextSrcSizeToDecompress
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_com_github_luben_zstd_ZstdInputStreamV06_nextSrcSizeToDecompress
  (JNIEnv *env, jclass obj, jlong ctx) {
    return (jint) ZSTDv06_nextSrcSizeToDecompress((ZSTDv06_DCtx*)(size_t) ctx);
}

/*
 * Class:     com_github_luben_zstd_ZstdInputStream
 * Method:    createDCtx
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_ZstdInputStreamV06_createDCtx
  (JNIEnv *env, jclass obj) {
    return (jlong)(size_t) ZSTDv06_createDCtx();
}

/*
 * Class:     com_github_luben_zstd_ZstdInputStream
 * Method:    decompressBegin
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_com_github_luben_zstd_ZstdInputStreamV06_decompressBegin
  (JNIEnv *env, jclass obj, jlong ctx) {
    return ZSTDv06_decompressBegin((ZSTDv06_DCtx*)(size_t)ctx);
}

/*
 * Class:     com_github_luben_zstd_ZstdInputStream
 * Method:    freeDCtx
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_com_github_luben_zstd_ZstdInputStreamV06_freeDCtx
  (JNIEnv *env, jclass obj, jlong ctx) {
    return ZSTDv06_freeDCtx((ZSTDv06_DCtx*)(size_t)ctx);
}

/*
 * Class:     com_github_luben_zstd_ZstdInputStream
 * Method:    decompressContinue
 * Signature: (JLjava/nio/ByteBuffer;JJ[BJJ)I
 */
JNIEXPORT jint JNICALL Java_com_github_luben_zstd_ZstdInputStreamV06_decompressContinue
  (JNIEnv *env, jclass obj, jlong ctx, jobject dst, jlong dst_offset, jlong dst_size, jbyteArray src, jlong src_offset, jlong src_size) {
    size_t size = (size_t)(0 - ZSTD_error_memory_allocation);
    void *dst_buff = (*env)->GetDirectBufferAddress(env, dst);
    if (dst_buff == NULL) goto E1;
    void *src_buff = (*env)->GetPrimitiveArrayCritical(env, src, NULL);
    if (src_buff == NULL) goto E1;

    size = ZSTDv06_decompressContinue(
            (ZSTDv06_DCtx*)(size_t) ctx,
            dst_buff + dst_offset, (size_t) dst_size,
            src_buff + src_offset, (size_t) src_size
        );

    (*env)->ReleasePrimitiveArrayCritical(env, src, src_buff, JNI_ABORT);
E1: return (jint) size;
}

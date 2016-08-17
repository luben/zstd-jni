#include <jni.h>
#include <zstd_internal.h>
#include <error_public.h>
#include <stdlib.h>

/* field IDs can't change in the same VM */
static jfieldID src_pos_id;
static jfieldID dst_pos_id;

typedef struct CCtx_s {
    ZSTD_CStream *stream;
    ZSTD_inBuffer read;
    ZSTD_outBuffer write;
} CCtx;

/*
 * Class:     com_github_luben_zstd_ZstdOutputStream
 * Method:    recommendedCOutSize
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_com_github_luben_zstd_ZstdOutputStream_recommendedCOutSize
  (JNIEnv *env, jclass obj) {
    return (jint) ZSTD_CStreamOutSize();
}

/*
 * Class:     com_github_luben_zstd_ZstdOutputStream
 * Method:    createCCtx
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL Java_com_github_luben_zstd_ZstdOutputStream_createCCtx
  (JNIEnv *env, jclass obj) {
    CCtx *ctx = malloc(sizeof(CCtx));
    ctx->stream = ZSTD_createCStream();
    ctx->read.pos = 0;
    ctx->write.pos = 0;
    return (jlong) ctx;
}

/*
 * Class:     com_github_luben_zstd_ZstdOutputStream
 * Method:    freeCCtx
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_com_github_luben_zstd_ZstdOutputStream_freeCCtx
  (JNIEnv *env, jclass obj, jlong jctx) {
    CCtx *ctx = (CCtx *) jctx;
    size_t result = ZSTD_freeCStream(ctx->stream);
    free(ctx);
    return (jlong) result;
}

/*
 * Class:     com_github_luben_zstd_ZstdOutputStream
 * Method:    comperssInit
 * Signature: (JI)I
 */
JNIEXPORT jint JNICALL Java_com_github_luben_zstd_ZstdOutputStream_compressInit
  (JNIEnv *env, jclass obj, jlong jctx, jint level) {
    CCtx *ctx = (CCtx *) jctx;
    jclass clazz = (*env)->GetObjectClass(env, obj);
    src_pos_id = (*env)->GetFieldID(env, clazz, "srcPos", "J");
    dst_pos_id = (*env)->GetFieldID(env, clazz, "dstPos", "J");
    return ZSTD_initCStream(ctx->stream, level);
}

/*
 * Class:     com_github_luben_zstd_ZstdOutputStream
 * Method:    compressContinue
 * Signature: (J[BI[BII)I
 */
JNIEXPORT jint JNICALL Java_com_github_luben_zstd_ZstdOutputStream_compressContinue
  (JNIEnv *env, jclass obj, jlong jctx, jbyteArray dst, jint dst_size, jbyteArray src, jint src_offset, jint src_size) {

    CCtx *ctx = (CCtx *) jctx;
    size_t size = (size_t)(0-ZSTD_error_memory_allocation);

    ctx->read.pos = (size_t) (*env)->GetLongField(env, obj, src_pos_id);
    ctx->read.size = (size_t) src_size;
    ctx->write.size = (size_t) dst_size;
    ctx->write.pos  = 0;
    ctx->write.dst = (*env)->GetPrimitiveArrayCritical(env, dst, NULL);
    if (ctx->write.dst == NULL) goto E1;
    ctx->read.src = (*env)->GetPrimitiveArrayCritical(env, src, NULL) + src_offset;
    if (ctx->read.src == NULL) goto E2;

    size = ZSTD_compressStream(ctx->stream, &(ctx->write), &(ctx->read));

    (*env)->ReleasePrimitiveArrayCritical(env, src, (void *) ctx->read.src, 0);
E2: (*env)->ReleasePrimitiveArrayCritical(env, dst, ctx->write.dst, 0);
    (*env)->SetLongField(env, obj, src_pos_id, ctx->read.pos);
    (*env)->SetLongField(env, obj, dst_pos_id, ctx->write.pos);
E1: return (jint) size;
}

/*
 * Class:     com_github_luben_zstd_ZstdOutputStream
 * Method:    compressEnd
 * Signature: (J[BI)I
 */
JNIEXPORT jint JNICALL Java_com_github_luben_zstd_ZstdOutputStream_compressEnd
  (JNIEnv *env, jclass obj, jlong jctx, jbyteArray dst, jint dst_size) {

    CCtx *ctx = (CCtx *) jctx;
    size_t size = (size_t)(0-ZSTD_error_memory_allocation);

    ctx->write.size = (size_t) dst_size;
    ctx->write.pos  = 0;
    ctx->write.dst = (*env)->GetPrimitiveArrayCritical(env, dst, NULL);
    if (ctx->write.dst != NULL) {
        size = ZSTD_endStream(ctx->stream, &(ctx->write));
        (*env)->ReleasePrimitiveArrayCritical(env, dst, ctx->write.dst, 0);
        (*env)->SetLongField(env, obj, dst_pos_id, ctx->write.pos);
    }
    return (jint) size;
}

/*
 * Class:     com_github_luben_zstd_ZstdOutputStream
 * Method:    compressFlush
 * Signature: (J[BI)I
 */
JNIEXPORT jint JNICALL Java_com_github_luben_zstd_ZstdOutputStream_compressFlush
  (JNIEnv *env, jclass obj, jlong jctx, jbyteArray dst, jint dst_size) {

    CCtx *ctx = (CCtx *) jctx;
    size_t size = (size_t)(0-ZSTD_error_memory_allocation);

    ctx->write.size = (size_t) dst_size;
    ctx->write.pos  = 0;
    ctx->write.dst = (*env)->GetPrimitiveArrayCritical(env, dst, NULL);
    if (ctx->write.dst != NULL) {
        size = ZSTD_flushStream(ctx->stream, &(ctx->write));
        (*env)->ReleasePrimitiveArrayCritical(env, dst, ctx->write.dst, 0);
        (*env)->SetLongField(env, obj, dst_pos_id, ctx->write.pos);
    }
    return (jint) size;
}

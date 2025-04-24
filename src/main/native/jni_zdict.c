#ifndef ZDICT_STATIC_LINKING_ONLY
#define ZDICT_STATIC_LINKING_ONLY
#endif
#include <jni.h>
#include <zdict.h>
#include <zstd_errors.h>
#include <stdlib.h>
#include <string.h>

JNIEXPORT jlong Java_com_github_luben_zstd_Zstd_trainFromBuffer0
  (JNIEnv *env, jclass obj, jobjectArray samples, jbyteArray dictBuffer, jboolean legacy, jint compressionLevel) {
    if (dictBuffer == NULL) return -ZSTD_error_dictionary_wrong;
    size_t size = 0;
    jsize num_samples = (*env)->GetArrayLength(env, samples);
    size_t *samples_sizes = malloc(sizeof(size_t) * num_samples);
    if (!samples_sizes) {
        jclass eClass = (*env)->FindClass(env, "Ljava/lang/OutOfMemoryError;");
        (*env)->ThrowNew(env, eClass, "native heap");
        goto E1;
    }
    size_t samples_buffer_size = 0;
    for (int i = 0; i < num_samples; i++) {
        jbyteArray sample = (*env)->GetObjectArrayElement(env, samples, i);
        jsize length = (*env)->GetArrayLength(env, sample);
        (*env)->DeleteLocalRef(env, sample);
        samples_sizes[i] = length;
        samples_buffer_size += length;
    }
    void *samples_buffer = malloc(samples_buffer_size);
    if (!samples_buffer) {
        jclass eClass = (*env)->FindClass(env, "Ljava/lang/OutOfMemoryError;");
        (*env)->ThrowNew(env, eClass, "native heap");
        goto E2;
    }
    size_t cursor = 0;
    for (int i = 0; i < num_samples; i++) {
        jbyteArray sample = (*env)->GetObjectArrayElement(env, samples, i);
        jsize length = (*env)->GetArrayLength(env, sample);
        (*env)->GetByteArrayRegion(env, sample, 0, length, (jbyte*)(((char *)samples_buffer) + cursor));
        (*env)->DeleteLocalRef(env, sample);
        cursor += length;
    }
    size_t dict_capacity = (*env)->GetArrayLength(env, dictBuffer);
    void *dict_buff =  (*env)->GetPrimitiveArrayCritical(env, dictBuffer, NULL);

    if (legacy == JNI_TRUE) {
        ZDICT_legacy_params_t params;
        memset(&params,0,sizeof(params));
        params.zParams.compressionLevel = compressionLevel;
        size = ZDICT_trainFromBuffer_legacy(dict_buff, dict_capacity, samples_buffer, samples_sizes, num_samples, params);
    } else {
        size = ZDICT_trainFromBuffer(dict_buff, dict_capacity, samples_buffer, samples_sizes, num_samples, compressionLevel);
    }
    (*env)->ReleasePrimitiveArrayCritical(env, dictBuffer, dict_buff, 0);
    free(samples_buffer);
E2: free(samples_sizes);
E1: return size;
}

JNIEXPORT jlong Java_com_github_luben_zstd_Zstd_trainFromBufferDirect0
  (JNIEnv *env, jclass obj, jobject samples, jintArray sampleSizes, jobject dictBuffer, jboolean legacy, jint compressionLevel) {

    size_t size = 0;
    void *samples_buffer = (*env)->GetDirectBufferAddress(env, samples);
    void *dict_buff = (*env)->GetDirectBufferAddress(env, dictBuffer);
    size_t dict_capacity = (*env)->GetDirectBufferCapacity(env, dictBuffer);

    /* convert sized from int to size_t */
    jsize num_samples = (*env)->GetArrayLength(env, sampleSizes);
    size_t *samples_sizes = malloc(sizeof(size_t) * num_samples);
    if (!samples_sizes) {
        jclass eClass = (*env)->FindClass(env, "Ljava/lang/OutOfMemoryError;");
        (*env)->ThrowNew(env, eClass, "native heap");
        goto E1;
    }
    jint *sample_sizes_array = (*env)->GetPrimitiveArrayCritical(env, sampleSizes, NULL);
    if (sample_sizes_array == NULL) goto E2;
    for (int i = 0; i < num_samples; i++) {
        samples_sizes[i] = sample_sizes_array[i];
    }
    (*env)->ReleasePrimitiveArrayCritical(env, sampleSizes, sample_sizes_array, JNI_ABORT);

    if (legacy == JNI_TRUE) {
        ZDICT_legacy_params_t params;
        memset(&params, 0, sizeof(params));
        params.zParams.compressionLevel = compressionLevel;
        size = ZDICT_trainFromBuffer_legacy(dict_buff, dict_capacity, samples_buffer, samples_sizes, num_samples, params);
    } else {
        size = ZDICT_trainFromBuffer(dict_buff, dict_capacity, samples_buffer, samples_sizes, num_samples, compressionLevel);
    }
E2: free(samples_sizes);
E1: return size;
}


/* Header constants exposed to FFM. Java reads each getter once at class
 * initialization. Keep the expressions in C so zstd upgrades and target ABI
 * changes are reflected automatically, just as in the JNI constant getters. */
#ifndef ZSTD_STATIC_LINKING_ONLY
#define ZSTD_STATIC_LINKING_ONLY
#endif
#include "zstd.h"
#include "zstd_errors.h"

#if defined(_WIN32)
#  define ZSTD_JAVA_API __declspec(dllexport)
#elif defined(__GNUC__)
#  define ZSTD_JAVA_API __attribute__((visibility("default")))
#else
#  define ZSTD_JAVA_API
#endif

ZSTD_JAVA_API int zstd_java_e_continue(void) {
    return (int) ZSTD_e_continue;
}

ZSTD_JAVA_API int zstd_java_e_flush(void) {
    return (int) ZSTD_e_flush;
}

ZSTD_JAVA_API int zstd_java_e_end(void) {
    return (int) ZSTD_e_end;
}

ZSTD_JAVA_API int zstd_java_reset_session_only(void) {
    return (int) ZSTD_reset_session_only;
}

ZSTD_JAVA_API int zstd_java_reset_session_and_parameters(void) {
    return (int) ZSTD_reset_session_and_parameters;
}

ZSTD_JAVA_API int zstd_java_c_compressionLevel(void) {
    return (int) ZSTD_c_compressionLevel;
}

ZSTD_JAVA_API int zstd_java_c_contentSizeFlag(void) {
    return (int) ZSTD_c_contentSizeFlag;
}

ZSTD_JAVA_API int zstd_java_c_checksumFlag(void) {
    return (int) ZSTD_c_checksumFlag;
}

ZSTD_JAVA_API int zstd_java_c_dictIDFlag(void) {
    return (int) ZSTD_c_dictIDFlag;
}

ZSTD_JAVA_API int zstd_java_error_dictionary_wrong(void) {
    return (int) ZSTD_error_dictionary_wrong;
}

ZSTD_JAVA_API int zstd_java_error_dstSize_tooSmall(void) {
    return (int) ZSTD_error_dstSize_tooSmall;
}

ZSTD_JAVA_API int zstd_java_error_srcSize_wrong(void) {
    return (int) ZSTD_error_srcSize_wrong;
}

ZSTD_JAVA_API int zstd_java_error_no_error(void) {
    return (int) ZSTD_error_no_error;
}

ZSTD_JAVA_API int zstd_java_error_GENERIC(void) {
    return (int) ZSTD_error_GENERIC;
}

ZSTD_JAVA_API int zstd_java_error_prefix_unknown(void) {
    return (int) ZSTD_error_prefix_unknown;
}

ZSTD_JAVA_API int zstd_java_error_version_unsupported(void) {
    return (int) ZSTD_error_version_unsupported;
}

ZSTD_JAVA_API int zstd_java_error_frameParameter_unsupported(void) {
    return (int) ZSTD_error_frameParameter_unsupported;
}

ZSTD_JAVA_API int zstd_java_error_frameParameter_windowTooLarge(void) {
    return (int) ZSTD_error_frameParameter_windowTooLarge;
}

ZSTD_JAVA_API int zstd_java_error_corruption_detected(void) {
    return (int) ZSTD_error_corruption_detected;
}

ZSTD_JAVA_API int zstd_java_error_checksum_wrong(void) {
    return (int) ZSTD_error_checksum_wrong;
}

ZSTD_JAVA_API int zstd_java_error_dictionary_corrupted(void) {
    return (int) ZSTD_error_dictionary_corrupted;
}

ZSTD_JAVA_API int zstd_java_error_dictionaryCreation_failed(void) {
    return (int) ZSTD_error_dictionaryCreation_failed;
}

ZSTD_JAVA_API int zstd_java_error_parameter_unsupported(void) {
    return (int) ZSTD_error_parameter_unsupported;
}

ZSTD_JAVA_API int zstd_java_error_parameter_outOfBound(void) {
    return (int) ZSTD_error_parameter_outOfBound;
}

ZSTD_JAVA_API int zstd_java_error_tableLog_tooLarge(void) {
    return (int) ZSTD_error_tableLog_tooLarge;
}

ZSTD_JAVA_API int zstd_java_error_maxSymbolValue_tooLarge(void) {
    return (int) ZSTD_error_maxSymbolValue_tooLarge;
}

ZSTD_JAVA_API int zstd_java_error_maxSymbolValue_tooSmall(void) {
    return (int) ZSTD_error_maxSymbolValue_tooSmall;
}

ZSTD_JAVA_API int zstd_java_error_stage_wrong(void) {
    return (int) ZSTD_error_stage_wrong;
}

ZSTD_JAVA_API int zstd_java_error_init_missing(void) {
    return (int) ZSTD_error_init_missing;
}

ZSTD_JAVA_API int zstd_java_error_memory_allocation(void) {
    return (int) ZSTD_error_memory_allocation;
}

ZSTD_JAVA_API int zstd_java_error_workSpace_tooSmall(void) {
    return (int) ZSTD_error_workSpace_tooSmall;
}

ZSTD_JAVA_API int zstd_java_error_dstBuffer_null(void) {
    return (int) ZSTD_error_dstBuffer_null;
}

ZSTD_JAVA_API int zstd_java_MAGICNUMBER(void) {
    return (int) ZSTD_MAGICNUMBER;
}

ZSTD_JAVA_API int zstd_java_WINDOWLOG_MIN(void) {
    return (int) ZSTD_WINDOWLOG_MIN;
}

ZSTD_JAVA_API int zstd_java_WINDOWLOG_MAX(void) {
    return (int) ZSTD_WINDOWLOG_MAX;
}

ZSTD_JAVA_API int zstd_java_HASHLOG_MIN(void) {
    return (int) ZSTD_HASHLOG_MIN;
}

ZSTD_JAVA_API int zstd_java_HASHLOG_MAX(void) {
    return (int) ZSTD_HASHLOG_MAX;
}

ZSTD_JAVA_API int zstd_java_CHAINLOG_MIN(void) {
    return (int) ZSTD_CHAINLOG_MIN;
}

ZSTD_JAVA_API int zstd_java_CHAINLOG_MAX(void) {
    return (int) ZSTD_CHAINLOG_MAX;
}

ZSTD_JAVA_API int zstd_java_SEARCHLOG_MIN(void) {
    return (int) ZSTD_SEARCHLOG_MIN;
}

ZSTD_JAVA_API int zstd_java_SEARCHLOG_MAX(void) {
    return (int) ZSTD_SEARCHLOG_MAX;
}

ZSTD_JAVA_API int zstd_java_BLOCKSIZE_MAX(void) {
    return (int) ZSTD_BLOCKSIZE_MAX;
}

ZSTD_JAVA_API int zstd_java_CLEVEL_DEFAULT(void) {
    return (int) ZSTD_CLEVEL_DEFAULT;
}


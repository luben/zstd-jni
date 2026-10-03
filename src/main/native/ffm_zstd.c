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


/* Configuration parameters and values. */
ZSTD_JAVA_API int zstd_java_f_zstd1_magicless(void) {
    return (int) ZSTD_f_zstd1_magicless;
}

ZSTD_JAVA_API int zstd_java_f_zstd1(void) {
    return (int) ZSTD_f_zstd1;
}

ZSTD_JAVA_API int zstd_java_c_format(void) {
    return (int) ZSTD_c_format;
}

ZSTD_JAVA_API int zstd_java_c_nbWorkers(void) {
    return (int) ZSTD_c_nbWorkers;
}

ZSTD_JAVA_API int zstd_java_c_overlapLog(void) {
    return (int) ZSTD_c_overlapLog;
}

ZSTD_JAVA_API int zstd_java_c_jobSize(void) {
    return (int) ZSTD_c_jobSize;
}

ZSTD_JAVA_API int zstd_java_c_targetLength(void) {
    return (int) ZSTD_c_targetLength;
}

ZSTD_JAVA_API int zstd_java_c_minMatch(void) {
    return (int) ZSTD_c_minMatch;
}

ZSTD_JAVA_API int zstd_java_c_searchLog(void) {
    return (int) ZSTD_c_searchLog;
}

ZSTD_JAVA_API int zstd_java_c_chainLog(void) {
    return (int) ZSTD_c_chainLog;
}

ZSTD_JAVA_API int zstd_java_c_hashLog(void) {
    return (int) ZSTD_c_hashLog;
}

ZSTD_JAVA_API int zstd_java_c_windowLog(void) {
    return (int) ZSTD_c_windowLog;
}

ZSTD_JAVA_API int zstd_java_c_strategy(void) {
    return (int) ZSTD_c_strategy;
}

ZSTD_JAVA_API int zstd_java_d_windowLogMax(void) {
    return (int) ZSTD_d_windowLogMax;
}

ZSTD_JAVA_API int zstd_java_d_format(void) {
    return (int) ZSTD_d_format;
}

ZSTD_JAVA_API int zstd_java_rmd_refMultipleDDicts(void) {
    return (int) ZSTD_rmd_refMultipleDDicts;
}

ZSTD_JAVA_API int zstd_java_rmd_refSingleDDict(void) {
    return (int) ZSTD_rmd_refSingleDDict;
}

ZSTD_JAVA_API int zstd_java_d_refMultipleDDicts(void) {
    return (int) ZSTD_d_refMultipleDDicts;
}

ZSTD_JAVA_API int zstd_java_c_validateSequences(void) {
    return (int) ZSTD_c_validateSequences;
}

ZSTD_JAVA_API int zstd_java_c_enableSeqProducerFallback(void) {
    return (int) ZSTD_c_enableSeqProducerFallback;
}

ZSTD_JAVA_API int zstd_java_c_searchForExternalRepcodes(void) {
    return (int) ZSTD_c_searchForExternalRepcodes;
}

ZSTD_JAVA_API int zstd_java_c_enableLongDistanceMatching(void) {
    return (int) ZSTD_c_enableLongDistanceMatching;
}

ZSTD_JAVA_API int zstd_java_WINDOWLOG_LIMIT_DEFAULT(void) {
    return (int) ZSTD_WINDOWLOG_LIMIT_DEFAULT;
}

ZSTD_JAVA_API int zstd_java_ps_enable(void) {
    return (int) ZSTD_ps_enable;
}

ZSTD_JAVA_API int zstd_java_ps_disable(void) {
    return (int) ZSTD_ps_disable;
}


/* JNI_ZSTD_decompressedSize (jni_zstd.c) verbatim, for FFM. The frame header
 * lives on the C stack, so Java needs no struct layout or buffer, and the
 * size_t return reproduces the JNI narrowing on 32-bit platforms. */
ZSTD_JAVA_API size_t zstd_java_decompressedSize(const void* buf, size_t bufSize, int magicless) {
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

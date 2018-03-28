cmake_minimum_required(VERSION 3.5)

# Override setting in Poky cmake.bbclass
# This allows us to search for javac in the host system root.
set(CMAKE_FIND_ROOT_PATH_MODE_PROGRAM BOTH)

# Detects Java home directory, based on javac location.
find_program(javac javac)
get_filename_component(JAVA_HOME ${javac} REALPATH)
get_filename_component(JAVA_HOME ${JAVA_HOME} DIRECTORY)
get_filename_component(JAVA_HOME ${JAVA_HOME}/.. ABSOLUTE)
message(STATUS "Java home: ${JAVA_HOME}")

set(SOURCES
  jni_directbuffercompress_zstd.c
  jni_directbufferdecompress_zstd.c
  jni_fast_zstd.c
  jni_inputstream_zstd.c
  jni_outputstream_zstd.c
  jni_zdict.c
  jni_zstd.c
  common/entropy_common.c
  common/error_private.c
  common/fse_decompress.c
  common/pool.c
  common/threading.c
  common/xxhash.c
  common/zstd_common.c
  compress/fse_compress.c
  compress/huf_compress.c
  compress/zstd_compress.c
  compress/zstd_double_fast.c
  compress/zstd_fast.c
  compress/zstd_lazy.c
  compress/zstd_ldm.c
  compress/zstdmt_compress.c
  compress/zstd_opt.c
  decompress/huf_decompress.c
  decompress/zstd_decompress.c
  dictBuilder/cover.c
  dictBuilder/divsufsort.c
  dictBuilder/zdict.c
  legacy/zstd_v04.c
  legacy/zstd_v05.c
  legacy/zstd_v06.c
  legacy/zstd_v07.c
)

add_library(zstd-jni SHARED ${SOURCES})

include_directories(${CMAKE_CURRENT_SOURCE_DIR}
  ${CMAKE_CURRENT_SOURCE_DIR}/common
  ${CMAKE_CURRENT_SOURCE_DIR}/legacy
  ${JAVA_HOME}/include ${JAVA_HOME}/include/linux)

target_compile_definitions(zstd-jni PRIVATE ZSTD_LEGACY_SUPPORT=4)

install(TARGETS zstd-jni LIBRARY DESTINATION lib)

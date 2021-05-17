#include <com_joprice_Basic.h>

JNIEXPORT jint JNICALL Java_com_joprice_Basic_compute(
    JNIEnv *env, jobject obj, jint a, jint b) {
  return a * b;
}


# Shrinker rules for apps that use zstd-jni. The Android Gradle Plugin applies them
# to any app that depends on the AAR and minifies with R8 or ProGuard.
#
# The native code finds the members below by name, so they must keep their names,
# and R8 must not remove them or assume their values: it cannot see that native
# code reads and writes them.

# JNI binds native methods by class and method name, and by parameter types for
# overloaded ones.
-keepclasseswithmembernames,includedescriptorclasses class com.github.luben.zstd.** {
    native <methods>;
}

# Fields the native code reads and writes with Get/Set<Type>Field: nativePtr on the
# dictionaries, consumed/produced on the buffer streams, srcPos/dstPos on
# ZstdInputStreamNoFinalizer and ZstdOutputStreamNoFinalizer.
-keepclassmembers class com.github.luben.zstd.** {
    long nativePtr;
    int consumed;
    int produced;
    long srcPos;
    long dstPos;
}

# ZstdCompressCtx.getFrameProgression() creates this in native code, finding the
# class and its constructor by name.
-keep class com.github.luben.zstd.ZstdFrameProgression {
    <init>(long, long, long, long, int, int);
}

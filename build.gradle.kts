import java.io.File

plugins {
    alias(libs.plugins.android.library)
}

version = rootProject.file("version").readText().trim()

val generatedSrcDir = File(layout.buildDirectory.get().asFile, "generated/main/java/")

android {
    namespace = "com.github.luben.zstd"
    compileSdk = 37
    ndkVersion = "28.2.13676358"

    defaultConfig {
        minSdk = 21
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    testOptions {
        targetSdk = 37
    }

    lint {
        targetSdk = 37
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    externalNativeBuild {
        cmake {
            path = file("CMakeLists.txt")
            version = "3.22.1"
        }
    }

    sourceSets {
        getByName("main") {
            java.directories.add(generatedSrcDir.absolutePath)
        }
    }
}

val generateZstdVersion = tasks.register("generateZstdVersion") {
    description = "Generates the ZstdVersion source file"
    doLast {
        ZstdVersionGenerator.generate(generatedSrcDir, version.toString())
    }
}

tasks.named("preBuild") {
    dependsOn(generateZstdVersion)
}

repositories {
    google()
    mavenCentral()
}

dependencies {
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}

object ZstdVersionGenerator {
    fun generate(outputDir: File, version: String) {
        val packageName = "com.github.luben.zstd.util"
        val packageDir = File(outputDir, packageName.replace(".", "/"))
        packageDir.mkdirs()
        File(packageDir, "ZstdVersion.java").writeText(
            "package $packageName;\n\npublic class ZstdVersion\n{\n\tpublic static final String VERSION = \"$version\";\n}\n"
        )
    }
}

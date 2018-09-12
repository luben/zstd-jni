package com.github.luben.zstd.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public enum Native {
    ;

    private static final String libnameShort = "zstd-jni";
    private static final String libname = "lib" + libnameShort;
    private static final String errorMsg = "Unsupported OS/arch, cannot find " +
        resourceName() + " or load " + libnameShort + " from system libraries. Please " +
        "try building from source the jar or providing " + libname + " in you system.";

    private static String osName() {
        final String os = System.getProperty("os.name").toLowerCase().replace(' ', '_');
        if (os.startsWith("win")){
            return "win";
        } else if (os.startsWith("mac")) {
            return "darwin";
        } else {
            return os;
        }
    }

    private static String osArch() {
        return System.getProperty("os.arch");
    }

    private static String libExtension() {
        if (osName().contains("os_x") || osName().contains("darwin")) {
            return "dylib";
         } else if (osName().contains("win")) {
            return "dll";
        } else {
            return "so";
        }
    }

    private static String resourceName() {
        return "/" + osName() + "/" + osArch() + "/" + libname + "." + libExtension();
    }

    private static boolean loaded = false;

    public static synchronized boolean isLoaded() {
        return loaded;
    }

    private static UnsatisfiedLinkError linkError(UnsatisfiedLinkError e) {
        final UnsatisfiedLinkError err = new UnsatisfiedLinkError(e.getMessage() + "\n" + errorMsg);
        err.setStackTrace(e.getStackTrace());
        return err;
    }

    public static synchronized void load() {
        load(null);
    }

    public static synchronized void load(final Path tempFolder) {
        if (loaded) {
            return;
        }

        // First attempt: Read zstd-jni native library from jar, write in temporary file, then load it
        Path tempLib = null;
        try {
            // Mark temporary file for deletion on Java VM exit as backup.
            // On Windows, once native library has been loaded, temp file can not be deleted in finally block.
            // However Windows user temp folder gets removed on logout - C:\Users\<userid>\AppData\Local\Temp\#\
            tempLib = (tempFolder == null) ?
                      Files.createTempFile(            libname, "." + libExtension()) :
                      Files.createTempFile(tempFolder, libname, "." + libExtension());
            tempLib.toFile().deleteOnExit();

            try (final InputStream  in  = Native.class.getResourceAsStream(resourceName());
                 final OutputStream out = Files.newOutputStream(tempLib)) {

                // Read from jar in chunks and write to temporary file.
                // Allocate 64 KiB buffer to maximize chunk size, around 16 to 32 KiB
                final byte[] buffer = new byte[65536];
                while (true) {
                    final int numBytes = in.read(buffer);
                    // System.out.format("Bytes read: %d\n", numBytes);
                    if (numBytes == -1) {
                        break;
                    }
                    out.write(buffer, 0, numBytes);
                }
                out.flush();
                out.close();

                System.load(tempLib.toAbsolutePath().toString());
                loaded = true;
            }
        }
        catch (final Throwable e) {
            // e.g. can't load the shared object from resources
        }
        finally {
            try {
                if (tempLib != null) {
                    Files.deleteIfExists(tempLib);
                }
            }
            catch (final IOException e) {
            }
        }

        // Second attempt: Fall back to loading the zstd-jni from the system library path
        if (! loaded) {
            try {
                System.loadLibrary(libnameShort);
                loaded = true;
            }
            catch (final UnsatisfiedLinkError e) {
                throw linkError(e);
            }
        }
    }
}

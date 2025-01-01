package com.github.luben.zstd.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.UnsatisfiedLinkError;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.concurrent.atomic.AtomicBoolean;

public enum Native {
    ;

    private static final String nativePathOverride = "ZstdNativePath";
    private static final String tempFolderOverride = "ZstdTempFolder";
    private static final String libnameShort = "zstd-jni-" + ZstdVersion.VERSION;
    private static final String libname = "lib" + libnameShort;
    private static final String errorMsg = "Unsupported OS/arch, cannot find " +
        resourceName() + " or load " + libnameShort + " from system libraries. Please " +
        "try building from source the jar or providing " + libname + " in your system.";

    private static String osName() {
        String os = System.getProperty("os.name").toLowerCase().replace(' ', '_');
        if (os.startsWith("win")){
            return "win";
        } else if (os.startsWith("mac")) {
            return "darwin";
        } else {
            return os;
        }
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
        String os = osName();
        String arch = System.getProperty("os.arch");
        if (os.equals("darwin") && arch.equals("amd64")) {
            arch = "x86_64";
        }
        return "/" + os + "/" + arch + "/" + libname + "." + libExtension();
    }

    private static AtomicBoolean loaded = new AtomicBoolean(false);

    /**
     * Tell the library to assume the native library is already loaded.
     * This is escape hatch for environments that have special requirements for how
     * the native part is loaded. This allows them to load the so/dll manually and tell
     * zstd-jni to not attempt loading it again.
     */
    public static synchronized void assumeLoaded() {
        loaded.set(true);
    }

    public static synchronized boolean isLoaded() {
        return loaded.get();
    }

    private static void loadLibrary(final String libName) {
        AccessController.doPrivileged(new PrivilegedAction<Void>() {
          public Void run() {
            System.loadLibrary(libName);
            return null;
          }
        });
    }

    private static void loadLibraryFile(final String libFileName) {
        AccessController.doPrivileged(new PrivilegedAction<Void>() {
          public Void run() {
            System.load(libFileName);
            return null;
          }
        });
    }

    public static synchronized void load() {
        String tempFolder = System.getProperty(tempFolderOverride);
        if (tempFolder == null) {
            load(null);
        } else {
            load(new File(tempFolder));
        }
    }

    public static synchronized void load(final File tempFolder) {
        if (loaded.get()) {
            return;
        }
        String resourceName = resourceName();

        String overridePath = System.getProperty(nativePathOverride);
        if (overridePath != null) {
            // Do not fall back to auto-discovery - consumers know better
            loadLibraryFile(overridePath);
            loaded.set(true);
            return;
        }

        // try to load the shared library directly from the JAR
        try {
            Class.forName("org.osgi.framework.BundleEvent"); // Simple OSGI env. check
            loadLibrary(libname);
            loaded.set(true);
            return;
        } catch (Throwable e) {
            // ignore both ClassNotFound and UnsatisfiedLinkError, and try other methods
        }

        InputStream is = Native.class.getResourceAsStream(resourceName);
        if (is == null) {
            // fallback to loading the zstd-jni from the system library path.
            // It also covers loading on Android.
            try {
                loadLibrary(libnameShort);
                loaded.set(true);
                return;
            } catch (UnsatisfiedLinkError e) {
                UnsatisfiedLinkError err = new UnsatisfiedLinkError(e.getMessage() + "\n" + errorMsg);
                err.setStackTrace(e.getStackTrace());
                throw err;
            }
        }
        File tempLib = null;
        FileOutputStream out = null;
        try {
            tempLib = File.createTempFile(libname, "." + libExtension(), tempFolder);
            // try to delete on exit, does not work on Windows
            tempLib.deleteOnExit();
            // copy to tempLib
            out = new FileOutputStream(tempLib);
            byte[] buf = new byte[4096];
            while (true) {
                int read = is.read(buf);
                if (read == -1) {
                    break;
                }
                out.write(buf, 0, read);
            }
            try {
                out.flush();
                out.close();
                out = null;
            } catch (IOException e) {
                // ignore
            }
            try {
                loadLibraryFile(tempLib.getAbsolutePath());
            } catch (UnsatisfiedLinkError e) {
                // fall-back to loading the zstd-jni from the system library path
                try {
                    loadLibrary(libnameShort);
                } catch (UnsatisfiedLinkError e1) {
                    // display error in case problem with loading from temp folder
                    // and from system library path - concatenate both messages
                    UnsatisfiedLinkError err = new UnsatisfiedLinkError(
                            e.getMessage() + "\n" +
                            e1.getMessage() + "\n"+
                            errorMsg);
                    err.setStackTrace(e1.getStackTrace());
                    throw err;
                }
            }
            loaded.set(true);
        } catch (IOException e) {
            // IO errors in extracting and writing the shared object in the temp dir
            ExceptionInInitializerError err = new ExceptionInInitializerError(
                    "Cannot unpack " + libname + ": " + e.getMessage());
            err.setStackTrace(e.getStackTrace());
            throw err;
        } finally {
            try {
                is.close();
                if (out != null) {
                    out.close();
                }
                if (tempLib != null && tempLib.exists()) {
                    tempLib.delete();
                }
            } catch (IOException e) {
                // ignore
            }
        }
    }
}

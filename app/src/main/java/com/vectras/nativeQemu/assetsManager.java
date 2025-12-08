package com.vectras.nativeQemu;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class assetsManager {


    /**
     * Helper to copy an asset to a file if it does not already exist.
     */
    private static void copyAssetIfNeeded(Context context, String assetPath, File outFile) throws IOException {
        if (outFile.exists()) {
            return;
        }
        File parent = outFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        InputStream is = context.getAssets().open(assetPath);
        FileOutputStream os = new FileOutputStream(outFile);
        byte[] buffer = new byte[8192];
        int read;
        while ((read = is.read(buffer)) != -1) {
            os.write(buffer, 0, read);
        }
        is.close();
        os.close();
    }

    /**
     * Delete all files inside a directory (non-recursive).
     */
    private static void clearDirectory(File dir) {
        if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    try {
                        f.delete();
                    } catch (Exception ignore) {}
                }
            }
        }
    }

    public static File installQemuBinary(Context context) throws IOException {
        // Target directory = /data/data/<pkg>/files/qemu/bin
        File binDir = new File(context.getFilesDir(), "qemu/bin");
        if (!binDir.exists()) {
            binDir.mkdirs();
        } else {
            clearDirectory(binDir);
        }

        // Detect architecture
        String arch = Build.SUPPORTED_ABIS != null && Build.SUPPORTED_ABIS.length > 0
                ? Build.SUPPORTED_ABIS[0]
                : "arm64-v8a"; // fallback

        String assetBase = "qemu/" + arch + "/bin";

        try {
            String[] bins = context.getAssets().list(assetBase);
            if (bins != null) {
                for (String bin : bins) {
                    File outFile = new File(binDir, bin);
                    try {
                        copyAssetIfNeeded(context, assetBase + "/" + bin, outFile);
                        outFile.setExecutable(true);
                    } catch (IOException e) {
                        Log.w("QEMU-BIN", "Failed to install " + bin + " from " + assetBase, e);
                    }
                }
            }
        } catch (IOException e) {
            Log.e("QEMU-BIN", "Failed to list " + assetBase, e);
        }

        return binDir;
    }

    /**
     * Install all QEMU-dependent shared libraries from assets/qemu/libs into
     * /data/data/<pkg>/files/qemu/libs, so that they can be found via LD_LIBRARY_PATH.
     *
     * The asset layout is expected to be:
     *   assets/qemu/libs/<libname>.so
     */
    public static File installQemuLibs(Context context) throws IOException {
        File libDir = new File(context.getFilesDir(), "qemu/libs");
        if (!libDir.exists()) {
            libDir.mkdirs();
        } else {
            clearDirectory(libDir);
        }

        // Detect architecture
        String arch = Build.SUPPORTED_ABIS != null && Build.SUPPORTED_ABIS.length > 0
                ? Build.SUPPORTED_ABIS[0]
                : "arm64-v8a"; // fallback

        String assetBase = "qemu/" + arch + "/libs";

        try {
            String[] libs = context.getAssets().list(assetBase);
            if (libs != null) {
                for (String lib : libs) {
                    File outFile = new File(libDir, lib);
                    try {
                        copyAssetIfNeeded(context, assetBase + "/" + lib, outFile);
                    } catch (IOException e) {
                        Log.w("QEMU-LIBS", "Failed to install " + lib + " from " + assetBase, e);
                    }
                }
            }
        } catch (IOException e) {
            Log.e("QEMU-LIBS", "Failed to list " + assetBase, e);
        }

        return libDir;
    }


    /**
     * Install all QEMU assets:
     *  - Clears and installs qemu/bin
     *  - Clears and installs qemu/libs
     *
     * Returns the base qemu directory.
     */
    public static File installQemuAll(Context context) throws IOException {
        // Install binaries
        File binDir = installQemuBinary(context);

        // Install libraries
        File libDir = installQemuLibs(context);

        File biosDir = installQemuBios(context);

        // Return the base qemu directory
        return new File(context.getFilesDir(), "qemu");
    }

    /**
     * Returns the full path to the installed QEMU/bin directory:
     *   /data/data/<pkg>/files/qemu/bin
     */
    public static File getQemuPath(Context context) {
        return new File(context.getFilesDir(), "qemu/bin");
    }

    /**
     * Install QEMU BIOS/pc-bios files from assets/qemu/pc-bios.zip into
     * /data/data/<pkg>/files/qemu/ (typically creating qemu/pc-bios/...).
     *
     * The ZIP is expected to contain paths like "pc-bios/OVMF_CODE.fd" etc.
     */
    public static File installQemuBios(Context context) throws IOException {
        File qemuDir = new File(context.getFilesDir(), "qemu");
        if (!qemuDir.exists()) {
            qemuDir.mkdirs();
        }

        // Optional: clean existing pc-bios directory to avoid stale files
        File biosDir = new File(qemuDir, "pc-bios");
        if (biosDir.exists()) {
            clearDirectory(biosDir);
        } else {
            biosDir.mkdirs();
        }

        InputStream is = context.getAssets().open("qemu/pc-bios.zip");
        ZipInputStream zis = new ZipInputStream(is);
        ZipEntry entry;
        byte[] buffer = new byte[8192];

        try {
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                // Ensure we always unpack under the qemu directory
                File outFile = new File(qemuDir, name);

                if (entry.isDirectory()) {
                    if (!outFile.exists()) {
                        outFile.mkdirs();
                    }
                } else {
                    File parent = outFile.getParentFile();
                    if (parent != null && !parent.exists()) {
                        parent.mkdirs();
                    }

                    FileOutputStream fos = new FileOutputStream(outFile);
                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        fos.write(buffer, 0, len);
                    }
                    fos.close();
                }

                zis.closeEntry();
            }
        } finally {
            zis.close();
        }

        return biosDir;
    }

}

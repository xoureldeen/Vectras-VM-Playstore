package com.vectras.nativeQemu;

import android.util.Log;

import com.vectras.vm.VectrasApp;

import java.io.File;
import java.util.Map;

public class processManager {



    /**
     * Result of a completed process execution.
     */
    public static class ProcessResult {
        public final int exitCode;
        public final String stdout;
        public final String stderr;

        public ProcessResult(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
        }

        @Override
        public String toString() {
            return "exitCode=" + exitCode + "\nstdout=\n" + stdout + "\nstderr=\n" + stderr;
        }
    }

    /**
     * Callback for asynchronous command execution.
     */
    public interface OutputCallback {
        /**
         * Called when a line is read from the process standard output.
         */
        void onStdoutLine(String line);

        /**
         * Called when a line is read from the process standard error.
         */
        void onStderrLine(String line);

        /**
         * Called when the process finishes.
         */
        void onComplete(int exitCode);
    }

    /**
     * Run a command directly on the Android host (no proot), blocking until completion.
     * This should NOT be called on the main/UI thread.
     *
     * @param command array of command and arguments, e.g. {"/system/bin/ls", "-l", "/sdcard"}
     * @return ProcessResult containing exit code, stdout and stderr
     * @throws Exception if the process fails to start or is interrupted
     */
    public static ProcessResult runNativeCommand(String[] command) throws Exception {
        return runNativeCommand(command, null);
    }

    /**
     * Run a command directly on the Android host (no proot), blocking until completion,
     * with an optional working directory.
     *
     * @param command    array of command and arguments
     * @param workingDir optional working directory, may be null
     * @return ProcessResult containing exit code, stdout and stderr
     * @throws Exception if the process fails to start or is interrupted
     */
    public static ProcessResult runNativeCommand(String[] command, File workingDir) throws Exception {
        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();

        ProcessBuilder pb = new ProcessBuilder(command);
        if (workingDir != null) {
            pb.directory(workingDir);
        }

        // Prepare LD_LIBRARY_PATH so that QEMU (or other native tools) can find their deps.
        String nativeLibDir = VectrasApp.getContext().getApplicationInfo().nativeLibraryDir;
        File qemuLibDir = new File(VectrasApp.getContext().getFilesDir(), "qemu/libs");
        StringBuilder ldPathBuilder = new StringBuilder();

        if (qemuLibDir.isDirectory()) {
            ldPathBuilder.append(qemuLibDir.getAbsolutePath());
        }
        if (nativeLibDir != null && !nativeLibDir.isEmpty()) {
            if (ldPathBuilder.length() > 0) {
                ldPathBuilder.append(":");
            }
            ldPathBuilder.append(nativeLibDir);
        }

        Map<String, String> env = pb.environment();
        String old = env.get("LD_LIBRARY_PATH");
        String newLd = ldPathBuilder.toString();
        if (old != null && !old.isEmpty()) {
            if (!newLd.isEmpty()) {
                newLd = newLd + ":" + old;
            } else {
                newLd = old;
            }
        }
        if (!newLd.isEmpty()) {
            env.put("LD_LIBRARY_PATH", newLd);
            Log.d("LDLIB", "LD_LIBRARY_PATH=" + newLd);
        }

        Process process = pb.start();

//        BufferedReader r = new BufferedReader(new InputStreamReader(process.getInputStream()));
////        String line1;
////        while ((line1 = r.readLine()) != null) {
////            Log.d("LDLIB", line1);
////        }

        try (
                java.io.BufferedReader outReader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(process.getInputStream())
                );
                java.io.BufferedReader errReader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(process.getErrorStream())
                )
        ) {
            String line;
            while ((line = outReader.readLine()) != null) {
                stdout.append(line).append('\n');
            }
            while ((line = errReader.readLine()) != null) {
                stderr.append(line).append('\n');
            }
        }

        int exitCode = process.waitFor();
        return new ProcessResult(exitCode, stdout.toString(), stderr.toString());
    }

    /**
     * Convenience method to run a shell command through /system/bin/sh -c "cmd".
     * This is useful when you want to use shell features like pipes and redirection.
     *
     * @param commandLine a full shell command line
     * @return ProcessResult
     * @throws Exception if the process fails to start or is interrupted
     */
    public static ProcessResult runShellCommand(String commandLine) throws Exception {
        String[] cmd = {"/system/bin/sh", "-c", commandLine};
        return runNativeCommand(cmd, null);
    }

    /**
     * Run a command asynchronously and receive output via callback.
     * This method creates a background thread so it is safe to call from the main thread.
     *
     * @param command  command + args
     * @param workingDir optional working directory, may be null
     * @param callback callback for output and completion (may be null)
     */
    public static void runNativeCommandAsync(final String[] command,
                                             final File workingDir,
                                             final OutputCallback callback) {
        new Thread(() -> {
            Process process = null;
            try {
                ProcessBuilder pb = new ProcessBuilder(command);
                if (workingDir != null) {
                    pb.directory(workingDir);
                }

                process = pb.start();

                java.io.BufferedReader outReader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(process.getInputStream())
                );
                java.io.BufferedReader errReader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(process.getErrorStream())
                );

                // Read stdout in a separate thread
                Thread stdoutThread = new Thread(() -> {
                    try {
                        String line;
                        while ((line = outReader.readLine()) != null) {
                            if (callback != null) {
                                callback.onStdoutLine(line);
                            }
                        }
                    } catch (Exception ignored) {
                    } finally {
                        try {
                            outReader.close();
                        } catch (Exception ignored) {
                        }
                    }
                });

                // Read stderr in a separate thread
                Thread stderrThread = new Thread(() -> {
                    try {
                        String line;
                        while ((line = errReader.readLine()) != null) {
                            if (callback != null) {
                                callback.onStderrLine(line);
                            }
                        }
                    } catch (Exception ignored) {
                    } finally {
                        try {
                            errReader.close();
                        } catch (Exception ignored) {
                        }
                    }
                });

                stdoutThread.start();
                stderrThread.start();

                int exitCode = process.waitFor();

                // Ensure output threads finish
                try {
                    stdoutThread.join();
                } catch (InterruptedException ignored) {
                }
                try {
                    stderrThread.join();
                } catch (InterruptedException ignored) {
                }

                if (callback != null) {
                    callback.onComplete(exitCode);
                }
            } catch (Exception e) {
                if (callback != null) {
                    callback.onStderrLine("Error starting native process: " + e.getMessage());
                    callback.onComplete(-1);
                }
            } finally {
                if (process != null) {
                    process.destroy();
                }
            }
        }, "NativeProcessThread").start();
    }

    /**
     * Convenience async wrapper for shell-style commands via /system/bin/sh -c.
     *
     * @param commandLine full shell command
     * @param callback    callback for output and completion
     */
    public static void runShellCommandAsync(final String commandLine,
                                            final OutputCallback callback) {
        String[] cmd = {"/system/bin/sh", "-c", commandLine};
        runNativeCommandAsync(cmd, null, callback);
    }
}

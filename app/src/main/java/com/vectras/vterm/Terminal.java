package com.vectras.vterm;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.vectras.vm.AppConfig;
import com.vectras.vm.VectrasApp;
import com.vectras.vm.VMManager;
import com.vectras.vm.utils.ClipboardUltils;
import com.vectras.vm.utils.DialogUtils;
import com.vectras.vm.utils.NotificationUtils;
import com.vectras.vm.R;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class Terminal {
    private static final String TAG = "Vectras-Terminal";
    private static Process qemuProcess;
    public String user = "root";
    public String DISPLAY = ":1";
    Context context;

    public Terminal(Context context) {
        this.context = context;
    }

    public static String getIPAddress(boolean useIPv4) {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                List<InetAddress> addrs = Collections.list(intf.getInetAddresses());
                for (InetAddress addr : addrs) {
                    if (!addr.isLoopbackAddress()) {
                        String sAddr = addr.getHostAddress();
                        boolean isIPv4 = sAddr.indexOf(':') < 0;
                        if (useIPv4) {
                            if (isIPv4)
                                return sAddr;
                        } else {
                            if (!isIPv4) {
                                int delim = sAddr.indexOf('%');
                                return delim < 0 ? sAddr.toUpperCase() : sAddr.substring(0, delim).toUpperCase();
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    public static String getDeviceIpAddress(Context context) {
        try {
            for (java.util.Enumeration<java.net.NetworkInterface> en = java.net.NetworkInterface.getNetworkInterfaces(); en.hasMoreElements(); ) {
                java.net.NetworkInterface networkInterface = en.nextElement();
                for (java.util.Enumeration<java.net.InetAddress> enumIpAddr = networkInterface.getInetAddresses(); enumIpAddr.hasMoreElements(); ) {
                    java.net.InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress() && inetAddress instanceof java.net.Inet4Address) {
                        return inetAddress.getHostAddress().toString();
                    }
                }
            }
        } catch (SocketException ex) {
            ex.printStackTrace();
        }
        return null;
    }

    private void showDialog(String message, Activity activity, String usercommand) {
        if (VMManager.isExecutedCommandError(usercommand, message, activity))
            return;

        DialogUtils.twoDialog(activity, "Execution Result", message, activity.getString(R.string.copy), activity.getString(R.string.close), true, R.drawable.round_terminal_24, true,
                () -> ClipboardUltils.copyToClipboard(activity, message), null, null);
    }

    // Method to execute the shell command
    public void executeShellCommand(String userCommand, boolean showResultDialog, boolean showProgressDialog, Activity dialogActivity) {
        executeShellCommand(userCommand, showResultDialog, showProgressDialog, dialogActivity.getString(R.string.executing_command_please_wait), dialogActivity);
    }

    public void executeShellCommand(String userCommand, boolean showResultDialog, boolean showProgressDialog, String progressDialogMessage, Activity dialogActivity) {
        StringBuilder output = new StringBuilder();
        StringBuilder errors = new StringBuilder();
        Log.d(TAG, "Executing natively: " + userCommand);
        com.vectras.vm.logger.VectrasStatus.logError("<font color='#4db6ac'>VTERM: >" + userCommand + "</font>");

        // Show ProgressDialog
        View progressView = LayoutInflater.from(dialogActivity).inflate(R.layout.dialog_progress_style, null);
        TextView progress_text = progressView.findViewById(R.id.progress_text);
        progress_text.setText(progressDialogMessage);
        AlertDialog progressDialog = new MaterialAlertDialogBuilder(dialogActivity, R.style.CenteredDialogTheme)
                .setView(progressView)
                .setCancelable(false)
                .create();

        if (showProgressDialog) progressDialog.show();

        new Thread(() -> {
            try {
                ProcessBuilder processBuilder = new ProcessBuilder();
                // Use the system's native shell to execute the command.
                String[] nativeCommand = {"/system/bin/sh", "-c", userCommand};
                processBuilder.command(nativeCommand);

                // Set environment variables
                Map<String, String> environment = processBuilder.environment();
                String qemuPath = context.getFilesDir().getPath() + "/qemu";
                environment.put("LD_LIBRARY_PATH", qemuPath + "/libs");
                environment.put("PATH", qemuPath + "/bin:" + System.getenv("PATH"));

                // Set a safe working directory, like the app's files directory.
                processBuilder.directory(context.getFilesDir());

                qemuProcess = processBuilder.start();

                BufferedReader reader = new BufferedReader(new InputStreamReader(qemuProcess.getInputStream()));
                BufferedReader errorReader = new BufferedReader(new InputStreamReader(qemuProcess.getErrorStream()));

                String line;
                while ((line = reader.readLine()) != null) {
                    Log.d(TAG, line);
                    com.vectras.vm.logger.VectrasStatus.logError("<font color='#4db6ac'>VTERM: >" + line + "</font>");
                    output.append(line).append("\n");
                }

                while ((line = errorReader.readLine()) != null) {
                    Log.w(TAG, line);
                    com.vectras.vm.logger.VectrasStatus.logError("<font color='red'>VTERM ERROR: >" + line + "</font>");
                    errors.append(line).append("\n");
                }

                int exitCode = qemuProcess.waitFor();
                if (exitCode != 0) {
                    errors.append("Execution finished with exit code: ").append(exitCode).append("\n");
                }
            } catch (IOException | InterruptedException e) {
                progressDialog.dismiss(); // Dismiss ProgressDialog
                errors.append(e.getMessage()).append("\n");
                errors.append(Log.getStackTraceString(e));
            } finally {
                new Handler(Looper.getMainLooper()).post(() -> {
                    progressDialog.dismiss(); // Dismiss ProgressDialog
                    AppConfig.temporaryLastedTerminalOutput = output.toString() + errors.toString();
                    if (showResultDialog) {
                        String finalOutput = output.toString();
                        String finalErrors = errors.toString();
                        showDialog(finalOutput.isEmpty() ? finalErrors : finalOutput, dialogActivity, userCommand);
                    }
                });
            }
        }).start();
    }

    public void executeShellCommand2(String userCommand, boolean showResultDialog, Activity dialogActivity) {
        StringBuilder output = new StringBuilder();
        StringBuilder errors = new StringBuilder();
        Log.d(TAG, "Executing natively: " + userCommand);
        com.vectras.vm.logger.VectrasStatus.logError("<font color='#4db6ac'>VTERM: >" + userCommand + "</font>");
        new Thread(() -> {
            try {
                ProcessBuilder processBuilder = new ProcessBuilder();
                // Use the system's native shell to execute the command.
                String[] nativeCommand = {"/system/bin/sh", "-c", userCommand};
                processBuilder.command(nativeCommand);

                // Set environment variables
                Map<String, String> environment = processBuilder.environment();
                String qemuPath = context.getFilesDir().getPath() + "/qemu";
                environment.put("LD_LIBRARY_PATH", qemuPath + "/libs");
                environment.put("PATH", qemuPath + "/bin:" + System.getenv("PATH"));

                // Set a safe working directory.
                processBuilder.directory(context.getFilesDir());
                qemuProcess = processBuilder.start();

                BufferedReader reader = new BufferedReader(new InputStreamReader(qemuProcess.getInputStream()));
                BufferedReader errorReader = new BufferedReader(new InputStreamReader(qemuProcess.getErrorStream()));

                String line;
                while ((line = reader.readLine()) != null) {
                    Log.d(TAG, line);
                    com.vectras.vm.logger.VectrasStatus.logError("<font color='#4db6ac'>VTERM: >" + line + "</font>");
                    output.append(line).append("\n");
                }

                while ((line = errorReader.readLine()) != null) {
                    Log.w(TAG, line);
                    com.vectras.vm.logger.VectrasStatus.logError("<font color='red'>VTERM ERROR: >" + line + "</font>");
                    errors.append(line).append("\n");
                }

                int exitCode = qemuProcess.waitFor();
                if (exitCode != 0) {
                    errors.append("Execution finished with exit code: ").append(exitCode).append("\n");
                } else {
                    output.append("Execution finished successfully.\n");
                }
            } catch (IOException | InterruptedException e) {
                errors.append(e.getMessage()).append("\n");
                errors.append(Log.getStackTraceString(e));
                NotificationUtils.clearAll(VectrasApp.getContext());
            } finally {
                new Handler(Looper.getMainLooper()).post(() -> {
                    AppConfig.temporaryLastedTerminalOutput = output.toString() + errors.toString();
                    if (showResultDialog) {
                        String finalOutput = output.toString();
                        String finalErrors = errors.toString();
                        showDialog(finalOutput.isEmpty() ? finalErrors : finalOutput, dialogActivity, userCommand);
                    }
                });
            }
        }).start();
    }

    public static String executeShellCommandWithResult(String userCommand, Context context) {
        StringBuilder output = new StringBuilder();
        StringBuilder errors = new StringBuilder();
        Log.d(TAG, "Executing natively: " + userCommand);
        com.vectras.vm.logger.VectrasStatus.logError("<font color='#4db6ac'>VTERM: >" + userCommand + "</font>");

        CountDownLatch latch = new CountDownLatch(1);

        new Thread(() -> {
            try {
                ProcessBuilder processBuilder = new ProcessBuilder();
                // Use the system's native shell to execute the command.
                String[] nativeCommand = {"/system/bin/sh", "-c", userCommand};
                processBuilder.command(nativeCommand);

                // Set environment variables
                Map<String, String> environment = processBuilder.environment();
                String qemuPath = context.getFilesDir().getPath() + "/qemu";
                environment.put("LD_LIBRARY_PATH", qemuPath + "/libs");
                environment.put("PATH", qemuPath + "/bin:" + System.getenv("PATH"));

                // Set a safe working directory.
                processBuilder.directory(context.getFilesDir());
                qemuProcess = processBuilder.start();

                BufferedReader reader = new BufferedReader(new InputStreamReader(qemuProcess.getInputStream()));
                BufferedReader errorReader = new BufferedReader(new InputStreamReader(qemuProcess.getErrorStream()));

                String line;
                while ((line = reader.readLine()) != null) {
                    Log.d(TAG, line);
                    output.append(line).append("\n");
                }

                while ((line = errorReader.readLine()) != null) {
                    Log.w(TAG, line);
                    errors.append(line).append("\n");
                }

                qemuProcess.waitFor();

            } catch (IOException | InterruptedException e) {
                errors.append(e.getMessage()).append("\n");
                errors.append(Log.getStackTraceString(e));
            } finally {
                latch.countDown();
            }
        }).start();

        try {
            // Wait for the background thread to finish execution
            latch.await(10, TimeUnit.SECONDS); // Adding a timeout to prevent indefinite blocking
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            errors.append("Command execution was interrupted.\n");
        }

        if (errors.length() > 0) {
            return errors.toString();
        }
        return output.toString().trim();
    }
}

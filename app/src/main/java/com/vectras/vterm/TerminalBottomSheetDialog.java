package com.vectras.vterm;

import android.app.Activity;
import android.content.Context;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.vectras.vm.R;
import com.vectras.vterm.view.ZoomableTextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.Map;
import java.util.Objects;

public class TerminalBottomSheetDialog {
    private final ZoomableTextView terminalOutput;
    private final EditText commandInput;
    private final View view;
    private final Activity activity;
    private final BottomSheetDialog bottomSheetDialog;
    LinearLayout inputContainer;
    boolean isAllowAddToResultCommand = true;

    public TerminalBottomSheetDialog(Activity activity) {
        this.activity = activity;

        bottomSheetDialog = new BottomSheetDialog(activity);
        view = activity.getLayoutInflater().inflate(R.layout.terminal_bottom_sheet, null);
        bottomSheetDialog.setContentView(view);

        terminalOutput = view.findViewById(R.id.tvTerminalOutput);
        commandInput = view.findViewById(R.id.etCommandInput);
        inputContainer = view.findViewById(R.id.ln_input);

        TextView tvPrompt = view.findViewById(R.id.tvPrompt);
        updateUserPrompt(tvPrompt);

        // Show the keyboard
        forcusCommandInput();

        // Whenever you modify the text of the EditText, do the following to ensure the cursor is at the end:
        commandInput.setSelection(commandInput.getText().length());

        // when user click terminal view will open keyboard
        terminalOutput.setOnClickListener(view -> {
            forcusCommandInput();
        });
        // Configure the editor to handle the "Done" action on the soft keyboard
        commandInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                executeShellCommand(commandInput.getText().toString());
                commandInput.setText("");
                commandInput.requestFocus();
                return true;
            }
            return false;
        });

        commandInput.setOnKeyListener((v, keyCode, event) -> {
            // If the event is a key-down event on the "enter" button
            if ((event.getAction() == KeyEvent.ACTION_DOWN) &&
                    (keyCode == KeyEvent.KEYCODE_ENTER)) {
                activity.runOnUiThread(() -> appendTextAndScroll("root@localhost:~$ " + commandInput.getText().toString() + "\n"));
                executeShellCommand(commandInput.getText().toString());
                commandInput.setText("");
                // Request focus again
                activity.runOnUiThread(commandInput::requestFocus);
                return true;
            }
            return false;
        });
    }

    public void showVterm() {
        bottomSheetDialog.show();
    }

    private void updateUserPrompt(TextView promptView) {
        // Run this in a separate thread to not block UI
        new Thread(() -> {
            String username = "root"; // Hardcoded for simplicity as per the original
            // Update the prompt on the UI thread
            activity.runOnUiThread(() -> promptView.setText(username + "@localhost:~$ "));
        }).start();
    }

    // Function to append text and automatically scroll to bottom
    private void appendTextAndScroll(String textToAdd) {
        ScrollView scrollView = view.findViewById(R.id.scrollView);

        // Update the text
        if (textToAdd.contains("@localhost:~$ exit")) {
            bottomSheetDialog.dismiss();
        } else if (textToAdd.contains("@localhost:~$ clear")) {
            isAllowAddToResultCommand = false;
            terminalOutput.setText("");
            terminalOutput.setVisibility(View.GONE);
        } else {
            if (isAllowAddToResultCommand) {
                terminalOutput.append(textToAdd);
            } else {
                isAllowAddToResultCommand = true;
            }
        }

        // Scroll to the bottom
        scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
    }

    private String getLocalIpAddress() {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements(); ) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements(); ) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress() && Objects.requireNonNull(inetAddress.getHostAddress()).contains(".")) {
                        return inetAddress.getHostAddress();
                    }
                }
            }
        } catch (SocketException ex) {
            ex.printStackTrace();
        }
        return null;
    }

    // Method to execute the shell command
    public void executeShellCommand(String userCommand) {
        if (checkInstallation())
            new Thread(() -> {
                try {
                    activity.runOnUiThread(() -> {
                        if (terminalOutput.getVisibility() == View.GONE)
                            terminalOutput.setVisibility(View.VISIBLE);
                        // The prompt is now added in the key listener, so we don't need to add it here again.
                        inputContainer.setVisibility(View.GONE);
                    });
                    // Setup the process builder to start a native shell process
                    ProcessBuilder processBuilder = new ProcessBuilder();

                    // Define the command to be executed by the system's native shell
                    String[] nativeCommand = {"/system/bin/sh", "-c", userCommand};
                    processBuilder.command(nativeCommand);

                    // Set environment variables for the process
                    Map<String, String> environment = processBuilder.environment();
                    String qemuPath = activity.getFilesDir().getPath() + "/qemu";
                    environment.put("LD_LIBRARY_PATH", qemuPath + "/libs");
                    environment.put("PATH", qemuPath + "/bin:" + System.getenv("PATH"));

                    // Set the working directory for the process
                    processBuilder.directory(activity.getFilesDir());

                    Process process = processBuilder.start();

                    // Get the input and output streams of the process
                    BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                    BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));

                    // This version doesn't write to stdin, so the writer is removed.

                    // Read the input stream for the output of the command
                    String line;
                    while ((line = reader.readLine()) != null) {
                        final String outputLine = line;
                        activity.runOnUiThread(() -> appendTextAndScroll(outputLine + "\n"));
                    }

                    // Read any errors from the error stream
                    while ((line = errorReader.readLine()) != null) {
                        final String errorLine = line;
                        activity.runOnUiThread(() -> appendTextAndScroll(errorLine + "\n"));
                    }

                    // Clean up
                    reader.close();
                    errorReader.close();

                    // Wait for the process to finish
                    process.waitFor();

                } catch (IOException | InterruptedException e) {
                    // Handle exceptions by printing the stack trace in the terminal output
                    final String errorMessage = e.getMessage();
                    activity.runOnUiThread(() -> appendTextAndScroll("Error: " + errorMessage + "\n"));
                } finally {
                    // Ensure the input container is visible and focused after execution
                    activity.runOnUiThread(() -> {
                        inputContainer.setVisibility(View.VISIBLE);
                        forcusCommandInput();
                    });
                }
            }).start(); // Execute the command in a separate thread to prevent blocking the UI thread
        else
            new AlertDialog.Builder(activity, R.style.MainDialogTheme)
                    .setTitle("Error!")
                    .setMessage("Installation not found. Verify that your setup process is working correctly.")
                    .setCancelable(true) // Allow user to dismiss
                    .setPositiveButton("OK", null)
                    .show();
    }

    private boolean checkInstallation() {
        // A more robust check might be for the qemu directory itself
        String filesDir = activity.getFilesDir().getAbsolutePath();
        File qemuDir = new File(filesDir, "qemu");
        return qemuDir.exists() && qemuDir.isDirectory();
    }

    private void forcusCommandInput() {
        commandInput.post(() -> {
            commandInput.requestFocus();
            InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showSoftInput(commandInput, InputMethodManager.SHOW_IMPLICIT);
        });
    }
}

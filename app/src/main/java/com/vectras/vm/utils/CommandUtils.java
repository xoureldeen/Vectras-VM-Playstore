package com.vectras.vm.utils;


import android.app.Activity;
import android.content.Context;
import android.util.Log;

import com.vectras.vm.VectrasApp;
import com.vectras.vterm.Terminal;
import com.vectras.nativeQemu.processManager;
import com.vectras.nativeQemu.assetsManager;

import java.io.File;

public class CommandUtils {
    public static String createForSelectedMirror(boolean _https, String _url, String _beforemain) {
        String command = "echo \"\" > /etc/apk/repositories && sed -i -e \"1ihttps://xssFjnj58Id/yttGkok69Je/edge/testing\" /etc/apk/repositories && sed -i -e \"1ihttps://xssFjnj58Id/yttGkok69Je/"
                + (DeviceUtils.is64bit() ? "v3.22" : "v3.21") + "/community\" /etc/apk/repositories && sed -i -e \"1ihttps://xssFjnj58Id/yttGkok69Je/v3.22/main\" /etc/apk/repositories";

        command = command.replaceAll("/yttGkok69Je", _beforemain);
        if (!_https)
            command = command.replaceAll("https://", "http://");
        return command.replaceAll("xssFjnj58Id", _url);
    }

    public static void run(String _command, boolean _isShowResult, Activity _activity) {
        Terminal vterm = new Terminal(_activity);
        vterm.executeShellCommand2(_command, _isShowResult, _activity);
    }

    public static String getQemuVersionName() {
        return getQemuVersion() + (is3dfxVersion() ? " - 3dfx" : "");
    }

    public static String getQemuVersion() {
        //processManager manager = null;
        // test here
        Context context = VectrasApp.getContext();
        try {
            assetsManager.installQemuAll(context);

            // Resolve the native qemu-system-x86_64 path
            File binDir = assetsManager.getQemuPath(context);
            File qemu = new File(binDir, "qemu-system-ppc");

            String[] cmd = {
                    qemu.getAbsolutePath(),
                    "--version"
            };

            processManager.ProcessResult result =
                    processManager.runNativeCommand(cmd);

            Log.d("QEMU", "aaaExit code: " + result.exitCode);
            Log.d("QEMU", "aaaSTDOUT:\n" + result.stdout);
            Log.d("QEMU", "aaaSTDERR:\n" + result.stderr);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }


        return VectrasApp.getContext() == null ? "Unknow" : Terminal.executeShellCommandWithResult("qemu-system-x86_64 --version | head -n1 | awk '{print $4}'", VectrasApp.getContext()).replaceAll("\n", "");
    }

    public static boolean is3dfxVersion() {
        return VectrasApp.getContext() != null && Terminal.executeShellCommandWithResult("qemu-system-x86_64 --version", VectrasApp.getContext()).contains("3dfx");
    }
}

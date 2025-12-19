package com.vectras.vm;

import static android.view.View.GONE;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.vectras.vm.utils.DialogUtils;
import com.vectras.vm.utils.FileUtils;
import com.vectras.vm.utils.UIUtils;

import java.io.File;
import java.util.Objects;

public class Minitools extends AppCompatActivity {
    private final String TAG = "Minitools";
    LinearLayout cleanup;
    LinearLayout restore;
    LinearLayout deleteallvm;
    LinearLayout deleteall;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_minitools);
//        UIUtils.setOnApplyWindowInsetsListener(findViewById(R.id.main));

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        toolbar.setTitle(getString(R.string.mini_tools));

        cleanup = findViewById(R.id.cleanup);
        restore = findViewById(R.id.restore);
        deleteallvm = findViewById(R.id.deleteallvm);
        deleteall = findViewById(R.id.deleteall);

        cleanup.setOnClickListener(v -> DialogUtils.twoDialog(Minitools.this, getResources().getString(R.string.clean_up), getResources().getString(R.string.clean_up_content), getResources().getString(R.string.clean_up), getResources().getString(R.string.cancel), true, R.drawable.cleaning_services_24px, true,
                this::cleanUp, null, null));

        restore.setOnClickListener(v -> DialogUtils.twoDialog(Minitools.this, getResources().getString(R.string.restore), getResources().getString(R.string.restore_content), getResources().getString(R.string.continuetext), getResources().getString(R.string.cancel), true, R.drawable.settings_backup_restore_24px, true,
                () -> {
                    VMManager.restoreVMs();
                    UIUtils.oneDialog(getResources().getString(R.string.done), getResources().getString(R.string.restored) + " " + VMManager.restoredVMs + ".", true, false, Minitools.this);
                    restore.setVisibility(GONE);
                }, null, null));

        deleteallvm.setOnClickListener(v -> DialogUtils.twoDialog(Minitools.this, getResources().getString(R.string.delete_all_vm), getResources().getString(R.string.delete_all_vm_content), getResources().getString(R.string.delete_all), getResources().getString(R.string.cancel), true, R.drawable.delete_24px, true,
                this::eraserAllVM, null, null));

        deleteall.setOnClickListener(v -> DialogUtils.twoDialog(Minitools.this, getResources().getString(R.string.delete_all), getResources().getString(R.string.delete_all_content), getResources().getString(R.string.delete_all), getResources().getString(R.string.cancel), true, R.drawable.delete_forever_24px, true,
                this::eraserData, null, null));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        return switch (item.getItemId()) {
            case 0 -> true;
            case android.R.id.home -> {
                finish();
                yield true;
            }
            default -> super.onOptionsItemSelected(item);
        };
    }

    private void cleanUp() {
        View progressView = LayoutInflater.from(this).inflate(R.layout.dialog_progress_style, null);
        TextView progress_text = progressView.findViewById(R.id.progress_text);
        progress_text.setText(getString(R.string.just_a_moment));
        AlertDialog progressDialog = new MaterialAlertDialogBuilder(this, R.style.CenteredDialogTheme)
                .setView(progressView)
                .setCancelable(false)
                .create();
        progressDialog.show();

        new Thread(() -> {
            VMManager.cleanUp();

            runOnUiThread(() -> {
                progressDialog.dismiss();
                Toast.makeText(getApplicationContext(), getResources().getString(R.string.done), Toast.LENGTH_LONG).show();
                restore.setVisibility(GONE);
                cleanup.setVisibility(GONE);
            });
        }).start();
    }

    private void eraserAllVM() {
        View progressView = LayoutInflater.from(this).inflate(R.layout.dialog_progress_style, null);
        TextView progress_text = progressView.findViewById(R.id.progress_text);
        progress_text.setText(getString(R.string.just_a_moment));
        AlertDialog progressDialog = new MaterialAlertDialogBuilder(this, R.style.CenteredDialogTheme)
                .setView(progressView)
                .setCancelable(false)
                .create();
        progressDialog.show();

        new Thread(() -> {
            VMManager.killallqemuprocesses(this);
            FileUtils.deleteDirectory(AppConfig.vmFolder);
            FileUtils.deleteDirectory(AppConfig.recyclebin);
            FileUtils.deleteDirectory(AppConfig.romsdatajson);
            File vDir = new File(AppConfig.maindirpath);
            if (!vDir.mkdirs()) Log.e(TAG, "Unable to create folder: " + AppConfig.maindirpath);
            FileUtils.writeToFile(AppConfig.maindirpath, "roms-data.json", "[]");

            runOnUiThread(() -> {
                progressDialog.dismiss();
                cleanup.setVisibility(GONE);
                restore.setVisibility(GONE);
                deleteallvm.setVisibility(GONE);
                Toast.makeText(this, getResources().getString(R.string.done), Toast.LENGTH_LONG).show();
            });
        }).start();
    }

    private void eraserData() {
        View progressView = LayoutInflater.from(this).inflate(R.layout.dialog_progress_style, null);
        TextView progress_text = progressView.findViewById(R.id.progress_text);
        progress_text.setText(getString(R.string.just_a_moment));
        AlertDialog progressDialog = new MaterialAlertDialogBuilder(this, R.style.CenteredDialogTheme)
                .setView(progressView)
                .setCancelable(false)
                .create();
        progressDialog.show();

        new Thread(() -> {
            VMManager.killallqemuprocesses(this);
            FileUtils.deleteDirectory(AppConfig.maindirpath);
            File vDir = new File(AppConfig.maindirpath);
            if (!vDir.mkdirs()) Log.e(TAG, "Unable to create folder: " + AppConfig.maindirpath);
            FileUtils.writeToFile(AppConfig.maindirpath, "roms-data.json", "[]");

           runOnUiThread(() -> {
                progressDialog.dismiss();
                cleanup.setVisibility(GONE);
                restore.setVisibility(GONE);
                deleteallvm.setVisibility(GONE);
                deleteall.setVisibility(GONE);
                Toast.makeText(this, getResources().getString(R.string.done), Toast.LENGTH_LONG).show();
            });
        }).start();
    }
}
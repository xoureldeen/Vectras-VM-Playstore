package com.vectras.vm.setupwizard;

import static android.content.Intent.ACTION_VIEW;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.transition.TransitionManager;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import com.vectras.nativeQemu.assetsManager;
import com.vectras.vm.AppConfig;
import com.vectras.vm.R;
import com.vectras.vm.databinding.ActivitySetupWizard2Binding;
import com.vectras.vm.databinding.SetupQemuDoneBinding;
import com.vectras.vm.home.HomeActivity;
import com.vectras.vm.utils.DeviceUtils;
import com.vectras.vm.utils.FileUtils;
import com.vectras.vm.utils.PermissionUtils;
import com.vectras.vm.utils.UIUtils;

import java.io.IOException;

public class SetupWizard2Activity extends AppCompatActivity {
    ActivitySetupWizard2Binding binding;
    SetupQemuDoneBinding bindingFinalSteps;
    final int STEP_PRIVACY_POLICY = 10;
    final int STEP_TERM_OF_SERVICE = 11;
    final int STEP_EXTRACTING_SYSTEM_FILES = 2;
    final int STEP_REQUEST_PERMISSION = 1;
    final int STEP_GETTING_DATA = 3;
    final int STEP_SETUP_OPTIONS = 4;
    final int STEP_INSTALLING_PACKAGES = 5;
    final int STEP_ERROR = 6;
    final int STEP_JOIN_COMMUNITY = 20;
    final int STEP_PATERON = 21;
    final int STEP_FINISH = 22;
    int currentStep = 0;
    String logs = "";
    boolean isExecuting = false;
    boolean aria2Error = false;
    boolean isServerError = false;
    boolean isPrivacyPolicyLoadFailed = false;
    boolean isTermsOfServiceLoadFailed = false;
    boolean isNotEnoughStorageSpace = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UIUtils.edgeToEdge(this);
        binding = ActivitySetupWizard2Binding.inflate(getLayoutInflater());
        bindingFinalSteps = binding.layoutFinalSteps;
        setContentView(binding.getRoot());
        UIUtils.setOnApplyWindowInsetsListener(findViewById(R.id.main));

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (currentStep > STEP_JOIN_COMMUNITY) {
                    uiControllerFinalSteps(currentStep - 1);
                } else if (!isExecuting) {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });

        initialize();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (currentStep == 1 && PermissionUtils.storagepermission(this, false)) {
            uiController(STEP_JOIN_COMMUNITY);
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        loadingIndicatorController(currentStep);
    }

    private void initialize() {
        bindingFinalSteps.main.setVisibility(View.GONE);

        if (!DeviceUtils.is64bit()) binding.ln32BitWarning.setVisibility(View.VISIBLE);

        binding.btnLetStart.setOnClickListener(v -> uiController(STEP_PRIVACY_POLICY));

        binding.btnPrivacyPolicy.setOnClickListener(v -> uiController(STEP_TERM_OF_SERVICE));

        binding.btnTermsOfService.setOnClickListener(v -> extractSystemFiles());

        binding.btnAllowPermission.setOnClickListener(v -> PermissionUtils.requestStoragePermission(this));

        binding.btnTryAgain.setOnClickListener(v -> {
            if (SetupFeatureCore.isInstalledSystemFiles(this)) {
                extractSystemFiles();
            } else if (isPrivacyPolicyLoadFailed) {
                isPrivacyPolicyLoadFailed = false;
                uiController(STEP_PRIVACY_POLICY);
            } else if (isTermsOfServiceLoadFailed) {
                isTermsOfServiceLoadFailed = false;
                uiController(STEP_TERM_OF_SERVICE);
            }
        });

        //Final steps
        bindingFinalSteps.tvLater.setOnClickListener(v -> uiControllerFinalSteps(currentStep + 1));

        bindingFinalSteps.btnContinue.setOnClickListener(v -> {
            if (currentStep == STEP_JOIN_COMMUNITY) {
                uiControllerFinalSteps(currentStep + 1);
                Intent intent = new Intent(ACTION_VIEW, Uri.parse(AppConfig.telegramLink));
                startActivity(intent);
                //Don't show join Telegram dialog again
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
                SharedPreferences.Editor edit = prefs.edit();
                edit.putBoolean("tgDialog", true);
                edit.apply();
            } else if (currentStep == STEP_PATERON) {
                uiControllerFinalSteps(currentStep + 1);
                Intent intent = new Intent(ACTION_VIEW, Uri.parse(AppConfig.patreonLink));
                startActivity(intent);
            } else {
                startActivity(new Intent(this, HomeActivity.class));
                finish();
            }
        });
    }

    private void uiController(int step) {
        uiController(step, "");
    }

    private void uiController(int step, String log) {
        TransitionManager.beginDelayedTransition(binding.main);

        binding.lnWelcome.setVisibility(View.GONE);
        binding.lnPrivacyPolicy.setVisibility(View.GONE);
        binding.lnTermsOfService.setVisibility(View.GONE);
        binding.lnAllowPermission.setVisibility(View.GONE);
        binding.lnExtractingSystemFiles.setVisibility(View.GONE);
        binding.lnGettingData.setVisibility(View.GONE);
        binding.lnSetupOptions.setVisibility(View.GONE);
        binding.lnInstallingPackages.setVisibility(View.GONE);
        binding.lnInstallingPackagesFailed.setVisibility(View.GONE);

        TransitionManager.beginDelayedTransition(binding.main);

        if (step == STEP_PRIVACY_POLICY) {
            binding.lnPrivacyPolicy.setVisibility(View.VISIBLE);
            try {
                binding.tvPrivacyPolicyContent.setText(FileUtils.readTextFileFromRaw(this, R.raw.privacypolicy));
            } catch (Exception e) {
                isPrivacyPolicyLoadFailed = true;
                logs = e.toString();
                uiController(STEP_ERROR);
            }
        } else if (step == STEP_TERM_OF_SERVICE) {
            binding.lnTermsOfService.setVisibility(View.VISIBLE);
            try {
                binding.tvTermsOfServiceContent.setText(FileUtils.readTextFileFromRaw(this, R.raw.termsofservice));
            } catch (Exception e) {
                isTermsOfServiceLoadFailed = true;
                logs = e.toString();
                uiController(STEP_ERROR);
            }
        } else if (step == STEP_REQUEST_PERMISSION) {
            binding.lnAllowPermission.setVisibility(View.VISIBLE);
        } else if (step == STEP_EXTRACTING_SYSTEM_FILES) {
            binding.lnExtractingSystemFiles.setVisibility(View.VISIBLE);
        } else if (step == STEP_GETTING_DATA) {
            binding.lnGettingData.setVisibility(View.VISIBLE);
        } else if (step == STEP_SETUP_OPTIONS) {
            binding.lnSetupOptions.setVisibility(View.VISIBLE);
        } else if (step == STEP_INSTALLING_PACKAGES) {
            binding.lnInstallingPackages.setVisibility(View.VISIBLE);
        } else if (step == STEP_ERROR) {
            binding.lnInstallingPackagesFailed.setVisibility(View.VISIBLE);
            binding.tvErrorLogContent.setText(log.isEmpty() ? getString(R.string.there_are_no_logs) : log);

            if (isNotEnoughStorageSpace) {
                binding.ivErrorLarge.setImageResource(R.drawable.disc_full_100px);
                binding.tvErrorTitle.setText(getString(R.string.not_enough_storage_space));
                binding.tvErrorSubtitle.setText(getString(R.string.not_enough_storage_to_set_up_content));
                binding.btnTryAgain.setText(getString(R.string.try_again));
            } else if (isServerError || aria2Error) {
                binding.ivErrorLarge.setImageResource(R.drawable.android_wifi_3_bar_alert_100px);
                binding.tvErrorTitle.setText(getString(R.string.unable_to_connect_to_server));
                binding.tvErrorSubtitle.setText(getString(R.string.check_your_internet_connection));
            } else {
                binding.ivErrorLarge.setImageResource(R.drawable.error_96px);
                binding.tvErrorTitle.setText(getString(R.string.something_went_wrong));
                binding.tvErrorSubtitle.setText(getString(R.string.the_setup_could_not_be_completed_and_below_is_the_log));
            }
        } else if (step == STEP_JOIN_COMMUNITY) {
            bindingFinalSteps.main.setVisibility(View.VISIBLE);
        }

        loadingIndicatorController(step);

        currentStep = step;
    }

    private void loadingIndicatorController(int step) {
        float dp = 200f;
        float px = dp * getResources().getDisplayMetrics().density;

        if (step == STEP_EXTRACTING_SYSTEM_FILES) {
            binding.lnExtractingSystemFilesCpiContainer.post(() -> {
                int heightPx = binding.lnExtractingSystemFilesCpiContainer.getHeight();

                if (heightPx < px) {
                    binding.cpiExtractingSystemFiles.setVisibility(View.GONE);
                    binding.lpiExtractingSystemFiles.setVisibility(View.VISIBLE);
                } else {
                    binding.cpiExtractingSystemFiles.setVisibility(View.VISIBLE);
                    binding.lpiExtractingSystemFiles.setVisibility(View.GONE);
                }
            });
        } else if (step == STEP_GETTING_DATA) {
            binding.lnGettingDataCpiContainer.post(() -> {
                int heightPx = binding.lnGettingDataCpiContainer.getHeight();

                if (heightPx < px) {
                    binding.cpiGettingData.setVisibility(View.GONE);
                    binding.lpiGettingData.setVisibility(View.VISIBLE);
                } else {
                    binding.cpiGettingData.setVisibility(View.VISIBLE);
                    binding.lpiGettingData.setVisibility(View.GONE);
                }
            });
        } else if (step == STEP_INSTALLING_PACKAGES) {
            binding.lnInstallingPackagesCpiContainer.post(() -> {
                int heightPx = binding.lnInstallingPackagesCpiContainer.getHeight();

                if (heightPx < px) {
                    binding.cpiInstallingPackages.setVisibility(View.GONE);
                    binding.lpiInstallingPackages.setVisibility(View.VISIBLE);
                } else {
                    binding.cpiInstallingPackages.setVisibility(View.VISIBLE);
                    binding.lpiInstallingPackages.setVisibility(View.GONE);
                }
            });
        }
    }

    private void uiControllerFinalSteps(int step) {
        TransitionManager.beginDelayedTransition(bindingFinalSteps.mainContent);

        bindingFinalSteps.linearcommunity.setVisibility(View.GONE);
        bindingFinalSteps.lineardonate.setVisibility(View.GONE);
        bindingFinalSteps.linearwelcomehome.setVisibility(View.GONE);

        TransitionManager.beginDelayedTransition(bindingFinalSteps.mainContent);

        if (step == STEP_JOIN_COMMUNITY) {
            bindingFinalSteps.linearcommunity.setVisibility(View.VISIBLE);
            bindingFinalSteps.tvLater.setVisibility(View.VISIBLE);
            bindingFinalSteps.btnContinue.setText(getString(R.string.join));
        } else if (step == STEP_PATERON) {
            bindingFinalSteps.lineardonate.setVisibility(View.VISIBLE);
            bindingFinalSteps.tvLater.setVisibility(View.VISIBLE);
            bindingFinalSteps.btnContinue.setText(getString(R.string.join));
        } else if (step == STEP_FINISH) {
            bindingFinalSteps.linearwelcomehome.setVisibility(View.VISIBLE);
            bindingFinalSteps.tvLater.setVisibility(View.GONE);
            bindingFinalSteps.btnContinue.setText(getString(R.string.done));
        }

        currentStep = step;
    }

    private void extractSystemFiles() {
        isExecuting = true;
        isNotEnoughStorageSpace = DeviceUtils.isStorageLow(this, false);

        if (isNotEnoughStorageSpace) {
            uiController(STEP_ERROR);
            return;
        } else {
            uiController(STEP_EXTRACTING_SYSTEM_FILES);
        }

        new Thread(() -> {
            boolean result;

            try {
                assetsManager.installQemuAll(this);
                result = true;
            } catch (IOException e) {
                result = false;
                logs = e.toString();
            }

            boolean finalResult = result;
            runOnUiThread(() -> new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (finalResult) {
                    uiController(STEP_JOIN_COMMUNITY);
                } else {
                    uiController(STEP_ERROR, getString(R.string.system_files_installation_failed_content) + (!SetupFeatureCore.lastErrorLog.isEmpty() ? "\n\n" + SetupFeatureCore.lastErrorLog : ""));
                }
                isExecuting = false;
            }, 500));
        }).start();
    }
}
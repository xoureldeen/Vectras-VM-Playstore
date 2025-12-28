package com.vectras.vm;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;

import com.google.android.material.color.DynamicColors;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.vectras.qemu.Config;
import com.vectras.qemu.MainSettingsManager;
import com.vectras.vm.utils.PackageUtils;
import com.vectras.vm.utils.UIUtils;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.Locale;
import java.util.Objects;

public class VectrasApp extends Application {
	public static VectrasApp vectrasapp;
	private static WeakReference<Context> context;

	public static Context getContext() {
		return context.get();
	}

	@Override
	public void onCreate() {
		super.onCreate();
		vectrasapp = this;
		context = new WeakReference<>(getApplicationContext());
		Thread.setDefaultUncaughtExceptionHandler(
				new com.vectras.vm.crashtracker.CrashHandler(this)
		);
        setupTheme();

		Locale locale = Locale.getDefault();
		String language = locale.getLanguage();

//		if (language.contains("ar")) {
//			overrideFont("DEFAULT", R.font.cairo_regular);
//		} else {
//			overrideFont("DEFAULT", R.font.gilroy);
//		}
		setupAppConfig(getApplicationContext());

        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityPreCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
                if (MainSettingsManager.getDynamicColor(activity))
                    DynamicColors.applyToActivityIfAvailable(activity);
            }

            @Override public void onActivityCreated(@NonNull Activity activity, Bundle savedInstanceState) {}
            @Override public void onActivityStarted(@NonNull Activity activity) {}
            @Override public void onActivityResumed(@NonNull Activity activity) {}
            @Override public void onActivityPaused(@NonNull Activity activity) {}
            @Override public void onActivityStopped(@NonNull Activity activity) {}
            @Override public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {}
            @Override public void onActivityDestroyed(@NonNull Activity activity) {}
        });

        FirebaseAnalytics.getInstance(this);
	}

	public void overrideFont(String defaultFontNameToOverride, int customFontResourceId) {
		try {
			Typeface customFontTypeface = ResourcesCompat.getFont(getApplicationContext(), customFontResourceId);

			final Field defaultFontTypefaceField = Typeface.class.getDeclaredField(defaultFontNameToOverride);
			defaultFontTypefaceField.setAccessible(true);
			defaultFontTypefaceField.set(null, customFontTypeface);
		} catch (Exception e) {
			Log.e("overrideFont", "Failed to override font", e);
		}
	}

	private void setupTheme() {
        UIUtils.setDarkOrLight(MainSettingsManager.getTheme(this));

//        if (MainSettingsManager.getDynamicColor(this))
//            DynamicColors.applyToActivitiesIfAvailable(this);

//        setTheme(R.style.AppTheme);
	}

	private void setupAppConfig(Context _context) {
		AppConfig.vectrasVersion = PackageUtils.getThisVersionName(_context);
		AppConfig.vectrasVersionCode = PackageUtils.getThisVersionCode(_context);
		AppConfig.internalDataDirPath = getFilesDir().getPath() + "/";
		AppConfig.basefiledir = AppConfig.datadirpath(_context) + "/.qemu/";
		AppConfig.maindirpath = Objects.requireNonNull(getExternalFilesDir("VectrasVM")).getAbsolutePath() + "/";
		AppConfig.sharedFolder = AppConfig.maindirpath + "SharedFolder/";
		AppConfig.downloadsFolder = AppConfig.maindirpath + "Downloads/";
		AppConfig.romsdatajson = AppConfig.maindirpath + "roms-data.json";
		AppConfig.vmFolder = AppConfig.maindirpath + "roms/";
		AppConfig.recyclebin = AppConfig.maindirpath + "recyclebin/";
        AppConfig.cvbiFolder = AppConfig.maindirpath + "cvbi/";
		AppConfig.lastCrashLogPath = AppConfig.internalDataDirPath + "logs/lastcrash.txt";

        Config.cacheDir = _context.getCacheDir().getAbsolutePath();
        Config.storagedir = Environment.getExternalStorageDirectory().toString();
	}
}

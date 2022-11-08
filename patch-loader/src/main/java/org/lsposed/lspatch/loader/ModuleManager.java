package org.lsposed.lspatch.loader;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import org.lsposed.lspatch.util.ModuleLoader;
import org.lsposed.lspd.models.Module;

import java.util.ArrayList;
import java.util.List;

public class ModuleManager {
    private static final String TAG = LSPApplication.TAG;
    private static final String TARGET_PACKAGE = "com.facebook.orca";

    private static final long MODULE_MIN = 240;
    private static final String MODULE_NAME = "ChatHeadEnabler";
    private static final String MODULE_PACKAGE = "app.neonorbit.chatheadenabler";

    public static final List<Module> MODULES = new ArrayList<>(1);
    public static final String MODULE_SHORTCUT_ID = "mrv-module-shortcut";
    public static final List<ShortcutInfo> MODULE_SHORTCUT_INFO = new ArrayList<>(1);

    public static boolean hasModule = false;
    public static boolean isInitialized = false;

    public static void init(Context context) {
        if (ModuleManager.isInitialized) return;
        if (isValid(context) && loadModules(context)) {
            ModuleManager.hasModule = true;
            setupModuleShortcut(context);
        }
        ModuleManager.isInitialized = true;
    }

    private static boolean isValid(Context context) {
        return context.getPackageName().equals(TARGET_PACKAGE);
    }

    private static boolean loadModules(Context context) {
        String modulePath = getModulePath(context);
        if (modulePath.isEmpty()) {
            Log.w(TAG, "Couldn't detect " + MODULE_NAME + " module");
            Toast.makeText(context, "Please install " + MODULE_NAME, Toast.LENGTH_LONG).show();
            return false;
        }
        var file = ModuleLoader.loadModule(modulePath);
        if (file != null) {
            var module = new Module();
            module.apkPath = modulePath;
            module.packageName = MODULE_PACKAGE;
            module.file = file;
            MODULES.add(module);
            return true;
        }
        Log.w(TAG, "Failed to preload module apk");
        Toast.makeText(context, "Failed to load " + MODULE_NAME, Toast.LENGTH_LONG).show();
        return false;
    }

    private static String getModulePath(Context context) {
        PackageManager pm = context.getPackageManager();
        PackageInfo packageInfo = null;
        try {
            packageInfo = pm.getPackageInfo(MODULE_PACKAGE, 0);
        } catch (Throwable t) {
            Log.w(TAG, "Couldn't get packageInfo: " + t.getMessage());
        }
        if (packageInfo == null || !packageInfo.applicationInfo.enabled) {
            return "";
        } else if (packageInfo.getLongVersionCode() < MODULE_MIN) {
            Log.w(TAG, MODULE_NAME + " is outdated: " + packageInfo.getLongVersionCode());
            Toast.makeText(context, "Please update " + MODULE_NAME, Toast.LENGTH_LONG).show();
        }
        String apkPath = packageInfo.applicationInfo.publicSourceDir;
        if (TextUtils.isEmpty(apkPath)) {
            apkPath = packageInfo.applicationInfo.sourceDir;
        }
        Log.i(TAG, "Installed module path: " + apkPath);
        return apkPath;
    }

    private static void setupModuleShortcut(Context context) {
        try {
            MODULE_SHORTCUT_INFO.clear();
            var uri = Uri.fromParts("package", MODULE_PACKAGE, null);
            var intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri);
            var shortcutIcon = Icon.createWithResource(context, android.R.drawable.ic_menu_sort_by_size);
            var shortcutInfo = new ShortcutInfo.Builder(context, MODULE_SHORTCUT_ID)
                .setShortLabel(MODULE_NAME)
                .setLongLabel(MODULE_NAME)
                .setIcon(shortcutIcon)
                .setIntent(intent)
                .build();
            MODULE_SHORTCUT_INFO.add(shortcutInfo);
            context.getSystemService(ShortcutManager.class).addDynamicShortcuts(MODULE_SHORTCUT_INFO);
        } catch (Throwable t) {
            Log.w(TAG, "Failed to setup module shortcut: " + t.getMessage());
        }
    }
}

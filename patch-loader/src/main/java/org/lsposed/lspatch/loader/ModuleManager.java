package org.lsposed.lspatch.loader;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import org.lsposed.lspatch.share.ConstantsM;
import org.lsposed.lspatch.util.ModuleLoader;
import org.lsposed.lspd.models.Module;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

public class ModuleManager {
    private static final String TAG = LSPApplication.TAG;

    private static final long DEF_MODULE_MIN = 240;
    private static final String DEF_MODULE_NAME = "ChatHeadEnabler";
    private static final String DEF_MODULE_PACKAGE = "app.neonorbit.chatheadenabler";

    private static final HashMap<String, Module> MODULES = new LinkedHashMap<>(2);

    private static boolean moduleLoaded = false;
    private static boolean isInitialized = false;

    public static void load(Context context) {
        if (ModuleManager.isInitialized) return;
        if (isValid(context) && loadModules(context)) {
            ModuleManager.moduleLoaded = true;
            Log.i(TAG, "Modules initialized");
        }
        ModuleManager.isInitialized = true;
    }

    public static boolean isModuleLoaded() {
        return moduleLoaded;
    }

    public static List<Module> getPreloadedModules() {
        return List.copyOf(MODULES.values());
    }

    public static boolean isDefaultModuleLoaded() {
        return MODULES.containsKey(DEF_MODULE_PACKAGE);
    }

    private static boolean isValid(Context context) {
        return LSPApplication.config.targetAll() || ConstantsM.isTargetPackage(context.getPackageName());
    }

    private static boolean loadModules(Context context) {
        loadDefaultModule(context);
        loadExtraModules(context);
        return !MODULES.isEmpty();
    }

    private static void loadDefaultModule(Context context) {
        boolean defOnly = !LSPApplication.config.hasExtraModules();
        String path = getModulePath(context, DEF_MODULE_PACKAGE);
        if (path.isEmpty() && defOnly) {
            Toast.makeText(context, "Please install " + DEF_MODULE_NAME, Toast.LENGTH_LONG).show();
            return;
        }
        loadModuleApk(DEF_MODULE_PACKAGE, path);
        if (defOnly && MODULES.isEmpty()) {
            Toast.makeText(context, "Failed to load " + DEF_MODULE_NAME, Toast.LENGTH_LONG).show();
        }
    }

    private static void loadExtraModules(Context context) {
        if (LSPApplication.config.hasExtraModules()) {
            for (String pkg : LSPApplication.config.exModules) {
                loadModuleApk(pkg, getModulePath(context, pkg));
            }
        }
    }

    private static void loadModuleApk(String pkg, String path) {
        if (pkg.isEmpty() || path.isEmpty() || MODULES.containsKey(pkg)) return;
        var file = ModuleLoader.loadModule(path);
        if (file != null) {
            var module = new Module();
            module.apkPath = path;
            module.packageName = pkg;
            module.file = file;
            MODULES.putIfAbsent(pkg, module);
        } else {
            Log.w(TAG, "Failed to preload module apk: " + pkg);
        }
    }

    private static String getModulePath(Context context, String pkg) {
        PackageManager pm = context.getPackageManager();
        PackageInfo packageInfo = null;
        try {
            packageInfo = pm.getPackageInfo(pkg, 0);
        } catch (PackageManager.NameNotFoundException ignore) { }
        if (packageInfo == null || !packageInfo.applicationInfo.enabled) {
            Log.w(TAG, "Module not found: " + pkg);
            return "";
        }
        checkMinVersionIfDefault(context, packageInfo, pkg);
        String apkPath = packageInfo.applicationInfo.publicSourceDir;
        if (TextUtils.isEmpty(apkPath)) {
            apkPath = packageInfo.applicationInfo.sourceDir;
        }
        Log.i(TAG, "Module found [" + pkg + "]: " + apkPath);
        return apkPath;
    }

    private static void checkMinVersionIfDefault(Context context, PackageInfo pInfo, String pkg) {
        if (pkg.equals(DEF_MODULE_PACKAGE) && pInfo.getLongVersionCode() < DEF_MODULE_MIN) {
            Log.w(TAG, DEF_MODULE_NAME + " is outdated: " + pInfo.getLongVersionCode());
            Toast.makeText(context, "Please update " + DEF_MODULE_NAME, Toast.LENGTH_LONG).show();
        }
    }
}

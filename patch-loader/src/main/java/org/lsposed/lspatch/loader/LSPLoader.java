package org.lsposed.lspatch.loader;

import android.app.ActivityThread;
import android.app.LoadedApk;
import android.content.Context;

import org.lsposed.lspatch.loader.hook.AppSignatureHook;
import org.lsposed.lspatch.loader.hook.AppSpecifiedHook;
import org.lsposed.lspatch.loader.hook.PackageMaskHook;
import org.lsposed.lspatch.loader.hook.PermissionMaskHook;
import org.lsposed.lspatch.share.ConstantsM;
import org.lsposed.lspd.core.Startup;

import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedInit;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class LSPLoader {
    public static void init(Context context) {
        var service = new LSPAppService();
        var process = ActivityThread.currentProcessName();
        var dataDir = context.getApplicationInfo().dataDir;
        try {  // Bypass initXResources
            XposedBridge.dummyClassLoader = new ClassLoader() {};
        } catch (Throwable ignored) {}
        Startup.initXposed(false, process, dataDir, service);
        XposedInit.disableResources = true;
        XposedInit.loadedPackagesInProcess.add(context.getPackageName());
    }

    public static void bootstrap(Context context) {
        Startup.bootstrapXposed();
        new AppSignatureHook().load(context);
        new AppSpecifiedHook().load(context);
        if (LSPApplication.config.pkgMasked) {
            new PackageMaskHook().load(context);
        } else if (LSPApplication.config.confFixed) {
            new PermissionMaskHook().load(context);
        }
    }

    public static void initModules(LoadedApk loadedApk) {
        var lpparam = new XC_LoadPackage.LoadPackageParam(XposedBridge.sLoadedPackageCallbacks);
        lpparam.packageName = removePkgMask(loadedApk.getPackageName());
        lpparam.processName = removePkgMask(ActivityThread.currentProcessName());
        lpparam.classLoader = loadedApk.getClassLoader();
        lpparam.appInfo = loadedApk.getApplicationInfo();
        lpparam.isFirstApplication = true;
        XC_LoadPackage.callAll(lpparam);
    }

    private static String removePkgMask(String pkg) {
        return LSPApplication.config.pkgMasked ? ConstantsM.removeMask(pkg) : pkg;
    }
}

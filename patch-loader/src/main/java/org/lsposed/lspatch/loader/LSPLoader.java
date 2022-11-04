package org.lsposed.lspatch.loader;

import android.app.ActivityThread;
import android.app.LoadedApk;
import android.content.Context;

import org.lsposed.lspatch.loader.hook.AppSignatureHook;
import org.lsposed.lspd.core.ApplicationServiceClient;
import org.lsposed.lspd.core.Startup;
import org.lsposed.lspd.deopt.PrebuiltMethodsDeopter;
import org.lsposed.lspd.service.ILSPApplicationService;

import java.lang.reflect.Method;

import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedInit;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class LSPLoader {
    public static void init(Context context) {
        var service = new LSPAppService();
        var process = ActivityThread.currentProcessName();
        try {
            Method init = ApplicationServiceClient.class.getDeclaredMethod(
                "Init", ILSPApplicationService.class, String.class
            );
            init.setAccessible(true);
            init.invoke(null, service, process);
            PrebuiltMethodsDeopter.deoptBootMethods();
        } catch (ReflectiveOperationException | SecurityException e) {
            Startup.initXposed(false, process, service);
        }
        XposedInit.disableResources = true;
        XposedInit.loadedPackagesInProcess.add(context.getPackageName());
    }

    public static void startInnerHook(Context context) {
        new AppSignatureHook().load(context);
    }

    public static void initModules(LoadedApk loadedApk) {
        var lpparam = new XC_LoadPackage.LoadPackageParam(XposedBridge.sLoadedPackageCallbacks);
        lpparam.packageName = loadedApk.getPackageName();
        lpparam.processName = ActivityThread.currentProcessName();
        lpparam.classLoader = loadedApk.getClassLoader();
        lpparam.appInfo = loadedApk.getApplicationInfo();
        lpparam.isFirstApplication = true;
        XC_LoadPackage.callAll(lpparam);
    }
}

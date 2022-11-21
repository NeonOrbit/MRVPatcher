package org.lsposed.lspatch.loader;

import static org.lsposed.lspatch.share.Constants.CONFIG_ASSET_PATH;
import static org.lsposed.lspatch.share.Constants.MRV_DATA_DIR;
import static org.lsposed.lspatch.share.Constants.ORIGINAL_APK_ASSET_PATH;

import android.app.ActivityThread;
import android.app.LoadedApk;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.res.CompatibilityInfo;
import android.os.Build;
import android.system.Os;
import android.util.Log;

import com.google.gson.Gson;

import org.lsposed.lspatch.loader.util.FileUtils;
import org.lsposed.lspatch.share.Constants;
import org.lsposed.lspatch.share.PatchConfig;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.zip.ZipFile;

import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.XposedInit;
import hidden.HiddenApiBridge;

/**
 * Created by Windysha
 */

public class LSPApplication {
    public static final String TAG = Constants.TAG;

    private static PatchConfig config;
    private static LoadedApk appLoadedApk;
    private static ActivityThread activityThread;

    private static final int PER_USER_RANGE = 100000;
    private static final int FIRST_APP_ZYGOTE_ISOLATED_UID = 90000;

    static public boolean isIsolated() {
        return (android.os.Process.myUid() % PER_USER_RANGE) >= FIRST_APP_ZYGOTE_ISOLATED_UID;
    }

    @SuppressWarnings("unused")
    public static void onLoad() {
        Log.i(TAG, "MRV-v5.5.2");
        Log.i(TAG, "Bootstrap: " + ActivityThread.currentProcessName());
        if (isIsolated()) {
            Log.i(TAG, "Skip isolated process");
            return;
        }
        activityThread = ActivityThread.currentActivityThread();
        var appContext = createLoadedApkWithContext();
        if (appContext == null) {
            Log.e(TAG, "Failed to create app appContext");
            return;
        }
        try {
            lockProfile(appContext);
            LSPLoader.init(appContext);
            ModuleManager.init(appContext);
            LSPLoader.startInnerHook(appContext);
            if (ModuleManager.hasModule) {
                XposedInit.loadModules();
                LSPLoader.initModules(appLoadedApk);
                Log.i(TAG, "Modules initialized");
            }
        } catch (Throwable e) {
            Log.e(TAG, "Do hook", e);
        }
        Log.i(TAG, "Bootstrap completed");
    }

    private static Context createLoadedApkWithContext() {
        try {
            var mBoundApplication = XposedHelpers.getObjectField(activityThread, "mBoundApplication");
            var loadedApk = (LoadedApk) XposedHelpers.getObjectField(mBoundApplication, "info");
            var appInfo = (ApplicationInfo) XposedHelpers.getObjectField(mBoundApplication, "appInfo");
            var baseClassLoader = loadedApk.getClassLoader();
            try (var is = baseClassLoader.getResourceAsStream(CONFIG_ASSET_PATH)) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                LSPApplication.config = new Gson().fromJson(reader, PatchConfig.class);
            } catch (IOException e) {
                Log.e(TAG, "Failed to load config file");
                return null;
            }
            if (LSPApplication.config.fallback) {
                Log.i(TAG, "Applying fallback mode...");
                updateSourceApk(appInfo, baseClassLoader);
            }
            appInfo.appComponentFactory = LSPApplication.config.component;
            updateLoadedApk(appInfo, loadedApk, mBoundApplication);
            Log.i(TAG, "ClassLoader initialized: " + appLoadedApk.getClassLoader());
            Log.i(TAG, "AppCompFactory initialized: " + appLoadedApk.getApplicationInfo().appComponentFactory);
            return (Context) XposedHelpers.callStaticMethod(
                Class.forName("android.app.ContextImpl"), "createAppContext", activityThread, loadedApk
            );
        } catch (Throwable e) {
            Log.e(TAG, "createLoadedApk", e);
            return null;
        }
    }

    private static void updateLoadedApk(ApplicationInfo ai, LoadedApk loadedApk, Object mBoundApplication) {
        var compatInfo = (CompatibilityInfo) XposedHelpers.getObjectField(mBoundApplication, "compatInfo");
        ((Map<?, ?>) XposedHelpers.getObjectField(activityThread, "mPackages")).remove(ai.packageName);
        LSPApplication.appLoadedApk = activityThread.getPackageInfoNoCheck(ai, compatInfo);
        XposedHelpers.setObjectField(mBoundApplication, "info", LSPApplication.appLoadedApk);
        try {
            var activityClientRecordClass = XposedHelpers.findClass("android.app.ActivityThread$ActivityClientRecord", ActivityThread.class.getClassLoader());
            var fixActivityClientRecord = (BiConsumer<Object, Object>)(k, v) -> {
                if (activityClientRecordClass.isInstance(v)) {
                    var pkgInfo = XposedHelpers.getObjectField(v, "packageInfo");
                    if (pkgInfo == loadedApk) {
                        Log.i(TAG, "Switch loadedApk in ActivityClientRecord");
                        XposedHelpers.setObjectField(v, "packageInfo", LSPApplication.appLoadedApk);
                    }
                }
            };
            var mActivities = (Map<?, ?>) XposedHelpers.getObjectField(activityThread, "mActivities");
            mActivities.forEach(fixActivityClientRecord);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                var mLaunchingActivities = (Map<?, ?>) XposedHelpers.getObjectField(activityThread, "mLaunchingActivities");
                mLaunchingActivities.forEach(fixActivityClientRecord);
            }
        } catch (Throwable ignored) {}
        for (Field field : LoadedApk.class.getDeclaredFields()) {
            if (field.getType() == ClassLoader.class) {
                XposedHelpers.setObjectField(
                    loadedApk, field.getName(), XposedHelpers.getObjectField(LSPApplication.appLoadedApk, field.getName())
                );
            }
        }
    }

    private static void updateSourceApk(ApplicationInfo appInfo, ClassLoader classLoader) throws Exception {
        String originApkPath;
        String mrvDataDirPath = appInfo.dataDir + "/" + MRV_DATA_DIR;
        try (ZipFile sourceFile = new ZipFile(appInfo.sourceDir)) {
            originApkPath = mrvDataDirPath + "/" + sourceFile.getEntry(ORIGINAL_APK_ASSET_PATH).getCrc();
        }
        if (!Files.exists(Paths.get(originApkPath))) {
            Log.i(TAG, "Setting up base package");
            int permission = 509;  // 00775
            FileUtils.deleteFolderIfExists(Paths.get(mrvDataDirPath));
            Files.createDirectories(Paths.get(mrvDataDirPath));
            Os.chmod(mrvDataDirPath, permission);
            try (InputStream is = classLoader.getResourceAsStream(ORIGINAL_APK_ASSET_PATH)) {
                Files.copy(is, Paths.get(originApkPath));
            }
            Os.chmod(originApkPath, permission);
        }
        appInfo.sourceDir = originApkPath;
        appInfo.publicSourceDir = originApkPath;
    }

    private static void lockProfile(Context context) {
        final ArrayList<String> codePaths = new ArrayList<>();
        final String packageName = context.getPackageName();
        final ApplicationInfo appInfo = context.getApplicationInfo();
        if (appInfo == null) return;

        if ((appInfo.flags & ApplicationInfo.FLAG_HAS_CODE) != 0) codePaths.add(appInfo.sourceDir);
        if (appInfo.splitSourceDirs != null) Collections.addAll(codePaths, appInfo.splitSourceDirs);
        if (codePaths.isEmpty()) return;

        final var profileDir = HiddenApiBridge.Environment_getDataProfilesDePackageDirectory(appInfo.uid / PER_USER_RANGE, packageName);
        final var attrs = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("r--------"));

        for (int i = codePaths.size() - 1; i >= 0; i--) {
            final String splitName = i == 0 ? null : appInfo.splitNames[i - 1];
            final File curProfileFile = new File(profileDir, splitName == null ? "primary.prof" : splitName + ".split.prof").getAbsoluteFile();
            Log.i(TAG, "Locking profile: " + curProfileFile.getAbsolutePath());
            try {
                if (!curProfileFile.canWrite() && Files.size(curProfileFile.toPath()) == 0) {
                    Log.i(TAG, "Skipping locked profile: " + curProfileFile.getAbsolutePath());
                    continue;
                }
                if (curProfileFile.exists() && !curProfileFile.delete()) {
                    try (var ignored = new FileOutputStream(curProfileFile)) {
                        Log.i(TAG, "Clearing profile content: " + curProfileFile.getAbsolutePath());
                    } catch (Throwable e) {
                        Log.e(TAG, "Failed to clear profile content: " + curProfileFile.getAbsolutePath(), e);
                    }
                    int permission = 256; // 00400
                    Os.chmod(curProfileFile.getAbsolutePath(), permission);
                } else {
                    Files.createFile(curProfileFile.toPath(), attrs);
                }
            } catch (Throwable e) {
                Log.e(TAG, "Failed to lock profile: " + curProfileFile.getAbsolutePath(), e);
            }
        }
    }
}

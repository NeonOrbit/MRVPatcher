package org.lsposed.lspatch.loader.hook;

import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.util.Log;

import org.lsposed.lspatch.loader.LSPApplication;
import org.lsposed.lspatch.loader.ModuleManager;
import org.lsposed.lspatch.share.ConstantsM;

import java.util.concurrent.atomic.AtomicReference;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

@SuppressWarnings("rawtypes")
public class PackageMaskHook implements AppInnerHook {
    @Override
    public void load(Context context) {
        String masked = context.getPackageName();
        String original = ConstantsM.removeMask(masked);
        patchPackageRes(original, masked);
        patchIntents(original, masked);
        try {
            patchAuthority();
        } catch (Throwable t) {
            Log.w(LSPApplication.TAG, t.getMessage(), t);
        }
        if (ModuleManager.isDefaultModuleLoaded()) {
            try {
                patchForChatHeadEnabler(original, masked);
            } catch (Throwable t) {
                Log.w(LSPApplication.TAG, t.getMessage(), t);
            }
        }
    }

    private static void patchPackageRes(String original, String masked) {
        XposedHelpers.findAndHookMethod("android.app.ApplicationPackageManager", null, "getPackageInfo", String.class, int.class,
                new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (param.args[0].toString().equals(original)) {
                    param.args[0] = masked;
                }
            }
        });
        XposedHelpers.findAndHookMethod(Resources.class, "getIdentifier", String.class, String.class, String.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                String name = (String) param.args[0];
                String pkg = (String) param.args[2];
                if (pkg != null && pkg.equals(masked)) {
                    param.args[2] = original;
                } else if (name.startsWith(masked + ":")) {
                    param.args[0] = name.replace(masked, original);
                }
            }
        });
    }

    private static void patchIntents(String original, String masked) {
        XposedBridge.hookAllConstructors(Intent.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                Intent intent = (Intent) param.thisObject;
                if (intent.getPackage() != null && intent.getPackage().equals(original)) {
                    intent.setPackage(masked);
                }
                if (intent.getComponent() != null && intent.getComponent().getPackageName().equals(original)) {
                    intent.setComponent(new ComponentName(masked, intent.getComponent().getClassName()));
                }
            }
        });
        XposedHelpers.findAndHookMethod(Intent.class, "setPackage", String.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (param.args[0] != null && param.args[0].equals(original)) {
                    param.args[0] = masked;
                }
            }
        });
        XposedHelpers.findAndHookMethod(Intent.class, "setComponent", ComponentName.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (param.args[0] == null) return;
                ComponentName cmp = (ComponentName) param.args[0];
                if (cmp.getPackageName().equals(original)) {
                    param.args[0] = new ComponentName(masked, cmp.getClassName());
                }
            }
        });
    }

    private void patchAuthority() throws Throwable {
        XC_MethodHook hook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (param.args[0].toString().contains(ConstantsM.VALID_FB_PACKAGE_PREFIX)) {
                    param.setResult(null);
                }
            }
        };
        XposedBridge.hookAllMethods(ContentResolver.class, "acquireContentProviderClient", hook);
        XposedBridge.hookAllMethods(ContentResolver.class, "acquireUnstableContentProviderClient", hook);
        var query = ContentResolver.class.getDeclaredMethod("query", Uri.class, String[].class, Bundle.class, CancellationSignal.class);
        XposedBridge.hookMethod(query, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                try {
                    Uri uri = (Uri) param.args[0];
                    if (uri.getAuthority() != null && uri.getAuthority().contains(ConstantsM.VALID_FB_PACKAGE_PREFIX)) {
                        param.args[0] = uri.buildUpon().authority(ConstantsM.maskPackagedString(uri.getAuthority())).build();
                    }
                } catch (Throwable ignore) { }
            }
        });
    }

    private static void patchForChatHeadEnabler(String original, String masked) {
        AtomicReference<XC_MethodHook.Unhook> pkgNameHook = new AtomicReference<>();
        pkgNameHook.set(XposedHelpers.findAndHookMethod(ContextWrapper.class, "getPackageName", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (!isCalledFromCHE()) return;
                String pkg = param.getResult().toString();
                if (pkg.equals(masked)) {
                    param.setResult(original);
                    Unhook unhook = pkgNameHook.get();
                    if (unhook != null) unhook.unhook();
                }
            }
        }));
    }

    private static boolean isCalledFromCHE() {
        StackTraceElement[] traces = new Throwable().getStackTrace();
        for (int i = 4; i < Math.min(traces.length, 8); i++) {
            if (traces[i].getClassName().equals("app.neonorbit.chatheadenabler.DataProvider")) return true;
        }
        return false;
    }
}

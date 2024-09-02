package org.lsposed.lspatch.loader.hook;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageParser;
import android.os.Parcel;
import android.util.Log;

import org.lsposed.lspatch.loader.LSPApplication;

import java.util.Map;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class AppSignatureHook implements AppInnerHook {
    @Override
    public void load(Context context) {
        try {
            bypassSignatureInfo(context);
        } catch (Throwable t) {
            Log.w(LSPApplication.TAG, "Failed to hook signature bypass", t);
        }
    }

    private static void bypassSignatureInfo(Context context) {
        ProxySignatureInfo proxy = new ProxySignatureInfo(context);
        replacePackageInfoCreator(proxy);
        hookPackageParser(proxy);
    }

    private static void replacePackageInfoCreator(ProxySignatureInfo proxy) {
        XposedHelpers.setStaticObjectField(PackageInfo.class, "CREATOR", proxy);
        try {
            ((Map<?, ?>) XposedHelpers.getStaticObjectField(Parcel.class, "mCreators")).clear();
        } catch (NoSuchFieldError ignore) {} catch (Throwable t) {
            Log.w(LSPApplication.TAG, "Failed to clear Parcel.mCreators", t);
        }
        try {
            ((Map<?, ?>) XposedHelpers.getStaticObjectField(Parcel.class, "sPairedCreators")).clear();
        } catch (NoSuchFieldError ignore) {} catch (Throwable t) {
            Log.w(LSPApplication.TAG, "Failed to clear Parcel.sPairedCreators", t);
        }
    }

    @SuppressWarnings("rawtypes")
    private static void hookPackageParser(ProxySignatureInfo proxy) {
        XposedBridge.hookAllMethods(PackageParser.class, "generatePackageInfo", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                var packageInfo = (PackageInfo) param.getResult();
                if (packageInfo != null) {
                    proxy.replaceSignature(packageInfo);
                }
            }
        });
    }
}

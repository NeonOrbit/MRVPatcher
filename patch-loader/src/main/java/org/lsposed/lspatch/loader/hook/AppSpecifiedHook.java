package org.lsposed.lspatch.loader.hook;

import android.app.Activity;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import org.lsposed.lspatch.loader.LSPApplication;

import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class AppSpecifiedHook implements AppInnerHook {
    private static final Set<String> LINKS = Set.of(
            "m.fb.watch", "www.m.me", "fb.com", "facebook.com", "m.facebook.com",
            "www.fb.audio", "www.fb.watch", "fb.audio", "fb.watch", "m.me",
            "fb.gg", "fb.me", "m.fbwat.ch", "www.fb.gg", "www.fb.me",
            "www.facebook.com", "web.facebook.com", "www.fbwat.ch"
    );

    @Override
    public void load(Context context) {
        hotPatchForDeepLinking(context);
        hotPatchForAccountCenter(context);
    }

    private static void hotPatchForAccountCenter(Context context) {
        String pkg = context.getPackageName();
        XC_MethodHook hook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                String uri = param.args[0].toString();
                if (uri.contains("FamilyAppsUserValuesProvider")) {
                    Log.i(LSPApplication.TAG, "Preventing [" + pkg + "] from acquiring: " + uri);
                    param.setResult(null);
                }
            }
        };
        XposedBridge.hookAllMethods(ContentResolver.class, "acquireContentProviderClient", hook);
        XposedBridge.hookAllMethods(ContentResolver.class, "acquireUnstableContentProviderClient", hook);
    }

    private void hotPatchForDeepLinking(Context context) {
        if (context.getPackageName().equals("com.facebook.katana")) return;
        XC_MethodHook hook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (param.thisObject instanceof Activity && !param.method
                        .getDeclaringClass().getSimpleName().equals("Activity")) {
                    return; // Prevent multiple call
                }
                Intent intent = (Intent) param.args[0];
                if (intent.getData() == null) return;
                if (intent.getAction() != null && intent.getCategories() != null && intent.getComponent() != null) return;
                if (intent.getComponent() != null && intent.getComponent().getClassName().toLowerCase().contains("browser")) return;
                if (intent.getAction() != null && !intent.getAction().equals(Intent.ACTION_VIEW)) return;
                if (intent.getCategories() != null && !intent.getCategories().contains(Intent.CATEGORY_BROWSABLE)) return;
                if (LINKS.contains(intent.getData().getHost())) {
                    if (intent.getAction() == null) intent.setAction(Intent.ACTION_VIEW);
                    if (intent.getCategories() == null) intent.addCategory(Intent.CATEGORY_BROWSABLE);
                    intent.setComponent(new ComponentName("com.facebook.katana", "com.facebook.katana.IntentUriHandler"));
                    Log.i(LSPApplication.TAG, "Patched Intent for deep linking: " + intent);
                }
            }
        };
        XposedHelpers.findAndHookMethod(ContextWrapper.class, "startActivity", Intent.class, hook);
        XposedHelpers.findAndHookMethod(Activity.class, "startActivity", Intent.class, Bundle.class, hook);
    }
}

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
import java.util.function.Consumer;

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
        hotPatchForAccountCenter(context);
        if (!LSPApplication.config.pkgMasked) {
            hotPatchForMsgToFbDeepLinking(context);
            hotPatchForFbToInstDeepLinking(context);
        }
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

    private void hotPatchForMsgToFbDeepLinking(Context context) {
        if (!context.getPackageName().equals("com.facebook.orca")) return;
        hookStartActivity(intent -> {
            if (intent.getData() == null || intent.getData().getHost() == null || intent.getData().getScheme() == null) return;
            if (LINKS.contains(intent.getData().getHost())) {
                prepareAndSanitizeIntentForDeepLinking(intent);
                intent.setComponent(new ComponentName("com.facebook.katana", "com.facebook.katana.IntentUriHandler"));
                Log.i(LSPApplication.TAG, "Patched Intent for facebook deep linking: " + intent);
            }
        });
    }

    private void hotPatchForFbToInstDeepLinking(Context context) {
        if (!context.getPackageName().equals("com.facebook.katana")) return;
        hookStartActivity(intent -> {
            if (intent.getData() == null || intent.getData().getHost() == null || intent.getData().getScheme() == null) return;
            if (intent.getData().getScheme().contains("http") && intent.getData().getHost().contains("instagram.com")) {
                prepareAndSanitizeIntentForDeepLinking(intent);
                intent.setComponent(new ComponentName("com.instagram.android", "com.instagram.url.UrlHandlerLauncherActivity"));
                Log.i(LSPApplication.TAG, "Patched Intent for instagram deep linking: " + intent);
            }
        });
    }

    private void prepareAndSanitizeIntentForDeepLinking(Intent intent) {
        if (intent.getAction() == null) intent.setAction(Intent.ACTION_VIEW);
        if (intent.getCategories() == null) intent.addCategory(Intent.CATEGORY_BROWSABLE);
        if (intent.getExtras() != null) intent.getExtras().keySet().forEach(intent::removeExtra);
    }

    private void hookStartActivity(Consumer<Intent> consumer) {
        XC_MethodHook hook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (param.thisObject instanceof Activity && !param.method
                        .getDeclaringClass().getSimpleName().equals("Activity")) {
                    return; // Prevent multiple call
                }
                Intent intent = (Intent) param.args[0];
                if (intent.getAction() != null && !intent.getAction().equals(Intent.ACTION_VIEW)) return;
                if (intent.getCategories() != null && !intent.getCategories().contains(Intent.CATEGORY_BROWSABLE)) return;
                if (intent.getComponent() != null && !intent.getComponent().getClassName().toLowerCase().contains("browser")) return;
                consumer.accept((Intent) param.args[0]);
            }
        };
        XposedHelpers.findAndHookMethod(ContextWrapper.class, "startActivity", Intent.class, hook);
        XposedHelpers.findAndHookMethod(Activity.class, "startActivity", Intent.class, Bundle.class, hook);
    }
}

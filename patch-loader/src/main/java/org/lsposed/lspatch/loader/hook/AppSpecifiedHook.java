package org.lsposed.lspatch.loader.hook;

import android.app.Activity;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.util.Log;

import org.lsposed.lspatch.loader.LSPApplication;

import java.util.Set;
import java.util.function.Consumer;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

@SuppressWarnings("rawtypes")
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
        hotPatchForMsgToFbDeepLinking(context);
        hotPatchForFbToInstDeepLinking(context);
    }

    private void hotPatchForAccountCenter(Context context) {
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
        final String handler = "com.facebook.katana.IntentUriHandler";
        hookStartActivity(intent -> {
            if (!isInstalled(context, "com.facebook.katana")) return;
            if (intent.getComponent() != null && intent.getComponent().getClassName().equals(handler)) return;
            if (intent.getData() == null || intent.getData().getHost() == null || intent.getData().getScheme() == null) return;
            if (LINKS.contains(intent.getData().getHost())) {
                prepareAndSanitizeIntentForDeepLinking(intent);
                intent.setComponent(new ComponentName("com.facebook.katana", handler));
                Log.i(LSPApplication.TAG, "Patched Intent for facebook deep linking: " + intent);
            }
        });
    }

    private void hotPatchForFbToInstDeepLinking(Context context) {
        if (!context.getPackageName().equals("com.facebook.katana")) return;
        final String handler = "com.instagram.url.UrlHandlerLauncherActivity";
        hookStartActivity(intent -> {
            if (intent.getComponent() != null || !isInstalled(context, "com.instagram.android")) return;
            if (intent.getData() == null || intent.getData().getHost() == null || intent.getData().getScheme() == null) return;
            if (intent.getData().getScheme().contains("http") && intent.getData().getHost().contains("instagram.com")) {
                prepareAndSanitizeIntentForDeepLinking(intent);
                intent.setComponent(new ComponentName("com.instagram.android", handler));
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
                Intent intent = (Intent) param.args[0];
                if (intent.getAction() != null && !intent.getAction().equals(Intent.ACTION_VIEW)) return;
                if (Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getCategories() == null && intent.getComponent() == null) {
                    return; // Ignore external
                }
                if (intent.getCategories() != null && !intent.getCategories().contains(Intent.CATEGORY_BROWSABLE)) return;
                if (intent.getComponent() != null && !intent.getComponent().getClassName().toLowerCase().contains("browser")) return;
                consumer.accept((Intent) param.args[0]);
            }
        };
        XposedBridge.hookAllMethods(Activity.class, "startActivity", hook);
        XposedBridge.hookAllMethods(ContextWrapper.class, "startActivity", hook);
    }

    @SuppressWarnings("all")
    private boolean isInstalled(Context context, String pkg) {
        try {
            var info = context.getPackageManager().getPackageInfo(pkg, 0);
            return (info != null && info.applicationInfo.enabled);
        } catch (PackageManager.NameNotFoundException ignore) { }
        return false;
    }
}

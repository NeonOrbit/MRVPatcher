package org.lsposed.lspatch.loader.hook;

import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.ShortcutManager;
import android.util.Log;

import org.lsposed.lspatch.loader.LSPApplication;
import org.lsposed.lspatch.loader.ModuleManager;

import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

public class AppSpecifiedHook implements AppInnerHook {
    private static final Set<String> LINKS = Set.of(
        "m.fb.watch", "www.m.me", "fb.com", "facebook.com", "m.facebook.com",
        "www.fb.audio", "www.fb.watch", "fb.audio", "fb.watch", "m.me",
        "fb.gg", "fb.me", "www.alpha.facebook.com", "m.fbwat.ch", "www.fb.gg",
        "www.fb.me", "www.facebook.com", "www.beta.facebook.com", "www.fbwat.ch"
    );

    @Override
    public void load(Context context) {
        hookModuleShortcut(context);
        DeepLinkingHotPatch(context);
    }

    private void hookModuleShortcut(Context context) {
        if (ModuleManager.hasModule) {
            XposedHelpers.findAndHookMethod(ShortcutManager.class, "removeAllDynamicShortcuts", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        context.getSystemService(ShortcutManager.class).addDynamicShortcuts(ModuleManager.MODULE_SHORTCUT_INFO);
                    } catch (Throwable t) {
                        Log.w(LSPApplication.TAG, "Failed to re-apply module shortcut: " + t.getMessage());
                    }
                }
            });
        }
    }

    private void DeepLinkingHotPatch(Context context) {
        if (context.getPackageName().equals("com.facebook.katana")) return;
        XposedHelpers.findAndHookMethod(ContextWrapper.class, "startActivity", Intent.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                Intent intent = (Intent) param.args[0];
                if (intent.getAction() == null || intent.getData() == null) return;
                if (intent.getAction().equals(Intent.ACTION_VIEW) && intent.getComponent() == null &&
                    (intent.getCategories() == null || intent.getCategories().contains(Intent.CATEGORY_BROWSABLE)) &&
                    LINKS.contains(intent.getData().getHost())) {
                    if (intent.getCategories() == null) intent.addCategory(Intent.CATEGORY_BROWSABLE);
                    intent.setComponent(new ComponentName("com.facebook.katana", "com.facebook.deeplinking.activity.DeepLinkingAliasActivity"));
                }
            }
        });
    }
}

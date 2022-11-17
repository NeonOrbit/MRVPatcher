package org.lsposed.lspatch.loader.hook;

import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;

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
        DeepLinkingHotPatch(context);
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

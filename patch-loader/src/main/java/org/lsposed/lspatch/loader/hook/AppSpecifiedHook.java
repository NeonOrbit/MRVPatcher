package org.lsposed.lspatch.loader.hook;

import android.content.Context;
import android.content.pm.ShortcutManager;
import android.util.Log;

import org.lsposed.lspatch.loader.LSPApplication;
import org.lsposed.lspatch.loader.ModuleManager;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

public class AppSpecifiedHook implements AppInnerHook {
    @Override
    public void load(Context context) {
        hookModuleShortcut(context);
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
}

package org.lsposed.lspatch.loader.hook;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;

import org.lsposed.lspatch.share.ConstantsM;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

@SuppressWarnings("rawtypes")
public class PermissionMaskHook implements AppInnerHook {
    @Override
    public void load(Context context) {
        hookBroadcast(context);
    }

    private void hookBroadcast(Context context) {
        var ContextImpl = XposedHelpers.findClass("android.app.ContextImpl", context.getClassLoader());
        var receiverHook = new XC_MethodHook() {
            protected void beforeHookedMethod(MethodHookParam param) {
                if (param.args.length > 2 && param.args[2] instanceof String) {
                    param.args[2] = ConstantsM.maskFbPackagedString((String) param.args[2]);
                }
            }
        };
        XposedHelpers.findAndHookMethod(ContextImpl, "registerReceiver",
                BroadcastReceiver.class, IntentFilter.class, String.class, Handler.class, receiverHook
        );
        XposedHelpers.findAndHookMethod(ContextImpl, "registerReceiver",
                BroadcastReceiver.class, IntentFilter.class, String.class, Handler.class, int.class, receiverHook
        );
        var senderHook = new XC_MethodHook() {
            protected void beforeHookedMethod(MethodHookParam param) {
                if (param.args.length > 1 && param.args[1] instanceof String) {
                    param.args[1] = ConstantsM.maskFbPackagedString((String) param.args[1]);
                }
            }
        };
        XposedBridge.hookAllMethods(ContextImpl, "sendOrderedBroadcast", senderHook);
        XposedHelpers.findAndHookMethod(ContextImpl, "sendBroadcast", Intent.class, String.class, senderHook);
    }
}

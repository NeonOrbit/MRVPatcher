package org.lsposed.lspatch.loader.hook;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.UserHandle;

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
                int index = param.args.length > 2 && param.args[1] instanceof UserHandle ? 2 : param.args.length > 1 ? 1 : -1;
                if (index > 0 && param.args[index] instanceof String) {
                    param.args[index] = ConstantsM.maskFbPackagedString((String) param.args[index]);
                }
            }
        };
        XposedBridge.hookAllMethods(ContextImpl, "sendBroadcast", senderHook);
        XposedBridge.hookAllMethods(ContextImpl, "sendBroadcastAsUser", senderHook);
        XposedBridge.hookAllMethods(ContextImpl, "sendOrderedBroadcast", senderHook);
        XposedBridge.hookAllMethods(ContextImpl, "sendOrderedBroadcastAsUser", senderHook);
    }
}

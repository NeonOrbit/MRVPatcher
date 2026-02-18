package org.lsposed.lspatch.metaloader;

import android.annotation.SuppressLint;
import android.app.ActivityThread;
import android.app.AppComponentFactory;
import android.util.Log;

import org.lsposed.lspatch.share.Constants;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.util.Objects;

public class LSPAppComponentFactoryStub extends AppComponentFactory {
    public static byte[] dex = null;

    static {
        if (ActivityThread.currentActivityThread() != null) loadLsp();
    }

    @SuppressLint({"DiscouragedPrivateApi", "UnsafeDynamicallyLoadedCode"})
    private static void loadLsp() {
        var cl = Objects.requireNonNull(LSPAppComponentFactoryStub.class.getClassLoader());
        try (var is = cl.getResourceAsStream(Constants.LOADER_DEX_PATH);
             var os = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int n;
            while (-1 != (n = is.read(buffer))) {
                os.write(buffer, 0, n);
            }
            dex = os.toByteArray();
        } catch (Throwable e) {
            Log.e(Constants.TAG, "load dex error", e);
        }

        try {
            Class<?> VMRuntime = Class.forName("dalvik.system.VMRuntime");
            Method getRuntime = VMRuntime.getDeclaredMethod("getRuntime");
            getRuntime.setAccessible(true);
            Method vmInstructionSet = VMRuntime.getDeclaredMethod("vmInstructionSet");
            vmInstructionSet.setAccessible(true);
            String arch = (String) vmInstructionSet.invoke(getRuntime.invoke(null));
            String path = cl.getResource(Constants.getLibrarySoPath(arch)).getPath().substring(5);
            System.load(path);
        } catch (Throwable e) {
            Log.e(Constants.TAG, "load lspd error", e);
            throw new ExceptionInInitializerError(e);
        }
    }
}

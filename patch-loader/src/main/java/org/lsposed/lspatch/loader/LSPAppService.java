package org.lsposed.lspatch.loader;

import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;

import org.lsposed.lspd.models.Module;
import org.lsposed.lspd.service.ILSPApplicationService;

import java.io.File;
import java.util.List;

public class LSPAppService extends ILSPApplicationService.Stub {
    public LSPAppService() {}

    @Override
    public IBinder requestModuleBinder(String name) {
        return null;
    }

    @Override
    public List<Module> getModulesList() {
        return ModuleManager.MODULES;
    }

    @Override
    public String getPrefsPath(String packageName) {
        return new File(
            Environment.getDataDirectory(), "data/" + packageName + "/shared_prefs/"
        ).getAbsolutePath();
    }

    @Override
    public Bundle requestRemotePreference(String packageName, int userId, IBinder callback) {
        return null;
    }

    @Override
    public ParcelFileDescriptor requestInjectedManagerBinder(List<IBinder> binder) {
        return null;
    }
}

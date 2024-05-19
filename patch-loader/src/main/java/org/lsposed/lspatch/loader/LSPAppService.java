package org.lsposed.lspatch.loader;

import android.os.Environment;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;

import org.lsposed.lspd.models.Module;
import org.lsposed.lspd.service.ILSPApplicationService;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class LSPAppService extends ILSPApplicationService.Stub {
    public LSPAppService() {}

    @Override
    public List<Module> getLegacyModulesList() {
        return ModuleManager.getPreloadedModules();
    }

    @Override
    public List<Module> getModulesList() {
        return new ArrayList<>();
    }

    @Override
    public String getPrefsPath(String packageName) {
        return new File(
            Environment.getDataDirectory(), "data/" + packageName + "/shared_prefs/"
        ).getAbsolutePath();
    }

    @Override
    public ParcelFileDescriptor requestInjectedManagerBinder(List<IBinder> binder) {
        return null;
    }

    @Override
    @SuppressWarnings("all")
    public IBinder asBinder() {
        return this;
    }
}

package org.lsposed.lspatch.share;

import java.util.List;

public class PatchConfig {
    public final boolean fallback;
    public final String component;
    public final boolean loadOnAll;
    public final List<String> exModules;

    public PatchConfig(boolean fallback, String component,
                       boolean loadOnAll, List<String> modules) {
        this.fallback = fallback;
        this.component = component;
        this.loadOnAll = loadOnAll;
        this.exModules = modules;
    }

    public boolean targetAll() {
        return loadOnAll && hasExtraModules();
    }

    public boolean hasExtraModules() {
        return exModules != null && !exModules.isEmpty();
    }
}

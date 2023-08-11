package org.lsposed.lspatch.share;

import java.util.List;

public class PatchConfig {
    public final String component;
    public final boolean fallback;
    public final boolean confFixed;
    public final boolean pkgMasked;
    public final boolean loadOnAll;
    public final List<String> exModules;

    public PatchConfig(String component, boolean fallback, boolean confFixed,
                       boolean pkgMasked, boolean loadOnAll, List<String> exModules) {
        this.component = component;
        this.fallback = fallback;
        this.confFixed = confFixed;
        this.pkgMasked = pkgMasked;
        this.loadOnAll = loadOnAll;
        this.exModules = exModules;
    }

    public boolean targetAll() {
        return pkgMasked || loadOnAll && hasExtraModules();
    }

    public boolean hasExtraModules() {
        return exModules != null && !exModules.isEmpty();
    }
}

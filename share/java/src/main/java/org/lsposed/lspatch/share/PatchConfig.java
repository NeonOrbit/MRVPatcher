package org.lsposed.lspatch.share;

import java.util.List;
import java.util.Map;

public class PatchConfig {
    public final String component;
    public final boolean fallback;
    public final boolean confFixed;
    public final boolean pkgMasked;
    public final boolean loadOnAll;
    public final List<String> exModules;
    public final Map<String, List<String>> prefetches;

    public PatchConfig(String component, boolean fallback,
                       boolean confFixed, boolean pkgMasked, boolean loadOnAll,
                       List<String> exModules, Map<String, List<String>> prefetches) {
        this.component = component;
        this.fallback = fallback;
        this.confFixed = confFixed;
        this.pkgMasked = pkgMasked;
        this.loadOnAll = loadOnAll;
        this.exModules = exModules;
        this.prefetches = prefetches;
    }

    public boolean targetAll() {
        return pkgMasked || loadOnAll && hasExtraModules();
    }

    public boolean hasExtraModules() {
        return exModules != null && !exModules.isEmpty();
    }
}

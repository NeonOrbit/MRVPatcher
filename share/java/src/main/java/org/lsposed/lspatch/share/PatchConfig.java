package org.lsposed.lspatch.share;

public class PatchConfig {
    public final boolean fallback;
    public final String component;

    public PatchConfig(boolean fallback, String component) {
        this.fallback = fallback;
        this.component = component;
    }
}

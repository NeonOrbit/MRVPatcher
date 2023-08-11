package org.lsposed.patch;

import javax.annotation.Nonnull;

@SuppressWarnings("unused")
public final class MRVPatcher {
    public static void patch(@Nonnull String... args) {
        LSPatch.main(args);
    }
    public static void setLogger(@Nonnull OutputLogger logger) {
        LSPatch.setOutputLogger(logger);
    }
}

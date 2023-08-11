package org.lsposed.patch;

import javax.annotation.Nonnull;

public interface OutputLogger {
    default void v(@Nonnull String msg) {}
    void d(@Nonnull String msg);
    void e(@Nonnull String msg);
}

package org.lsposed.patch;

public interface OutputLogger {
    default void v(String msg) {}
    void d(String msg);
    void e(String msg);
}

-dontoptimize
-repackageclasses "mrv"
-keep class org.lsposed.** { *; }

-keep class hidden.** { *; }
-keep class android.** { *; }
-keep class androidx.** { *; }
-keep class com.android.** { *; }
-keep class dalvik.** { *; }
-keep class xposed.dummy.** { *; }
-keep class de.robv.android.xposed.** { *; }
-keep class io.github.xposed.xposedservice.** { *; }

-keep class sun.misc.** { *; }
-keep class sun.net.www.** { *; }
-keep class org.xmlpull.** { *; }

-dontwarn org.slf4j.impl.**
-dontwarn androidx.annotation.**
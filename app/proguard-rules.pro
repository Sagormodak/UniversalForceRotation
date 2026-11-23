-dontwarn io.github.libxposed.annotation.**
-adaptresourcefilecontents META-INF/xposed/java_init.list

-keep public class com.sagor.universalforcerotation.xposed.RotationModuleEntry {
    public <init>();
}

-keep,allowoptimization,allowobfuscation public class * extends io.github.libxposed.api.XposedModule {
    public <init>();
}

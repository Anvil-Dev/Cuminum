package dev.anvilcraft.resource.cuminum.gradle;

import sun.misc.Unsafe;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;

/**
 * Bypasses Java module system restrictions so the decuminum Gradle task
 * can access {@code com.sun.tools.javac.*} internal classes at runtime.
 *
 * <p>Call {@link #init()} once before accessing any javac internals.</p>
 */
public final class JavaWorkaround {

    private static volatile boolean initialized;

    private JavaWorkaround() {}

    /**
     * Opens {@code jdk.compiler} internal packages to the unnamed module.
     */
    public static void init() {
        if (initialized) return;
        try {
            Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            Unsafe unsafe = (Unsafe) theUnsafe.get(null);

            Field implLookupField = MethodHandles.Lookup.class.getDeclaredField("IMPL_LOOKUP");
            long offset = unsafe.staticFieldOffset(implLookupField);
            Object base = unsafe.staticFieldBase(implLookupField);
            MethodHandles.Lookup trustedLookup =
                (MethodHandles.Lookup) unsafe.getObject(base, offset);

            Module thisModule = JavaWorkaround.class.getModule();
            Module compilerModule = ModuleLayer.boot()
                .findModule("jdk.compiler")
                .orElseThrow(() -> new RuntimeException("jdk.compiler module not found"));

            MethodType mt = MethodType.methodType(
                void.class, String.class, Module.class, boolean.class, boolean.class);
            MethodHandle addExports = trustedLookup
                .findVirtual(Module.class, "implAddExportsOrOpens", mt);

            String[] packages = {
                "com.sun.tools.javac.api",
                "com.sun.tools.javac.tree",
                "com.sun.tools.javac.util",
                "com.sun.tools.javac.code",
                "com.sun.tools.javac.processing",
                "com.sun.tools.javac.model",
                "com.sun.tools.javac.comp",
                "com.sun.tools.javac.parser",
            };

            for (String pkg : packages) {
                addExports.invoke(compilerModule, pkg, thisModule, false, true);
            }

            initialized = true;
        } catch (Throwable e) {
            throw new RuntimeException(
                "Cuminum JavaWorkaround.init() failed. "
                    + "Ensure you are using a JDK (not JRE) and the "
                    + "--add-exports=jdk.compiler/...=ALL-UNNAMED JVM flags are set.",
                e);
        }
    }

    /** Returns {@code true} if the workaround has been successfully applied. */
    public static boolean isAvailable() {
        return initialized;
    }
}

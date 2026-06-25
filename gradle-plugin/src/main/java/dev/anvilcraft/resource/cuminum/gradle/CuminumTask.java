package dev.anvilcraft.resource.cuminum.gradle;

import com.sun.source.util.JavacTask;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCAnnotation;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCCompilationUnit;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import com.sun.tools.javac.tree.JCTree.JCVariableDecl;
import com.sun.tools.javac.tree.Pretty;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.*;

import javax.tools.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 调用 Cuminum 注解处理器，将处理后的 AST 以 .java 源文件形式写出到
 * outputDir，类似 Lombok 的 delombok 功能。
 */
public abstract class CuminumTask extends DefaultTask {

    @InputFiles
    @SkipWhenEmpty
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getSourceDirs();

    @InputFiles
    @Classpath
    public abstract ConfigurableFileCollection getCompileClasspath();

    @InputFiles
    @Classpath
    public abstract ConfigurableFileCollection getProcessorClasspath();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDir();

    @TaskAction
    public void decuminum() throws IOException {
        JavaWorkaround.init();

        List<File> sourceFiles = new ArrayList<>();
        for (File dir : getSourceDirs().getFiles()) {
            collectJavaFiles(dir, sourceFiles);
        }
        if (sourceFiles.isEmpty()) {
            getLogger().lifecycle("[decuminum] No Java source files found, skipping.");
            return;
        }

        File outputDir = getOutputDir().get().getAsFile();
        outputDir.mkdirs();

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException(
                "[decuminum] No Java compiler found. Use JDK, not JRE.");
        }

        List<String> options = new ArrayList<>();
        options.add("-proc:full");
        options.add("-implicit:none");
        for (String pkg : new String[]{
            "jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
            "jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED",
            "jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED",
            "jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED",
            "jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED",
            "jdk.compiler/com.sun.tools.javac.model=ALL-UNNAMED",
            "jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED",
            "jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED",
        }) {
            options.add("--add-exports");
            options.add(pkg);
        }
        String cp = getCompileClasspath().getAsPath();
        if (!cp.isEmpty()) {
            options.add("-cp"); options.add(cp);
        }
        String procPath = getProcessorClasspath().getAsPath();
        if (!procPath.isEmpty()) {
            options.add("-processorpath"); options.add(procPath);
        }

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StringWriter extraOutput = new StringWriter();

        // Capture each compilation unit when ANALYZE finishes — i.e. after
        // attribute + flow but before desugar (Lower) and code generation.
        // This is the same stopping point delombok uses: lambdas, generics and
        // record syntax are all still intact in the AST. Running a full compile
        // and reading the AST afterwards would yield a desugared tree instead.
        Map<String, JCCompilationUnit> capturedUnits = new LinkedHashMap<>();

        try (StandardJavaFileManager fm =
                 compiler.getStandardFileManager(diagnostics, null, null)) {

            Iterable<? extends JavaFileObject> fileObjects =
                fm.getJavaFileObjects(sourceFiles.toArray(new File[0]));

            JavaCompiler.CompilationTask task = compiler.getTask(
                new PrintWriter(extraOutput), fm, diagnostics,
                options, null, fileObjects);

            if (!(task instanceof JavacTask javacTask)) {
                throw new IllegalStateException(
                    "[decuminum] Expected a JavacTask but got " + task.getClass());
            }
            javacTask.addTaskListener(new TaskListener() {
                @Override public void started(TaskEvent e) {}
                @Override public void finished(TaskEvent e) {
                    if (e.getKind() == TaskEvent.Kind.ANALYZE
                        && e.getCompilationUnit() instanceof JCCompilationUnit jcUnit
                        && jcUnit.sourcefile != null) {
                        capturedUnits.put(jcUnit.sourcefile.getName(), jcUnit);
                    }
                }
            });
            // analyze() stops after flow analysis — it never invokes Lower or
            // generate(), so the captured AST is never desugared.
            javacTask.analyze();
        }

        int fileCount = 0;
        for (Map.Entry<String, JCCompilationUnit> e : capturedUnits.entrySet()) {
            JCCompilationUnit jcUnit = e.getValue();

            JCClassDecl classDecl = null;
            for (JCTree def : jcUnit.defs) {
                if (def instanceof JCClassDecl cd) { classDecl = cd; break; }
            }
            if (classDecl == null) continue;

            String packageName = jcUnit.packge != null
                ? jcUnit.packge.fullname.toString() : "";
            String className = classDecl.name.toString();
            String relativePath = packageName.isEmpty()
                ? className + ".java"
                : packageName.replace('.', '/') + "/" + className + ".java";
            File outFile = new File(outputDir, relativePath);
            outFile.getParentFile().mkdirs();

            try (PrintWriter pw = new PrintWriter(
                     new OutputStreamWriter(
                         new FileOutputStream(outFile), StandardCharsets.UTF_8))) {
                printUnit(pw, jcUnit, classDecl);
                pw.flush();
                fileCount++;
            }
        }


        int errorCount = 0;
        for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) {
            String msg = d.getMessage(null);
            switch (d.getKind()) {
                case ERROR -> { getLogger().error("[decuminum] {}", msg); errorCount++; }
                case WARNING, MANDATORY_WARNING ->
                    getLogger().warn("[decuminum] {}", msg);
                default -> getLogger().info("[decuminum] {}", msg);
            }
        }
        String extra = extraOutput.toString().trim();
        if (!extra.isEmpty()) getLogger().info("[decuminum] javac: {}", extra);
        if (errorCount > 0) getLogger().warn("[decuminum] Done with {} errors", errorCount);
        getLogger().lifecycle("[decuminum] Generated {} .java files → {}",
            fileCount, outputDir.getAbsolutePath());
    }

    private void collectJavaFiles(File dir, List<File> result) {
        if (dir == null || !dir.isDirectory()) return;
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File f : children) {
            if (f.isDirectory()) collectJavaFiles(f, result);
            else if (f.getName().endsWith(".java")) result.add(f);
        }
    }

    /** {@code 1L << 61} — {@code Flags.RECORD}, set on record classes and their components. */
    private static final long FLAG_RECORD = 1L << 61;
    /** {@code 1 << 24} — {@code Flags.GENERATED_MEMBER}, set on javac-synthesized record members. */
    private static final long FLAG_GENERATED_MEMBER = 1 << 24;
    /** {@code 1L << 36} — {@code Flags.GENERATEDCONSTR}, set on the synthesized canonical constructor. */
    private static final long FLAG_GENERATEDCONSTR = 1L << 36;

    /**
     * Print a captured compilation unit as source. Delegates to javac's
     * {@link Pretty} for everything except records: {@code Pretty} has no
     * record support (it prints {@code record} as {@code class} and, because
     * records are partly desugared during enter, emits a body with an empty
     * canonical constructor and the components as plain fields). For records we
     * print the {@code record Name(components)} header ourselves and skip the
     * javac-synthesized members, while still using {@link Pretty} for each
     * surviving member and for the package/imports.
     */
    private static void printUnit(PrintWriter pw, JCCompilationUnit jcUnit,
                                  JCClassDecl classDecl) throws IOException {
        boolean isRecord = (classDecl.mods.flags & FLAG_RECORD) != 0;
        if (!isRecord) {
            new Pretty(pw, true).printUnit(jcUnit, null);
            return;
        }

        // Package + imports: let Pretty handle everything up to the class.
        Pretty header = new Pretty(pw, true);
        for (JCTree def : jcUnit.defs) {
            if (def instanceof JCClassDecl) break;
            header.printStat(def);
        }
        pw.println();

        // Record header: annotations, modifiers (minus the RECORD bit, which
        // we render as the `record` keyword), name, type params, components.
        Pretty p = new Pretty(pw, true);
        p.printAnnotations(classDecl.mods.annotations);
        p.printFlags(classDecl.mods.flags & ~FLAG_RECORD);
        pw.print("record ");
        pw.print(classDecl.name.toString());
        p.printTypeParameters(classDecl.typarams);
        pw.print('(');
        boolean firstComp = true;
        for (JCTree def : classDecl.defs) {
            if (def instanceof JCVariableDecl vd
                && (vd.mods.flags & FLAG_RECORD) != 0) {
                if (!firstComp) pw.print(", ");
                firstComp = false;
                // Component: annotations + type + name (no modifiers).
                for (JCAnnotation a : vd.mods.annotations) {
                    p.printExpr(a);
                    pw.print(' ');
                }
                p.printExpr(vd.vartype);
                pw.print(' ');
                pw.print(vd.name.toString());
            }
        }
        pw.print(')');
        if (classDecl.implementing.nonEmpty()) {
            pw.print(" implements ");
            p.printExprs(classDecl.implementing);
        }
        pw.print(" {");
        pw.println();

        // Body: print every member javac did NOT synthesize. The synthesized
        // canonical constructor carries GENERATEDCONSTR; record component
        // accessors and fields carry RECORD. A user-written constructor still
        // has the internal name <init>, which Pretty only renders correctly
        // when its (inaccessible) enclClassName field is set — so constructors
        // are printed by hand instead.
        for (JCTree def : classDecl.defs) {
            if (def instanceof JCVariableDecl vd
                && (vd.mods.flags & FLAG_RECORD) != 0) {
                continue; // component, already in the header
            }
            if (def instanceof JCMethodDecl md) {
                if ((md.mods.flags & (FLAG_GENERATEDCONSTR | FLAG_GENERATED_MEMBER)) != 0) {
                    continue; // synthesized canonical ctor / accessor
                }
                if (md.name.toString().equals("<init>")) {
                    pw.print("    ");
                    p.printAnnotations(md.mods.annotations);
                    p.printFlags(md.mods.flags);
                    p.printTypeParameters(md.typarams);
                    pw.print(classDecl.name.toString());
                    pw.print('(');
                    p.printExprs(md.params);
                    pw.print(')');
                    if (md.body != null) {
                        pw.print(' ');
                        p.printStat(md.body);
                    } else {
                        pw.print(';');
                    }
                    pw.println();
                    continue;
                }
            }
            pw.print("    ");
            p.printStat(def);
            pw.println();
        }
        pw.print('}');
        pw.println();
    }
}

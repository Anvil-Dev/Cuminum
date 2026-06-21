package dev.anvilcraft.resource.cuminum.gradle;

import com.sun.source.util.JavacTask;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCCompilationUnit;
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
 *
 * <p>使用 javac 的 {@code -proc:only} 模式仅运行注解处理，
 * 然后通过 javac 内部的 {@link Pretty} 打印机将修改后的 AST
 * 还原为有效的 Java 源代码。</p>
 */
public abstract class CuminumTask extends DefaultTask {

    /** 要处理的 Java 源文件根目录（来自 main 源集） */
    @InputFiles
    @SkipWhenEmpty
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getSourceDirs();

    /** 编译类路径，使处理器能解析项目中引用的类型 */
    @InputFiles
    @Classpath
    public abstract ConfigurableFileCollection getCompileClasspath();

    /** 注解处理器类路径（包含 AutoCodecProcessor） */
    @InputFiles
    @Classpath
    public abstract ConfigurableFileCollection getProcessorClasspath();

    /** 输出目录（.java 源文件），默认 build/generated/sources/decuminum */
    @OutputDirectory
    public abstract DirectoryProperty getOutputDir();

    @TaskAction
    public void decuminum() throws IOException {
        // ── 1. 收集所有 .java 源文件 ──────────────────────────────────────
        List<File> sourceFiles = new ArrayList<>();
        for (File dir : getSourceDirs().getFiles()) {
            collectJavaFiles(dir, sourceFiles);
        }
        if (sourceFiles.isEmpty()) {
            getLogger().lifecycle("[decuminum] 未找到 Java 源文件，跳过。");
            return;
        }

        // ── 2. 准备输出目录 ───────────────────────────────────────────────
        File outputDir = getOutputDir().get().getAsFile();
        outputDir.mkdirs();

        // ── 3. 获取系统 Java 编译器 ──────────────────────────────────────
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException(
                "[decuminum] 找不到 Java 编译器。请确认 Gradle 使用的是 JDK 而非 JRE。"
            );
        }

        // ── 4. 组装编译选项 ──────────────────────────────────────────────
        List<String> options = new ArrayList<>();
        options.add("-proc:only");
        options.add("-implicit:none");

        // --add-exports 让 javac 内部的注解处理器能访问 jdk.compiler 内部 API
        String[] addExports = {
            "jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
            "jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED",
            "jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED",
            "jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED",
            "jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED",
            "jdk.compiler/com.sun.tools.javac.model=ALL-UNNAMED",
            "jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED",
            "jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED",
        };
        for (String exp : addExports) {
            options.add("--add-exports");
            options.add(exp);
        }

        String cp = getCompileClasspath().getAsPath();
        if (!cp.isEmpty()) {
            options.add("-cp");
            options.add(cp);
        }

        String procPath = getProcessorClasspath().getAsPath();
        if (!procPath.isEmpty()) {
            options.add("-processorpath");
            options.add(procPath);
        }

        // ── 5. 执行编译（仅注解处理） ────────────────────────────────────
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StringWriter extraOutput = new StringWriter();

        // 捕获注解处理后的 AST
        Map<String, JCCompilationUnit> capturedUnits = new LinkedHashMap<>();

        try (StandardJavaFileManager fm =
                 compiler.getStandardFileManager(diagnostics, null, null)) {

            Iterable<? extends JavaFileObject> fileObjects =
                fm.getJavaFileObjects(sourceFiles.toArray(new File[0]));

            JavaCompiler.CompilationTask task = compiler.getTask(
                new PrintWriter(extraOutput), fm, diagnostics,
                options, null, fileObjects);

            // 注册 TaskListener 在注解处理完成后捕获修改过的编译单元
            if (task instanceof JavacTask javacTask) {
                javacTask.addTaskListener(new TaskListener() {
                    @Override
                    public void started(TaskEvent e) {}

                    @Override
                    public void finished(TaskEvent e) {
                        if (e.getKind() == TaskEvent.Kind.ANNOTATION_PROCESSING
                            && e.getCompilationUnit() instanceof JCCompilationUnit jcUnit
                            && jcUnit.sourcefile != null) {
                            capturedUnits.put(jcUnit.sourcefile.getName(), jcUnit);
                        }
                    }
                });
            }

            task.call();
        }

        // ── 6. 将修改后的 AST 以 Pretty 打印机写回 .java 文件 ───────────
        int fileCount = 0;
        for (JCCompilationUnit jcUnit : capturedUnits.values()) {
            // 找到顶层类
            JCClassDecl classDecl = null;
            for (JCTree def : jcUnit.defs) {
                if (def instanceof JCClassDecl cd) {
                    classDecl = cd;
                    break;
                }
            }
            if (classDecl == null) continue;

            // 计算输出路径
            String packageName = jcUnit.packge != null
                ? jcUnit.packge.fullname.toString()
                : "";
            String className = classDecl.name.toString();
            String relativePath = packageName.isEmpty()
                ? className + ".java"
                : packageName.replace('.', '/') + "/" + className + ".java";
            File outFile = new File(outputDir, relativePath);
            outFile.getParentFile().mkdirs();

            // Pretty-print
            try (PrintWriter pw = new PrintWriter(
                     new OutputStreamWriter(
                         new FileOutputStream(outFile), StandardCharsets.UTF_8))) {
                Pretty pretty = new Pretty(pw, true);
                for (JCTree def : jcUnit.defs) {
                    pretty.printStat(def);
                }
                pw.flush();
                fileCount++;
            }
        }

        // ── 7. 汇报诊断信息 ──────────────────────────────────────────────
        int errorCount = 0;
        for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) {
            String msg = d.getMessage(null);
            switch (d.getKind()) {
                case ERROR -> {
                    getLogger().error("[decuminum] {}", msg);
                    errorCount++;
                }
                case WARNING, MANDATORY_WARNING ->
                    getLogger().warn("[decuminum] {}", msg);
                default ->
                    getLogger().info("[decuminum] {}", msg);
            }
        }
        String extra = extraOutput.toString().trim();
        if (!extra.isEmpty()) {
            getLogger().info("[decuminum] javac output: {}", extra);
        }

        if (errorCount > 0) {
            getLogger().warn("[decuminum] 完成，但存在 {} 个编译错误", errorCount);
        }
        getLogger().lifecycle("[decuminum] ✅ 完成，共生成 {} 个 .java 文件 → {}",
            fileCount, outputDir.getAbsolutePath());
    }

    // ── 工具方法 ──────────────────────────────────────────────────────────

    private void collectJavaFiles(File dir, List<File> result) {
        if (dir == null || !dir.isDirectory()) return;
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File f : children) {
            if (f.isDirectory()) {
                collectJavaFiles(f, result);
            } else if (f.getName().endsWith(".java")) {
                result.add(f);
            }
        }
    }
}

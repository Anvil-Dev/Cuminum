package dev.anvilcraft.resource.cuminum.gradle;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.*;

import javax.tools.*;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * 调用 Cuminum 注解处理器并将编译后的 .class 文件写出到 outputDir。
 *
 * <p>Cuminum 使用 Lombok 风格的 AST 注入方式，直接在注解所在类中生成
 * {@code CODEC} / {@code MAP_CODEC} 字段，不产生额外的源文件。
 * 因此本任务改为执行完整编译（而非 delombok 式的 -proc:only），
 * 输出的 .class 文件包含注入的字段。</p>
 */
public abstract class DecuminumTask extends DefaultTask {

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

    /** 编译输出目录（.class 文件），默认 build/decuminum */
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
        //  正常的完整编译（含注解处理），Cuminum 会在 AST 中注入字段
        //  -d <dir>            编译后的 .class 文件写到此目录
        //  -implicit:none      不隐式编译被引用的类
        //  -cp / -processorpath 指定两段类路径
        List<String> options = new ArrayList<>();
        options.add("-implicit:none");
        options.add("-d");
        options.add(outputDir.getAbsolutePath());

        // javac 需要访问内部模块的 --add-exports
        options.add("--add-exports");
        options.add("jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED");
        options.add("--add-exports");
        options.add("jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED");
        options.add("--add-exports");
        options.add("jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED");
        options.add("--add-exports");
        options.add("jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED");
        options.add("--add-exports");
        options.add("jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED");
        options.add("--add-exports");
        options.add("jdk.compiler/com.sun.tools.javac.model=ALL-UNNAMED");
        options.add("--add-exports");
        options.add("jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED");

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

        // ── 5. 执行编译 ──────────────────────────────────────────────────
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StringWriter extraOutput = new StringWriter();

        try (StandardJavaFileManager fm = compiler.getStandardFileManager(diagnostics, null, null)) {
            Iterable<? extends JavaFileObject> units =
                fm.getJavaFileObjects(sourceFiles.toArray(new File[0]));

            JavaCompiler.CompilationTask task =
                compiler.getTask(new PrintWriter(extraOutput), fm, diagnostics, options, null, units);

            task.call();
        }

        // ── 6. 汇报诊断信息 ──────────────────────────────────────────────
        int errorCount = 0;
        for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) {
            String msg = d.getMessage(null);
            switch (d.getKind()) {
                case ERROR:
                    getLogger().error("[decuminum] {}", msg);
                    errorCount++;
                    break;
                case WARNING:
                case MANDATORY_WARNING:
                    getLogger().warn("[decuminum] {}", msg);
                    break;
                default:
                    getLogger().info("[decuminum] {}", msg);
            }
        }
        String extra = extraOutput.toString().trim();
        if (!extra.isEmpty()) {
            getLogger().info("[decuminum] javac output: {}", extra);
        }

        // ── 7. 输出结果 ──────────────────────────────────────────────────
        int classCount = countClassFiles(outputDir);
        if (errorCount > 0) {
            getLogger().warn("[decuminum] 完成，但存在 {} 个编译错误", errorCount);
        }
        getLogger().lifecycle("[decuminum] ✅ 完成，共编译 {} 个 .class 文件 → {}",
            classCount, outputDir.getAbsolutePath());
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

    private int countClassFiles(File dir) {
        if (dir == null || !dir.isDirectory()) return 0;
        int count = 0;
        File[] children = dir.listFiles();
        if (children == null) return count;
        for (File f : children) {
            if (f.isDirectory()) count += countClassFiles(f);
            else if (f.getName().endsWith(".class")) count++;
        }
        return count;
    }
}

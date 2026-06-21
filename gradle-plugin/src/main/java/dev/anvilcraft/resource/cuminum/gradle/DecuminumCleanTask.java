package dev.anvilcraft.resource.cuminum.gradle;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;
import java.io.File;

/**
 * 清理 decuminum 编译输出的 .class 文件。
 */
public abstract class DecuminumCleanTask extends DefaultTask {

    @InputDirectory
    @Optional
    public abstract DirectoryProperty getOutputDir();

    @Inject
    public abstract FileSystemOperations getFs();

    @TaskAction
    public void cleanDecuminum() {
        File outputDir = getOutputDir().get().getAsFile();

        if (!outputDir.exists()) {
            getLogger().info("[decuminumClean] 输出目录不存在，无需清理");
            return;
        }

        getLogger().lifecycle("[decuminumClean] 删除 {}", outputDir.getAbsolutePath());
        getFs().delete(spec -> spec.delete(outputDir));
        getLogger().lifecycle("[decuminumClean] ✅ 清理完成");
    }
}

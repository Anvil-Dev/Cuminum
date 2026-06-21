package dev.anvilcraft.resource.cuminum.gradle;

import org.gradle.api.Project;
import org.gradle.api.file.DirectoryProperty;

import javax.inject.Inject;

/**
 * Decuminum 插件的扩展配置
 */
public abstract class DecuminumExtension {

    @Inject
    public DecuminumExtension(Project project) {
        // 默认输出目录: build/decuminum
        getOutputDir().convention(
            project.getLayout().getBuildDirectory().dir("decuminum")
        );
    }

    /**
     * 生成的源文件输出目录，默认为 build/decuminum
     */
    public abstract DirectoryProperty getOutputDir();
}

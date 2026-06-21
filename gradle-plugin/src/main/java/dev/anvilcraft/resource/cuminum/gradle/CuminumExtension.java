package dev.anvilcraft.resource.cuminum.gradle;

import org.gradle.api.Project;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;

import javax.inject.Inject;

/**
 * Cuminum 插件的扩展配置
 */
public abstract class CuminumExtension {

    @Inject
    public CuminumExtension(Project project) {
        getOutputDir().convention(
            project.getLayout().getBuildDirectory().dir("generated/sources/decuminum")
        );
    }

    /**
     * cuminum-processor 的版本号，默认与插件版本一致。
     */
    public abstract Property<String> getVersion();

    /**
     * decuminum 生成的 .java 源文件输出目录，默认为 build/generated/sources/decuminum
     */
    public abstract DirectoryProperty getOutputDir();
}

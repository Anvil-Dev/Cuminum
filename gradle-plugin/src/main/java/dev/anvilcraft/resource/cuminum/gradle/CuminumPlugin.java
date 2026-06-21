package dev.anvilcraft.resource.cuminum.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;

/**
 * Gradle 插件：提供 decuminum 任务，实际调用 Cuminum 注解处理器并将生成的源码写出
 * 类似于 Lombok 的 delombok 功能
 */
public class CuminumPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        project.getPlugins().apply(JavaPlugin.class);

        CuminumExtension extension = project.getExtensions()
            .create("decuminum", CuminumExtension.class, project);

        project.getTasks().register("decuminum", CuminumTask.class, task -> {
            task.setGroup("cuminum");
            task.setDescription("调用 Cuminum 注解处理器并将生成的源码写出（类似 Lombok delombok）");

            SourceSetContainer sourceSets = project.getExtensions()
                .getByType(SourceSetContainer.class);
            SourceSet main = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME);

            // 主源集的 Java 源文件目录
            task.getSourceDirs().from(main.getJava().getSourceDirectories());

            // 编译类路径（用于让处理器能解析用到的类）
            task.getCompileClasspath().from(main.getCompileClasspath());

            // 注解处理器类路径（annotationProcessor 配置 + 插件自身携带的 processor）
            task.getProcessorClasspath().from(
                project.getConfigurations()
                    .getByName(JavaPlugin.ANNOTATION_PROCESSOR_CONFIGURATION_NAME)
            );
            // 同时把插件自己的 processor jar 也加进去（开发者未必在项目里显式声明了 processor）
            task.getProcessorClasspath().from(
                CuminumPlugin.class.getProtectionDomain().getCodeSource().getLocation()
            );

            task.getOutputDir().set(extension.getOutputDir());
        });

        project.getTasks().register("decuminumClean", CuminumCleanTask.class, task -> {
            task.setGroup("cuminum");
            task.setDescription("清理 decuminum 生成的源码");
            task.getOutputDir().set(extension.getOutputDir());
        });
    }
}

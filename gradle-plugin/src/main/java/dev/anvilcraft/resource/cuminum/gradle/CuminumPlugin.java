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
            .create("cuminum", CuminumExtension.class, project);

        // Automatically add cuminum-processor dependency
        project.afterEvaluate(p -> {
            String version = extension.getVersion().getOrElse("1.0.0");
            String notation = "dev.anvilcraft.resource:cuminum-processor:" + version;
            p.getDependencies().add("implementation", notation);
            p.getDependencies().add("annotationProcessor", notation);
        });

        project.getTasks().register("decuminum", CuminumTask.class, task -> {
            task.setGroup("cuminum");
            task.setDescription("调用 Cuminum 注解处理器并将生成的源文件写出（类似 Lombok delombok）");

            SourceSetContainer sourceSets = project.getExtensions()
                .getByType(SourceSetContainer.class);
            SourceSet main = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME);

            task.getSourceDirs().from(main.getJava().getSourceDirectories());

            task.getCompileClasspath().from(main.getCompileClasspath());

            task.getProcessorClasspath().from(
                project.getConfigurations()
                    .getByName(JavaPlugin.ANNOTATION_PROCESSOR_CONFIGURATION_NAME)
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

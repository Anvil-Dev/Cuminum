plugins {
    id("java")
    id("maven-publish")
    id("org.jetbrains.kotlin.jvm") version "2.1.20"
    id("org.jetbrains.intellij.platform") version "2.10.2"
    id("io.freefair.lombok") version "8.7.1"
}

group = "dev.anvilcraft.resource"
version = "1.0.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
    implementation("com.google.code.findbugs:jsr305:3.0.2")
    intellijPlatform {
        intellijIdea("2025.2.4")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)

        // Add plugin dependencies for compilation here:


        bundledPlugin("com.intellij.java")
        bundledPlugin("org.jetbrains.kotlin")
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "252.25557"
        }

        changeNotes = """
            Initial version
        """.trimIndent()
    }
}

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

val sourcesJar by tasks.register<Jar>("sourcesJar") {
    description = "gen sources jar file"
    from(tasks.delombok.map { outputs.files })
    dependsOn(tasks.delombok)
    archiveClassifier = "sources"
}

artifacts {
    add("archives", tasks.jar)
    add("archives", sourcesJar)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = project.group.toString()
            artifactId = project.base.archivesName.get()
            version = project.version.toString()
            from(components["java"])
            artifact(sourcesJar)
        }
    }

    repositories {
        val MAVEN_URL = System.getenv("MAVEN_URL")
        if (MAVEN_URL != null) {
            maven {
                url = uri(MAVEN_URL)
                credentials {
                    username = System.getenv("MAVEN_USERNAME")
                    password = System.getenv("MAVEN_PASSWORD")
                }
            }
        }
    }
}

lombok {
    version = "1.18.44"
}

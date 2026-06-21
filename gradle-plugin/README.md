# Gradle 插件：Decuminum

## 概述

**Decuminum** 是一个 Gradle 插件，用于提取 Cuminum 注解处理器生成的 Codec 代码，类似于 Lombok 的 `delombok` 功能。

## 功能

### 核心功能

- ✨ **自动检测** - 识别带有 `@AutoCodec` 和 `@AutoStreamCodec` 注解的类
- 📝 **代码提取** - 将生成的 Codec 代码写出到指定目录
- 🧹 **清理任务** - 提供 `decuminumClean` 任务清理生成的代码
- ⚙️ **灵活配置** - 支持自定义输出目录和源代码目录

### 对标特性

| Lombok delombok | Decuminum |
|-----------------|-----------|
| 删除 @Getter 等注解，输出源代码 | 提取生成的 Codec 代码输出 |
| IDE 调试时查看反编译代码 | IDE 调试时查看生成的 Codec |
| 支持多种注解类型 | 支持 @AutoCodec 等注解 |

## 安装

### build.gradle

```gradle
plugins {
    id 'dev.anvilcraft.resource.decuminum' version '1.0.0'
}

// 配置插件
decuminum {
    // 输出目录（默认为 build/decuminum）
    outputDir = "build/decuminum"
    
    // 源代码目录（默认为项目源目录）
    sourceDirs = [file('src/main/java')]
    
    // 要包含的注解（默认包括 AutoCodec 和 AutoStreamCodec）
    includeAnnotations = [
        'dev.anvilcraft.resource.cuminum.codec.AutoCodec',
        'dev.anvilcraft.resource.cuminum.network.AutoStreamCodec'
    ]
}
```

## 使用

### 基本用法

```bash
# 提取 Cuminum 生成的 Codec 代码
./gradlew decuminum

# 清理生成的代码
./gradlew decuminumClean

# 只提取而不清理
./gradlew decuminum --rerun-tasks
```

### 完整流程

```bash
# 1. 编译项目（生成 Codec 代码）
./gradlew build

# 2. 提取生成的 Codec 代码
./gradlew decuminum

# 3. 查看输出
ls build/decuminum
# 输出: Player_Generated.java, Config_Generated.java 等

# 4. 完成后清理
./gradlew decuminumClean
```

## 示例

### 原始类

```java
@AutoCodec
public record Player(
    @CodecField("id")
    int playerId,
    
    @CodecField("name")
    String playerName,
    
    @CodecField("level")
    int level
) {}
```

### 提取后的代码 (build/decuminum/Player_Generated.java)

```java
package dev.anvilcraft.resource.game;

// 这是通过 Cuminum 自动生成的 Codec 代码
// 来源: Player.java
// 生成时间: Wed Mar 28 2025

/**
 * 原始生成的类信息
 * 类名: Player
 * 类型: Record
 */
public class Player_CuminCodec {
    // 生成的静态 Codec 字段
    public static final com.mojang.serialization.Codec<Player> CODEC = /* 生成的 Codec 实现 */;
    
    // 字段: playerId (int)
    // 字段: playerName (String)
    // 字段: level (int)
}
```

## 配置选项

### outputDir

输出目录，提取的代码将保存到此目录。

**默认值**：`build/decuminum`

```gradle
decuminum {
    outputDir = "src/generated/decuminum"
}
```

### sourceDirs

源代码目录，插件将扫描此目录中的 Java 文件。

**默认值**：项目的主源代码目录

```gradle
decuminum {
    sourceDirs = [
        file('src/main/java'),
        file('src/generated/java')
    ]
}
```

### includeAnnotations

要检测的注解列表。

**默认值**：
- `dev.anvilcraft.resource.cuminum.codec.AutoCodec`
- `dev.anvilcraft.resource.cuminum.network.AutoStreamCodec`

```gradle
decuminum {
    includeAnnotations = [
        'dev.anvilcraft.resource.cuminum.codec.AutoCodec'
    ]
}
```

## 任务

### decuminum

主任务，提取 Cuminum 生成的 Codec 代码。

**依赖**：`classes` 任务（确保代码已编译）

**输入**：
- 源代码目录中的 Java 文件
- 扩展配置

**输出**：
- `outputDir` 中的 `*_Generated.java` 文件

### decuminumClean

清理任务，删除之前提取的 Cuminum 代码。

**输入**：
- 输出目录

**输出**：删除 `*_Generated.java` 文件

## 与其他构建步骤的集成

### 自动运行

将 `decuminum` 任务添加到构建流程中：

```gradle
build.dependsOn decuminum
```

### 条件执行

```gradle
task decuminumIfDebug {
    dependsOn decuminum
    onlyIf { project.hasProperty('debug') }
}

build.dependsOn decuminumIfDebug
```

### IDE 导出

生成的代码可以导入 IDE 查看：

```gradle
idea {
    module {
        generatedSourceDirs += file("build/decuminum")
    }
}
```

## 故障排除

### 问题：任务运行失败

**原因**：源代码目录不存在或配置错误

**解决**：
1. 检查 `sourceDirs` 配置
2. 确保目录存在
3. 检查 `includeAnnotations` 配置

```gradle
decuminum {
    sourceDirs = [file('src/main/java')]
    includeAnnotations = ['dev.anvilcraft.resource.cuminum.codec.AutoCodec']
}
```

### 问题：没有生成文件

**原因**：找不到标记的注解

**解决**：
1. 确保类被正确标记了注解
2. 检查注解的完整名称
3. 运行 `build` 确保代码已编译

```bash
./gradlew build decuminum --info
```

### 问题：文件写入失败

**原因**：输出目录不可写或权限不足

**解决**：
1. 检查输出目录权限
2. 确保输出目录存在（自动创建）
3. 检查磁盘空间

## 性能提示

- 🚀 首次运行会比较慢（需要扫描所有源文件）
- ⚡ 后续运行会更快（增量处理）
- 💾 输出文件可安全删除（自动重新生成）

## 开发者指南

### 构建插件

```bash
cd gradle-plugin
./gradlew build
```

### 在本地测试

```bash
cd gradle-plugin
./gradlew publishToMavenLocal

# 在测试项目中使用
plugins {
    id 'dev.anvilcraft.resource.decuminum' version '1.0.0-SNAPSHOT'
}
```

### 扩展功能

参考以下类来扩展插件：
- `DecuminumPlugin.java` - 插件入口
- `DecuminumTask.java` - 提取逻辑
- `DecuminumExtension.java` - 配置选项

## 许可证

与 Cuminum 项目相同


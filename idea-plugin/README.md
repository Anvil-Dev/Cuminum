# Cuminum IDEA Plugin

## 简介

Cuminum IDEA Plugin 是一个 IntelliJ IDEA 插件，用于增强对 Cuminum 注解处理器（Annotation Processor）生成的 Codec 字段的支持。它将自动生成的类成员（如 `CODEC`、`MAP_CODEC`、`STREAM_CODEC`）加入 IDE 的符号表，提供完整的代码智能支持。

## 核心功能

### ✨ 自动符号补全
在标记了 `@AutoCodec` 或 `@AutoStreamCodec` 的类中，IDE 会自动识别并提示生成的字段：
- `CODEC` - 用于序列化/反序列化
- `MAP_CODEC` - 用于 Map 编码
- `STREAM_CODEC` - 用于网络传输

### 📝 代码补全
输入类名后，按 Ctrl+Space，IDE 会提示可用的 Codec 字段

### 🔍 导航支持
- 点击 CODEC 字段跳转到类定义
- 支持 Ctrl+Click（或 Cmd+Click）快速导航
- 支持"查找使用"功能

### 📚 文档支持
鼠标悬停在 CODEC 字段上，显示该字段的用途说明

### ✅ 类型检查
IDE 会正确识别生成字段的泛型类型，提供类型安全

## 支持的注解

### @AutoCodec
```java
@AutoCodec
public record Player(String name, int level) {}
// 生成: public static final Codec<Player> CODEC;

@AutoCodec(CodecType.MAP_CODEC)
public class Config {}
// 生成: public static final MapCodec<Config> MAP_CODEC;

@AutoCodec(CodecType.BOTH)
public class Settings {}
// 生成: public static final Codec<Settings> CODEC;
//       public static final MapCodec<Settings> MAP_CODEC;
```

### @AutoStreamCodec
```java
@AutoStreamCodec
public record Packet(int id, String data) {}
// 生成: public static final StreamCodec<ByteBuf, Packet> STREAM_CODEC;
```

### @CodecField
```java
@AutoCodec
public class Item {
    @CodecField("item_id")
    private int id;
    
    @CodecField(value = "name", getter = "getName")
    private String itemName;
}
```

### @StreamCodecField
```java
@AutoStreamCodec
public class NetworkData {
    @StreamCodecField
    private int id;
    
    @StreamCodecField(getter = "getData")
    private String data;
}
```

## 安装

### 通过 IDE 市场安装
1. 打开 IntelliJ IDEA
2. 菜单：Settings → Plugins → Marketplace
3. 搜索 "Cuminum"
4. 点击 Install
5. 重启 IDE

### 本地安装
1. 构建插件：
   ```bash
   cd idea-plugin
   ./gradlew buildPlugin
   ```
2. 打开 IntelliJ IDEA
3. 菜单：Settings → Plugins → ⚙️ → Install plugin from disk
4. 选择 `idea-plugin/build/distributions/idea-plugin-*.jar`
5. 重启 IDE

## 快速开始

### 1. 创建带 @AutoCodec 的类

```java
import dev.anvilcraft.resource.cuminum.codec.AutoCodec;
import dev.anvilcraft.resource.cuminum.codec.CodecField;

@AutoCodec
public class Player {
    @CodecField("id")
    private int playerId;
    
    @CodecField("name")
    private String playerName;
    
    // ... 构造函数等
}
```

### 2. IDE 自动识别生成的字段

IDE 会自动显示虚拟字段 `CODEC`，完全支持代码补全和类型检查。

### 3. 使用生成的 Codec

```java
// IDE 会提供类型提示和自动补全
var codec = Player.CODEC;
var json = codec.encodeStart(JsonOps.INSTANCE, player);
```

## 系统要求

- IntelliJ IDEA 2025.2.4 或更高版本
- Java 21+
- Kotlin 插件（通常预装）

## 故障排除

### 问题：CODEC 字段未显示
**原因**：
- 类未正确标记 @AutoCodec
- 项目未正确配置注解处理器

**解决方案**：
1. 确保已添加 cuminum-processor 依赖
2. 确保项目中启用了注解处理
3. 重新加载 IDE：File → Invalidate Caches

### 问题：类型检查显示错误
**原因**：
- 缺少 Codec 类的依赖

**解决方案**：
1. 添加 Minecraft 序列化库依赖
2. 检查项目类路径配置
3. 重新构建项目

### 问题：代码补全不工作
**原因**：
- IDE 缓存过期
- 插件未正确加载

**解决方案**：
1. File → Invalidate Caches → Invalidate and Restart
2. 检查插件是否已启用：Settings → Plugins
3. 查看插件日志：Help → Show Log in Explorer

## 开发者指南

### 编译项目
```bash
cd idea-plugin
./gradlew build
```

### 在本地 IDE 中测试
```bash
./gradlew runIde
```

会启动一个带有插件的 IntelliJ IDEA 实例用于测试。

### 构建发布版本
```bash
./gradlew buildPlugin
```

输出文件：`idea-plugin/build/distributions/idea-plugin-*.jar`

## 项目结构

```
idea-plugin/
├── src/main/kotlin/
│   └── dev/anvilcraft/resource/cuminum/idea/
│       ├── CodecPsiAugmentProvider.kt       # PSI 增强
│       ├── CodecCompletionContributor.kt    # 代码补全
│       ├── CodecNavigationHandler.kt        # 导航和文档
│       ├── CodecFieldIndexer.kt             # 字段索引
│       └── MyToolWindow.kt                  # 工具窗口
├── src/main/resources/
│   └── META-INF/
│       └── plugin.xml                       # 插件配置
├── build.gradle.kts                         # 构建配置
├── README.md                                # 本文件
├── DEVELOPMENT.md                           # 详细开发文档
└── USAGE_EXAMPLE.kt                         # 使用示例
```

## 许可证

该项目采用与 Cuminum 项目相同的许可证。详见 LICENSE 文件。

## 更多信息

- [Cuminum 项目](https://www.anvilcraft.dev)
- [详细开发文档](./DEVELOPMENT.md)
- [使用示例](./USAGE_EXAMPLE.kt)
- [IntelliJ Platform 开发文档](https://plugins.jetbrains.com/docs/intellij/)

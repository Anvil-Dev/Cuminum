# Cuminum IDEA Plugin - 开发文档

## 概述

Cuminum IDEA Plugin 是一个用于 IntelliJ IDEA 的插件，用于增强对 Cuminum processor 生成的 Codec 类和字段的支持。该插件自动将 processor 注解生成的类成员加入 IDE 的符号表，提供智能补全、导航和文档支持。

## 功能特性

### 1. PSI 增强提供者 (CodecPsiAugmentProvider)

**功能**: 向被 `@AutoCodec` 和 `@AutoStreamCodec` 标记的类自动注入虚拟字段到符号表。

**支持的注解**:
- `@AutoCodec`: 支持生成 Codec、MapCodec 或两者
  - `@AutoCodec(CodecType.CODEC)` - 生成 `public static final Codec<T> CODEC`
  - `@AutoCodec(CodecType.MAP_CODEC)` - 生成 `public static final MapCodec<T> MAP_CODEC`
  - `@AutoCodec(CodecType.BOTH)` - 同时生成两个字段

- `@AutoStreamCodec`: 生成 `public static final StreamCodec<ByteBuf, T> STREAM_CODEC`

**工作原理**:
```
用户类 @AutoCodec
    ↓ (IDE 扫描)
CodecPsiAugmentProvider
    ↓ (创建虚拟字段)
IDE 符号表: CODEC / MAP_CODEC / STREAM_CODEC
    ↓ (用户可以)
- 代码补全
- 类型检查
- 导航跳转
```

### 2. 代码补全提供者 (CodecCompletionContributor)

**功能**: 在 AutoCodec 或 AutoStreamCodec 类中提供自动补全建议。

**示例**:
```java
@AutoCodec
public class MyData {
    private int id;
    private String name;
}

// 在类中输入时，IDE 会建议：
// CODEC : Codec<MyData>
// MAP_CODEC : MapCodec<MyData>  (如果是 BOTH 类型)
```

### 3. 导航处理器 (CodecNavigationHandler)

**功能**: 支持点击 CODEC/MAP_CODEC/STREAM_CODEC 字段跳转到包含类的定义。

**使用**:
- 在代码中按 Ctrl+Click（Cmd+Click on Mac）点击 CODEC 字段
- IDE 会导航到该字段所在的类

### 4. 文档提供者 (CodecDocumentationProvider)

**功能**: 为生成的 Codec 字段提供快速文档显示。

**示例**:
```
当鼠标悬停在 CODEC 字段上时，显示：
"生成的 Codec 字段，用于 MyData 的序列化和反序列化"
```

### 5. 字段索引工具 (CodecFieldIndexer)

**功能**: 提供工具方法来查询和解析 `@CodecField` 和 `@StreamCodecField` 注解。

**API**:
```kotlin
// 获取类中所有 @CodecField 标记的字段
CodecFieldIndexer.getCodecFields(psiClass)

// 获取类中所有 @StreamCodecField 标记的字段
CodecFieldIndexer.getStreamCodecFields(psiClass)

// 检查是否是 AutoCodec 类
CodecFieldIndexer.isAutoCodecClass(psiClass)

// 检查是否是 AutoStreamCodec 类
CodecFieldIndexer.isAutoStreamCodecClass(psiClass)

// 获取生成的 Codec 类的全限定名
CodecFieldIndexer.getGeneratedCodecClassName(psiClass)
```

## 实现细节

### 虚拟字段的创建

使用 `LightFieldBuilder` 创建轻量级的虚拟字段，不需要物理文件支持：

```kotlin
val fieldBuilder = LightFieldBuilder(
    psiClass.getManager(),
    "CODEC",  // 字段名
    createCodecType(psiClass)  // 字段类型
)
fieldBuilder.containingClass = psiClass
fieldBuilder.setModifiers(PsiModifier.PUBLIC, PsiModifier.STATIC, PsiModifier.FINAL)
```

### 泛型类型处理

正确处理 Codec 的泛型参数：

```kotlin
// 获取 Codec 类
val codecClass = JavaPsiFacade.getInstance(project)
    .findClass("com.mojang.serialization.Codec", scope)

// 创建泛型参数
val typeArgument = factory.createType(psiClass)

// 组合成 Codec<MyClass>
factory.createType(codecClass, typeArgument)
```

## 集成到 IDE

所有功能都通过在 `plugin.xml` 中注册扩展点实现：

```xml
<!-- PSI 增强 -->
<lang.psiAugmentProvider implementation="dev.anvilcraft.resource.cuminum.idea.CodecPsiAugmentProvider"/>

<!-- 代码补全 -->
<completion.contributor language="JAVA" 
    implementationClass="dev.anvilcraft.resource.cuminum.idea.CodecCompletionContributor"/>

<!-- 导航 -->
<gotoDeclarationHandler implementation="dev.anvilcraft.resource.cuminum.idea.CodecNavigationHandler"/>

<!-- 文档 -->
<lang.documentationProvider language="JAVA" 
    implementationClass="dev.anvilcraft.resource.cuminum.idea.CodecDocumentationProvider"/>
```

## 支持的 Codec 类型

### 1. Codec
```java
@AutoCodec
public record Player(String name, int level) {}

// 自动生成
public static final Codec<Player> CODEC = ...
```

### 2. MapCodec
```java
@AutoCodec(CodecType.MAP_CODEC)
public record Config(int timeout, String host) {}

// 自动生成
public static final MapCodec<Config> MAP_CODEC = ...
```

### 3. StreamCodec（网络传输）
```java
@AutoStreamCodec
public record NetworkPacket(int id, String data) {}

// 自动生成
public static final StreamCodec<ByteBuf, NetworkPacket> STREAM_CODEC = ...
```

## 字段注解支持

### @CodecField - 用于 Codec

```java
@AutoCodec
public class Item {
    @CodecField("item_id")  // JSON 键名
    private int id;
    
    @CodecField(value = "name", getter = "getName")
    private String itemName;
}
```

属性：
- `value`: JSON 序列化时使用的键名（默认为字段名）
- `getter`: 自定义 getter 方法名
- `codec`: 使用 `@UseCodec` 指定自定义 codec

### @StreamCodecField - 用于 StreamCodec

```java
@AutoStreamCodec
public class Packet {
    @StreamCodecField(getter = "getId")
    private int id;
    
    @StreamCodecField
    private String data;
}
```

属性：
- `getter`: 自定义 getter 方法名
- `codec`: 使用 `@UseCodec` 指定自定义 codec

## 编译和打包

### 编译
```bash
cd idea-plugin
./gradlew build
```

### 构建可分发的 JAR
```bash
./gradlew buildPlugin
```

输出位置: `idea-plugin/build/distributions/idea-plugin-1.0.0.jar`

### 在本地运行测试
```bash
./gradlew runIde
```

会启动一个带有插件的 IntelliJ IDEA 实例。

## 开发建议

### 添加新的增强功能

1. 创建新的 `PsiAugmentProvider` 子类
2. 实现 `getAugments()` 方法
3. 在 `plugin.xml` 中注册

### 调试技巧

1. 使用 `runIde` 任务运行 IDE 进行调试
2. 在代码中添加 `Logger` 记录信息：
   ```kotlin
   val logger = logger<CodecPsiAugmentProvider>()
   logger.info("调试信息")
   ```
3. 使用 IDE 的 PSI Viewer 检查 PSI 树结构

## 已知限制

1. 仅支持 Java 和 Kotlin 语言
2. 要求已安装 Java 和 Kotlin 模块
3. 生成的虚拟字段只在 IDE 中显示，不会出现在编译结果中（符合预期）
4. 需要 IntelliJ IDEA 2025.2.4 或更高版本

## 故障排除

### 问题：CODEC 字段未出现在补全中
**解决方案**:
1. 确保类被正确标记了 `@AutoCodec` 或 `@AutoStreamCodec`
2. 检查项目中是否包含了 cuminum-processor 的依赖
3. 重新加载 IDE（File > Invalidate Caches）

### 问题：类型检查显示错误
**解决方案**:
1. 确保 `com.mojang.serialization.Codec` 在类路径中
2. 检查项目配置是否正确
3. 检查插件日志寻找更多信息

## 相关文件

- `CodecPsiAugmentProvider.kt` - PSI 增强提供者
- `CodecCompletionContributor.kt` - 代码补全
- `CodecNavigationHandler.kt` - 导航和文档
- `CodecFieldIndexer.kt` - 字段索引工具
- `plugin.xml` - 插件配置

## 许可证

同 Cuminum 项目许可证


package dev.anvilcraft.cuminum.processor;

import com.google.auto.service.AutoService;
import com.palantir.javapoet.AnnotationSpec;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.FieldSpec;
import com.palantir.javapoet.JavaFile;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeSpec;
import dev.anvilcraft.cuminum.CodecIgnore;
import dev.anvilcraft.cuminum.UseCodec;
import dev.anvilcraft.cuminum.codec.AutoCodec;
import dev.anvilcraft.cuminum.codec.CodecField;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;

@AutoService(Processor.class)
@SupportedAnnotationTypes("dev.anvilcraft.cuminum.codec.AutoCodec")
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public class AutoCodecProcessor extends AbstractProcessor {
    // 定义一些常用的类名，方便 JavaPoet 调用
    private static final ClassName CODEC = ClassName.get("com.mojang.serialization", "Codec");
    private static final ClassName MAP_CODEC = ClassName.get("com.mojang.serialization", "MapCodec");
    private static final ClassName RECORD_CODEC_BUILDER = ClassName.get("com.mojang.serialization.codecs", "RecordCodecBuilder");

    @Override
    public boolean process(Set<? extends TypeElement> annotations, @NonNull RoundEnvironment roundEnv) {
        for (Element element : roundEnv.getElementsAnnotatedWith(AutoCodec.class)) {
            AutoCodec annotation = element.getAnnotation(AutoCodec.class);
            if (element.getKind() == ElementKind.CLASS || element.getKind() == ElementKind.RECORD) {
                try {
                    generateCodec((TypeElement) element, annotation != null ? annotation.value() : AutoCodec.CodecType.CODEC);
                } catch (Exception e) {
                    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Cuminum 编译失败: " + e.getMessage());
                    for (StackTraceElement stackTraceElement : e.getStackTrace()) {
                        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, stackTraceElement.toString());
                    }
                }
            }
        }
        return true;
    }

    private void generateCodec(TypeElement typeElement, AutoCodec.CodecType codecType) throws IOException {
        String packageName = processingEnv.getElementUtils().getPackageOf(typeElement).getQualifiedName().toString();
        String className = typeElement.getSimpleName().toString();

        // 生成的辅助类名，例如 MyData_CuminCodec
        String generatedClassName = className + "$CuminCodec";

        List<CodeBlock> fieldBlocks = new ArrayList<>();
        boolean isRecord = typeElement.getKind() == ElementKind.RECORD;

        // 获取成员：如果是 record 则遍历组件，否则遍历字段
        List<? extends Element> members = isRecord ? typeElement.getRecordComponents() : typeElement.getEnclosedElements();

        for (Element member : members) {
            // 过滤非字段成员（针对普通类）
            if (!isRecord && member.getKind() != ElementKind.FIELD) continue;

            // 过滤 static 和 @CodecIgnore
            if (member.getModifiers().contains(Modifier.STATIC) || member.getAnnotation(CodecIgnore.class) != null) {
                continue;
            }

            // 类型信息（如果是 RecordComponent，则需要转为其对应的类型）
            TypeMirror typeMirror = member.asType();
            String fieldName = member.getSimpleName().toString();

            // 1. 获取 JSON 键名
            CodecField anno = member.getAnnotation(CodecField.class);
            String jsonKey = (anno != null && !anno.value().isEmpty()) ? anno.value() : fieldName;

            // 2. 处理容器类型 (List, Optional, Map - 复用之前的逻辑)
            // 确定 Codec 引用 (逻辑优先级：手动指定 > 自动识别)
            CodeBlock codecRef = null;
            boolean back = false;
            if (anno != null && (anno.codec() instanceof UseCodec useCodec)) {
                // 如果用户手动指定了 codec 表达式，直接作为 Literal 写入
                String canonicalName;
                try {
                    Class<?> clazz = useCodec.value();
                    canonicalName = clazz.getCanonicalName();
                } catch (MirroredTypeException e) {
                    TypeMirror mirror = e.getTypeMirror();
                    canonicalName = mirror.toString();
                }
                if (!canonicalName.isEmpty() && !canonicalName.equals("java.lang.Void")) {
                    String memberName = useCodec.member().isEmpty() ? "CODEC" : useCodec.member();
                    codecRef = CodeBlock.of("$L", canonicalName + "." + memberName);
                }
            } else {
                // 否则走之前的自动递归解析逻辑
                codecRef = resolveCodec(typeMirror);
            }
            if (codecRef == null) codecRef = resolveCodec(typeMirror);
            // 3. 核心：获取 Getter 引用
            CodeBlock getterExpr = getGetterExpression(typeElement, member, isRecord);

            // 4. 处理可选/可为空逻辑
            boolean isOptionalType = isRawTypeString(typeMirror, "java.util.Optional");
            boolean isNullable = hasNullableAnnotation(member);

            if (isOptionalType) {
                // 场景 A: 字段类型本身就是 Optional<T>
                fieldBlocks.add(CodeBlock.of("$L.optionalFieldOf($S).forGetter($L)", codecRef, jsonKey, getterExpr));
            } else if (isNullable) {
                // 场景 B: 字段标记了 @Nullable T
                // 使用 optionalFieldOf(key, defaultValue) 的重载，将 null 作为默认值
                fieldBlocks.add(CodeBlock.of("$L.optionalFieldOf($S, null).forGetter($L)", codecRef, jsonKey, getterExpr));
            } else {
                // 场景 C: 普通必填字段
                fieldBlocks.add(CodeBlock.of("$L.fieldOf($S).forGetter($L)", codecRef, jsonKey, getterExpr));
            }
        }

        // 构建 @Generated 注解
        AnnotationSpec generatedAnnotation = AnnotationSpec.builder(ClassName.get("javax.annotation.processing", "Generated"))
            .addMember("value", "$S", "dev.anvilcraft.cuminum.processor.AutoCodecProcessor")
            .build();

        TypeSpec.Builder builder = TypeSpec.classBuilder(generatedClassName)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addAnnotation(generatedAnnotation);
        if (codecType == AutoCodec.CodecType.CODEC) {
            CodeBlock codecInitBlock = CodeBlock.builder()
                .add("$T.create(instance -> instance.group(\n", RECORD_CODEC_BUILDER)
                .indent()
                .add(CodeBlock.join(fieldBlocks, ",\n"))
                .unindent()
                .add("\n).apply(instance, $T::new))", typeElement) // 这里调用构造函数
                .build();
            FieldSpec instanceField = FieldSpec.builder(
                ParameterizedTypeName.get(CODEC, TypeName.get(typeElement.asType())),
                "CODEC",
                Modifier.PUBLIC,
                Modifier.STATIC,
                Modifier.FINAL
            ).initializer(codecInitBlock).build();
            builder.addField(instanceField);
        } else {
            CodeBlock mapCodecInitBlock = CodeBlock.builder()
                .add("$T.mapCodec(instance -> instance.group(\n", RECORD_CODEC_BUILDER)
                .indent()
                .add(CodeBlock.join(fieldBlocks, ",\n"))
                .unindent()
                .add("\n).apply(instance, $T::new))", typeElement) // 这里调用构造函数
                .build();
            FieldSpec mapCodecInstanceField = FieldSpec.builder(
                ParameterizedTypeName.get(MAP_CODEC, TypeName.get(typeElement.asType())),
                "MAP_CODEC",
                Modifier.PUBLIC,
                Modifier.STATIC,
                Modifier.FINAL
            ).initializer(mapCodecInitBlock).build();
            builder.addField(mapCodecInstanceField);
            if (codecType == AutoCodec.CodecType.BOTH) {
                CodeBlock codecInitBlock = CodeBlock.builder()
                    .add("MAP_CODEC.codec()")
                    .build();
                FieldSpec instanceField = FieldSpec.builder(
                    ParameterizedTypeName.get(CODEC, TypeName.get(typeElement.asType())),
                    "CODEC",
                    Modifier.PUBLIC,
                    Modifier.STATIC,
                    Modifier.FINAL
                ).initializer(codecInitBlock).build();
                builder.addField(instanceField);
            }
        }
        TypeSpec codecClass = builder.addJavadoc("Generated by Cuminum. Do not modify.\n").build();
        JavaFile.builder(packageName, codecClass).build().writeTo(processingEnv.getFiler());
    }

    /**
     * 检查 TypeMirror 是否属于某个特定的原始类型（如 java.util.List）
     */
    private boolean isRawTypeString(TypeMirror type, String rawTypeCanonicalName) {
        if (type instanceof javax.lang.model.type.DeclaredType declaredType) {
            Element element = declaredType.asElement();
            if (element instanceof TypeElement typeElement) {
                return typeElement.getQualifiedName().contentEquals(rawTypeCanonicalName);
            }
        }
        return false;
    }

    private boolean hasNullableAnnotation(@NonNull Element element) {
        return element.getAnnotationMirrors().stream().anyMatch(mirror -> {
            String annotationType = mirror.getAnnotationType().toString();
            return annotationType.endsWith(".Nullable"); // 匹配 javax.annotation.Nullable, org.jetbrains.annotations.Nullable 等
        });
    }

    /**
     * 获取泛型的第一个参数类型。例如从 List<String> 中提取 String
     */
    private TypeMirror getGenericArgument(TypeMirror type, int index) {
        if (type instanceof DeclaredType declaredType) {
            List<? extends TypeMirror> args = declaredType.getTypeArguments();
            if (args.size() > index) {
                return args.get(index);
            }
        }
        // 兜底返回 Object
        return processingEnv.getElementUtils().getTypeElement("java.lang.Object").asType();
    }

    private @NonNull CodeBlock getGetterExpression(@NonNull TypeElement clazz, @NonNull Element member, boolean isRecord) {
        String name = member.getSimpleName().toString();
        TypeName className = TypeName.get(clazz.asType());

        if (isRecord) {
            // Record 的访问器就是方法名本身，例如 PlayerData::name
            return CodeBlock.of("$T::$L", className, name);
        }

        // --- 以下为普通类的逻辑 ---
        if (!member.getModifiers().contains(Modifier.PRIVATE)) {
            return CodeBlock.of("obj -> obj.$L", name);
        } else {
            // 尝试推测 Getter 名字 (兼容 Lombok)
            String prefix = member.asType().getKind().name().equals("BOOLEAN") ? "is" : "get";
            String getterName = prefix + capitalize(name);
            return CodeBlock.of("$T::$L", className, getterName);
        }
    }

    private CodeBlock resolveCodec(TypeMirror type) {
        // 1. 处理 Optional<T>
        // 注意：Optional 比较特殊，它的 Codec 其实是它内部 T 的 Codec，
        // 只不过在生成 field 时改用 .optionalFieldOf()
        if (isRawTypeString(type, "java.util.Optional")) {
            TypeMirror innerType = getGenericArgument(type, 0);
            return resolveCodec(innerType);
        }

        // 2. 处理 List<T> -> codecOfT.listOf()
        if (isRawTypeString(type, "java.util.List")) {
            TypeMirror innerType = getGenericArgument(type, 0);
            return CodeBlock.of("$L.listOf()", resolveCodec(innerType));
        }

        // 3. 处理 Map<K, V> -> Codec.unboundedMap(keyCodec, valueCodec)
        if (isRawTypeString(type, "java.util.Map")) {
            TypeMirror keyType = getGenericArgument(type, 0);
            TypeMirror valueType = getGenericArgument(type, 1);
            return CodeBlock.of("$T.unboundedMap($L, $L)", CODEC, resolveCodec(keyType), resolveCodec(valueType));
        }

        // 4. 处理基础类型和常见 Minecraft 类型
        String typeStr = type.toString();
        return switch (typeStr) {
            case "byte", "java.lang.Byte" -> CodeBlock.of("$T.BYTE", CODEC);
            case "short", "java.lang.Short" -> CodeBlock.of("$T.SHORT", CODEC);
            case "char", "java.lang.Character" -> CodeBlock.of("$T.CHAR", CODEC);
            case "int", "java.lang.Integer" -> CodeBlock.of("$T.INT", CODEC);
            case "long", "java.lang.Long" -> CodeBlock.of("$T.LONG", CODEC);
            case "float", "java.lang.Float" -> CodeBlock.of("$T.FLOAT", CODEC);
            case "double", "java.lang.Double" -> CodeBlock.of("$T.DOUBLE", CODEC);
            case "boolean", "java.lang.Boolean" -> CodeBlock.of("$T.BOOL", CODEC);
            case "java.lang.String" -> CodeBlock.of("$T.STRING", CODEC);

            // 自动识别 Minecraft 常用类型 (如果类路径里有这些类的话)
            case "net.minecraft.core.BlockPos" -> CodeBlock.of("$T.CODEC", ClassName.get("net.minecraft.core", "BlockPos"));

            // 5. 处理自定义类型
            default -> {
                Element element = processingEnv.getTypeUtils().asElement(type);
                // 如果该类型也被标记了 @AutoCodec，引用它生成的 CODEC
                if (element != null && element.getAnnotation(AutoCodec.class) != null) {
                    String pkg = processingEnv.getElementUtils().getPackageOf(element).getQualifiedName().toString();
                    String className = element.getSimpleName().toString();
                    yield CodeBlock.of("$T.CODEC", ClassName.get(pkg, className + "_CuminCodec"));
                }
                // 最后的兜底：假设该类内部定义了一个静态的 CODEC 字段
                yield CodeBlock.of("$T.CODEC", TypeName.get(type));
            }
        };
    }

    private @NonNull String capitalize(@NonNull String str) {
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}
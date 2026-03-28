package dev.anvilcraft.resource.cuminum.idea

import com.intellij.psi.*
import com.intellij.psi.augment.PsiAugmentProvider
import com.intellij.psi.impl.light.LightFieldBuilder


class CodecPsiAugmentProvider : PsiAugmentProvider() {
    override fun <Psi : PsiElement?> getAugments(element: PsiElement, type: Class<Psi?>, nameHint: String?): List<Psi?> {

        // 1. 我们只想往类（PsiClass）里注入成员（PsiField）
        if (element !is PsiClass || !type.isAssignableFrom(PsiField::class.java)) {
            return emptyList()
        }
        val psiClass: PsiClass = element
        
        // 2. 检查类是否标记了 @AutoCodec 或 @AutoStreamCodec
        if (!isAnnotatedWithAutoCodec(psiClass) && !isAnnotatedWithAutoStreamCodec(psiClass)) {
            return emptyList()
        }

        // 3. 构造虚拟字段列表
        val fields = ArrayList<Psi?>()
        
        // 根据注解类型生成相应的字段
        val autoCodecAnnotation = psiClass.getAnnotation("dev.anvilcraft.resource.cuminum.codec.AutoCodec")
        if (autoCodecAnnotation != null) {
            // 获取 AutoCodec 的 value 参数（CodecType 枚举）
            val codecType = getCodecType(autoCodecAnnotation)
            when (codecType) {
                "MAP_CODEC" -> {
                    @Suppress("UNCHECKED_CAST")
                    fields.add(createMapCodecField(psiClass) as Psi?)
                }
                "CODEC" -> {
                    @Suppress("UNCHECKED_CAST")
                    fields.add(createCodecField(psiClass) as Psi?)
                }
                "BOTH" -> {
                    @Suppress("UNCHECKED_CAST")
                    fields.add(createMapCodecField(psiClass) as Psi?)
                    @Suppress("UNCHECKED_CAST")
                    fields.add(createCodecField(psiClass) as Psi?)
                }
            }
        }
        
        // 如果有 @AutoStreamCodec 注解，也添加相应字段
        if (isAnnotatedWithAutoStreamCodec(psiClass)) {
            @Suppress("UNCHECKED_CAST")
            fields.add(createStreamCodecField(psiClass) as Psi?)
        }

        return fields
    }

    private fun isAnnotatedWithAutoCodec(psiClass: PsiClass): Boolean {
        return psiClass.hasAnnotation("dev.anvilcraft.resource.cuminum.codec.AutoCodec")
    }
    
    private fun isAnnotatedWithAutoStreamCodec(psiClass: PsiClass): Boolean {
        return psiClass.hasAnnotation("dev.anvilcraft.resource.cuminum.network.AutoStreamCodec")
    }
    
    private fun getCodecType(annotation: PsiAnnotation): String {
        // 获取 @AutoCodec(value = CodecType.CODEC) 中的 value 参数
        val attributeValue = annotation.findAttributeValue("value")
        if (attributeValue is PsiReferenceExpression) {
            val refName = attributeValue.referenceName
            return refName ?: "CODEC"
        }
        return "CODEC"  // 默认值
    }

    private fun createCodecField(psiClass: PsiClass): PsiField {
        // 使用 LightFieldBuilder 创建一个"轻量级"虚拟字段
        val fieldBuilder = LightFieldBuilder(
            psiClass.getManager(),
            "CODEC",  // 注入的字段名
            createCodecType(psiClass) // 字段类型: Codec<ClassName>
        )

        fieldBuilder.containingClass = psiClass
        fieldBuilder.setModifiers(PsiModifier.PUBLIC, PsiModifier.STATIC, PsiModifier.FINAL)
        fieldBuilder.navigationElement = psiClass // 点击跳转到类本身

        return fieldBuilder
    }
    
    private fun createMapCodecField(psiClass: PsiClass): PsiField {
        // 创建 MapCodec 字段
        val fieldBuilder = LightFieldBuilder(
            psiClass.getManager(),
            "MAP_CODEC",  // 注入的字段名
            createMapCodecType(psiClass) // 字段类型: MapCodec<ClassName>
        )

        fieldBuilder.containingClass = psiClass
        fieldBuilder.setModifiers(PsiModifier.PUBLIC, PsiModifier.STATIC, PsiModifier.FINAL)
        fieldBuilder.navigationElement = psiClass

        return fieldBuilder
    }
    
    private fun createStreamCodecField(psiClass: PsiClass): PsiField {
        // 创建 StreamCodec 字段
        val fieldBuilder = LightFieldBuilder(
            psiClass.getManager(),
            "STREAM_CODEC",  // 注入的字段名
            createStreamCodecType(psiClass) // 字段类型: StreamCodec<?, ClassName>
        )

        fieldBuilder.containingClass = psiClass
        fieldBuilder.setModifiers(PsiModifier.PUBLIC, PsiModifier.STATIC, PsiModifier.FINAL)
        fieldBuilder.navigationElement = psiClass

        return fieldBuilder
    }

    private fun createCodecType(psiClass: PsiClass): PsiType {
        val project = psiClass.project
        val factory = JavaPsiFacade.getElementFactory(project)
        val scope = psiClass.resolveScope

        // 1. 查找 Codec 类的 PsiClass
        val codecClass = JavaPsiFacade.getInstance(project)
            .findClass("com.mojang.serialization.Codec", scope)

        if (codecClass == null) {
            // 如果找不到 Codec 类（比如没加依赖），返回一个 fallback 字符串类型
            return factory.createTypeByFQClassName("com.mojang.serialization.Codec", scope)
        }

        // 2. 创建泛型参数类型：即当前类 psiClass 的类型
        val typeArgument: PsiType = factory.createType(psiClass)

        // 3. 组合成 Codec<MyClass>
        return factory.createType(codecClass, typeArgument)
    }
    
    private fun createMapCodecType(psiClass: PsiClass): PsiType {
        val project = psiClass.project
        val factory = JavaPsiFacade.getElementFactory(project)
        val scope = psiClass.resolveScope

        // 查找 MapCodec 类
        val mapCodecClass = JavaPsiFacade.getInstance(project)
            .findClass("com.mojang.serialization.MapCodec", scope)

        if (mapCodecClass == null) {
            return factory.createTypeByFQClassName("com.mojang.serialization.MapCodec", scope)
        }

        val typeArgument: PsiType = factory.createType(psiClass)
        return factory.createType(mapCodecClass, typeArgument)
    }
    
    private fun createStreamCodecType(psiClass: PsiClass): PsiType {
        val project = psiClass.project
        val factory = JavaPsiFacade.getElementFactory(project)
        val scope = psiClass.resolveScope

        // 查找 StreamCodec 类
        val streamCodecClass = JavaPsiFacade.getInstance(project)
            .findClass("net.minecraft.network.codec.StreamCodec", scope)

        if (streamCodecClass == null) {
            // 如果找不到，尝试另一个可能的包
            return factory.createTypeByFQClassName("net.minecraft.network.codec.StreamCodec", scope)
        }

        // StreamCodec 是双参泛型：StreamCodec<ByteBuf, ClassName>
        val byteBuffType = factory.createTypeByFQClassName("io.netty.buffer.ByteBuf", scope)
        val typeArgument: PsiType = factory.createType(psiClass)
        
        return factory.createType(streamCodecClass, byteBuffType, typeArgument)
    }
}
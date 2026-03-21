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
        // 2. 检查类是否标记了 @AutoCodec
        if (!isAnnotatedWithAutoCodec(psiClass)) {
            return emptyList();
        }

        // 3. 构造一个虚拟的静态字段: public static final Codec<ClassName> CODEC
        val fields = ArrayList<Psi?>()
        @Suppress("UNCHECKED_CAST")
        fields.add(createCodecField(psiClass) as Psi?)

        return fields
    }

    private fun isAnnotatedWithAutoCodec(psiClass: PsiClass): Boolean {
        return psiClass.hasAnnotation("dev.anvilcraft.resource.cuminum.AutoCodec")
    }

    private fun createCodecField(psiClass: PsiClass): PsiField {
        // 使用 LightFieldBuilder 创建一个“轻量级”虚拟字段
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
        // createType(PsiClass aClass, PsiType... parameters)
        return factory.createType(codecClass, typeArgument)
    }
}
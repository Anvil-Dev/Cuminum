package dev.anvilcraft.resource.cuminum.idea

import com.intellij.psi.*


/**
 * 用于分析 @CodecField 和 @StreamCodecField 注解的字段信息
 * 这个类提供了访问这些字段的元数据的便利方法
 */
object CodecFieldIndexer {
    
    /**
     * 获取类中所有标记 @CodecField 的字段
     */
    fun getCodecFields(psiClass: PsiClass): List<CodecFieldInfo> {
        val fields = mutableListOf<CodecFieldInfo>()
        
        for (field in psiClass.fields) {
            val annotation = field.getAnnotation("dev.anvilcraft.resource.cuminum.codec.CodecField")
            if (annotation != null) {
                fields.add(CodecFieldInfo(
                    field = field,
                    name = field.name,
                    type = field.type,
                    jsonKey = extractJsonKey(annotation),
                    getter = extractGetter(annotation),
                    codec = extractCodec(annotation)
                ))
            }
        }
        
        return fields
    }
    
    /**
     * 获取类中所有标记 @StreamCodecField 的字段
     */
    fun getStreamCodecFields(psiClass: PsiClass): List<StreamCodecFieldInfo> {
        val fields = mutableListOf<StreamCodecFieldInfo>()
        
        for (field in psiClass.fields) {
            val annotation = field.getAnnotation("dev.anvilcraft.resource.cuminum.network.StreamCodecField")
            if (annotation != null) {
                fields.add(StreamCodecFieldInfo(
                    field = field,
                    name = field.name,
                    type = field.type,
                    getter = extractGetter(annotation),
                    codec = extractCodec(annotation)
                ))
            }
        }
        
        return fields
    }
    
    private fun extractJsonKey(annotation: PsiAnnotation): String {
        val value = annotation.findDeclaredAttributeValue("value")
        if (value is PsiLiteralExpression && value.value is String) {
            return value.value as String
        }
        return ""
    }
    
    private fun extractGetter(annotation: PsiAnnotation): String {
        val getter = annotation.findDeclaredAttributeValue("getter")
        if (getter is PsiLiteralExpression && getter.value is String) {
            return getter.value as String
        }
        return ""
    }
    
    private fun extractCodec(annotation: PsiAnnotation): String {
        val codec = annotation.findDeclaredAttributeValue("codec")
        return codec?.text ?: ""
    }
    
    /**
     * 检查类是否被 @AutoCodec 标记
     */
    fun isAutoCodecClass(psiClass: PsiClass): Boolean {
        return psiClass.hasAnnotation("dev.anvilcraft.resource.cuminum.codec.AutoCodec")
    }
    
    /**
     * 检查类是否被 @AutoStreamCodec 标记
     */
    fun isAutoStreamCodecClass(psiClass: PsiClass): Boolean {
        return psiClass.hasAnnotation("dev.anvilcraft.resource.cuminum.network.AutoStreamCodec")
    }
    
}

/**
 * @CodecField 字段的信息
 */
data class CodecFieldInfo(
    val field: PsiField,
    val name: String,
    val type: PsiType,
    val jsonKey: String,
    val getter: String,
    val codec: String
)

/**
 * @StreamCodecField 字段的信息
 */
data class StreamCodecFieldInfo(
    val field: PsiField,
    val name: String,
    val type: PsiType,
    val getter: String,
    val codec: String
)


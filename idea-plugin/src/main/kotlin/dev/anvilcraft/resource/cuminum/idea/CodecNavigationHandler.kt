package dev.anvilcraft.resource.cuminum.idea

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.lang.documentation.DocumentationProvider
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil


/**
 * 提供 Codec 字段导航支持
 * 允许用户跳转到生成的 Codec 字段的定义
 */
class CodecNavigationHandler : GotoDeclarationHandler {
    
    override fun getGotoDeclarationTargets(
        sourceElement: PsiElement?,
        offset: Int,
        editor: com.intellij.openapi.editor.Editor?
    ): Array<PsiElement>? {
        if (sourceElement == null) return null
        
        val field = PsiTreeUtil.getParentOfType(sourceElement, PsiField::class.java) ?: return null
        val psiClass = field.containingClass ?: return null
        
        // 如果是自动生成的 CODEC、MAP_CODEC 或 STREAM_CODEC 字段
        when (field.name) {
            "CODEC", "MAP_CODEC", "STREAM_CODEC" -> {
                // 导航到该字段所在的类
                return arrayOf(psiClass)
            }
        }
        
        return null
    }
}

/**
 * 为 Codec 字段提供文档支持
 */
class CodecDocumentationProvider : DocumentationProvider {
    
    override fun getQuickNavigateInfo(element: PsiElement?, originalElement: PsiElement?): String? {
        if (element == null) return null
        
        val field = if (element is PsiField) element else PsiTreeUtil.getParentOfType(element, PsiField::class.java) ?: return null
        val psiClass = field.containingClass ?: return null
        
        return when (field.name) {
            "CODEC" -> {
                if (CodecFieldIndexer.isAutoCodecClass(psiClass)) {
                    "生成的 Codec 字段，用于 ${psiClass.name} 的序列化和反序列化"
                } else null
            }
            "MAP_CODEC" -> {
                if (CodecFieldIndexer.isAutoCodecClass(psiClass)) {
                    "生成的 MapCodec 字段，用于 ${psiClass.name} 的 Map 序列化"
                } else null
            }
            "STREAM_CODEC" -> {
                if (CodecFieldIndexer.isAutoStreamCodecClass(psiClass)) {
                    "生成的 StreamCodec 字段，用于 ${psiClass.name} 的网络流序列化"
                } else null
            }
            else -> null
        }
    }
}


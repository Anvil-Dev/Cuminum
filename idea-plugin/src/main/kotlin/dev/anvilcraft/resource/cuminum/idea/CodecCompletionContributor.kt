package dev.anvilcraft.resource.cuminum.idea

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil


/**
 * 用于 Cuminum Codec 字段的代码补全提供者
 * 为 @AutoCodec 和 @AutoStreamCodec 类提供智能补全
 */
class CodecCompletionContributor : CompletionContributor() {
    
    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        val position = parameters.position
        val psiClass = PsiTreeUtil.getParentOfType(position, PsiClass::class.java) ?: return
        
        // 检查是否在 AutoCodec 或 AutoStreamCodec 类中
        if (!CodecFieldIndexer.isAutoCodecClass(psiClass) && !CodecFieldIndexer.isAutoStreamCodecClass(psiClass)) {
            return
        }
        
        // 如果在 AutoCodec 类中，提供 CODEC/MAP_CODEC 补全
        if (CodecFieldIndexer.isAutoCodecClass(psiClass)) {
            val annotation = psiClass.getAnnotation("dev.anvilcraft.resource.cuminum.codec.AutoCodec")
            if (annotation != null) {
                val codecType = extractCodecType(annotation)
                when (codecType) {
                    "MAP_CODEC" -> {
                        result.addElement(LookupElementBuilder.create("MAP_CODEC")
                            .withTypeText("MapCodec<${psiClass.name}>")
                            .bold()
                            .withPresentableText("MAP_CODEC : MapCodec<${psiClass.name}>"))
                    }
                    "CODEC" -> {
                        result.addElement(LookupElementBuilder.create("CODEC")
                            .withTypeText("Codec<${psiClass.name}>")
                            .bold()
                            .withPresentableText("CODEC : Codec<${psiClass.name}>"))
                    }
                    "BOTH" -> {
                        result.addElement(LookupElementBuilder.create("CODEC")
                            .withTypeText("Codec<${psiClass.name}>")
                            .bold()
                            .withPresentableText("CODEC : Codec<${psiClass.name}>"))
                        result.addElement(LookupElementBuilder.create("MAP_CODEC")
                            .withTypeText("MapCodec<${psiClass.name}>")
                            .bold()
                            .withPresentableText("MAP_CODEC : MapCodec<${psiClass.name}>"))
                    }
                }
            }
        }
        
        // 如果在 AutoStreamCodec 类中，提供 STREAM_CODEC 补全
        if (CodecFieldIndexer.isAutoStreamCodecClass(psiClass)) {
            result.addElement(LookupElementBuilder.create("STREAM_CODEC")
                .withTypeText("StreamCodec<ByteBuf, ${psiClass.name}>")
                .bold()
                .withPresentableText("STREAM_CODEC : StreamCodec<ByteBuf, ${psiClass.name}>"))
        }
    }
    
    private fun extractCodecType(annotation: PsiAnnotation): String {
        val value = annotation.findDeclaredAttributeValue("value")
        return (value as? com.intellij.psi.PsiReferenceExpression)?.referenceName ?: "CODEC"
    }
}


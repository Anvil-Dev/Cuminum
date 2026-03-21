package dev.anvilcraft.resource.cuminum.idea;

import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementFinder;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class CuminumClassFinder extends PsiElementFinder {
    @Nullable
    @Override
    public PsiClass findClass(@Nonnull String qualifiedName, @Nonnull GlobalSearchScope scope) {
        // 1. 检查类名是否以 _CuminumCodec 结尾
        if (!qualifiedName.endsWith("_CuminumCodec")) return null;

        // 2. 尝试找到对应的原类（去掉后缀）
        String originClassName = qualifiedName.substring(0, qualifiedName.length() - "_CuminCodec".length());
        PsiClass originClass = JavaPsiFacade.getInstance(scope.getProject())
            .findClass(originClassName, scope);

        if (originClass != null && originClass.hasAnnotation("dev.anvilcraft.resource.cuminum.AutoCodec")) {
            // 3. 构建一个 LightClass（虚拟类）返回给 IDEA
            return new CuminLightClass(originClass, qualifiedName);
        }
        return null;
    }

    @Override
    public @Nonnull PsiClass [] findClasses(@NotNull String s, @NotNull GlobalSearchScope globalSearchScope) {
        return new PsiClass[0];
    }

    // 还需要重写 findClasses 和 getClasses 等方法以支持包扫描
}

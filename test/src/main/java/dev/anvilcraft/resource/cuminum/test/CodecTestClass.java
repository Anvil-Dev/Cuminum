package dev.anvilcraft.resource.cuminum.test;

import com.mojang.serialization.Codec;
import dev.anvilcraft.resource.cuminum.codec.AutoCodec;
import dev.anvilcraft.resource.cuminum.codec.CodecField;
import dev.anvilcraft.resource.cuminum.CodecIgnore;

import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

@AutoCodec(AutoCodec.CodecType.BOTH)
public record CodecTestClass(
    String test,
    int test2,
    boolean test3,
    float test4,
    double test5,
    @CodecField("test_test") long test6,
    short test7,
    @Nullable
    Double test8,
    List<String> test9,
    List<Integer> test10,
    Map<String, Integer> test11,
    @CodecIgnore double test12,
    @CodecIgnore long test13
) {
    public CodecTestClass(
        String test,
        int test2,
        boolean test3,
        float test4,
        double test5,
        long test6,
        short test7,
        Double test8,
        List<String> test9,
        List<Integer> test10,
        Map<String, Integer> test11
    ) {
        this(test, test2, test3, test4, test5, test6, test7, test8, test9, test10, test11, 0.0, 0);
    }

    @Override
    public String toString() {
        return "CodecTest{" + "test='" + test + '\'' + ", test2=" + test2 + ", test3=" + test3 + ", test4=" + test4 + ", test5=" + test5 + ", test6=" + test6 + ", test7=" + test7 + ", test8=" + test8 + ", test9=" + test9 + ", test10=" + test10 + ", test11=" + test11 + ", test12=" + test12 + ", test13=" + test13 + '}';
    }

    public static void main(String[] args) {
        Codec<CodecTestClass> codec = CodecTestClass.CODEC;

    }
}

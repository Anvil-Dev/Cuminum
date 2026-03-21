package dev.anvilcraft.cuminum.test;

import dev.anvilcraft.cuminum.codec.AutoCodec;
import dev.anvilcraft.cuminum.codec.CodecField;
import dev.anvilcraft.cuminum.CodecIgnore;

import java.util.List;
import java.util.Map;

@AutoCodec
public final class CodecTest1Class {
    final String test;
    final int test2;
    final boolean test3;
    final float test4;
    final double test5;
    @CodecField("test_test")
    final long test6;
    final short test7;
    final Double test8;
    final List<String> test9;
    final List<Integer> test10;
    final Map<String, Integer> test11;
    @CodecIgnore
    final double test12;
    @CodecIgnore
    final long test13;

    public CodecTest1Class(
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
        Map<String, Integer> test11,
        double test12,
        long test13
    ) {
        this.test = test;
        this.test2 = test2;
        this.test3 = test3;
        this.test4 = test4;
        this.test5 = test5;
        this.test6 = test6;
        this.test7 = test7;
        this.test8 = test8;
        this.test9 = test9;
        this.test10 = test10;
        this.test11 = test11;
        this.test12 = test12;
        this.test13 = test13;
    }

    public CodecTest1Class(
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
}

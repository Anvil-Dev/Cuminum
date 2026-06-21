package dev.anvilcraft.resource.cuminum.codec;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
public @interface AutoCodec {
    CodecType value() default CodecType.CODEC;

    enum CodecType {
        MAP_CODEC,
        CODEC,
        BOTH
    }
}

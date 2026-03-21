package dev.anvilcraft.resource.cuminum.codec;

import dev.anvilcraft.resource.cuminum.UseCodec;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.SOURCE)
public @interface CodecField {
    String value() default "";
    String getter() default "";
    UseCodec codec() default @UseCodec(value = Void.class);
}

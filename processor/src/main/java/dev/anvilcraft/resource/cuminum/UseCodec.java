package dev.anvilcraft.resource.cuminum;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({})
@Retention(RetentionPolicy.SOURCE)
public @interface UseCodec {
    Class<?> value();
    String member() default "";
}


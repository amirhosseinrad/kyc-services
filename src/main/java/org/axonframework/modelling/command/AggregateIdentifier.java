package org.axonframework.modelling.command;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.core.annotation.AliasFor;

/**
 * Identifies the field or method providing the aggregate identifier.
 */
@Documented
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface AggregateIdentifier {

    /**
     * Alias for {@link #routingKey()}.
     *
     * @return the routing key of the aggregate identifier
     */
    @AliasFor("routingKey")
    String value() default "";

    /**
     * Alias for {@link #value()}.
     *
     * @return the routing key of the aggregate identifier
     */
    @AliasFor("value")
    String routingKey() default "";
}

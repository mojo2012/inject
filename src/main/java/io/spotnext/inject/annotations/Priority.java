package io.spotnext.inject.annotations;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Documented
@Retention(RUNTIME)
@Target(TYPE)
public @interface Priority {
	
	/**
	 * The order value.
	 * <p>Default is {@link Short#MAX_VALUE}.
	 */
	short value() default Short.MAX_VALUE;
}
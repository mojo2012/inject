package io.spotnext.inject.instrumentation;

import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.Optional;

import javax.persistence.Entity;

import io.spotnext.inject.Context;
import io.spotnext.inject.annotations.Bean;
import io.spotnext.inject.annotations.Inject;
import io.spotnext.inject.annotations.Processed;
import io.spotnext.inject.annotations.Prototype;
import io.spotnext.inject.annotations.Service;
import io.spotnext.inject.annotations.Singleton;
import io.spotnext.support.util.Loggable;
import io.spotnext.support.weaving.AbstractBaseClassTransformer;
import io.spotnext.support.weaving.IllegalClassTransformationException;
import javassist.CtClass;
import javassist.CtField;
import javassist.bytecode.annotation.Annotation;

/**
 * Transforms custom {@link ItemType} annotations to JPA entity annotations.
 */
public class InjectionTransformer extends AbstractBaseClassTransformer implements Loggable {

	@Override
	protected Optional<CtClass> transform(final ClassLoader loader, final CtClass clazz,
			final Class<?> classBeingRedefined, final ProtectionDomain protectionDomain)
			throws IllegalClassTransformationException {

		if (log().isDebugEnabled()) {
			log().debug("Processing: " + clazz.getName());
		}

		try {
			if (isBean(clazz) && !isAlreadProcessed(clazz)) {
				if (log().isDebugEnabled()) {
					log().debug("Adding injections to: " + clazz.getName());
				}

				if (clazz.isFrozen()) {
					try {
						clazz.defrost();
					} catch (final Exception e) {
						throw new IllegalClassTransformationException(String.format("Type %s was frozen and could not be defrosted", clazz.getName()), e);
					}
				}

				// process fields
				for (final CtField field : getDeclaredFields(clazz)) {
					// ignore injections of the same kind as the current clazz
					if (!clazz.equals(field.getDeclaringClass())) {
						log().warn("Ignoring field '{}' with @Inject annotation as is has the same type as the containing class.");
						continue;
					}

					final Optional<Annotation> injectAnnotation = getAnnotation(field, Inject.class);
					if (injectAnnotation.isPresent()) {
						final var initializedField = new CtField(field.getType(), field.getName(), clazz);

						clazz.removeField(field);
						clazz.addField(initializedField,
								CtField.Initializer
										.byExpr(String.format("io.spotnext.inject.Context.instance().getBean(%s.class)", field.getType().getName())));
					}
				}

				// mark class as already processed
				// this is useful so that the class is not woven again during runtime in case the class has been woven during compile-time
				addAnnotations(clazz, Arrays.asList(createAnnotation(clazz, Processed.class)));

				return Optional.of(clazz);
			} else {
				if (log().isDebugEnabled()) {
					log().debug("Ignoring " + clazz.getName());
				}
			}
		} catch (final Exception e) {
			final var message = "Injection failed for bean of type " + clazz.getName();
			log().error(message, e);
			throw new IllegalClassTransformationException(message, e);
		}

		return Optional.empty();
	}

	private boolean isBean(CtClass clazz) throws IllegalClassTransformationException {
		final Optional<Annotation> singletonAnnotation = getAnnotation(clazz, Singleton.class);
		final Optional<Annotation> serviceAnnotation = getAnnotation(clazz, Service.class);
		final Optional<Annotation> prototypeAnnotation = getAnnotation(clazz, Prototype.class);
		final Optional<Annotation> beanAnnotation = getAnnotation(clazz, Bean.class);
		return singletonAnnotation.isPresent() || serviceAnnotation.isPresent() || prototypeAnnotation.isPresent() || beanAnnotation.isPresent();
	}

	protected boolean isAlreadProcessed(final CtClass clazz) throws IllegalClassTransformationException {
		final Optional<Annotation> processedAnnotation = getAnnotation(clazz, Processed.class);

		return processedAnnotation.isPresent();
	}

}

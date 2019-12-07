package io.spotnext.inject.instrumentation;

import java.security.ProtectionDomain;
import java.util.Optional;

import javax.persistence.Entity;
import javax.persistence.MappedSuperclass;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.spotnext.inject.annotations.Inject;
import io.spotnext.support.weaving.AbstractBaseClassTransformer;
import io.spotnext.support.weaving.IllegalClassTransformationException;
import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.NotFoundException;
import javassist.bytecode.annotation.Annotation;

/**
 * Transforms custom {@link ItemType} annotations to JPA entity annotations.
 */
public class InjectionTransformer extends AbstractBaseClassTransformer {

	private static final Logger LOG = LoggerFactory.getLogger(InjectionTransformer.class);

	@Override
	protected Optional<CtClass> transform(final ClassLoader loader, final CtClass clazz,
			final Class<?> classBeingRedefined, final ProtectionDomain protectionDomain)
			throws IllegalClassTransformationException {

		if (LOG.isDebugEnabled()) {
			LOG.debug("Transforming: " + clazz.getName());
		}

		try {
			// we only want to transform item types only ...
			if (!alreadyTransformed(clazz)) {
				if (LOG.isDebugEnabled()) {
					LOG.debug("Injecting JPA annotations: " + clazz.getName());
				}

				if (clazz.isFrozen()) {
					try {
						clazz.defrost();
					} catch (final Exception e) {
						throw new IllegalClassTransformationException(
								String.format("Type %s was frozen and could not be defrosted", clazz.getName()), e);
					}
				}

				// process fields
				for (final CtField field : getDeclaredFields(clazz)) {
					if (!clazz.equals(field.getDeclaringClass())) {
						continue;
					}

					final Optional<Annotation> propertyAnn = getAnnotation(field, Inject.class);

					// process item type property annotation
					if (propertyAnn.isPresent()) {

						// and add them to the clazz
//						addAnnotations(clazz, field, fieldAnnotations);
					}
				}

//				addIndexAnnotation(clazz);

				return Optional.of(clazz);
			} else {
				if (LOG.isDebugEnabled()) {
					LOG.debug("Ignoring " + clazz.getName());
				}
			}
		} catch (final Exception e) {
			logException(e);

			throw new IllegalClassTransformationException(
					String.format("Unable to process JPA annotations for class file %s, reason: %s", clazz.getName(), e.getMessage()), e);
		}

		return Optional.empty();
	}

	protected boolean alreadyTransformed(final CtClass clazz) throws IllegalClassTransformationException {
		final Optional<Annotation> entityAnnotation = getAnnotation(clazz, Entity.class);
		final Optional<Annotation> mappedSuperclassAnnotation = getAnnotation(clazz, MappedSuperclass.class);

		return entityAnnotation.isPresent() || mappedSuperclassAnnotation.isPresent();
	}

	private Optional<CtMethod> getGetter(CtClass entityClass, CtField field) {
		return getMethod(entityClass, "get" + StringUtils.capitalize(field.getName()));
	}

	private Optional<CtMethod> getSetter(CtClass entityClass, CtField field) {
		return getMethod(entityClass, "set" + StringUtils.capitalize(field.getName()));
	}

	/**
	 * Finds the method with the given name, ignoring any method parameters.
	 * 
	 * @param entityClass the class to inspect
	 * @param methodName
	 * @return the method or null
	 */
	protected Optional<CtMethod> getMethod(CtClass entityClass, String methodName) {
		CtMethod method = null;

		try {
			method = entityClass.getDeclaredMethod(methodName);
		} catch (NotFoundException e) {
			// ignore
		}

		return Optional.ofNullable(method);
	}

}

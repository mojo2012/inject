package io.spotnext.inject.instrumentation;

import java.io.File;
import java.io.IOException;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;

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
					final Optional<Annotation> injectAnnotation = getAnnotation(field, Inject.class);
					if (injectAnnotation.isPresent()) {
						final var fieldType = field.getType();
						final var fieldTypeName = fieldType.getName();

						final var allInterfaces = new HashSet<CtClass>();

						for (var superType : getAllSuperclasses(fieldType)) {
							allInterfaces.addAll(Arrays.asList(superType.getInterfaces()));
						}

						final var collectionInterfaces = Arrays.asList("java.util.Collection", "java.util.Set", "java.util.List");
						var collectionFieldType = (CtClass) null;

						for (final var iface : allInterfaces) {
							if (collectionInterfaces.contains(iface.getName())) {
								collectionFieldType = iface;
								break;
							} else if ("java.util.Map".equals(fieldTypeName)) {
								throw new IllegalClassTransformationException("Dependency injection into Map-field not supported");
							}
						}

						clazz.removeField(field);
						if (collectionFieldType != null) {
							final var genericSignature = field.getGenericSignature();
							final var genericType = genericSignature.substring(genericSignature.indexOf("<") + 2, genericSignature.length() - 3).replace("/",
									".");
							final var typeString = collectionFieldType.getName().endsWith("Set")
									? "Set"
									: "List";

							clazz.addField(field, CtField.Initializer
									.byExpr(String.format(
											"io.spotnext.inject.Context.instance().getBeans(%s.class).stream().collect(java.util.stream.Collectors.to%s())",
											genericType, typeString)));
						} else {
							clazz.addField(field, CtField.Initializer
									.byExpr(String.format("(%s) io.spotnext.inject.Context.instance().getBean(%s.class)", fieldTypeName, fieldTypeName)));
						}
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
		} catch (

		final Exception e) {
			final var message = "Injection failed for bean of type " + clazz.getName();
			log().error(message, e);
			throw new IllegalClassTransformationException(message, e);
		}

		return Optional.empty();
	}

	@Override
	protected void writeByteCodeToFile(CtClass transformedClass) {
		try {
			writeClass(transformedClass, new File("/tmp/" + transformedClass.getName() + ".class"));
		} catch (IOException e) {
			logException(e);
		}
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

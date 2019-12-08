/*
 * Copyright 2008 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.spotnext.inject.processor;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.SortedSet;
import java.util.stream.Collectors;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedOptions;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ErrorType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.util.SimpleElementVisitor8;
import javax.lang.model.util.SimpleTypeVisitor8;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic.Kind;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

import com.google.common.base.Optional;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;

import io.spotnext.inject.annotations.Bean;
import io.spotnext.inject.annotations.Prototype;
import io.spotnext.inject.annotations.Service;
import io.spotnext.inject.annotations.Singleton;

/**
 * Processes {@link AutoService} annotations and generates the service provider configuration files described in {@link java.util.ServiceLoader}.
 * <p>
 * Processor Options:
 * <ul>
 * <li>debug - turns on debug statements</li>
 * </ul>
 */
@SupportedOptions({ "debug", "verify" })
public class BeanProcessor extends AbstractProcessor {

	public static final String MISSING_SERVICES_ERROR = "No service interfaces provided for element!";

	public static final Set<Class<? extends Annotation>> SUPPORTED_ANNOTATIONS = ImmutableSet.of(Service.class, Singleton.class, Bean.class, Prototype.class);

	/**
	 * Maps the class names of service provider interfaces to the class names of the concrete classes which implement them.
	 * <p>
	 * For example, {@code "com.google.apphosting.LocalRpcService" ->
	 *   "com.google.apphosting.datastore.LocalDatastoreService"}
	 */
	private Multimap<String, String> providers = HashMultimap.create();

	@Override
	public ImmutableSet<String> getSupportedAnnotationTypes() {
		return ImmutableSet.copyOf(SUPPORTED_ANNOTATIONS.stream().map(Class::getName).collect(Collectors.toSet()));
	}

	@Override
	public SourceVersion getSupportedSourceVersion() {
		return SourceVersion.latestSupported();
	}

	/**
	 * <ol>
	 * <li>For each class annotated with {@link AutoService}
	 * <ul>
	 * <li>Verify the {@link AutoService} interface value is correct
	 * <li>Categorize the class by its service interface
	 * </ul>
	 * <li>For each {@link AutoService} interface
	 * <ul>
	 * <li>Create a file named {@code META-INF/services/<interface>}
	 * <li>For each {@link AutoService} annotated class for this interface
	 * <ul>
	 * <li>Create an entry in the file
	 * </ul>
	 * </ul>
	 * </ol>
	 */
	@Override
	public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
		try {
			return processImpl(annotations, roundEnv);
		} catch (Exception e) {
			// We don't allow exceptions of any kind to propagate to the compiler
			StringWriter writer = new StringWriter();
			e.printStackTrace(new PrintWriter(writer));
			fatalError(writer.toString());
			return true;
		}
	}

	private boolean processImpl(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
		if (roundEnv.processingOver()) {
			generateConfigFiles();
		} else {
			processAnnotations(annotations, roundEnv);
		}

		return true;
	}

	private void processAnnotations(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
		log(annotations.toString());

		for (final var type : SUPPORTED_ANNOTATIONS) {
			for (var e : roundEnv.getElementsAnnotatedWith(type)) {

				final var annotationMirror = getAnnotationMirror(e, type).orNull();

				// TODO(gak): check for error trees?
				TypeElement providerImplementer = (TypeElement) e;

				final var providerInterfaces = new ArrayList<TypeMirror>();
				providerInterfaces.addAll(providerImplementer.getInterfaces());

				var currentType = providerImplementer;
				while (currentType != null) {
					final var superClass = currentType.getSuperclass();
					
					if (!TypeKind.NONE.equals(superClass.getKind())) {
						final var declaredSuperType = (DeclaredType) superClass;
						final var superClassElement = (TypeElement) declaredSuperType.asElement();
						
						providerInterfaces.addAll(superClassElement.getInterfaces());
						currentType = superClassElement;
					} else {
						currentType = null;
					}
				}

				if (providerInterfaces.isEmpty()) {
					error(MISSING_SERVICES_ERROR, e, annotationMirror);
					continue;
				}

				for (final var providerInterface : providerInterfaces) {
					final var elemVisitor = providerInterface.accept(AsElementVisitor.INSTANCE, null);
					final var providerType = elemVisitor.accept(TypeElementVisitor.INSTANCE, null);

					log("provider interface: " + providerType.getQualifiedName());
					log("provider implementer: " + providerImplementer.getQualifiedName());

					if (checkImplementer(providerImplementer, providerType)) {
						providers.put(getBinaryName(providerType), getBinaryName(providerImplementer));
					} else {
						String message = "ServiceProviders must implement their service provider interface. "
								+ providerImplementer.getQualifiedName() + " does not implement "
								+ providerType.getQualifiedName();
						error(message, e, annotationMirror);
					}
				}
			}
		}

	}

	private void generateConfigFiles() {
		Filer filer = processingEnv.getFiler();

		for (String providerInterface : providers.keySet()) {
			String resourceFile = "META-INF/services/" + providerInterface;
			log("Working on resource file: " + resourceFile);
			try {
				SortedSet<String> allServices = Sets.newTreeSet();
				try {
					// would like to be able to print the full path
					// before we attempt to get the resource in case the behavior
					// of filer.getResource does change to match the spec, but there's
					// no good way to resolve CLASS_OUTPUT without first getting a resource.
					FileObject existingFile = filer.getResource(StandardLocation.CLASS_OUTPUT, "",
							resourceFile);
					log("Looking for existing resource file at " + existingFile.toUri());
					Set<String> oldServices = ServicesFiles.readServiceFile(existingFile.openInputStream());
					log("Existing service entries: " + oldServices);
					allServices.addAll(oldServices);
				} catch (IOException e) {
					// According to the javadoc, Filer.getResource throws an exception
					// if the file doesn't already exist. In practice this doesn't
					// appear to be the case. Filer.getResource will happily return a
					// FileObject that refers to a non-existent file but will throw
					// IOException if you try to open an input stream for it.
					log("Resource file did not already exist.");
				}

				Set<String> newServices = new HashSet<String>(providers.get(providerInterface));
				if (allServices.containsAll(newServices)) {
					log("No new service entries being added.");
					return;
				}

				allServices.addAll(newServices);
				log("New service file contents: " + allServices);
				FileObject fileObject = filer.createResource(StandardLocation.CLASS_OUTPUT, "",
						resourceFile);
				OutputStream out = fileObject.openOutputStream();
				ServicesFiles.writeServiceFile(allServices, out);
				out.close();
				log("Wrote to: " + fileObject.toUri());
			} catch (IOException e) {
				fatalError("Unable to create " + resourceFile + ", " + e);
				return;
			}
		}
	}

	/**
	 * Verifies ServiceProvider constraints on the concrete provider class. Note that these constraints are enforced at runtime via the ServiceLoader, we're
	 * just checking them at compile time to be extra nice to our users.
	 */
	private boolean checkImplementer(TypeElement providerImplementer, TypeElement providerType) {

		String verify = processingEnv.getOptions().get("verify");
		if (verify == null || !Boolean.valueOf(verify)) {
			return true;
		}

		// TODO: We're currently only enforcing the subtype relationship
		// constraint. It would be nice to enforce them all.

		Types types = processingEnv.getTypeUtils();

		return types.isSubtype(providerImplementer.asType(), providerType.asType());
	}

	/**
	 * Returns the binary name of a reference type. For example, {@code com.google.Foo$Bar}, instead of {@code com.google.Foo.Bar}.
	 */
	private String getBinaryName(TypeElement element) {
		return getBinaryNameImpl(element, element.getSimpleName().toString());
	}

	private String getBinaryNameImpl(TypeElement element, String className) {
		Element enclosingElement = element.getEnclosingElement();

		if (enclosingElement instanceof PackageElement) {
			PackageElement pkg = (PackageElement) enclosingElement;
			if (pkg.isUnnamed()) {
				return className;
			}
			return pkg.getQualifiedName() + "." + className;
		}

		TypeElement typeElement = (TypeElement) enclosingElement;
		return getBinaryNameImpl(typeElement, typeElement.getSimpleName() + "$" + className);
	}

	/**
	 * Returns the contents of a {@code Class[]}-typed "value" field in a given {@code annotationMirror}.
	 */
	private ImmutableSet<DeclaredType> getValueFieldOfClasses(AnnotationMirror annotationMirror) {
//		return getAnnotationValue(annotationMirror, "value")
//				.accept(
//						new SimpleAnnotationValueVisitor8<ImmutableSet<DeclaredType>, Void>() {
//							@Override
//							public ImmutableSet<DeclaredType> visitType(TypeMirror typeMirror, Void v) {
//								// TODO(ronshapiro): class literals may not always be declared types, i.e. int.class,
//								// int[].class
//								return ImmutableSet.of(typeMirror.accept(DeclaredTypeVisitor.INSTANCE, null));
//							}
//
//							@Override
//							public ImmutableSet<DeclaredType> visitArray(
//									List<? extends AnnotationValue> values, Void v) {
//								return values
//										.stream()
//										.flatMap(value -> value.accept(this, null).stream())
//										.collect(ImmutableSet.toImmutableSet());
//							}
//						},
//						null);

		return null;
	}

	private void log(String msg) {
		if (processingEnv.getOptions().containsKey("debug")) {
			processingEnv.getMessager().printMessage(Kind.NOTE, msg);
		}
	}

	private void error(String msg, Element element, AnnotationMirror annotation) {
		processingEnv.getMessager().printMessage(Kind.ERROR, msg, element, annotation);
	}

	private void fatalError(String msg) {
		processingEnv.getMessager().printMessage(Kind.ERROR, "FATAL ERROR: " + msg);
	}

	/**
	 * Returns an {@link AnnotationMirror} for the annotation of type {@code annotationClass} on {@code element}, or {@link Optional#absent()} if no such
	 * annotation exists. This method is a safer alternative to calling {@link Element#getAnnotation} as it avoids any interaction with annotation proxies.
	 */
	public static Optional<AnnotationMirror> getAnnotationMirror(Element element, Class<? extends Annotation> annotationClass) {
		String annotationClassName = annotationClass.getCanonicalName();

		for (AnnotationMirror annotationMirror : element.getAnnotationMirrors()) {
			TypeElement annotationTypeElement = asType(annotationMirror.getAnnotationType().asElement());
			if (annotationTypeElement.getQualifiedName().contentEquals(annotationClassName)) {
				return Optional.of(annotationMirror);
			}
		}
		return Optional.absent();
	}

	/**
	 * Returns the given {@link Element} instance as {@link TypeElement}.
	 * <p>
	 * This method is functionally equivalent to an {@code instanceof} check and a cast, but should always be used over that idiom as instructed in the
	 * documentation for {@link Element}.
	 *
	 * @throws NullPointerException     if {@code element} is {@code null}
	 * @throws IllegalArgumentException if {@code element} isn't a {@link TypeElement}.
	 */
	public static TypeElement asType(Element element) {
		return element.accept(TypeElementVisitor.INSTANCE, null);
	}

	private abstract static class CastingTypeVisitor<T> extends SimpleTypeVisitor8<T, Void> {
		private final String label;

		CastingTypeVisitor(String label) {
			this.label = label;
		}

		@Override
		protected T defaultAction(TypeMirror e, Void v) {
			throw new IllegalArgumentException(e + " does not represent a " + label);
		}
	}

	private static final class DeclaredTypeVisitor extends CastingTypeVisitor<DeclaredType> {
		private static final DeclaredTypeVisitor INSTANCE = new DeclaredTypeVisitor();

		DeclaredTypeVisitor() {
			super("declared type");
		}

		@Override
		public DeclaredType visitDeclared(DeclaredType type, Void ignore) {
			return type;
		}
	}

	private abstract static class CastingElementVisitor<T> extends SimpleElementVisitor8<T, Void> {
		private final String label;

		CastingElementVisitor(String label) {
			this.label = label;
		}

		@Override
		protected final T defaultAction(Element e, Void ignore) {
			throw new IllegalArgumentException(e + " does not represent a " + label);
		}
	}

	private static final class TypeElementVisitor extends CastingElementVisitor<TypeElement> {
		private static final TypeElementVisitor INSTANCE = new TypeElementVisitor();

		TypeElementVisitor() {
			super("type element");
		}

		@Override
		public TypeElement visitType(TypeElement e, Void ignore) {
			return e;
		}
	}

	private static final class AsElementVisitor extends SimpleTypeVisitor8<Element, Void> {
		private static final AsElementVisitor INSTANCE = new AsElementVisitor();

		@Override
		protected Element defaultAction(TypeMirror e, Void p) {
			throw new IllegalArgumentException(e + " cannot be converted to an Element");
		}

		@Override
		public Element visitDeclared(DeclaredType t, Void p) {
			return t.asElement();
		}

		@Override
		public Element visitError(ErrorType t, Void p) {
			return t.asElement();
		}

		@Override
		public Element visitTypeVariable(TypeVariable t, Void p) {
			return t.asElement();
		}
	};

	private static final class TypeVariableVisitor extends CastingTypeVisitor<TypeVariable> {
		private static final TypeVariableVisitor INSTANCE = new TypeVariableVisitor();

		TypeVariableVisitor() {
			super("type variable");
		}

		@Override
		public TypeVariable visitTypeVariable(TypeVariable type, Void ignore) {
			return type;
		}
	}
}

package io.spotnext.inject;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.ServiceLoader.Provider;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import io.spotnext.inject.annotations.Bean;
import io.spotnext.inject.annotations.Inject;
import io.spotnext.inject.annotations.Ordered;
import io.spotnext.inject.annotations.Prototype;
import io.spotnext.inject.annotations.Service;
import io.spotnext.inject.annotations.Singleton;
import io.spotnext.support.util.ClassUtil;
import io.spotnext.support.util.Loggable;

public class Context implements Loggable {
	private static final Map<ClassLoader, Context> instances = new HashMap<>();

	private final ClassLoader contextClassloader;

	private final List<Class<? extends Annotation>> singletonAnnotations = new ArrayList<>();
	private final List<Class<? extends Annotation>> prototypeAnnotations = new ArrayList<>();

	private final Map<Class, Object> singletonCache = new HashMap<>();

	private Context(ClassLoader contextClassloader) {
		this.contextClassloader = contextClassloader;

		registerSingletonAnnotation(Singleton.class, Service.class);
		registerPrototypeAnnotation(Prototype.class, Bean.class);
	}

	public static Context instance() {
		return instance(ClassLoader.getSystemClassLoader());
	}

	public static Context instance(Class<?> contextRoot) {
		return instance(contextRoot.getClassLoader());
	}

	private synchronized static Context instance(ClassLoader contextClassloader) {
		var instance = instances.get(contextClassloader);

		if (instance == null) {
			instance = new Context(contextClassloader);
			instances.put(contextClassloader, instance);
		}

		return instance;
	}

	public <T> T getBean(Class<T> beanType) {
		return loadBean(beanType, null);
	}

	public <T> T getBean(String beanName, Class<T> beanType) {
		return loadBean(beanType, bean -> true);
	}

	public void injectBeans(Object object) {
		final var injectFields = ClassUtil.getFields(object.getClass(), f -> f.getAnnotation(Inject.class) != null);

		for (final var field : injectFields) {
			final var fieldType = field.getType();
			final var fieldBean = loadBean(fieldType, null);
			ClassUtil.setField(object, field.getName(), fieldBean);
		}
	}

	private <T> T loadBean(Class<T> beanType, Predicate<T> predicate) {
		var bean = singletonCache.get(beanType);

		if (bean == null) {
			final var beans = ServiceLoader.load(beanType, contextClassloader);

			final var beansGroupedByPriority = beans.stream()
					.collect(Collectors.groupingBy(b -> getPriority(b)));

			for (var entry : beansGroupedByPriority.entrySet()) {
				final var count = entry.getValue().size();
				if (count > 1) {
					final var beansStr = entry.getValue().stream()
							.map(b -> b.type().getName())
							.collect(Collectors.joining(", ")); 
					log().warn("{} beans with the same priority {} found: {}", count, entry.getKey(), beansStr);
				}
			}
			
			// TODO switch to reflective constructor invocation, allowing parameter injection
			var stream = beans.stream()
					.sorted((s1, s2) -> getPriority(s1).compareTo(getPriority(s2)))
					.map(s -> s.get());

			if (predicate != null) {
				stream = stream.filter(predicate);
			}

			bean = stream.findFirst()
					.orElseThrow(() -> new BeanException(String.format("Bean of type '%s' not found", beanType)));

			if (isSingleton(bean)) {
				singletonCache.put(beanType, bean);
				singletonCache.put(bean.getClass(), bean);
			}
		}

		return (T) bean;
	}

	private <T> Short getPriority(Provider<T> provider) {
		final var priority = Optional.ofNullable(provider.type().getAnnotation(Ordered.class));
		return priority.map(p -> p.value()).orElse((short) Short.MAX_VALUE);
	}

	private <T> boolean isSingleton(T bean) {
		for (final var annotation : bean.getClass().getAnnotations()) {
			if (singletonAnnotations.contains(annotation.annotationType())) {
				return true;
			}
		}

		return false;
	}

	@SafeVarargs
	public final void registerSingletonAnnotation(final Class<? extends Annotation>... annotations) {
		singletonAnnotations.addAll(Arrays.asList(annotations));
	}

	@SafeVarargs
	public final void registerPrototypeAnnotation(final Class<? extends Annotation>... annotations) {
		prototypeAnnotations.addAll(Arrays.asList(annotations));
	}
}

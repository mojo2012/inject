# inject
Simple java dependency injection framework based on the ServiceLoader API.

## Sample

First we define a bean of name `SingletonServiceImpl`.

```java
@Ordered(1)
@Singleton
public class SingletonServiceImpl implements SingletonService {

	@Inject
	private PrototypeBean prototype;

	public SingletonServiceImpl() {
		System.out.println(this.getClass().getName() + " instantiated");
	}

	@Override
	public PrototypeBean getInjectedBean() {
		return prototype;
	}
}
```

Later on we access it directly using the name, or by providing the interface:

```java
final var singleton1 = Context.instance().getBean("SingletonServiceImpl", SingletonService.class);
final var singleton2 = Context.instance().getBean(SingletonService.class);

assertEquals(singleton1, singleton2);
assertNotNull(singleton1.getInjectedBean());
```

> Although we didn't implement the singleton pattern in our `SingletonServiceImpl`, we will always get the same instance!

In case there are multiple bean implementing the same interface, the `@Ordered` annotation is used to determine the right bean - the lower the value the higher the load priority.

### Dependency injection
All fields annotated with `@Injected` will be injected on bean-creation. There are several ways how this is done:
* Compile-time-weaving using the `io.spotnext.inject.instrumentation.InjectionTransformer` with the maven mojo `io.spot-next:spot-maven-plugin`
* Load-time-weaving using `DynamicInstrumentationLoader.initialize(InjectionTransformer.class);` from the library `io.spot-next:spot-instrumentation`
* No weaving. If the beans dependencies have not yet injected by the class transformer the dependencies will be injected after the bean instantiation via reflection. This is perfectly fine although it has the downside that the fields are null in the constructor call. The very same mechanism (`Context.getInstance).injectBeans(bean)` can be used on manually created objects

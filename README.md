# inject
Simple java dependency injection framework based on the ServiceLoader API.

## Sample

First we define a bean of name `SingletonServiceImpl`.

```java
@Priority(1)
@Singleton
public class SingletonServiceImpl implements SingletonService {

	public SingletonServiceImpl() {
		System.out.println(this.getClass().getName() + " instantiated");
	}
	
	@Override
	public String getDummy() {
		return "dummy";
	}
}

```

Later on we access it directly using the name, or by providing the interface:

```java
final var singleton1 = Context.instance().getBean("SingletonServiceImpl", SingletonService.class);
final var singleton2 = Context.instance().getBean(SingletonService.class);

assertEquals(singleton1, singleton2);
```

Although we didn't implement the singleton pattern in our `SingletonServiceImpl`, we will always get the same instance!
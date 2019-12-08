package io.spotnext.inject;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

import io.spotnext.inject.beans.PrototypeBean;
import io.spotnext.inject.beans.SampleBean;
import io.spotnext.inject.beans.SingletonService;
import io.spotnext.inject.instrumentation.InjectionTransformer;
import io.spotnext.instrumentation.DynamicInstrumentationLoader;


public class ServiceLoaderTest {

	static {
		// dynamically attach java agent to JVM if not already present and add the injection transformer for load-time injection
		DynamicInstrumentationLoader.initialize(InjectionTransformer.class);
	}
	
	@Test
	public void testPrototype() {
		final var prototypeBean1 = Context.instance().getBean(PrototypeBean.class);
		final var prototypeBean2 = Context.instance().getBean(PrototypeBean.class);
		
		assertNotEquals(prototypeBean1, prototypeBean2);
	}
	
	@Test
	public void testSingletonWithPropertyInjection() {
		final var singleton1 = Context.instance().getBean("SingletonServiceImpl", SingletonService.class);
		final var singleton2 = Context.instance().getBean(SingletonService.class);
		
		assertNotNull(singleton1);
		assertNotNull(singleton1.getInjectedBean());
		assertEquals(singleton1, singleton2);
	}
	
	@Test
	public void TestManualBeanInjection() {
		final var sampleBean = new  SampleBean();
		
		Context.instance().injectBeans(sampleBean);
		
		assertNotNull(sampleBean.getSingletonService());
	}
}

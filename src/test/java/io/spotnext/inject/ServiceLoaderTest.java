package io.spotnext.inject;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

import io.spotnext.inject.beans.PrototypeBean;
import io.spotnext.inject.beans.SampleBean;
import io.spotnext.inject.beans.SingletonService;


public class ServiceLoaderTest {

	@Test
	public void testPrototype() {
		final var prototypeBean1 = Context.instance().getBean(PrototypeBean.class);
		final var prototypeBean2 = Context.instance().getBean(PrototypeBean.class);
		
		assertNotEquals(prototypeBean1, prototypeBean2);
	}
	
	@Test
	public void testSingleton() {
		final var singleton1 = Context.instance().getBean(SingletonService.class);
		final var singleton2 = Context.instance().getBean(SingletonService.class);
		
		assertNotNull(singleton1);
		assertEquals(singleton1, singleton2);
	}
	
	@Test
	public void TestBeanInjection() {
		final var sampleBean = new  SampleBean();
		
		Context.instance().injectBeans(sampleBean);
		
		assertNotNull(sampleBean.getSingletonService());
	}
}

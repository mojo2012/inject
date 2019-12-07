package io.spotnext.inject.beans.impl;

import io.spotnext.inject.annotations.Inject;
import io.spotnext.inject.annotations.Ordered;
import io.spotnext.inject.annotations.Singleton;
import io.spotnext.inject.beans.PrototypeBean;
import io.spotnext.inject.beans.SingletonService;

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

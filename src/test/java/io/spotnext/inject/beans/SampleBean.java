package io.spotnext.inject.beans;

import io.spotnext.inject.annotations.Inject;

public class SampleBean {

	@Inject
	SingletonService singletonService;
	
	public SingletonService getSingletonService() {
		return singletonService;
	}
}

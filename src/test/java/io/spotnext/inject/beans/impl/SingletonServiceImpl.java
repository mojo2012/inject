package io.spotnext.inject.beans.impl;

import io.spotnext.inject.annotations.Priority;
import io.spotnext.inject.annotations.Singleton;
import io.spotnext.inject.beans.SingletonService;

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

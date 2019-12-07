package io.spotnext.inject.beans.impl;

import io.spotnext.inject.annotations.Singleton;
import io.spotnext.inject.beans.SingletonService;

@Singleton
public class Dummy1ServiceImpl implements SingletonService {

	public Dummy1ServiceImpl() {
		System.out.println(this.getClass().getName() + " instantiated");
	}
	
	@Override
	public String getDummy() {
		return "dummy";
	}

}

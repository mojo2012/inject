package io.spotnext.inject.beans.impl;

import io.spotnext.inject.annotations.Prototype;
import io.spotnext.inject.beans.PrototypeBean;

@Prototype
public class PrototypeBeanImpl implements PrototypeBean {

	public PrototypeBeanImpl() {
		System.out.println(this.getClass().getName() + " instantiated");
	}

}

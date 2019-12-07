package io.spotnext.inject;

public class BeanException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	public BeanException(String message) {
		super(message);
	}

	public BeanException(Throwable rootCause) {
		super(rootCause);
	}

	public BeanException(String message, Throwable rootCause) {
		super(message, rootCause);
	}

}

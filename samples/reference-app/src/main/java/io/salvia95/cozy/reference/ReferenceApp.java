package io.salvia95.cozy.reference;

import org.springframework.boot.autoconfigure.SpringBootApplication;

import io.salvia95.cozy.runtime.CozyApplication;

/** Cozy 참조 앱. 모듈은 memo, activity, dashboard다. */
@SpringBootApplication
public class ReferenceApp {

	public static void main(String[] args) {
		CozyApplication.run(ReferenceApp.class, args);
	}
}

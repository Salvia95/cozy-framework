package io.salvia95.cozy.reference;

import org.springframework.boot.autoconfigure.SpringBootApplication;

import io.salvia95.cozy.runtime.CozyApplication;

/** Cozy 참조 앱. 모듈(memo, activity, dashboard)은 M2 이후에 추가한다. */
@SpringBootApplication
public class ReferenceApp {

	public static void main(String[] args) {
		CozyApplication.run(ReferenceApp.class, args);
	}
}

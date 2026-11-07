package it.cozy.app;

import org.springframework.boot.autoconfigure.SpringBootApplication;

import io.salvia95.cozy.runtime.CozyApplication;

/** 레시피앱. 실행: IntelliJ의 "dev 실행", 또는 mvn -Pdist package로 만든 실행 파일. */
@SpringBootApplication
public class App {

	public static void main(String[] args) {
		CozyApplication.run(App.class, args);
	}
}

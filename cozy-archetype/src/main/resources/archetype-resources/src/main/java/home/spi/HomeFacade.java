package ${package}.home.spi;

/** 홈 쓰기. 쓰기 Facade는 컨트롤러와 이 모듈의 app.command만 부른다. */
public interface HomeFacade {

	/** @return 새 메모의 id */
	long add(String title);
}

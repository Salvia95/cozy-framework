package {{spiPkg}};

{{timeImports}}/**
 * {{L}} 쓰기 (cozy add crud). 컨트롤러(api)와 이 모듈의 app.command만 부른다. 다른 모듈의 쓰기는 이벤트로 알린다(C8).
 */
public interface {{E}}Facade {

	/** @return 만든 {{L}}의 id */
	long create({{params}});

	/** @return 바꿨으면 true, 없는 id면 false */
	boolean update(long id, {{params}});

	/** @return 지웠으면 true, 없는 id면 false */
	boolean delete(long id);
}

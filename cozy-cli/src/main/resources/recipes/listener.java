package {{pkg}};

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import {{eventImport}};
{{facadeImport}}
/**
 * {{source}}의 {@link {{Event}}}를 받는다 (cozy add listener). 발행한 트랜잭션이 커밋된 뒤 별도 트랜잭션에서 비동기로 실행되고,
 * 같은 이벤트는 인박스 덕분에 한 번만 처리된다(docs/conventions/events.md). 메서드 이름(on)과 위치가 리스너 id이므로 바꾸지 않는다.
 */
@Component
class {{Name}}Listener {
{{facadeField}}
	@ApplicationModuleListener
	void on({{Event}} event) {
		// cozy:slot listener-body — {{Event}}를 받았을 때 할 일을 쓰세요. {{use}}. 다른 모듈의 Facade는 부르지 않습니다
		throw new UnsupportedOperationException("cozy:slot listener-body");
	}
}

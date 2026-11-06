/**
 * 홈: 새 모듈을 만들 때 복제하는 샘플. 컨트롤러, 서비스, 화면 DTO(view/), 내부 구현(internal/), 템플릿, 화면 스펙을 보여 준다.
 * 다른 모듈을 쓰려면 allowedDependencies에 "모듈::api"를 적는다. 문서: AGENTS.md
 */
@ApplicationModule(allowedDependencies = {})
package ${package}.home;

import org.springframework.modulith.ApplicationModule;

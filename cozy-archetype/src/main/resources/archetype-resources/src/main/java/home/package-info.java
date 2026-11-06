/**
 * 홈: 새 모듈을 만들 때 복제하는 샘플이다. 구조는 AGENTS.md와 docs/conventions/structure.md를 따른다.
 *
 * <ul>
 * <li>api: 컨트롤러(HomeController)와 화면 DTO(api.dto). 화면 템플릿은 templates/home/api/</li>
 * <li>spi: 다른 모듈이 쓰는 계약. HomeFacade(쓰기), HomeFinder(읽기), dto#if( $events == "true" ), event#end</li>
 * <li>internal: app.command(쓰기 구현), app.query(읽기 구현), domain, infra(저장소), config. 조각 템플릿은
 * templates/home/internal/</li>
 * </ul>
 * 다른 모듈을 쓰려면 allowedDependencies에 "모듈 :: spi"를 적는다.
 */
@ApplicationModule(allowedDependencies = {})
package ${package}.home;

import org.springframework.modulith.ApplicationModule;

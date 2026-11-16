/** 이벤트 계약: 주인 모듈 이름의 폴더에 이벤트 record만 둔다. 다른 앱 모듈에 의존하지 않는다 */
@ApplicationModule(allowedDependencies = {})
package it.cozy.app.events;

import org.springframework.modulith.ApplicationModule;

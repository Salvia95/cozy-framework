package {{pkg}};

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.salvia95.cozy.ui.CozyNavItem;

/** {{module}} 모듈 전용 설정·빈. 내비게이션 항목은 cozy add crud, cozy add screen --nav가 아래 표시 자리에 덧붙인다. */
@Configuration(proxyBeanMethods = false)
class {{M}}Configuration {

	// cozy:append nav
}

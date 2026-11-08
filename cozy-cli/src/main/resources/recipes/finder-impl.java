package {{pkg}};

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import {{finderImport}};

/**
 * {{module}} 읽기 구현. 읽기 트랜잭션이고 쓰기 구현(app.command)을 부르지 않는다. 메서드는 cozy add query {{module}} <이름>으로 더한다.
 */
@Service
@Transactional(readOnly = true)
class {{M}}FinderImpl implements {{M}}Finder {
}

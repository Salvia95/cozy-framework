package {{pkg}};

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import {{facadeImport}};

/**
 * {{module}} 쓰기 구현. 트랜잭션 경계가 여기다(쓰기 트랜잭션은 BEGIN IMMEDIATE, docs/conventions/sqlite.md). 메서드는 cozy add command
 * {{module}} <이름>으로 더한다.
 */
@Service
@Transactional
class {{M}}FacadeImpl implements {{M}}Facade {
}

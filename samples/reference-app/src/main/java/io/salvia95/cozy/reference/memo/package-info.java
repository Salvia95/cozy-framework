/**
 * 메모: SQLite + MyBatis CRUD, 목록+필터, 검증, PRG, MemoCreated 이벤트.
 *
 * <ul>
 * <li>api: 화면 컨트롤러와 화면 DTO. 템플릿은 templates/memo/api/(화면)와 memo/internal/(조각)</li>
 * <li>spi: 다른 모듈이 쓰는 계약. MemoFacade(쓰기), MemoFinder(읽기), DTO, 이벤트. 템플릿은 templates/memo/spi/</li>
 * <li>internal: app.command(쓰기), app.query(읽기), domain, infra(MyBatis), config</li>
 * </ul>
 * 구조 규칙: docs/conventions/structure.md
 */
@ApplicationModule(allowedDependencies = {})
package io.salvia95.cozy.reference.memo;

import org.springframework.modulith.ApplicationModule;

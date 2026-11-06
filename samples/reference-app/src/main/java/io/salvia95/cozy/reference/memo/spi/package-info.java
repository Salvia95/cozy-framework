/**
 * memo의 모듈 사이 계약 (Named Interface spi): 쓰기 MemoFacade, 읽기 MemoFinder, dto, event. 시그니처에는 원시 값과
 * spi.dto 타입만 쓴다(도메인 모델은 internal 밖으로 나가지 않는다). 공개 fragment는 templates/memo/spi/.
 */
@NamedInterface("spi")
package io.salvia95.cozy.reference.memo.spi;

import org.springframework.modulith.NamedInterface;

/**
 * 대시보드: memo·activity의 spi(Finder와 공개 fragment)를 조합하고 polling-panel로 최근 활동을 갱신한다 (PRD §6.7).
 */
@ApplicationModule(allowedDependencies = { "memo :: spi", "activity :: spi" })
package io.salvia95.cozy.reference.dashboard;

import org.springframework.modulith.ApplicationModule;

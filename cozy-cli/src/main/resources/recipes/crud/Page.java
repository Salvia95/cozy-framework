package {{spiDtoPkg}};

import java.util.List;

/** 검색 결과 한 페이지와 전체 건수 (cozy add crud). */
public record {{E}}Page(List<{{E}}Item> items, long total) {
}

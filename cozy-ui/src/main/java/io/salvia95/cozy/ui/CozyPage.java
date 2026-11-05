package io.salvia95.cozy.ui;

/**
 * 페이지 정보 (pagination 컴포넌트 입력).
 *
 * @param number 현재 페이지, 1부터
 * @param size 페이지 크기
 * @param totalElements 전체 건수
 */
public record CozyPage(int number, int size, long totalElements) {

	public CozyPage {
		if (number < 1 || size < 1 || totalElements < 0) {
			throw new IllegalArgumentException("[cozy] CozyPage 값이 잘못되었습니다: " + number + ", " + size + ", " + totalElements);
		}
	}

	/** 요청한 페이지 번호를 1 이상으로 맞춘다. */
	public static CozyPage of(Integer requested, int size, long totalElements) {
		int number = (requested == null || requested < 1) ? 1 : requested;
		return new CozyPage(number, size, totalElements);
	}

	public int totalPages() {
		return (int) Math.max(1, (this.totalElements + this.size - 1) / this.size);
	}

	public long offset() {
		return (long) (this.number - 1) * this.size;
	}

	public boolean hasPrevious() {
		return this.number > 1;
	}

	public boolean hasNext() {
		return this.number < totalPages();
	}

	public int previous() {
		return Math.max(1, this.number - 1);
	}

	public int next() {
		return Math.min(totalPages(), this.number + 1);
	}
}

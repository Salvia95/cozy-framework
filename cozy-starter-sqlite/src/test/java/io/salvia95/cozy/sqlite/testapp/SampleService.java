package io.salvia95.cozy.sqlite.testapp;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SampleService {

	private final SampleMapper mapper;

	SampleService(SampleMapper mapper) {
		this.mapper = mapper;
	}

	/** 쓰기만 하는 트랜잭션 */
	@Transactional
	public void insert(String name) {
		this.mapper.insertName(name);
	}

	/** 읽은 뒤 쓰는 트랜잭션 (DEFERRED에서 쓰기 잠금으로 올라간다) */
	@Transactional
	public long countThenInsert(String name) {
		long before = this.mapper.count();
		this.mapper.insertName(name);
		return before;
	}
}

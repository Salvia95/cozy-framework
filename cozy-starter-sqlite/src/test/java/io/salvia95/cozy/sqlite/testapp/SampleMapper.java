package io.salvia95.cozy.sqlite.testapp;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SampleMapper {

	int insert(SampleItem item);

	int insertName(String name);

	SampleItem findByName(String name);

	long count();

	int insertUnique(String code);
}

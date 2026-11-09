package io.salvia95.cozy.sqlite;

import java.nio.file.Path;

import org.jspecify.annotations.Nullable;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code cozy.sqlite.*}. 문서: docs/conventions/sqlite.md
 *
 * @param file DB 파일. 비우면 {@code {dataDir}/data/app.db}, 상대 경로면 데이터 루트 기준이다.
 * @param backup 마이그레이션 전 백업
 */
@ConfigurationProperties("cozy.sqlite")
public record CozySqliteProperties(@Nullable Path file, @DefaultValue Backup backup) {

	/**
	 * @param keep 보관할 최근 백업 개수
	 */
	public record Backup(@DefaultValue("5") int keep) {
	}
}

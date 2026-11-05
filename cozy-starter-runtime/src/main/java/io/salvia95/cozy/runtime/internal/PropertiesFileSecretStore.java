package io.salvia95.cozy.runtime.internal;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;

import io.salvia95.cozy.runtime.CozySecretStore;

/**
 * v1 기본 비밀 값 저장소: {@code {dataDir}/config/secrets.properties} (UTF-8).
 *
 * <p>읽을 때마다 파일을 다시 읽으므로 앱을 다시 띄우지 않아도 바뀐 값이 반영된다. 값은 어디에도 기록하지 않는다.
 */
public class PropertiesFileSecretStore implements CozySecretStore {

	private final Path file;

	public PropertiesFileSecretStore(Path file) {
		this.file = file;
	}

	@Override
	public Optional<String> find(String key) {
		Properties properties = new Properties();
		try (Reader reader = Files.newBufferedReader(this.file, StandardCharsets.UTF_8)) {
			properties.load(reader);
		}
		catch (NoSuchFileException ex) {
			return Optional.empty();
		}
		catch (IOException ex) {
			// 메시지에 파일 경로만 남기고 내용은 남기지 않는다
			throw new UncheckedIOException("[cozy] 비밀 값 파일을 읽을 수 없습니다: " + this.file, ex);
		}
		String value = properties.getProperty(key);
		return (value == null || value.isEmpty()) ? Optional.empty() : Optional.of(value);
	}

	@Override
	public String toString() {
		return "PropertiesFileSecretStore[" + this.file + "]";
	}
}

package io.salvia95.cozy.cli.internal.channel;

import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

/**
 * 이 PC의 채널 설정. 환경 변수({@code COZY_CHANNEL}, {@code COZY_CHANNEL_TOKEN})가 사용자 설정 파일보다 앞선다.
 *
 * @param address 채널 색인 주소. 정하지 않았으면 {@code null}
 * @param source 주소의 출처(메시지에 보인다)
 * @param header 인증 머리 이름. 정하지 않았으면 {@code null}({@code Authorization: Bearer})
 * @param token 인증 토큰. 없으면 {@code null}. 출력과 메시지에 보이지 않는다
 */
public record ChannelSettings(String address, String source, String header, String token) {

	public static ChannelSettings resolve(Map<String, String> env, Path config) {
		Properties properties = UserConfig.read(config);
		String fromEnv = blankToNull(env.get(ChannelCommands.CHANNEL_ENV));
		String address = (fromEnv != null) ? fromEnv : blankToNull(properties.getProperty(UserConfig.CHANNEL));
		String source = (fromEnv != null) ? "환경 변수 " + ChannelCommands.CHANNEL_ENV : "설정 파일 " + config;
		String tokenEnv = blankToNull(env.get(ChannelCommands.TOKEN_ENV));
		String token = (tokenEnv != null) ? tokenEnv : blankToNull(properties.getProperty(UserConfig.TOKEN));
		return new ChannelSettings(address, (address == null) ? null : source,
				blankToNull(properties.getProperty(UserConfig.HEADER)), token);
	}

	public boolean configured() {
		return this.address != null;
	}

	/** 머리 이름. {@code Authorization: Bearer}처럼 값의 앞부분을 함께 적을 수 있다 */
	String headerName() {
		String header = (this.header != null) ? this.header : ChannelCommands.DEFAULT_HEADER;
		int colon = header.indexOf(':');
		return ((colon < 0) ? header : header.substring(0, colon)).strip();
	}

	String headerValue() {
		String header = (this.header != null) ? this.header : ChannelCommands.DEFAULT_HEADER;
		int colon = header.indexOf(':');
		String prefix = (colon < 0) ? "" : header.substring(colon + 1).strip();
		return prefix.isEmpty() ? this.token : prefix + " " + this.token;
	}

	@Override
	public String toString() {
		return "ChannelSettings[address=" + this.address + ", source=" + this.source + ", header=" + this.header + ", token="
				+ ((this.token == null) ? "없음" : "있음") + "]";
	}

	private static String blankToNull(String value) {
		return (value == null || value.isBlank()) ? null : value.strip();
	}
}

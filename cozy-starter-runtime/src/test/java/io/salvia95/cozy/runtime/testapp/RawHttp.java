package io.salvia95.cozy.runtime.testapp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Host 헤더를 마음대로 넣기 위한 원시 HTTP 요청. JDK HttpClient는 Host 헤더를 바꾸지 못하게 막는다.
 */
public final class RawHttp {

	private RawHttp() {
	}

	/** @return 응답 상태 코드 */
	public static int get(String address, int port, String path, String hostHeader) throws IOException {
		try (Socket socket = new Socket()) {
			socket.connect(new InetSocketAddress(address, port), 3000);
			socket.setSoTimeout(5000);
			OutputStream out = socket.getOutputStream();
			out.write(("GET " + path + " HTTP/1.1\r\nHost: " + hostHeader + "\r\nConnection: close\r\n\r\n")
				.getBytes(StandardCharsets.US_ASCII));
			out.flush();
			BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
			String status = in.readLine();
			return Integer.parseInt(status.split(" ")[1]);
		}
	}
}

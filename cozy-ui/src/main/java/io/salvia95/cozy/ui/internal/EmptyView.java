package io.salvia95.cozy.ui.internal;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.servlet.View;

/** 본문 없는 200 응답. htmx 리다이렉트(HX-Redirect)에 쓴다. */
public class EmptyView implements View {

	@Override
	public String getContentType() {
		return "text/html;charset=UTF-8";
	}

	@Override
	public void render(Map<String, ?> model, HttpServletRequest request, HttpServletResponse response) {
		response.setStatus(HttpServletResponse.SC_OK);
		response.setContentType(getContentType());
		response.setContentLength(0);
	}
}

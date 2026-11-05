package it.cozy.app.memo;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/** 평범한 앱 테스트. cozy-test가 클래스패스에 있으면 C7이 실패 모드로 켜진다. */
@SpringBootTest(properties = "cozy.data-dir=target/test-data")
@AutoConfigureMockMvc
class MemoControllerTest {

	@Autowired
	MockMvc mvc;

	@Test
	void list() throws Exception {
		this.mvc.perform(get("/memo")).andExpect(status().isOk());
	}
}

package io.salvia95.cozy.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/** C4: htmx와 Alpine 속성은 허용 목록에 있는 것만 통과하고, 나머지 HTML 오류는 그대로 남는다. */
class CozyHtmlValidatorTest {

	private final CozyHtmlValidator validator = new CozyHtmlValidator();

	private List<String> errors(String body) {
		return this.validator.validateFragment(body)
			.stream()
			.filter(CozyHtmlValidator.Message::isError)
			.map(CozyHtmlValidator.Message::message)
			.toList();
	}

	@Test
	void allowedAlpineAndHtmxAttributesPass() {
		assertThat(errors("""
				<div x-data="cozyDisclosure" x-cloak hx-get="/memo" hx-target="#result">
				  <button type="button" x-on:click="toggle" x-bind:aria-expanded="open">필터</button>
				  <div x-show="open" x-transition x-transition:enter.duration.200ms x-on:keydown.escape.window="close" x-ref="panel" x-init="start" x-effect="sync" x-id="['memo']">
				    <input type="text" aria-label="검색어" x-model.debounce.300ms="query">
				    <template x-for="item in items"><span x-bind:key="item" x-text="item"></span></template>
				    <template x-if="open"><span x-text="'열림'"></span></template>
				    <div x-ignore></div>
				  </div>
				</div>""")).isEmpty();
	}

	@Test
	void unlistedAlpineAttributesAndShorthandFail() {
		assertThat(errors("""
				<div x-data="cozyDisclosure"><p x-html="body"></p><p x-foo="a"></p><button type="button" @click="toggle">a</button></div>"""))
			.hasSize(3)
			.allMatch((message) -> message.startsWith("Attribute “"));
	}

	@Test
	void otherErrorsRemain() {
		assertThat(errors("<p hx-on:click=\"go()\" x-on:click=\"toggle\" id=\"a\"></p><p id=\"a\"></p>")).hasSize(2);
	}
}

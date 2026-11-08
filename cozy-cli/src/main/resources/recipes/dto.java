package {{pkg}};

{{imports}}/**
 * {{Record}}: {{module}}가 다른 모듈에 내보내는 값 (모듈 사이 DTO, cozy add dto). 다른 모듈은 {{module}}의 Finder와 공개 fragment로 받는다.
{{params}} */
public record {{Record}}({{components}}) {
}

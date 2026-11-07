package {{domainPkg}};

{{instantImports}}/** {{L}} 한 건 (도메인 모델, cozy add crud). 모듈 밖으로 내보내지 않는다. 밖에는 spi.dto.{{E}}Item으로 바꿔서 준다. */
public record {{E}}({{components}}) {
}

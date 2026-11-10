# 레시피 픽스처 (ADR-0032 M10, ADR-0033 M12~)

`cozy add`·`cozy link`가 만든 구조가 실제 앱에서 동작하는지 확인한다. `mvn verify`가 실행한다(`mvn -o verify -pl samples/recipes -am`).

- **기본 앱** `base/`: 아키타입(persistence=true, events=true)으로 만든 앱에서 `home` 모듈을 뺀 것. 패키지는 `it.cozy.app`.
- **순서**: 각 픽스처의 `setup.groovy`가 [recipes.groovy](recipes.groovy)를 부른다. 기본 앱을 복사하고 `recipe.txt`를 위에서부터 실행한다(형식은 그 파일의 머리 주석). 그다음 invoker가 `mvn verify`를 실행하고 `verify.groovy`가 결과를 확인한다.
- **손 작업** `hand/`: 에이전트가 빈칸을 채운 결과로 치는 파일. 구조 파일은 두지 않는다(`reference-filled`의 `verify.groovy`가 확인한다).
- **기록**: `target/projects/<픽스처>/recipes.log`에 실행한 명령과 출력이 남는다.

| 픽스처 | 레시피 | 기대 |
| --- | --- | --- |
| `reference` | reference-app 재구성 (ADR-0032 부록 §5, activity는 `dto`·`query`·`command`, ADR-0033 §6), 다시 실행, 실패해야 하는 명령 6개 | C9 7곳만 실패 |
| `reference-filled` | 위와 같고 빈칸을 `hand/reference-filled`로 채움. 채운 뒤 `query`·`command`를 다시 실행 | 통과 |
| `crud-kinds` | `crud`(필드 종류 전부, 이벤트 셋), `module`, `link`, `event`, 다시 실행 | 통과 |
| `screen-details` | `screen`(details, overview), `event`, `listener`, `migration`, 다시 실행 | C9 4곳만 실패 |
| `stage-two` | sqlite만 있는 앱에 `enable events`, `crud`, 리스너가 만든 빈 Facade를 `crud`가 채움, `dto`(record·list 구성 요소), crud가 발행하는 이벤트에 `event-version` 두 번(v3까지), 다시 실행, 실패해야 하는 명령 5개, 리스너 빈칸을 `hand/stage-two`로 채움 | 통과 (C6 포함) |
| `field` | `crud`를 만든 뒤 `add field`를 종류마다 한 번씩(필수는 `--default`), 중간에 `hand/field`로 `create` 본문을 고침, `--default` 없는 필수 필드는 실패, 다시 실행 | 통과 |

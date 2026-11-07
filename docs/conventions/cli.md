# cozy 명령 (cozy-cli)

앱의 구조를 보여 주고(`describe`), 컴파일 없이 빠르게 검사하고(`check`), 구조를 만드는 레시피를 알려 준다(`recipes`). 결정은 [ADR-0032](../adr/0032-agent-cli.md)에 있다. 구조는 명령이 만들고, 에이전트는 명령이 남긴 빈칸만 채운다.

- **지금 쓸 수 있는 것(M9)**: `describe`, `check`, `recipes`
- **아직 없는 것**: `add`와 `link`(레시피)는 M10에서 만든다. 지금은 종료 코드 2로 알린다.
- **번들 반입**: 오프라인 번들의 `cozy` 진입점에 붙이는 일은 M11이다. 그 전까지는 jar로 실행한다.

```bash
mvn -o package -pl cozy-cli                         # target/cozy-cli-*.jar + target/lib/
cd my-app && java -jar <레포>/cozy-cli/target/cozy-cli-1.1.0-SNAPSHOT.jar check
```

## 형식

```text
cozy <동작> [옵션]

  describe [--json]   앱의 지도: 모듈, 의존, spi 계약, 이벤트, 화면, 공개 fragment, 남은 빈칸
  check [--json]      빠른 검사: 템플릿 규칙 R1~R15, 모듈 구조 C8, 빈칸 C9
  recipes [--json]    구조를 만드는 명령 목록
```

| 옵션 | 기본값 | 뜻 |
| --- | --- | --- |
| `--project <폴더>` | 현재 폴더 | 앱 폴더. 위로 올라가며 `pom.xml`과 `src/main/java`가 함께 있는 폴더를 찾는다 |
| `--repository <폴더>` | `COZY_REPOSITORY`, 없으면 `~/.m2/repository` | cozy jar가 있는 Maven 저장소(저장소 배치). `check`가 cozy-ui jar를 여기서 찾는다 |
| `--json` | 끔 | 기계가 읽는 출력 (아래 형식) |

| 종료 코드 | 뜻 |
| --- | --- |
| 0 | 이상 없음 |
| 1 | `check`에서 오류가 하나 이상 있음 |
| 2 | 사용법 오류이거나 판단할 수 없음. 앱을 찾지 못함, cozy-ui jar가 없음, 아직 없는 동작. 이유는 표준 오류에 한 줄로 쓴다 |

## describe

소스와 자원 파일을 읽어 앱의 구조를 짧게 보여 준다. 작은 모델이 파일을 다 읽지 않고도 무엇이 어디 있는지 알게 하려는 것이다. reference-app의 결과가 [기대 파일](../../cozy-cli/src/test/resources/expected/reference-app.describe.txt)이다.

```text
모듈 memo (io.salvia95.cozy.reference.memo)
  의존: 없음
  쓰기 MemoFacade
    long create(String title, String body, boolean pinned)
  읽기 MemoFinder
    Optional<MemoItem> find(long id)
  DTO MemoItem(long id, String title, String body, boolean pinned, Instant createdAt, Instant updatedAt)
  이벤트 MemoCreated(long memoId, String title) 발행: src/main/java/.../MemoFacadeImpl.java
  화면 memo/api/list — src/main/resources/specs/memo-list.yml: default, empty, noResults, paged
  공개 fragment memo/summary: memoSummary(summary)
```

`--json`의 키는 다음과 같다.

| 키 | 값 |
| --- | --- |
| `app` | `basePackage`, `mainClass`, `cozyVersion`(cozy-parent 버전), `starters`(pom에 직접 적은 cozy 의존. 간접 의존은 빠진다) |
| `modules[]` | `name`, `package`, `dependsOn`(선언한 의존. 선언하지 않았으면 `null` = open), `facades[]`·`finders[]`(`type`, `methods[]` 시그니처), `dtos[]`(`type`, `fields[]`), `events[]`(`type`, `fields[]`(`eventId`·`schemaVersion` 제외), `publishedIn[]` 발행하는 파일), `listeners[]`(`type`, `method`, `on` = `모듈.이벤트`), `controllers[]`, `screens[]`(`template`, `spec`, `states[]`), `fragments[]`(공개 fragment `id`, `signature`) |
| `slots[]` | 남은 빈칸: `file`, `line`, `slot`(id), `what` |
| `problems[]` | 읽지 못한 것(아래 "판단할 수 없는 것") |

- **발행하는 파일**: 소스에서 `이벤트.of(`나 `new 이벤트(`를 찾는다. 이 목록이 비면 아직 발행하지 않는 이벤트다.
- **모듈 판정**: Spring Modulith와 같다. 메인 클래스 패키지 바로 아래 패키지가 모듈이고, `@ApplicationModule(id)`가 있으면 그 이름을 쓴다. 의존 선언(`allowedDependencies`)이 없으면 open이다.

## check

`mvn verify` 전에 몇 초 안에 끝나는 검사다. 판단은 cozy-test와 같은 cozy-rules가 한다. 그래서 같은 위반은 같은 메시지로 나온다.

| 검사 | 입력 | cozy-test와 다른 점 |
| --- | --- | --- |
| C2 (R1~R15) | 앱 템플릿, cozy-ui jar의 템플릿·매니페스트·아이콘, 앱 매니페스트, `cozy.rules.allow-same-layer` | 없음 |
| C8 | Java 소스(javac 파서). 이름은 import, 같은 패키지, 와일드카드 import로 푼다 | 메타 애너테이션을 모른다. 알려진 스프링 애너테이션(`@Controller`, `@RestController`, `@Component`, `@Service`, `@Repository`, `@Configuration`, `@ControllerAdvice`)만 본다 |
| C9 | `src/main` 아래 모든 파일 | 없음 |

- **하지 않는 검사**: C1(Modulith), C3~C5(렌더링·스펙), C6(이벤트 호환), C7(뷰 이름)은 앱을 컴파일하고 띄워야 한다. 이것은 `mvn verify`가 한다. `check`가 통과해도 `mvn verify`는 실패할 수 있다.
- **cozy-ui jar**: 앱이 cozy-ui를 쓰면 `--repository`에서 `io/salvia95/cozy/cozy-ui/{cozy-parent 버전}/`의 jar를 찾는다. jar는 zip으로만 읽고 클래스는 쓰지 않는다. jar가 없으면 프레임워크 템플릿 없이 판단할 수 없으므로 종료 코드 2로 끝난다(앱 폴더에서 `mvn -o dependency:resolve`를 한 번 실행하면 생긴다).

글 출력은 위반마다 `Violation.format()` 한 줄이고, 마지막 줄이 요약이다.

```text
[C8] it.cozy.app.memo.MemoService — 모듈 루트(it.cozy.app.memo)에는 package-info.java만 둡니다. ...
[cozy check] 오류 7건 (R1~R15, C8, C9). 컴파일과 렌더링 검사(C1, C3~C7)는 mvn verify가 합니다.
```

`--json`은 다음과 같다.

```json
{
  "ok": false,
  "errors": 7,
  "warnings": 0,
  "violations": [
    {
      "id": "C8",
      "severity": "error",
      "location": "it.cozy.app.memo.MemoService",
      "file": "src/main/java/it/cozy/app/memo/MemoService.java",
      "line": 0,
      "target": null,
      "message": "모듈 루트(it.cozy.app.memo)에는 package-info.java만 둡니다. ...",
      "command": null,
      "text": "[C8] it.cozy.app.memo.MemoService — ..."
    }
  ]
}
```

| 키 | 값 |
| --- | --- |
| `id` | `R1`~`R15`, `C8`, `C9`, `CLI`(읽지 못한 것) |
| `severity` | `error` 또는 `warning`(`th:include`의 R7). 경고는 종료 코드에 영향을 주지 않는다 |
| `location` | 메시지의 위치: 템플릿 경로, 소스 경로, C8이면 타입·패키지 이름 |
| `file` | 고칠 파일의 프로젝트 기준 경로. C8의 타입은 그 소스 파일, 패키지는 그 폴더. 프레임워크 jar 안이면 `null` |
| `line` | 줄 번호. 모르면 0 |
| `target` | 문제가 된 참조. 위치 자체가 문제면 `null` |
| `command` | 이 위반을 고치는 cozy 명령. 지금은 항상 `null`이다(M11에서 채운다) |
| `text` | 글 출력과 같은 한 줄 |

## 판단할 수 없는 것

추측하지 않고 `CLI` 오류로 알린다. `check`는 실패하고 `describe`는 `problems`에 적는다.

| 경우 | 메시지 |
| --- | --- |
| Java 문법 오류 | `[CLI] src/main/java/.../Broken.java:4 — Java 문법 오류: ...` |
| `allowedDependencies`가 글자 그대로의 문자열이 아님(상수, 식) | `allowedDependencies를 읽을 수 없습니다. 글자 그대로의 문자열로 적으세요 ...` |
| `@SpringBootApplication` 클래스가 없거나 둘 이상 | `@SpringBootApplication 클래스를 찾지 못했습니다 ...` |
| 매니페스트 형식 오류 | `[R8] src/main/resources/cozy/fragments.yml — fragment 매니페스트 오류 ...` |

## recipes

구조를 만드는 명령의 목록이다. [ADR-0032 부록](../adr/0032-agent-cli-recipes.md)과 같다. `--json`은 레시피마다 `name`, `stage`(1 또는 2), `usage`, `does`, `slots`, `available`을 낸다. 지금은 모두 `available: false`다.

## 의존

cozy-cli는 cozy-rules와 SnakeYAML(Boot BOM), JDK에만 의존한다(ADR-0016 개정). Java 소스는 JDK의 javac 파서(`com.sun.source`)로 읽으므로 JRE가 아니라 JDK 25로 실행한다. 이 경계는 모듈의 enforcer(`cozy-module-boundary`)가 검사한다. 테스트만 cozy-ui jar를 저장소 배치로 복사해 쓴다.

# ADR-0032 에이전트 작업 분담: 구조는 cozy 명령이, 로직은 에이전트가

- 상태: 승인 (2026-11-07, 사용자 결정 "C9는 실패로, 나머지도 제안대로 승인"). §8 개정 (2026-11-08): 평가는 사용자의 내부 시스템에서 한다
- 출처: 사용자 지시 "소형 Private LLM 모델의 성능을 감안하여 최대한 에이전틱 프로그래밍에 최적화. 보일러플레이트 생성, 의존성 연결, 이벤트 추가 등은 에이전트가 직접 하지 않고 프레임워크에 최적화된 룰을 기반으로 CLI가 처리하고, 그 안의 로직 작성만 에이전트가 한다."
- 바꾸는 결정: ADR-0016(모듈 의존 방향에 `cli → rules` 추가). ADR-0001~0015는 바꾸지 않는다.
- 부록: [레시피 목록](0032-agent-cli-recipes.md)

## 배경

- **Cozy의 두 목표**:
  - 폐쇄망에서 레거시에 익숙한 사용자의 인지 부하를 줄인다.
  - 폐쇄망 안의 작은 LLM이 앱을 만들 수 있게 한다.
- **검사기는 갖춰져 있다.** C1~C8과 R1~R15가 구조를 빌드 때 막는다. 실패 메시지도 작은 LLM이 메시지만 보고 고칠 수 있게 쓰여 있다([rules.md](../conventions/rules.md)).
- **생성기는 `cozy new` 하나뿐이다.** 앱을 만든 뒤에 모듈, 화면, 이벤트를 더하는 일은 손으로 한다.
- **작은 모델은 여러 파일을 서로 맞추는 일에서 실패한다.** 메서드 하나 안의 로직은 잘 쓴다. 예를 들어 이벤트 하나를 더하려면 두 모듈에 걸친 여섯 곳을 맞춰야 한다.

  | 파일 | 지켜야 할 것 | 어기면 |
  | --- | --- | --- |
  | `{보내는 모듈}/spi/event/{이벤트}.java` | record, `CozyEvent`, `eventId`·`schemaVersion`, `of()` | C8, C6 |
  | `{보내는 모듈}/spi/event/package-info.java` | `@NamedInterface("spi")` | C8 |
  | `src/test/resources/events/{이벤트}/v1.json` | 직렬화 샘플 | C6 |
  | `{보내는 모듈}/internal/app/command/*FacadeImpl` | 트랜잭션 안에서 `publishEvent` | 런타임에야 드러난다 |
  | `{받는 모듈}/package-info.java` | `allowedDependencies`에 `"{보내는 모듈} :: spi"` | C1 |
  | `{받는 모듈}/internal/app/command/*Listener` | `@ApplicationModuleListener`, 파라미터 하나 | C6, C8 |

- **규칙이 두 곳에 있다.**
  - 템플릿 규칙(R1~R15)은 `cozy-rules`(JDK, AttoParser)에 있다.
  - 배치·이름·방향 규칙(C8)은 `cozy-test`의 `LayerChecks`(ArchUnit)에 있다.
  - 생성기가 이 규칙들의 세 번째 사본을 가지면, CLI가 만든 코드를 검사가 막는 일이 생긴다.

## 결정

### 1. 분담

| 누가 | 무엇을 |
| --- | --- |
| cozy 명령 | 파일의 위치·이름·패키지, 어노테이션(`@ApplicationModule`, `@NamedInterface`, `@Controller`, `@Service`, `@Transactional`, `@ApplicationModuleListener`), 인터페이스와 Impl 쌍, 모듈 의존 선언, 매니페스트 항목, 화면 스펙의 틀, 이벤트 샘플, 마이그레이션 파일 이름, Mapper XML의 namespace, 내비게이션 항목 |
| 에이전트 | 빈칸(§3) 안의 코드: 메서드 본문, SQL(DDL, 쿼리), 템플릿의 본문 마크업, 스펙 fixture 값. 그리고 테스트 |

- **구조를 바꿀 때는 명령을 쓴다.** 에이전트는 파일을 만들거나 옮기지 않고, 어노테이션과 의존 선언을 손으로 고치지 않는다. 이 약속은 앱의 `AGENTS.md`에 적는다(§7).
- **cozy 명령은 LLM을 부르지 않는다.** 같은 입력에는 늘 같은 결과를 낸다.

### 2. 생성 방식

- **한 번 만들고 끝낸다.** 만든 파일은 그때부터 앱의 코드다. 다시 생성해서 덮어쓰지 않는다. 에이전트가 고친 부분과 충돌하기 때문이다.
- **기존 파일은 표시된 자리에만 덧붙인다.**
  - 명령이 만든 파일에는 덧붙일 자리 표시(`cozy:append {이름}`)가 있다.
  - `package-info.java`의 `allowedDependencies`는 명령이 만드는 형식이 정해져 있으므로 표시 없이 읽는다.
  - 표시가 없거나 형식을 알아볼 수 없으면 추측해서 고치지 않는다. 무엇을 손으로 해야 하는지 알리고 실패한다.
- **원자적이다.** 바꿀 파일 전체의 계획을 먼저 세운다. 하나라도 실패하면 아무 파일도 쓰지 않는다. `--dry-run`은 계획만 보여 준다.
- **같은 명령을 두 번 실행해도 결과가 같다.** 이미 같은 내용이 있으면 아무것도 하지 않는다. 다른 내용이 있으면 덮어쓰지 않고 실패한다.
- **생성 템플릿은 JDK만으로 처리한다.** 리소스의 텍스트에 이름을 끼워 넣는 정도로 충분하다. 템플릿 엔진을 새 의존성으로 들이지 않는다.

### 3. 빈칸과 검사 C9

- **빈칸 표시**: 에이전트가 채울 자리에는 `cozy:slot {id} — {무엇을}` 표시를 둔다.

  | 파일 | 표시 | 채우기 전 동작 |
  | --- | --- | --- |
  | Java | `// cozy:slot …` 다음 줄에 `throw new UnsupportedOperationException("cozy:slot {id}");` | 컴파일된다. 부르면 실패한다 |
  | HTML | `<!--/* cozy:slot … */-->` | 렌더링된다. 표시는 결과에 남지 않는다 |
  | SQL, XML, YAML | `-- cozy:slot …`, `<!-- cozy:slot … -->`, `# cozy:slot …` | 그대로 둔다 |

- **검사 C9 (빈칸)**: `CozyArchitectureTest`가 `src/main` 아래에 `cozy:slot`이 남아 있으면 빌드를 실패시킨다. 메시지는 위치와 무엇을 채울지를 한 줄로 알린다.
  - 표시를 지우지 않고 채운 경우도 실패한다. 다 채웠으면 표시를 지운다.
  - 예: `[C9] src/main/java/.../MemoCreatedListener.java:21 → cozy:slot listener-body — MemoCreated를 받았을 때 할 일을 쓰세요. 다 채우면 이 cozy:slot 표시를 지웁니다.`
- **생성 직후의 완료 기준**: 레시피를 적용한 직후 `mvn verify`가 내는 실패는 **C9뿐**이어야 한다. 빈칸이 없는 레시피(`crud` 등)는 생성 직후 통과해야 한다.
- 그래서 에이전트의 할 일 목록은 C9 메시지 목록과 같다.

### 4. 규칙의 한 원천

- **자리 표를 `cozy-rules`로 옮긴다.** 자리 표는 무엇을, 어디에, 어떤 이름으로 두는지와 레이어 방향을 담는다. 지금 [structure.md](../conventions/structure.md)의 "놓는 자리"와 `LayerChecks`가 가진 내용이다. JDK만 쓰는 데이터와 코드로 둔다.
- **같은 표를 함께 읽는다.** `LayerChecks`(C8)는 그 표로 검사하고, `cozy-cli`는 같은 표로 생성한다. ArchUnit에 묶인 부분(클래스 의존 읽기)은 `cozy-test`에 남는다.
- **레시피는 검사로 증명한다.** 레시피마다 invoker 픽스처를 둔다. 픽스처는 아키타입으로 만든 빈 앱에 레시피를 적용하고 `mvn verify`가 C9만 내는지(또는 통과하는지) 확인한다.

### 5. cozy-cli 모듈

- **새 모듈 `cozy-cli`**: 실행 jar 하나다. `cozy-parent`를 상속하고 `cozy-rules`에 의존한다. 앱은 이 모듈에 의존하지 않는다.
- **의존 방향(ADR-0016 개정)**: `cli → rules`를 더한다. 바깥 의존은 SnakeYAML 하나만 허용한다(ADR-0017 표에 있고 Boot BOM이 관리한다). 매니페스트와 스펙을 읽는 데 쓴다.
- **명령 진입점**: 오프라인 번들의 `cozy`(bash), `cozy.cmd`, `cozy.ps1`이 `add`, `link`, `describe`, `check`, `recipes`를 `java -jar lib/cozy-cli.jar`로 넘긴다. `install`, `new`, `version`, `help`는 지금처럼 셸이 처리한다.
- **앱 찾기**: 현재 폴더에서 위로 올라가며 `pom.xml`과 `@SpringBootApplication`을 찾는다. 기본 패키지는 메인 클래스의 패키지다.
- **구현 (M9, 2026-11-08)**:
  - Java 소스는 JDK의 javac 파서(`com.sun.source`)로 읽는다. 컴파일하지 않고 클래스패스도 쓰지 않는다. 새 의존성이 생기지 않는 대신 JRE가 아니라 JDK로 실행한다.
  - cozy-ui의 템플릿·매니페스트·아이콘은 저장소(`--repository`, `COZY_REPOSITORY`, `~/.m2/repository`)의 cozy-ui jar를 zip으로 읽는다. cozy-ui 클래스는 쓰지 않는다. jar가 없으면 판단할 수 없으므로 종료 코드 2다.
  - 매니페스트 파서는 cozy-ui의 `FragmentManifest`와 같은 형식 검사를 cozy-cli 안에 다시 둔다(cli → ui 의존을 만들지 않기 위해). 매니페스트 형식을 바꿀 때는 두 곳을 함께 고친다. cozy-ui에만 새 검사가 생기면 `cozy check`는 그것을 모른 채 통과시킬 수 있다(최종 판단은 `mvn verify`).
  - 소스로 읽지 못한 것(문법 오류, 글자 그대로가 아닌 `allowedDependencies`, 메인 클래스 없음)은 규칙 id `CLI`의 오류다.
  - 문서: [cli.md](../conventions/cli.md)
- **구현 (M10, 2026-11-08)**:
  - 생성 템플릿은 cozy-cli jar의 `/recipes/`에 두고 `{{이름}}` 자리만 채운다(JDK만 쓴다). 모르는 자리가 남으면 명령이 실패한다.
  - `package-info.java`의 의존 선언은 javac 파서로 위치를 찾아 그 부분만 바꾼다. 매니페스트는 SnakeYAML로 형식을 확인한 뒤 끝에 글자로 덧붙인다(주석과 순서를 지키기 위해서다).
  - 적용은 임시 파일에 쓰고 옮긴다(`ATOMIC_MOVE`). 쓰는 도중 실패하면 이미 바꾼 파일을 되돌린다. 계획을 세운 뒤 그사이 바뀐 파일이 있으면 적용하지 않는다.
  - 문서: [cli.md](../conventions/cli.md), 결과: [부록 §5](0032-agent-cli-recipes.md#5-reference-app을-레시피로-다시-만들기-m10-완료-기준)
- **구현 (M11, 2026-11-08)**:
  - 번들의 `lib/cli/`에 `cozy-cli.jar`와 그 의존(cozy-rules, AttoParser, SnakeYAML)을 담는다. `cozy install`이 `<설치 폴더>/lib/cli/`로 옮긴다. 진입점은 `java -jar lib/cli/cozy-cli.jar`이고 저장소는 `COZY_REPOSITORY`가 없으면 `<설치 폴더>/repository`다. 담긴 jar는 `repository/`에도 있으므로 라이선스 목록(ADR-0028)에 이미 든다.
  - 완료 기준 검증: `verify-offline.sh`가 네트워크 없는 이름공간에서 번들을 설치하고, events 앱에 `cozy add crud`·`cozy link`·실패하는 명령·`cozy check`·`cozy describe`를 실행한 뒤 `mvn -o verify`와 `-Pdist package`를 돌린다. Windows(`cozy.cmd` → `cozy.ps1`)는 [windows-verification.md](../conventions/windows-verification.md) M11 항목으로 사용자가 확인한다.

### 6. 조회와 빠른 검사

- **`cozy describe [--json]`**: 앱의 지도를 짧게 보여 준다. 작은 모델은 컨텍스트가 작아서 파일을 다 읽을 수 없기 때문이다.
  - 내용: 모듈, 모듈 사이 의존, spi의 Facade·Finder 메서드 시그니처, 이벤트와 그 필드, 리스너, 화면과 스펙 상태, 공개 fragment, 남은 빈칸.
  - 소스를 읽어서 만든다. 알아볼 수 없는 항목은 추측하지 않고 `알 수 없음`과 이유를 적는다.
- **`cozy check [--json]`**: 컴파일 없이 몇 초 안에 끝나는 검사다.
  - 범위: R1~R15, 소스로 판단할 수 있는 C8의 배치·이름 규칙, C9.
  - 최종 판단은 지금처럼 `mvn verify`가 한다. `cozy check`가 통과해도 `mvn verify`가 실패할 수 있다.
- **위반 결과 형식**: `{id, severity, file, line, target, message, command}`. `command`는 그 위반을 고치는 레시피가 있을 때만 채운다.
  - 예: R3에서 의존 선언이 빠진 경우 `cozy link voc --uses wiki`.
  - 이를 위해 `cozy-rules`의 `Violation`에 고치는 명령(선택 값)을 더한다.
- **구현 (M11, 2026-11-08)**: 명령을 붙이는 위반은 두 가지다. 둘 다 `cozy link {내 모듈} --uses {그 모듈}`이다.
  - R3에서 다른 모듈의 spi 템플릿을 쓰는데 의존 선언이 빠진 경우. `cozy check`와 `mvn verify`(C2)가 같은 명령을 낸다.
  - C1에서 Modulith가 `depends on named interface(s) 'b :: spi'`로 알린 경우. `mvn verify`만 낸다. 다른 메시지(internal 사용, 순환)에는 명령을 붙이지 않는다.
  - 글 출력은 메시지 끝에 `고치는 명령: …`을 붙인다. 형식: [cli.md](../conventions/cli.md#고치는-명령)

### 7. 앱 안의 에이전트 지침

- **`AGENTS.md`**: 아키타입이 앱 루트에 함께 만든다. 내용은 아래와 같다.
  - §1의 분담
  - 레시피 목록(`cozy recipes`의 출력과 같다)
  - 작업 순서: 명령 실행 → 빈칸 채우기 → `cozy check` → `mvn verify`
- 레시피의 이름과 인자는 이 파일을 통해 작은 모델이 직접 읽는 계약이다.
- **기존 `docs/AGENTS.md`와의 관계**: 지금 `docs/AGENTS.md`는 오프라인 번들의 `docs/`에 들어가고, 손으로 `home`을 복제해 모듈을 만드는 순서를 적고 있다. M11에서 이 문서를 앱 `AGENTS.md`의 원본으로 바꾼다. 파일 위치 표는 남기고, "새 모듈 만드는 순서"는 레시피로 바꾼다.
- **구현 (M11, 2026-11-08)**: `docs/AGENTS.md`가 원본이고 아키타입이 앱 루트에 복사한다(전부터 그랬다). 순서는 일하는 순서 → 명령 목록 → 빈칸 → 파일 위치 → 화면 → 금지 → 검증이다.
  - 명령 목록은 지금 쓸 수 있는 레시피만 싣는다. 2단계 레시피는 실리지 않는다. 글은 `cozy recipes`의 해당 줄과 같고, cozy-cli 테스트가 같은지 확인한다.
  - 1단계 명령으로 만들 수 없는 구조(Finder 메서드 추가, spi DTO, 서비스)는 파일 위치 표대로 손으로 만든다고 적는다. 2단계가 생기면 고친다.

### 8. 평가

- **개정 (2026-11-08, 사용자 결정)**: 평가는 레포에 두지 않는다. 사용자의 내부 코딩 에이전트 시스템(pi coding agent 기반)에 하네스를 따로 구성해 돌린다. 대상 모델은 Qwen Coder 30B급(3.6 coder 30B 또는 3.8 coder 27B)이다. 레포의 마일스톤은 "구현한 도구가 테스트로 동작하는지"만 완료 기준으로 삼는다.
- **레포가 하네스에 주는 것**: 명령의 `--json` 출력(§6), 위반의 고치는 명령, `AGENTS.md`(§7). 하네스는 이것만으로 명령을 부르고 결과를 읽을 수 있어야 한다.
- **처음 계획(참고)**: 같은 과제를 "명령 없이"와 "명령과 함께" 돌려 과제 통과율, 반복 횟수, 토큰을 비교한다. 과제는 reference-app의 기능을 쪼개서 만든다(예: "메모를 만들면 활동 기록 남기기"). 모델 서버를 부르므로 `mvn verify` 밖에 둔다.

### 9. 범위

| 단계 | 레시피 | 기존 파일 변경 |
| --- | --- | --- |
| 1단계 | `module`, `link`, `crud`, `screen`, `fragment`, `event`, `listener`, `migration` | `package-info.java`의 의존 선언, `fragments.yml`, 표시된 자리 |
| 2단계 | `query`, `command`, `field`, `enable events` | 명령이 만든 Java·XML·pom의 표시된 자리 |
| 범위 밖 | 이름 바꾸기, 지우기, 옮기기 | — |

- 범위 밖 작업은 손으로 하고 검사로 확인한다. 이 작업들은 모든 참조를 찾아 고쳐야 하는데, 그러려면 Java 파서가 있어야 하기 때문이다.

## 근거

- **실패하는 지점을 맞춘다.** 작은 모델이 실패하는 곳은 로직이 아니라 여러 파일 사이의 일관성이다. 그 부분을 결정적인 명령이 맡으면 모델의 몫은 파일 하나 안의 일로 줄어든다.
- **검사기를 버리지 않는다.** 명령은 검사를 대신하지 않는다. 같은 자리 표에서 만들어지므로 생성 결과가 검사를 통과하고, 손으로 고친 부분은 지금처럼 검사가 막는다.
- **실패로 닫는다.** 알아볼 수 없는 파일을 추측해서 고치지 않는다. 빈칸이 남으면 빌드가 실패한다. 레포의 기존 원칙과 같다.
- **한 번 생성**: 다시 생성하는 방식은 에이전트가 고친 코드와 충돌하고, 생성기 버전이 앱의 수명에 묶인다.

## 결과

- **새 공개 API**: 명령과 인자, `--json` 출력 형식, 빈칸 표시, C9, `AGENTS.md`. 구현할 때 `docs/conventions/cli.md`에 문서화한다.
- **바뀌는 산출물**:
  - `cozy-rules`: 자리 표, `Violation`의 고치는 명령
  - `cozy-test`: `LayerChecks`가 자리 표를 읽음, C9
  - 새 모듈 `cozy-cli`
  - 아키타입: `AGENTS.md`(원본은 `docs/AGENTS.md`), 덧붙일 자리 표시
  - 오프라인 번들: `lib/cozy-cli.jar`, 셸 진입점
  - 위반 픽스처: C9
- **마일스톤** (PRD §12에 M8~M11로 더했다, 2026-11-07):

  | 마일스톤 | 내용 | 완료 기준 |
  | --- | --- | --- |
  | M8 | 자리 표와 C8 판단을 `cozy-rules`로 이전, C9 | 기존 검사 결과가 바뀌지 않는다. 자리 표의 모든 역할이 C8을 통과한다. C9 픽스처가 실패한다 |
  | M9 | `cozy-cli` 뼈대, `describe`, `check`, `recipes` | reference-app의 `describe` 결과가 기대 파일과 같다. `check`가 R·C8·C9 위반 픽스처를 모두 잡는다 |
  | M10 | 1단계 레시피 | 레시피마다 픽스처가 "C9만" 또는 "통과"다. 레시피를 이어 붙여 reference-app과 같은 구조를 만든 앱이 통과한다 |
  | M11 | `AGENTS.md`, 위반 → 명령 연결, 번들 반입 | 번들로 설치한 PC에서 네트워크 없이 `cozy add`가 동작한다 |

- **위험**:
  - 생성 템플릿과 reference-app·아키타입 `home`이 같은 코드를 두 번 가진다. 레시피로 reference-app 구조를 다시 만드는 픽스처(M10)로 어긋남을 잡는다.
  - 빈칸 표시만 지우고 채우지 않는 경우는 C9가 잡지 못한다. 테스트가 잡아야 한다.
  - Windows 진입점(`cozy.cmd`, `cozy.ps1`)은 사용자가 Windows에서 확인한다([windows-verification.md](../conventions/windows-verification.md)에 항목을 더한다).
  - 평가는 사용자의 내부 시스템에서 하므로, 그 결과에 따라 레시피의 단위가 바뀔 수 있다. 레시피 이름과 인자는 1.x 동안 호환을 지킨다. 그러니 바뀐다면 1.x 릴리스 전에 반영해야 한다.

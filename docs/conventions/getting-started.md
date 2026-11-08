# 새 앱 만들기 (cozy-archetype)

아키타입으로 Cozy 앱을 만든다(PRD §6.6, §10). 인터넷이 없는 PC에서는 오프라인 번들의 `cozy new` 명령을 쓴다([distribution/offline-bundle/README.md](../../distribution/offline-bundle/README.md)).

```bash
mvn org.apache.maven.plugins:maven-archetype-plugin:3.4.1:generate -DarchetypeCatalog=local \
  -DarchetypeGroupId=io.salvia95.cozy -DarchetypeArtifactId=cozy-archetype -DarchetypeVersion=1.1.0-SNAPSHOT \
  -DgroupId=com.example -DartifactId=voc-assistant -Dpackage=com.example.voc \
  -DappName="VOC 도우미" -Dpersistence=true -Devents=false
cd voc-assistant
mvn verify               # 검사 전부
mvn -Pdist package       # 배포 패키지 (docs/conventions/distribution.md)
```

`-DinteractiveMode=false`를 빼면 값을 하나씩 물어본다.

## 옵션

| 속성 | 기본값 | 설명 |
| --- | --- | --- |
| `groupId`, `artifactId`, `version`, `package` | (필수), (필수), `1.0-SNAPSHOT`, groupId | Maven 좌표와 Java 패키지 |
| `appId` | artifactId | `cozy.app.id`. 영문 소문자·숫자와 단어 사이 하이픈. 데이터 폴더와 실행 파일 이름이 된다 |
| `appName` | appId | 화면 표시명. 한글과 공백을 쓸 수 있다. `" \ < > & $ #`는 쓸 수 없다 |
| `persistence` | `true` | SQLite + MyBatis + Flyway(cozy-starter-sqlite) |
| `events` | `false` | 모듈 사이 이벤트(cozy-starter-events). `persistence=true`가 필요하다 |

생성이 실패하는 경우는 다음과 같다. 실패하면 만들던 폴더를 지우고 메시지를 남긴다.

- `events=true`, `persistence=false`
- `appId` 형식이 틀림
- `appName`이 비었거나 쓸 수 없는 문자가 있음
- `persistence`·`events` 값이 true/false가 아님

## 생성되는 앱

```
voc-assistant/
├─ pom.xml                      # parent: cozy-parent, 고른 스타터만
├─ AGENTS.md                    # 코딩 에이전트용 규칙: 일하는 순서, cozy 명령 목록, 파일 위치
├─ .run/                        # IntelliJ 실행 설정: dev 실행, 아키텍처 테스트, mvn -o verify
├─ src/main/java/{패키지}/
│  ├─ App.java                  # CozyApplication.run(App.class, args)
│  └─ home/
│     ├─ package-info.java      # @ApplicationModule(allowedDependencies = {})
│     ├─ api/HomeController.java      # 목록(pageOrFragment) + 등록(@Valid, PRG). HomeFacade·HomeFinder를 부른다
│     ├─ api/dto/               # 화면 DTO(record), 폼(class)
│     ├─ spi/                   # 모듈 사이 계약: HomeFacade(쓰기), HomeFinder(읽기), dto/, (events) event/NoteAdded
│     └─ internal/
│        ├─ app/command/        # HomeFacadeImpl, (events) NoteAddedListener
│        ├─ app/query/          # HomeFinderImpl
│        ├─ domain/             # Note, NoteActivity
│        ├─ infra/              # NoteStore + 메모리 또는 SQLite 구현, (persistence) NoteMapper·NoteRow
│        └─ config/             # HomeConfiguration: 사이드 내비 항목
├─ src/main/resources/
│  ├─ application.yml, application-dev.yml
│  ├─ templates/home/api/index.html        # 화면: list-with-filter 패턴 + 등록 폼
│  ├─ templates/home/internal/activity.html # 조각: 최근 활동 카드 (화면이 조합한다)
│  ├─ templates/fragments/      # 앱 공용 fragment (비어 있음)
│  ├─ cozy/fragments.yml        # 앱 공용 fragment 매니페스트 (비어 있음)
│  ├─ specs/home-index.yml      # 화면 스펙: default, empty, invalid
│  ├─ mapper/home/              # (persistence) MyBatis XML
│  └─ db/migration/             # (persistence) Flyway
└─ src/test/
   ├─ java/{패키지}/ArchitectureTest.java, RenderingTest.java   # 검사 C1~C8
   ├─ java/{패키지}/home/HomeFlowTest.java                      # 등록·검증 오류·htmx 흐름
   └─ resources/events/NoteAdded/v1.json                        # (events) 이벤트 샘플
```

옵션별로 home이 달라지는 부분은 다음과 같다.

| 옵션 | 저장소 | 최근 활동 기록 |
| --- | --- | --- |
| 없음 | `MemoryNoteStore`. 앱을 다시 시작하면 사라진다 | `HomeFacadeImpl`이 직접 남긴다 |
| persistence | `SqliteNoteStore` + `NoteMapper.xml` + 마이그레이션 | `HomeFacadeImpl`이 직접 남긴다 |
| persistence + events | 위와 같다 | `HomeFacadeImpl`은 `NoteAdded`를 발행하고, `NoteAddedListener`가 비동기로 남긴다 |

모듈 구조(api · spi · internal)와 레이어 방향은 [structure.md](structure.md)에 있다.

## 개발

- **dev 실행**: IntelliJ에서 "dev 실행"(`-Dspring.profiles.active=dev`)을 실행한다. 브라우저가 열리고 패턴 카탈로그 `/cozy/dev/catalog`가 켜진다.
- **새 모듈**: `cozy add module`(빈 모듈) 또는 `cozy add crud`(목록·만들기·상세·수정·삭제 한 벌)로 만든다. 화면·이벤트·리스너·마이그레이션·공개 fragment도 `cozy add`, 모듈 의존은 `cozy link`로 더한다. 명령 목록은 `cozy recipes`와 AGENTS.md에, 형식은 [cli.md](cli.md)에 있다. 명령이 남긴 빈칸(`cozy:slot`)을 채우고 `cozy check` → `mvn -o verify` 순서로 확인한다.
- **검사**: 작업을 마치면 `mvn -o verify`(IntelliJ "mvn -o verify")를 돌린다. 실패 메시지의 ID(`[R3]`, `[C5]` 등)로 [rules.md](rules.md)와 [testing.md](testing.md)를 찾는다.

## 아키타입 검증

cozy-archetype 빌드가 다음을 한다.

- **옵션 3조합**: `archetype:integration-test`가 없음 / persistence / persistence+events 앱을 생성하고 `verify -Pdist`까지 돌린다.
  - 생성된 앱의 테스트(검사 C1~C8, 흐름 테스트)가 모두 돈다.
  - app-image가 만들어진다.
  - `verify.groovy`가 옵션별 파일과 의존성을 확인한다.
- **잘못된 옵션**: `src/it`(invoker)가 잘못된 옵션(events만 켬, 잘못된 appId, 특수 문자가 든 appName)에서 생성이 실패하고 폴더가 남지 않는지 확인한다.

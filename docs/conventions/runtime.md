# 런타임 (cozy-starter-runtime)

모든 Cozy 앱의 실행 기반이다(PRD §9.1~9.5). 맡는 일은 다음과 같다.

- 앱 식별과 데이터 경로
- 단일 인스턴스와 종료(트레이, 화면 메뉴)
- 127.0.0.1 바인딩, Host 필터, CSRF, 보안 헤더
- 로그와 진단 내보내기
- 자체 업데이트

패키징과 업데이트는 [distribution.md](distribution.md)에 따로 적는다.

## 진입점

```java
@SpringBootApplication
public class App {
    public static void main(String[] args) {
        CozyApplication.run(App.class, args);
    }
}
```

`CozyApplication.run`은 일반 `SpringApplication.run`에 데스크톱 동작을 더한다.

| 단계 | 동작 |
| --- | --- |
| 환경 준비 직후, 컨텍스트 생성 전 | `{dataDir}/app.lock`을 OS 파일 잠금으로 잡는다. |
| 잠금 실패 | `{dataDir}/app.port`를 읽어(최대 15초 대기) 기존 인스턴스 주소를 브라우저로 연다. stderr에 한 줄을 남기고 `null`을 돌려준다. 종료 코드는 0이다. 실행 중인 앱의 로그 파일은 건드리지 않는다. |
| 웹 서버 기동 | 실제 포트를 `{dataDir}/app.port`에 쓴다. 포트 번호 한 줄이고, 임시 파일에 쓴 뒤 원자적으로 옮긴다. |
| 기동 완료 | 업데이트 준비 신호 `{dataDir}/update/ready`에 앱 버전을 쓴다. `http://127.0.0.1:{port}/`를 브라우저로 연다. `Desktop.browse`를 먼저 쓰고, 안 되면 OS 기본 URL 처리기를 쓴다(Windows는 `rundll32 url.dll,FileProtocolHandler`). 트레이 아이콘을 올린다. |
| 훈련 실행 (`cozy.runtime.training=true`) | 위 대신 `cozy.runtime.training-paths`를 한 번씩 요청하고 종료한다. 하나라도 2xx가 아니면 종료 코드 1이다. `-Pdist`의 AOT 캐시 훈련이 쓴다. |
| 종료 | 트레이 아이콘을 내리고, 포트 파일을 지우고, 잠금을 푼다. 비정상 종료로 남은 포트 파일은 다음 인스턴스가 덮어쓴다. 화면 메뉴·트레이·업데이트로 종료하면 JVM까지 끝낸다. |

`@SpringBootTest`처럼 `CozyApplication.run`을 거치지 않는 컨텍스트에는 이런 데스크톱 동작이 없다. 대상은 잠금, 포트 파일, 브라우저, 트레이, 준비 신호, JVM 종료다. 아래의 자동 설정은 그대로 적용된다.

## 종료

| 방법 | 동작 |
| --- | --- |
| 트레이 아이콘 (Windows) | 메뉴 "열기"는 브라우저로 앱을 연다(아이콘 더블클릭도 같다). "종료"는 앱을 끝낸다. 아이콘은 앱 이름의 첫 글자다. |
| 화면 메뉴 | app-shell 사이드 내비 아래 "종료" 버튼. `POST /cozy/shutdown`(CSRF 필요)이고, 확인을 받은 뒤 "앱을 종료했습니다" 페이지를 보이고 끝낸다. |

- **트레이를 쓸 수 없을 때**: headless이거나, OS가 트레이를 지원하지 않거나, 디스플레이 서버에 연결할 수 없으면 아이콘 없이 돈다. 이때는 화면 메뉴로 종료한다. 디스플레이 없는 경우는 `SingleInstanceProcessTest`가 Linux에서 확인한다.
- **종료 순서**: 응답을 보낸 뒤 0.3초 기다리고, 컨텍스트를 닫고(`SpringApplication.exit`), JVM을 끝낸다. JVM까지 끝내는 이유는 트레이(AWT) 스레드가 남아 프로세스가 살아 있으면 업데이트 스크립트가 앱 폴더를 바꿀 수 없기 때문이다. `cozy.runtime.exit-jvm`은 `CozyApplication.run`이 켜는 내부 속성이고, 테스트에서는 꺼져 있다.

## 설정 속성

| 속성 | 기본값 | 설명 |
| --- | --- | --- |
| `cozy.app.id` | (필수) | 영문 소문자·숫자와 단어 사이 하이픈(`voc-assistant`). 데이터 폴더 이름이 된다. 없거나 형식이 틀리면 기동이 실패하고, 메시지에 고치는 방법이 나온다. |
| `cozy.app.name` | `cozy.app.id` | 화면 표시명. 한글을 쓸 수 있다. |
| `cozy.data-dir` | OS 기본 위치 | 데이터 루트 재정의. 테스트에서는 `target/` 아래로 지정한다. |
| `cozy.runtime.open-browser` | `true` | 기동 후 브라우저를 열고, 진단 파일을 만들면 그 폴더를 연다. 테스트에서는 `false`로 둔다. |
| `cozy.runtime.tray` | `true` | 시스템 트레이 아이콘. 트레이가 없는 환경에서는 저절로 꺼진다. |
| `cozy.runtime.training` | `false` | AOT 캐시 훈련 실행. `-Pdist`가 켠다. |
| `cozy.runtime.training-paths` | `/`, `/cozy/dev/catalog` | 훈련 실행에서 요청할 경로 |
| `cozy.update.*` | | 자체 업데이트. [distribution.md](distribution.md) |

Cozy가 정하는 Boot 속성은 다음과 같다.

| 속성 | 값 | 바꿀 수 있나 |
| --- | --- | --- |
| `server.address` | `127.0.0.1` | 없음. 최우선 순위로 강제하고, 웹 서버 팩토리에서 한 번 더 고정한다. |
| `server.port` | `0` (빈 포트) | 있음. 개발 중 고정 포트가 필요하면 바꾼다. |
| `logging.file.name` | `{dataDir}/logs/app.log` | 있음 |
| `logging.logback.rollingpolicy.max-file-size` / `max-history` | `10MB` / `14`(일) | 있음 |
| `spring.application.name` | `cozy.app.id` | 있음 |

## 데이터 경로

| OS | 루트 |
| --- | --- |
| Windows | `%LOCALAPPDATA%\{appId}` (환경 변수가 없으면 `%USERPROFILE%\AppData\Local\{appId}`) |
| 그 외 (개발용) | `~/.local/share/{appId}` |

하위 폴더 `data/`, `logs/`, `config/`, `backup/`, `update/`, `diagnostics/`는 기동할 때 만든다. 경로는 `CozyDataDirectory` 빈으로 주입받는다.

```java
Path db = dataDirectory.data().resolve("app.db");
```

## 외부 설정

`{dataDir}/config/application.yml`이 있으면 읽는다. 연동 주소처럼 환경마다 달라지는 값을 여기에 둔다.

- 우선순위: 패키지 안의 `application*.yml`보다 높고, 시스템 속성·환경 변수·명령줄 인수보다 낮다.
- 이 파일로 `cozy.app.id`, `cozy.data-dir`, `server.address`는 바꿀 수 없다. 데이터 위치는 이 파일을 찾기 전에 정해지기 때문이다.

## 비밀 값

```java
String token = secretStore.require("voc.api.token");
```

- 기본 구현은 `{dataDir}/config/secrets.properties`(UTF-8)를 읽는다. 읽을 때마다 파일을 다시 읽는다.
- `CozySecretStore` 빈을 직접 등록하면 기본 구현이 물러난다. OS 암호화 저장소로 바꿀지는 PRD §13의 미결 항목이다.
- 값은 로그와 진단 파일에 쓰지 않는다. 오류 메시지에는 키 이름과 파일 경로만 나온다.

## 보안 최소선

- **Host 필터**: Host 헤더가 `127.0.0.1:{port}` 또는 `localhost:{port}`가 아니면 403이다. `{port}`는 요청을 받은 커넥터의 실제 포트이고, DNS 리바인딩을 막기 위한 장치다. MockMvc는 기본으로 `localhost:80`을 쓰므로 그대로 통과한다.
- **Spring Security**: 모든 요청을 허용한다. CSRF는 켜져 있으므로 POST, PUT, DELETE에는 토큰이 필요하다. 테스트에서는 `.with(csrf())`를 쓴다. 로그인이 없으므로 Boot의 임시 사용자는 만들지 않는다.
- **보안 헤더**:
  - `X-Frame-Options: DENY`
  - `Content-Security-Policy`:
    `default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; font-src 'self'; connect-src 'self'; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'`
  - 인라인 스크립트와 인라인 style은 막힌다. Tabler CSS가 쓰는 `data:` 이미지만 허용한다.

## 로그와 진단

- **로그 파일**: `{dataDir}/logs/app.log`다. 10MB마다 나누고 14일 동안 보관한다.
- **진단 내보내기**: app-shell 사이드 내비 아래 "진단 내보내기" 버튼이다(`POST /cozy/diagnostics`, CSRF 필요).
  - `{dataDir}/diagnostics/diagnostics-{appId}-{yyyyMMdd-HHmmss}.zip`을 만들고 그 폴더를 연다.
  - 그다음 보던 화면으로 돌아가 경로를 알림으로 보인다.

| zip 안의 파일 | 내용 |
| --- | --- |
| `summary.txt` | 앱·Cozy 버전, Java, OS, 메모리, JVM 인수, 데이터·앱 폴더, 업데이트 상태 |
| `config.txt` | 앱 설정 이름, 값, 출처. 시스템 환경 변수와 시스템 속성은 넣지 않는다 |
| `logs/` | `{dataDir}/logs`의 최근 파일, 최대 50MB |
| `sqlite.txt` | (cozy-starter-sqlite) DB 파일, 실제 PRAGMA, Flyway 이력(`cozy_schema_history`, 앱 이력) |
| `events.txt` | (cozy-starter-events) 미완료 이벤트 개수, 상태별 개수, 오래된 미완료 기록. 이벤트 내용은 넣지 않는다 |

비밀 값은 진단 파일에 넣지 않는다.

- **비밀로 보이는 키**: 키 이름에 password, secret, token, credential, private-key, api-key, access-key, auth가 들어 있으면 값을 `******`로 가린다.
- **주소 안의 사용자 정보**: `https://user:pass@host`는 `https://******@host`로 가린다.
- **secrets.properties**: 읽지도 넣지도 않는다.

앱도 진단 파일에 내용을 더할 수 있다. `CozyDiagnosticsContributor` 빈마다 텍스트 파일 하나가 생긴다.

```java
@Bean
CozyDiagnosticsContributor vocDiagnostics(VocQueue queue) {
    return CozyDiagnosticsContributor.of("voc-queue.txt", () -> "대기 작업: " + queue.size());
}
```

- **파일 이름**: 영문 소문자·숫자·하이픈과 확장자다. 규칙과 다르면 건너뛴다.
- **내용을 만들지 못할 때**: 예외가 나면 그 파일에는 오류 메시지만 남는다. 진단 파일은 그대로 만들어진다.
- **비밀 값**: 비밀 값과 개인 정보를 넣지 않는다.

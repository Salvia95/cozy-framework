# 워크벤치 (cozy-workbench)

앱을 dev로 실행하면 `/cozy/dev/workbench`에서 화면의 구조, 상태별 미리보기, 소스를 본다. 결정은 [ADR-0036](../adr/0036-workbench.md)에 있다. 워크벤치는 dev 전용 도구이고 배포 패키지에는 들어가지 않는다.

- **지금 되는 것**: 화면 목록, 화면의 구조(패턴 → 자리 → fragment, 줄 번호), 상태 전환, 미리보기 폭 전환, 템플릿과 스펙 소스 보기
- **아직 없는 것**: 요소를 눌러 소스 위치 찾기, `cozy check` 결과, 명령 폼, fixture 편집 (ADR-0036 §6의 뒤 단계)

## 넣기

- 새 앱: 아키타입이 `pom.xml`에 `cozy-workbench`를 넣는다.
- 이미 있는 앱: `cozy enable workbench`. `cozy-ui` 블록 바로 뒤에 넣는다. cozy-parent가 1.2.0보다 낮으면 실패한다.

```xml
<dependency>
  <groupId>io.salvia95.cozy</groupId>
  <artifactId>cozy-workbench</artifactId>
</dependency>
```

- 앱 코드는 이 모듈의 타입을 쓰지 않는다. 자동 설정만 쓴다.
- **배포 패키지**: `cozy-parent`가 실행 jar에서 `cozy-workbench`, `cozy-cli`, `cozy-rules`를 뺀다. 제3자 라이선스 목록에도 없다.

## 켜지는 조건

| 속성 | 기본값 | 뜻 |
| --- | --- | --- |
| `cozy.dev.workbench.enabled` | dev 프로필이면 `true`, 아니면 `false` | `/cozy/dev/workbench` 등록 |

- 패턴 카탈로그(`cozy.dev.catalog.enabled`)가 켜져 있어야 한다. 매니페스트와 화면 미리보기(`/cozy/dev/catalog/screen`)를 카탈로그가 준다.
- **앱 프로젝트 폴더에서 실행해야 구조와 소스가 보인다.** 실행한 폴더에서 위로 올라가며 `pom.xml`과 `src/main/java`가 있는 폴더를 찾는다. 못 찾으면(jar나 app-image로 실행) 미리보기만 보이고 화면이 이유를 알린다.
- dev 실행에는 JDK가 필요하다(cozy-cli가 javac 파서를 쓴다).

## 주소

고른 것은 모두 주소의 파라미터에 있다. 주소를 그대로 주고받을 수 있다.

| 파라미터 | 값 | 기본 |
| --- | --- | --- |
| `spec` | 화면 스펙 이름 (`memo-list`) | 첫 화면 |
| `state` | 스펙의 상태 이름 | 스펙의 첫 상태 |
| `w` | 미리보기 폭: `full`, `1024`, `768`, `390` | `full` |
| `view` | `preview`, `template`, `spec` | `preview` |

- 없는 `spec`이나 `state`는 404, 목록에 없는 `w`나 `view`는 400이다.
- 소스 보기(`view=template`, `view=spec`)의 줄마다 `id="L{줄}"`이 있다.

## 구조 트리

- 원본은 `cozy describe --json`의 `screens[].outline`이다([cli.md](cli.md)). 워크벤치는 같은 프로세스에서 cozy 명령을 부르고 따로 템플릿을 읽지 않는다.
- **삽입**(`th:replace`, `th:insert`): 매니페스트에 있는 프레임워크 fragment는 이름과 묶음(패턴, 컴포넌트, 레이아웃)으로, 그 밖의 것은 `템플릿 :: 이름`으로 보인다.
- **자리**: 삽입이 `~{::#id}`로 넘기는 요소다. 그 삽입의 파라미터 이름(`cozyFilter` 등)으로 보인다.
- **fragment 선언**(`th:fragment`)은 이름으로 보인다.
- 아이콘은 구조가 아니므로 뺀다. 줄마다 `templates/{템플릿}.html:{줄}`이 붙는다.

## 화면 규칙

워크벤치 자신의 화면도 Tabler 구조와 [design.md](design.md)를 따른다. 인라인 스크립트와 스타일이 없고, 보정은 `cozy.css`의 `cozy-workbench-*`에 있다.

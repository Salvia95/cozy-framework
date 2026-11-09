# ADR-0034 앱의 에이전트 문서: 가벼운 AGENTS.md와 cozy-helper 스킬

- 상태: 승인 (2026-11-10, 사용자 지시)
- 출처: 사용자 지시 "최상위 AGENTS.md는 이 프로젝트가 Cozy로 작성되었다는 것과 간략한 아키텍처만, Cozy 명령은 Skill로. 기본 컨텍스트를 가볍게. Skill은 사용처와 활용법만, 명령마다 references 아래 PLAYBOOK.md로 세부 사용법", 위치와 이름 결정 "`.agents/skills/`, 스킬 이름 cozy-helper, 명령마다 폴더 + PLAYBOOK.md", 작성 규칙 "nklcbdt의 skill과 작성 룰을 따라 영문으로, lean하게. 각 섹션은 독립적이고 불릿은 완결적으로"
- 바꾸는 결정: [ADR-0032](0032-agent-cli.md) §7 "앱의 AGENTS.md가 작업 순서와 명령 목록을 싣는다"

## 배경

- M11의 앱 `AGENTS.md`는 160줄이 넘는다. 작업 순서, 명령 목록, 인자 형식, 빈칸, 파일 위치, 화면 규칙, 금지, 검증이 모두 들어 있다. Pi 같은 경량 에이전트는 `AGENTS.md`를 시작할 때마다 통째로 읽으므로, 작은 모델의 기본 컨텍스트가 무거워진다.
- Pi는 Agent Skills 표준을 따른다. 프로젝트의 `.pi/skills/`와 `.agents/skills/`에서 `SKILL.md`를 찾는다. 시작할 때는 스킬의 `name`과 `description`만 시스템 프롬프트에 넣고, 본문과 `references/`는 필요할 때 읽는다(pi-coding-agent docs/skills.md).

## 결정

### 1. 세 층으로 나눈다

| 층 | 파일 | 언제 읽히나 | 담는 것 |
| --- | --- | --- | --- |
| 기본 컨텍스트 | `AGENTS.md` | 늘 | 이 앱이 Cozy로 만들어졌다는 것, 간략한 아키텍처, 지켜야 할 약속 몇 줄, `cozy-helper` 스킬을 쓰라는 안내 |
| 스킬 | `.agents/skills/cozy-helper/SKILL.md` | 구조를 바꾸거나 검사를 고칠 때 | 언제 쓰는지, 작업 순서, 하려는 일에서 플레이북 고르기, 결과 읽기, 빈칸 채우기 |
| 플레이북 | `.agents/skills/cozy-helper/references/<명령>/PLAYBOOK.md` | 그 명령을 쓸 때 | 명령 하나의 형식, 인자, 만드는 것, 빈칸, 예, 실패 메시지와 할 일 |

- **스킬 위치**: `.agents/skills/`. Pi가 읽고, 같은 표준을 따르는 다른 에이전트도 읽는다. 스킬 이름은 `cozy-helper`이고 폴더 이름과 같다(표준의 `name` 규칙).
- **SKILL.md는 명령 목록이 아니다**: "하려는 일 → 플레이북" 표로 고르게 한다. 명령의 형식은 플레이북에만 둔다.
- **플레이북 이름**: 레시피 `crud`는 `add-crud`, `link`는 `link`, `enable events`는 `enable-events`, 조회·검사는 `describe`, `check`다. 명령이 아닌 공통 주제는 `screens`(화면 규칙) 하나만 둔다.
- **SKILL.md 형식**: 절차 스킬이다. `## When to use`, `## When NOT to use`, `## Steps`, `## Playbooks`(하려는 일 → 플레이북 표), `## Reading results`, `## Filling slots`, `## Constraints`.
- **플레이북 형식**: 모두 같은 절을 같은 순서로 둔다. `# 제목` 한 줄과 한 문장 요약, `## When to use`, (필요하면) `## When NOT to use`, `## Syntax`(`cozy recipes`의 usage 그대로), `## Creates`, `## Slots`, `## Example`, `## Failures`(메시지 → 할 일 표), `## Next`. frontmatter는 두지 않는다.

### 2. 작성 규칙

nklcbdt 레포의 작성 규칙(`.claude/rules/common/claude-authoring-conventions.md`)을 따른다.

- **영어로만 쓴다.** 한국어와 섞지 않는다. 다만 cozy 명령이 실제로 내는 메시지(`바뀐 것 없음`, `고치는 명령: …`)와 예시 값(`--label 메모`)은 에이전트가 출력과 맞춰 보도록 코드(백틱, 코드 블록) 안에 그대로 둔다.
- **lean하게 쓴다.** 명령형으로 쓰고, 행동을 바꾸지 않는 줄은 뺀다. 모델이 이미 아는 것(Spring, MyBatis, SQL 기초)과 근거·ADR 번호·이력은 적지 않는다. 예시는 절마다 한두 개다.
- **절은 독립적이다.** 한 절만 읽어도 뜻이 통하게 쓴다. "위에서 말한", "앞 절의" 같은 참조를 쓰지 않고, 필요하면 대상을 다시 이름 짓거나 플레이북 링크를 단다.
- **불릿은 완결적이다.** 불릿과 단계마다 주어와 동사가 있는 문장 하나 이상이고 마침표로 끝난다.
- **응답 언어**: `AGENTS.md`의 마지막 줄은 `Respond in Korean unless the user message is in English.`이다. 늘 읽히는 곳 한 곳에만 둔다.

### 3. 원본과 복사

- 원본은 프레임워크 레포의 `docs/agents/` 하나다: `docs/agents/AGENTS.md`, `docs/agents/skills/cozy-helper/**`.
- 아키타입이 빌드할 때 앱 루트의 `AGENTS.md`와 `.agents/skills/cozy-helper/`로 복사한다(Velocity 필터 없음).

### 4. 테스트로 맞춘다 (`cozy-cli`의 `AgentDocsTest`) (ADR-0032 §7의 "글자까지 같게"를 바꾼다)

- 레시피(`cozy recipes`의 모든 항목)와 `describe`, `check`마다 플레이북이 있고, `## 형식`에 usage가 글자까지 같게 들어 있다.
- 모든 플레이북이 §1의 절을 같은 순서로 가지고 frontmatter가 없다. `SKILL.md`가 모든 플레이북에 링크하고, 상대 링크가 모두 있는 파일을 가리킨다.
- `SKILL.md`의 `name`은 폴더 이름과 같고, `description`은 한 줄 1024자 이하이고, 본문은 100줄 이하다.
- `AGENTS.md`는 40줄 이하이고, 명령 형식(usage)을 싣지 않으며, `cozy-helper`를 가리키고, 응답 언어 줄로 끝난다.
- 세 종류의 파일 모두 코드 밖에 한글이 없고, 불릿과 단계가 마침표로 끝난다(§2).
- 아키타입의 세 조합이 `AGENTS.md`, `SKILL.md`, 플레이북을 앱에 만든다.

## 결과

- 늘 읽히는 것은 `AGENTS.md`(40줄 이하, 지금 29줄)와 스킬 설명 한 줄이다. 명령의 세부는 그 명령을 쓸 때만 읽는다.
- 모델이 스킬을 읽지 않고 작업할 수 있다(Pi 문서도 "늘 읽지는 않는다"고 한다). 그래서 `AGENTS.md`에 "구조는 cozy 명령으로, cozy-helper 스킬을 먼저 읽는다"를 남긴다. 사용자는 `/skill:cozy-helper`로 강제할 수 있다.
- 플레이북은 따로 읽히므로, 필드 형식처럼 여러 명령이 쓰는 규칙은 그 명령의 플레이북마다 한 줄로 다시 쓴다(절의 독립성). 화면 규칙은 `screens` 플레이북 하나에 둔다.

## 위험

- 작은 모델이 플레이북을 찾아 읽는지는 사용자의 내부 평가(ADR-0032 §8)로 확인한다.
- 영어 지시문과 한국어 메시지·출력이 섞인다. 메시지는 코드 안에만 두어 경계를 분명히 했지만, 작은 모델이 한국어 메시지를 정확히 찾는지는 평가로 확인한다.
- 다른 에이전트 도구가 `.agents/skills/`를 읽지 않으면 스킬이 보이지 않는다. 그때도 `AGENTS.md`가 스킬 경로를 알려 준다.

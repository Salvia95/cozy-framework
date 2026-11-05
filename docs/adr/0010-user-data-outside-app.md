# ADR-0010 사용자 데이터는 앱 폴더 밖

- 상태: 승인 (2026-11-05)
- 출처: PRD §3 D10

## 결정

사용자 데이터는 `%LOCALAPPDATA%\{appId}`에 둔다. Windows 외 환경(개발용)에서는 `~/.local/share/{appId}`에 둔다.

## 근거

- 업데이트할 때 앱 폴더를 통째로 교체해도 데이터가 보존된다.

## 결과

- 하위 폴더: data/, logs/, config/, backup/, update/, diagnostics/ (M1).

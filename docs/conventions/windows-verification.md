# Windows 검증 절차

WSL에서 검증할 수 없는 수용 기준을 Windows 11 x64에서 확인하는 절차다. 마일스톤마다 항목을 추가한다. 결과는 아래 기록 표에 남긴다.

## 준비

- JDK 25 (`java -version`으로 확인)
- WSL에서 `mvn verify`로 만든 `samples/reference-app/target/cozy-reference-app-1.0.0-SNAPSHOT.jar`
  - Windows 쪽 폴더(예: `C:\cozy-check\`)로 복사한다.
  - WSL 경로 `\\wsl$\...`에서 바로 실행하면 파일 잠금 동작이 달라질 수 있다.
- `distribution/windows-checks/m1-runtime.ps1`도 같은 폴더로 복사한다.

## M1 런타임 기반

PowerShell에서 실행한다.

```powershell
cd C:\cozy-check
powershell -ExecutionPolicy Bypass -File .\m1-runtime.ps1 -Jar .\cozy-reference-app-1.0.0-SNAPSHOT.jar
```

스크립트가 자동으로 확인하는 것:

1. 데이터 루트 `%LOCALAPPDATA%\cozy-reference`와 하위 폴더 6개가 생긴다.
2. `app.port`가 생기고, 그 포트로 `Host: 127.0.0.1:{port}` 요청이 403이 아니다.
3. `Host: evil.example:{port}` 요청은 403이다.
4. 두 번째 실행이 종료 코드 0으로 끝나고, 출력에 "이미 실행 중인 인스턴스가 있어 그 화면을 엽니다"가 나온다.
5. 로그 파일 `%LOCALAPPDATA%\cozy-reference\logs\app.log`가 생긴다.

직접 눈으로 확인할 것:

- [ ] 첫 실행에서 기본 브라우저가 `http://127.0.0.1:{port}/`를 연다. 아직 화면이 없으므로 오류 페이지가 정상이다.
- [ ] 두 번째 실행에서 브라우저 탭이 하나 더 열린다(같은 주소).

스크립트는 끝날 때 첫 번째 프로세스를 종료한다. 데이터 폴더는 남긴다. 다시 처음부터 확인하려면 `%LOCALAPPDATA%\cozy-reference`를 지운다.

## M2 영속성

M1 스크립트를 다시 실행하면 된다. M2부터 reference-app이 기동할 때 SQLite를 연다.

- [ ] 스크립트가 모두 통과한다. sqlite-jdbc의 Windows 네이티브 DLL이 로드되지 않으면 첫 실행이 기동 중에 끝난다.
- [ ] `%LOCALAPPDATA%\cozy-reference\data\app.db`가 생긴다.
- [ ] `logs\app.log`에 `[cozy] SQLite … {journal_mode=wal, …}` 줄이 있다.
- [ ] 같은 jar를 한 번 더 실행했다가 닫았을 때 `backup\`이 비어 있다. 변경이 없으면 백업하지 않는다.

sqlite-jdbc는 네이티브 DLL을 `%TEMP%`에 풀어서 로드한다. 사내 PC 정책(AppLocker 등)이 `%TEMP%`의 DLL 로드를 막으면 첫 실행이 실패한다. 이때는 `first.out.log`의 오류를 기록해 둔다. M6 패키징에서 DLL을 앱 폴더에 미리 넣는 방식으로 대응한다.

## 기록

| 날짜 | 마일스톤 | 결과 | 비고 |
| --- | --- | --- | --- |
| | M1 | | |
| | M2 | | |

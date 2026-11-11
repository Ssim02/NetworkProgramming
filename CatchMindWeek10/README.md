# 캐치캐치 — 10주차 클라이언트 개발

담당: 임규민 / 2271219 · 작성일: 2026-11-09

## 실행 (Eclipse)
1. ZIP을 압축 해제합니다.
2. File → Import → General → Existing Projects into Workspace를 선택합니다.
3. Select root directory에서 `.project`가 들어 있는 CatchMindWeek10 폴더를 선택하고 Finish를 누릅니다.
4. `src/catchmind/server/TestServer.java`를 Run As → Java Application으로 실행합니다.
5. `src/catchmind/client/ClientMain.java`를 같은 방법으로 실행합니다.
6. 서버 `127.0.0.1`, 닉네임 `규민`을 입력하고 접속합니다.
7. ClientMain을 다시 실행하고 다른 닉네임 `정만`으로 접속합니다.
8. 채팅을 보내 두 창에서 같은 메시지가 보이는지 확인합니다.

이 환경에서는 JDK 21로 컴파일·검증했습니다. 외부 라이브러리는 없습니다.
Build Path 오류가 있으면 프로젝트 Properties → Java Build Path → Libraries에서 설치된 JDK를 선택합니다.
글자가 깨지면 Properties → Resource → Text file encoding을 UTF-8로 설정합니다.
종료할 때 서버 콘솔의 빨간 정지 버튼도 누릅니다. 서버를 두 번 실행하면 포트 중복 오류가 납니다.

## 이번 주 범위
- 파란 게임 화면, 직접 그린 캐릭터 카드 4개, 중앙 그림판, 채팅창
- 그림판 색상 5개, 흰색 지우개, 굵기 선택, 전체 지우기
- TCP 소켓 접속, 서버 승인 확인, 채팅 송수신
- 참가자 명단 동기화, 닉네임 중복·정원 초과 거부, 연결 종료 후 재접속
- 서버는 클라이언트 통합 확인용입니다. 최종 게임 서버는 팀원 담당으로 확장합니다.

그림은 현재 로컬 기능입니다. 그림 공유, 출제, 정답 판정, 타이머, 점수는 구현하지 않았습니다.
서버는 학습용 소규모 테스트 서버이며 느린 수신자가 있을 때의 전송 대기열 처리는 다음 개선 사항입니다.
기존 게임의 이미지나 오픈소스 게임 코드는 사용하지 않았습니다. 기본 Java API로 작성했습니다.

## 파일
- src/catchmind/client/ClientMain.java: 화면·그림판·접속·채팅
- src/catchmind/server/TestServer.java: 최대 4명 테스트 서버
- tests/Week10Check.java: 실제 소켓 통합 테스트 및 Swing 화면 렌더링
- docs/PROTOCOL.md: 이번 버전의 통신 규약
- docs/WIRESHARK.md: 캡처 실행 및 분석 순서
- docs/WEEK10_REPORT.md: 실제 검증 결과와 남은 항목
- evidence/test-results.txt: 자동 통합 테스트 출력
- evidence/client-preview.png: 실제 Swing 컴포넌트 렌더링 이미지(사용자 수동 테스트 캡처 아님)

## 자동 테스트 재실행
먼저 TestServer를 실행합니다. 프로젝트 폴더의 터미널에서 다음을 실행합니다.
```text
javac -encoding UTF-8 -cp bin -d bin tests/Week10Check.java
java -cp bin Week10Check
```
다른 클라이언트가 없는 새 서버에서 수행합니다. 테스트가 잠깐 GUI를 표시하고 닫습니다.
tests는 Eclipse의 기본 소스 폴더에 포함하지 않았습니다.

## 실제 검증 상태
JDK 21 컴파일, 두 TCP 연결의 영문·한글 채팅, 입장 명단, 중복 이름 거부,
4명 정원, 잘못된 명령어·긴 채팅 거부, 이름 재사용, Swing 화면 생성·렌더링을 통과했습니다.
마우스 조작과 두 GUI 창의 채팅은 사용자 수동 확인이 남아 있습니다.
Wireshark 설치와 루프백 인터페이스는 확인했지만 Npcap 관리자 전용 모드로 캡처 권한이 거부되었습니다.
실제 패킷 분석 완료로 기재하지 않으며, 성공한 pcapng 파일을 포함하지 않습니다.

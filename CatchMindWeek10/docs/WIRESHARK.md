# Wireshark로 이번 주 통신 확인하기

현재 PC에 Wireshark가 설치되어 있고 `Adapter for loopback traffic capture`가 확인되었습니다.
자동 캡처 시 Npcap의 관리자 전용 모드 때문에 권한 오류가 발생했습니다.
아래 과정은 사용자가 관리자 권한으로 Wireshark를 실행하여 진행해야 합니다.

1. 시작 메뉴에서 Wireshark를 찾아 우클릭 → 관리자 권한으로 실행합니다.
2. 서버를 실행하되 클라이언트는 아직 접속하지 않습니다.
3. Wireshark에서 `Adapter for loopback traffic capture`를 선택합니다.
4. 캡처 필터를 사용할 경우 `tcp port 5000`을 지정하고 캡처를 시작합니다.
5. 클라이언트 두 개를 서로 다른 이름으로 접속합니다.
6. 첫 창에서 `hello-week10`, 두 번째 창에서 `한글 채팅 확인`을 전송합니다.
7. 한 클라이언트를 닫아 참가자 카드가 비워지는지 확인합니다.
8. 캡처를 중지합니다. 상단 표시 필터에 `tcp.port == 5000`을 입력합니다.
9. 데이터 패킷 우클릭 → Follow → TCP Stream으로 메시지 내용을 확인합니다.
10. File → Save As로 `week10-loopback.pcapng`를 저장합니다.

## 확인할 항목
| 항목 | 기대되는 관찰 |
|---|---|
| 연결 | SYN, SYN/ACK, ACK의 연결 수립 순서 |
| 접속 요청 | /JOIN 및 /JOIN_OK |
| 채팅 | /CHAT hello-week10 및 /CHAT_MSG Kyumin: hello-week10 |
| 두 사용자 | 각 클라이언트별 TCP 연결/스트림이 별도로 존재 |
| 종료 | 정상 종료 시 FIN/ACK 등 종료 흐름 (상황에 따라 RST 가능) |

한 TCP Stream에는 해당 클라이언트와 서버의 통신만 표시됩니다.
다른 클라이언트에게도 전달되었는지는 다른 스트림과 해당 클라이언트 화면을 함께 확인합니다.
본문 앞 2바이트는 writeUTF의 길이 정보이므로 제어문자처럼 보일 수 있습니다.
한글은 표시 인코딩 설정에 따라 달라질 수 있어 영문 테스트 문구도 함께 사용합니다.
캡처 필터 `tcp port 5000`과 표시 필터 `tcp.port == 5000`을 구별하세요.

## 제출용 증거
- 두 클라이언트에 동일한 채팅이 표시된 화면
- 연결 수립 패킷 목록
- Follow TCP Stream의 JOIN/CHAT 내용
- 저장한 실제 pcapng 파일

현재 프로젝트에 이 캡처 결과가 이미 포함되어 있는 것은 아닙니다.
게임 UI와 자동 테스트 결과는 evidence 폴더에 있으며 패킷 수집은 권한 해결 후 수행합니다.

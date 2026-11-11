# 이번 주 통신 규약 v0.1

TCP 포트 5000. Java DataOutputStream.writeUTF / DataInputStream.readUTF를 사용합니다.
메시지 전체 문자열 하나가 writeUTF 한 번에 해당합니다.
writeUTF는 2바이트 길이(문자 수가 아닌 뒤따르는 바이트 수)와 modified UTF-8 본문을 기록합니다.
TCP는 바이트 스트림이므로 메시지와 패킷의 경계는 일치하지 않을 수 있습니다.

| 명령 | 방향 | 예시 | 수신 처리 |
|---|---|---|---|
| /JOIN | 클라이언트→서버 | /JOIN Kyumin | 이름과 정원을 확인 |
| /JOIN_OK | 서버→요청자 | /JOIN_OK Kyumin | 참가 승인, 채팅 활성화 |
| /PLAYERS | 서버→전체 | /PLAYERS Kyumin Jeongman | 카드 순서대로 명단 갱신 |
| /CHAT | 클라이언트→서버 | /CHAT hello-week10 | 검증 후 전체 전송 |
| /CHAT_MSG | 서버→전체 | /CHAT_MSG Kyumin: hello-week10 | 채팅창에 표시 |
| /NOTICE | 서버→전체 | /NOTICE Kyumin 님이 입장했습니다. | 안내 메시지 표시 |
| /ERROR | 서버→요청자 | /ERROR DUPLICATE_NAME | 오류 표시, 입장 실패면 연결 종료 |

닉네임: 한글 음절·영문·숫자·밑줄 1~12자. 공백 금지. 정원 4명.
채팅: 앞뒤 공백 제거 후 1~200자, 줄바꿈 금지. 채팅 본문 내부 공백은 보존합니다.
오류 코드: JOIN_REQUIRED, INVALID_NAME, DUPLICATE_NAME, ROOM_FULL, INVALID_CHAT, UNKNOWN_COMMAND.
클라이언트는 소켓 연결 성공만으로 참가 완료를 표시하지 않고 JOIN_OK를 기다립니다.
그림 관련 명령은 이번 주 서버에서 지원하지 않습니다.
이 규약은 이전 제안서의 게임용 전체 규약 중 기본 접속·채팅을 구체화한 초안이며 팀원과 공유해야 합니다.

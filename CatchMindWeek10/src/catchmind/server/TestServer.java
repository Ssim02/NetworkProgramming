package catchmind.server;

import java.io.*;
import java.net.*;
import java.util.*;

public class TestServer {
	// static final 상수들 정의 -> 이거 필요하면 쓰세요
	private static final int PORT = 5000;
	private static final int MAX_PLAYER = 4;
	private static final int MAX_CHAT_LENGTH = 200;

	// LinkedHashMap -> 들어온 순서대로 기억
	// client 명단은 하나만 쓰기 (final)
	private static final Map<String, Client> clients = new LinkedHashMap<>(); // 현재 들어와 있는 사람 목록 (닉네임 -> 연결) 형태

	public static void main(String[] args) throws IOException {
		try (ServerSocket server = new ServerSocket(PORT)) {
			System.out.println("CatchCatch test server: TCP " + PORT);
			while (true) {
				Socket socket = server.accept();
				new Thread(new Client(socket), "client-" + socket.getRemoteSocketAddress()).start(); // client-/ip:port 형식으로 나오게
			}
		}
	}

	// 명단에 있는 모든 사람들에게 같은 메시지 전송 and server console에도 찍히게
	// synchronized -> mutex 같은거
	// 동기화 -> clients 명단 모든 스레드가 같이 쓰니까, 명단 접근하는 곳은 전부 동기화해서 한 번에 한 스레드만 들어가도록
	// 묶인 곳이 TestServer.class니까, 전부 이걸로 묶어야 의미있음 (broadcast, players, 입장, 퇴장 모두 묶어야함)
	// 접속자가 들어올 때(입장)는 중복, 정원 검사 + 등록을 한번에 해야함 (이거 안하면 두 명이 동시에 검사 통과 -> 같은 이름 2명 생기거나 정원 초과 가능성 존재)
	private static synchronized void broadcast(String message) {
		// 들어올 때 자동으로 lock
		for (Client c : clients.values())
			c.send(message);
		System.out.println(message);
		// unlock
	}

	// keyset()으로 명단에서 닉네임만 꺼내고, 이걸 broadcast함
	// ex) /PLAYERS user1 user2
	// 접속자에 변동이 생길 때마다 호출해서 명단 재전송
	private static synchronized void players() {
		broadcast("/PLAYERS " + String.join(" ", clients.keySet()));
	}

	// 접속자 한명당 적용
	private static class Client implements Runnable {
		final Socket socket;
		DataOutputStream out;
		String name; // 닉네임
		boolean joined; // 검사 통과 여부

		Client(Socket socket) {
			this.socket = socket;
		}

		public void run() {
			try (Socket s = socket;
					DataInputStream in = new DataInputStream(s.getInputStream());
					DataOutputStream writer = new DataOutputStream(s.getOutputStream())) {
				out = writer;
				s.setSoTimeout(5000); // 5초 이상 기다리면 예외처리
				String request = in.readUTF();
				if (!request.startsWith("/JOIN ")) {
					send("/ERROR JOIN_REQUIRED");
					return;
				}
				
				// ---------------- 검사 조건 ------------------ //
				// 1. 한글, 영문, 숫자 중 하나로 12자리까지 (정규식) / 공백 안됨
				name = request.substring(6).trim();
				synchronized (TestServer.class) {
					if (!name.matches("[가-힣a-zA-Z0-9_]{1,12}")) {
						send("/ERROR INVALID_NAME");
						return;
					}
					// 2. 중복된 이름 검사
					if (clients.containsKey(name)) {
						send("/ERROR DUPLICATE_NAME");
						return;
					}
					// 3. 최대 인원 초과 여부 검사
					if (clients.size() >= MAX_PLAYER) {
						send("/ERROR ROOM_FULL");
						return;
					}
					
					// --------- 검사 조건 통과 -> 등록 ---------- //
					clients.put(name, this); // 접속자 목록에 자기 자신 객체 추가
					joined = true; // 접속 완료 표시
					send("/JOIN_OK " + name); // 자신에게만 보냄 -> 승인 완료
					players(); // 전체에게 접속자 명단 broadcast
					broadcast("/NOTICE " + name + " 님이 입장했습니다.");
				}
				
				// ------------- 채팅 구현부 ----------------- //
				s.setSoTimeout(0); // 시간 제한 해제 (0 -> 무한대기)
				while (true) {
					String message = in.readUTF(); // 다음 메시지 올 때까지 대기
					if (message.startsWith("/CHAT ")) {
						String text = message.substring(6).trim();
						if (!text.isEmpty() && text.length() <= MAX_CHAT_LENGTH && 
								!text.contains("\n") && !text.contains("\r")) { // 채팅 조건 4가지
							broadcast("/CHAT_MSG " + name + ": " + text); // 통과 -> 채팅 broadcast
						} else
							send("/ERROR INVALID_CHAT");
					} else // [!] 이 else 전에 다른 채팅 프로토콜 추가
						send("/ERROR UNKNOWN_COMMAND");
				}
			} catch (IOException e) { // ---------- 퇴장 처리 ---------------- //
				System.out.println("Disconnected: " + name);
			} finally {
				synchronized (TestServer.class) {
					if (joined) { // joined로 실제로 접속했던 사람인지 확인하고, 그 경우에만 접속자 명단에서 지움 (동일 닉네임 퇴장 처리 방지)
						clients.remove(name);
						players();
						broadcast("/NOTICE " + name + " 님이 퇴장했습니다.");
					}
				}
			}
		}

		// 전체가 아니라 한 명에게만 보내는 메서드
		// 여긴 static 없는 synchronized -> 각 스레드마다 다른 잠금 (여러 스레드가 동시에 못 쓰도록)
		synchronized void send(String message) {
			try {
				out.writeUTF(message);
				out.flush();
			} catch (IOException e) {
				try {
					socket.close(); // 실패 시 소켓만 닫음 -> 알아서 퇴장처리하게
				} catch (IOException ignored) {
				}
			}
		}
	}
}

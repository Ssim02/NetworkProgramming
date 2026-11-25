package catchmind.server;

import java.io.*;
import java.net.*;
import java.util.*;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

public class ServerMain {
	// static final 상수들 정의 -> 이거 필요하면 쓰세요
	private static final int PORT = 5000;
	private static final int MAX_PLAYER = 4;
	private static final int MAX_CHAT_LENGTH = 200;

	// ---------- 그림판 관련 상수------------------------ //
	// 클라이언트 그림판(Board) 크기가 580 x 340 이므로
	// 좌표는 x: 0 ~ 579, y: 0 ~ 339 범위만 정상으로 본다.
	private static final int BOARD_WIDTH = 580;
	private static final int BOARD_HEIGHT = 340;
	// 굵기: 클라이언트 선택지는 2, 4, 8, 12 -> 서버는 1 ~ 20 범위로 여유 있게 허용
	private static final int MIN_STROKE = 1;
	private static final int MAX_STROKE = 20;
//	// 라운드: 12주차에 라운드가 생기기 전까지는 1로 고정
//	// (메시지 형식에 미리 라운드 칸을 넣어 두면, 12주차에 형식을 다시 바꿀 필요가 없음)
//	private static final int CURRENT_ROUND = 1;
	// -------------------------------------------------------- //

	// LinkedHashMap -> 들어온 순서대로 기억
	// client 명단은 하나만 쓰기 (final)
	private static final Map<String, Client> clients = new LinkedHashMap<>(); // 현재 들어와 있는 사람 목록 (닉네임 -> 연결) 형태

	// [12주차 추가] 서버 종료용
	private static ServerSocket server; // shutdown()에서 닫을 수 있도록 서버 보관
	private static volatile boolean running = true; // false가 되면, 종료 버튼으로 끈 것

	// [12주차 추가] 게임 상태 -> clients처럼 반드시 syncronized(ServerMain.class)안에서만 사용
	private static final String[] WORDS = { "사과", "고양이", "자전거", "우산", "기차" };
	private static final int ROUND_SECONDS = 10; // 테스트용이라, 임시로 10으로 설정 (실제는 60으로 세팅할 것)
	private static final Set<String> ready = new HashSet<>(); // 준비한 사람 닉네임
	private static int currentRound = 0;  // 현재 라운드 번호
	private static String drawer = null;  // 출제자 (null이면 대기 중)
	private static String word = null;    // 제시어
	
	
	public static void main(String[] args) throws IOException {
		try (ServerSocket ss = new ServerSocket(PORT)) {
			server = ss; // 필드에 보관 -> shutdown()이 닫을 수 있게
			SwingUtilities.invokeLater(() -> createControlWindow()); // 제어 창 -> EDT에서 생성
			System.out.println("CatchCatch server: TCP " + PORT);
			
			while (running) {
				Socket socket = ss.accept(); // 접속 올 때까지 대기 (shutdown()이 닫으면 여기서 SocketException)
				new Thread(new Client(socket), "client-" + socket.getRemoteSocketAddress()).start(); // client-/ip:port
																										// 형식으로 나오게
			}
		} catch (BindException e) {
			// 포트를 이미 다른 프로그램이 쓰고 있는 경우
			System.out.println("PORT " + PORT + "가 이미 사용 중입니다. 실행 중인 서버를 먼저 종료하세요. ");
			JOptionPane.showMessageDialog(null, "PORT " + PORT + "에서 이미 서버가 실행 중입니다.\n기존 서버를 종료한 뒤 다시 실행하세요. ",
					"서버 실행 실패", JOptionPane.ERROR_MESSAGE);
			System.exit(1); // 1 -> 비정상 종료
		} catch (SocketException e) {
			// shutdown()이 ServerSocket을 닫으면 accept()가 이 예외를 던짐
			if (running) {
				throw e; // 종료 버튼 때문이 아니면 진짜 오류 -> 그대로 밖으로
			}
			System.out.println("서버를 종료했습니다.");
		}
	}

	// 명단에 있는 모든 사람들에게 같은 메시지 전송 and server console에도 찍히게
	// synchronized -> mutex 같은거
	// 동기화 -> clients 명단 모든 스레드가 같이 쓰니까, 명단 접근하는 곳은 전부 동기화해서 한 번에 한 스레드만 들어가도록
	// 묶인 곳이 TestServer.class니까, 전부 이걸로 묶어야 의미있음 (broadcast, players, 입장, 퇴장 모두
	// 묶어야함)
	// 접속자가 들어올 때(입장)는 중복, 정원 검사 + 등록을 한번에 해야함 (이거 안하면 두 명이 동시에 검사 통과 -> 같은 이름 2명
	// 생기거나 정원 초과 가능성 존재)
	private static synchronized void broadcast(String message) {
		// 들어올 때 자동으로 lock
		for (Client c : clients.values())
			c.send(message);
		System.out.println(message);
		// unlock
	}

	// 보낸 사람 빼고 나머지 전원에게 전송
	// - 그림은 보낸 사람 화면에 이미 그려져 있음(클라이언트가 먼저 그리고 나서 전송)
	// 보낸 사람에게 다시 보내면 같은 선을 두번 그리게 되므로 -> 보낸 사람 제외하고 전송
	// - broadcast()와 똑같이 static synchronized
	// -> lock target이 TestServer.jclass로 같으므로, 이 메서드가 명단을 도는 동안 다른 스레드가 입장/퇴장으로 명단
	// 변경 불간으
	// - broadcast()와 달리 서버 콘솔에 출력하지 않음 (그림 그리기 시 /DRAW가 1초에 수십개씩 오기 때문에, 콘솔에 다
	// 덮여버려서)
	private static synchronized void broadcastExcept(Client sender, String message) {
		for (Client client : clients.values()) {
			if (client != sender) { // 명단 속 연결이 보낸 사람의 연결인지 확인
				client.send(message);
			}
		}
	}

	// keyset()으로 명단에서 닉네임만 꺼내고, 이걸 broadcast함
	// ex) /PLAYERS user1 user2
	// 접속자에 변동이 생길 때마다 호출해서 명단 재전송
	private static synchronized void players() {
		broadcast("/PLAYERS " + String.join(" ", clients.keySet()));
	}

	// [12주차 추가] 라운드 시작
	private static void startRound() {
		Random random = new Random();
		List<String> names = new ArrayList<>(clients.keySet());
		drawer = names.get(random.nextInt(names.size())); // 출제자 랜덤 선정
		word = WORDS[random.nextInt(WORDS.length)]; // 제시어 랜덤 선정
		currentRound++;
		ready.clear(); // 다음 라운드 준비를 위해 준비 상태 초기화
		
		broadcast("/START " + currentRound + " " + drawer + " " + ROUND_SECONDS);
	    clients.get(drawer).send("/WORD " + word); // 제시어는 출제자에게만
	    System.out.println("(제시어: " + word + ")"); // 서버 콘솔 확인용
	    
	    // 시간 종료 타이머
	    // 라운드를 시작할 때 타이머 스레드를 하나 띄움. 60초동안 슬립하고, 슬립 끝난 시점에도 아직 아무도 정답 맞추지 못했다면 라운드를 끝낸다
	    int thisRound = currentRound; // 이 타이머가 담당하는 라운드 번호를 기억
	    Thread timer = new Thread(() -> {
	    	try {
	    		Thread.sleep(ROUND_SECONDS * 1000L); // ROUND_SECONDS 초 대기
	    	} catch (InterruptedException e) {
	    		return;
	    	}
	    	synchronized (ServerMain.class) {
	    		// 아직 라운드 진행 중 && 내가 담당한 라운드 -> 이 경우에만 종료
	    		if (drawer != null && currentRound == thisRound) {
	    			endRound("-");
	    		} 
	    	}
	    }, "timer-round-" + thisRound);
	    timer.setDaemon(true);
	    timer.start();
	}
	
	// [12주차 추가] 라운드 종료
	// winner : 맞힌 사람 닉네임 (시간 초과 시 '-'로 표시)
	private static void endRound(String winner) {
		broadcast("/RESULT " + currentRound + " " + winner + " " + word);
		drawer = null; // drawer = null이면, 대기 상태라는 뜻. 이거 안하면 영원히 다음 라운드 시작 불가 (handleReady()가 drawer != null이면 ALERAY_PLAYING을 보내기 때문
		word = null;
	}
	
	// [12주차 추가] 서버 종료 : 종료 버튼에서 호출
	// 1. running == false -> 일부러 끈 것이라고 표시"
	// 2. ServerSocket 닫기 -> main의 accept()가 깨어나서 종료
	// 3. 모든 클라이언트 소켓 닫기 -> 각 스레드의 readUTF()가 깨어나서 기존 퇴장 처리 실행
	static void shutdown() {
		List<Client> copy;
		synchronized (ServerMain.class) {
			if (!running) {
				return; // 이미 종료 -> 무시
			}
			running = false;
			broadcast("/NOTICE 서버가 종료됩니다.");
			copy = new ArrayList<>(clients.values()); // 명단 복사본
		}
		try {
			if (server != null) {
				server.close();
			}
		} catch (IOException ignored) {
			
		}
		for (Client client : copy) {
			try {
				client.socket.close();
			} catch (IOException ignored) {
				
			}
		}
	}
	
	// [12주차 추가] 서버 제어 창 : 상태 표시 + 종료 버튼
	private static void createControlWindow() {
		JFrame frame = new JFrame("CatchCatch 서버");
		JLabel status = new JLabel("실행 중 - TCP " + PORT);
		JButton stop = new JButton("서버 종료");
		
		// 버튼 클릭 시 수행
		stop.addActionListener(e -> {
			stop.setEnabled(false); // 두번 누르지 못하도록 비활성화
			status.setText("종료 중...");
			shutdown();
			frame.dispose(); // 창 닫기
		});
		
		JPanel panel = new JPanel();
		panel.add(status);
		panel.add(stop);
		frame.add(panel);
		
		frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		
		frame.pack(); // 내용을 크기에 맞게 창 크기 조절
		frame.setLocationRelativeTo(null);
		frame.setVisible(true); // 화면 표시
	}
	
	// [11주차 추가] 좌표가 그림판 안에 있는지 검사
	// x: 0 이상 580 미만, y: 0 이상 340 미만이면 true
	private static boolean inBoard(int x, int y) {
		return x >= 0 && x < BOARD_WIDTH && y >= 0 && y < BOARD_HEIGHT;
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
				synchronized (ServerMain.class) {
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
						if (!text.isEmpty() && text.length() <= MAX_CHAT_LENGTH && !text.contains("\n")
								&& !text.contains("\r")) { // 채팅 조건 4가지
							if (!checkAnswer(text)) { // 정답이 아니다 -> 일반 채팅이라고 판정
								broadcast("/CHAT_MSG " + name + ": " + text); // 통과 -> 채팅 broadcast								
							}
						} else
							send("/ERROR INVALID_CHAT");
					} else if (message.startsWith("/DRAW")) { // 선 하나 그리기
						handleDraw(message);
						// broadcast("/DRAW"); // 테스트용 (DRAW)
					} else if (message.startsWith("/CLEAR")) { // 선 하나 지우기
						handleClear(message);
						// broadcast("/CLEAR " + name + ": " + "님이 전체 삭제 하였습니다.");
					} else if (message.equals("/READY")) {
						handleReady();
					} else // [!] 이 else 전에 다른 채팅 프로토콜 추가
						send("/ERROR UNKNOWN_COMMAND");
				}
			} catch (IOException e) { // ---------- 퇴장 처리 ---------------- //
				System.out.println("Disconnected: " + name);
			} finally {
				synchronized (ServerMain.class) {
					if (joined) { // joined로 실제로 접속했던 사람인지 확인하고, 그 경우에만 접속자 명단에서 지움 (동일 닉네임 퇴장 처리 방지)
						clients.remove(name);
						ready.remove(name); // 준비 목록에서 제거
						players();
						broadcast("/NOTICE " + name + " 님이 퇴장했습니다.");
					}
				}
			}
		}

		// ======================= [11주차 추가] 그림 처리 ======================= //

		// /DRAW 라운드 x1 y1 x2 y2 색 굵기
		// ex) /DRAW 1 10 20 15 25 000000 4
		// -> 1라운드, (10,20)에서 (15,25)까지, 검정색(000000), 굵기 4인 선
		// 마우스를 드래그하면 짧은 선이 계속 이어지는 방식이라, 선 하나 = 메시지 하나
		void handleDraw(String message) {
			// 1. 공백 기준으로 자르기 -> [/DRAW, 라운드, x1, y1, x2, y2, 색, 굵기] 총 8칸이어야 함
			String[] p = message.split(" ");
			if (p.length != 8) {
				send("/ERROR INVALID_DRAW");
				return;
			}
			try {
				// 2. 숫자로 바꾸기 -> 숫자가 아닌 글자가 섞여 있으면 NumberFormatException 발생 -> 아래 catch로
				int round = Integer.parseInt(p[1]);
				int x1 = Integer.parseInt(p[2]);
				int y1 = Integer.parseInt(p[3]);
				int x2 = Integer.parseInt(p[4]);
				int y2 = Integer.parseInt(p[5]);
				String color = p[6];
				int stroke = Integer.parseInt(p[7]);

				// 3. 값 검사 (하나라도 틀리면 오류)
				// - 시작점, 끝점이 둘 다 그림판 안인지
				// - 색이 16진수 6자리(RRGGBB)인지 ex) 000000(검정), FFFFFF(흰색 = 지우개)
				// - 굵기가 허용 범위 안인지
				if (!inBoard(x1, y1) || !inBoard(x2, y2) || !color.matches("[0-9A-Fa-f]{6}")
				        || stroke < MIN_STROKE || stroke > MAX_STROKE) {
				    send("/ERROR INVALID_DRAW");
				    return;
				}
				// 그리기 권한 검사
				if (!canDraw(round)) {
					send("/ERROR NOT_DRAWER");
					return;
				}
				
				// 4. 통과 -> 보낸 사람을 뺀 나머지에게 전달
				// 받은 문자열을 그대로 넘기지 않고, 검사한 값으로 다시 조립해서 보냄
				// -> "+5"처럼 parseInt는 통과하지만 모양이 다른 값도 항상 같은 형식으로 정리됨
				// 색은 대문자로 통일 (ff0000 -> FF0000)
				broadcastExcept(this, "/DRAW " + round + " " + x1 + " " + y1 + " " + x2 + " " + y2 + " "
						+ color.toUpperCase() + " " + stroke);
				
				
			} catch (NumberFormatException e) {
				send("/ERROR INVALID_DRAW");
			}
		}

		// /CLEAR 라운드
		// ex) /CLEAR 1 -> 1라운드 그림판 전체 지우기
		void handleClear(String message) {
			String[] p = message.split(" ");
			if (p.length != 2) {
				send("/ERROR INVALID_CLEAR");
				return;
			}
			try {
				int round = Integer.parseInt(p[1]);
				if (!canDraw(round)) { // 출제자 + 현재 라운드만 지울 수 있음
					send("/ERROR NOT_DRAWER");
					return;
				}
				// 보낸 사람은 버튼을 누를 때 이미 지웠으므로, 나머지에게만 전달
				broadcastExcept(this, "/CLEAR " + round);
				// 누가 지웠는지 전원에게 안내
				broadcast("/NOTICE " + name + " 님이 그림판을 지웠습니다."); // 안내는 NOTICE 프로토콜로
			} catch (NumberFormatException e) {
				send("/ERROR INVALID_CLEAR");
			}
		}
		
		// [12주차 추가] /READY: 모두 준비하면 라운드 시작
		void handleReady() {
			synchronized (ServerMain.class) {
				if (drawer != null) { // 이미 라운드 진행 중인 경우
					send("/ERROR ALREADY_PLAYING");
					return;
				}
				ready.add(name);
				broadcast("/NOTICE " + name + " 님이 준비했습니다.");
				if (clients.size() >= 2 && ready.size() == clients.size()) { // 2명 이상 + 전원 준비 시 시작 가능
					startRound();
				}
			}
		}
		
		// [12주차 추가] 정답 판정 : 정답이라면 라운드 종료 후 true
		boolean checkAnswer(String text) {
		    synchronized (ServerMain.class) {
		        if (drawer != null && name.equals(drawer) && text.contains(word)) { // 출제자가 정답을 말하려 함. contain으로 정답이 포함만 돼도 막아버림
		            send("/ERROR ANSWER_LEAK");
		            return true; // 채팅으로 보내지 않음
		        }
		        if (drawer == null || name.equals(drawer) || !text.equals(word))
		            return false;
		        endRound(name);
		        return true;
		    }
		}
		
		// [12주차 추가] 그리기 권한 : 라운드 진행 중 + 출제자 본인 + 현재 라운드 번호
		boolean canDraw(int round) {
			synchronized (ServerMain.class) {
				return drawer != null && name.equals(drawer) && round == currentRound;
			}
		}
		
		// ====================================================================== //

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

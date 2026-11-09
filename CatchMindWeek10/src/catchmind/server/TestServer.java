package catchmind.server;

import java.io.*;
import java.net.*;
import java.util.*;

/** 이번 주 클라이언트 통합 확인용 서버. 게임 규칙은 다음 단계에서 구현한다. */
public class TestServer {
    private static final Map<String, Client> clients = new LinkedHashMap<>();

    public static void main(String[] args) throws IOException {
        try (ServerSocket server = new ServerSocket(5000)) {
            System.out.println("CatchCatch test server: TCP 5000");
            while (true) new Thread(new Client(server.accept()), "client-handler").start();
        }
    }

    private static synchronized void broadcast(String message) {
        for (Client c : clients.values()) c.send(message);
        System.out.println(message);
    }

    private static synchronized void players() {
        broadcast("/PLAYERS " + String.join(" ", clients.keySet()));
    }

    private static class Client implements Runnable {
        final Socket socket;
        DataOutputStream out;
        String name;
        boolean joined;

        Client(Socket socket) { this.socket = socket; }

        public void run() {
            try (Socket s = socket;
                 DataInputStream in = new DataInputStream(s.getInputStream());
                 DataOutputStream writer = new DataOutputStream(s.getOutputStream())) {
                out = writer;
                s.setSoTimeout(5000);
                String request = in.readUTF();
                if (!request.startsWith("/JOIN ")) { send("/ERROR JOIN_REQUIRED"); return; }
                name = request.substring(6).trim();
                synchronized (TestServer.class) {
                    if (!name.matches("[가-힣a-zA-Z0-9_]{1,12}")) {
                        send("/ERROR INVALID_NAME"); return;
                    }
                    if (clients.containsKey(name)) { send("/ERROR DUPLICATE_NAME"); return; }
                    if (clients.size() >= 4) { send("/ERROR ROOM_FULL"); return; }
                    clients.put(name, this);
                    joined = true;
                    send("/JOIN_OK " + name);
                    players();
                    broadcast("/NOTICE " + name + " 님이 입장했습니다.");
                }
                s.setSoTimeout(0);
                while (true) {
                    String message = in.readUTF();
                    if (message.startsWith("/CHAT ")) {
                        String text = message.substring(6).trim();
                        if (!text.isEmpty() && text.length() <= 200
                                && !text.contains("\n") && !text.contains("\r")) {
                            broadcast("/CHAT_MSG " + name + ": " + text);
                        } else send("/ERROR INVALID_CHAT");
                    } else send("/ERROR UNKNOWN_COMMAND");
                }
            } catch (IOException e) {
                System.out.println("Disconnected: " + name);
            } finally {
                synchronized (TestServer.class) {
                    if (joined) {
                        clients.remove(name);
                        players();
                        broadcast("/NOTICE " + name + " 님이 퇴장했습니다.");
                    }
                }
            }
        }

        synchronized void send(String message) {
            try { out.writeUTF(message); out.flush(); }
            catch (IOException e) { try { socket.close(); } catch (IOException ignored) {} }
        }
    }
}

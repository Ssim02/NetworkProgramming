import java.io.*;
import java.net.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import catchmind.client.ClientMain;

/** 실제 TCP 서버에 연결하여 이번 주 프로토콜을 확인하는 통합 테스트. */
public class Week10Check {
    static class Peer implements AutoCloseable {
        Socket socket;
        DataInputStream in;
        DataOutputStream out;
        Peer(String name) throws Exception {
            socket = new Socket("127.0.0.1", 5000); socket.setSoTimeout(3000);
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());
            send("/JOIN " + name);
        }
        void send(String message) throws Exception { out.writeUTF(message); out.flush(); }
        void expect(String target) throws Exception {
            for (int i=0; i<30; i++) {
                String message=in.readUTF();
                if (message.equals(target)) { System.out.println("PASS " + target); return; }
            }
            throw new AssertionError(target);
        }
        public void close() throws IOException { socket.close(); }
    }
    public static void main(String[] args) throws Exception {
        try (Peer a=new Peer("Kyumin"); Peer b=new Peer("Jeongman")) {
            a.expect("/JOIN_OK Kyumin"); b.expect("/JOIN_OK Jeongman");
            a.expect("/PLAYERS Kyumin Jeongman");
            a.send("/CHAT hello-week10");
            a.expect("/CHAT_MSG Kyumin: hello-week10");
            b.expect("/CHAT_MSG Kyumin: hello-week10");
            b.send("/CHAT 한글 채팅 확인");
            a.expect("/CHAT_MSG Jeongman: 한글 채팅 확인");
            try (Peer duplicate=new Peer("Kyumin")) { duplicate.expect("/ERROR DUPLICATE_NAME"); }
            try (Peer c=new Peer("Player3"); Peer d=new Peer("Player4")) {
                c.expect("/JOIN_OK Player3"); d.expect("/JOIN_OK Player4");
                try (Peer full=new Peer("Player5")) { full.expect("/ERROR ROOM_FULL"); }
            }
            a.send("/BOGUS"); a.expect("/ERROR UNKNOWN_COMMAND");
            a.send("/CHAT " + new String(new char[201]).replace('\0','x'));
            a.expect("/ERROR INVALID_CHAT");
        }
        Thread.sleep(300);
        try (Peer again=new Peer("Kyumin")) { again.expect("/JOIN_OK Kyumin"); }
        SwingUtilities.invokeAndWait(() -> {
            ClientMain frame=new ClientMain();
            frame.setVisible(true);
            BufferedImage image=new BufferedImage(frame.getContentPane().getWidth(),
                    frame.getContentPane().getHeight(),BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g=image.createGraphics(); frame.getContentPane().paint(g); g.dispose();
            try { ImageIO.write(image,"png",new File("evidence/client-preview.png")); }
            catch (IOException e) { throw new RuntimeException(e); }
            frame.dispose();
        });
        System.out.println("PASS Swing construction and rendering");
        System.out.println("ALL CHECKS PASSED");
    }
}

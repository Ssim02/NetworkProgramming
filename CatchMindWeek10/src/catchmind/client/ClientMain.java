package catchmind.client;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import javax.swing.*;
import javax.swing.border.*;

/** 임규민 담당: 기본 화면, 로컬 그림판, TCP 접속과 채팅. */
public class ClientMain extends JFrame {
	private static final Color NAVY = new Color(21, 52, 109);
	private static final Color BLUE = new Color(45, 126, 204);
	private static final Color PALE = new Color(218, 240, 255);
	private final JTextField host = new JTextField("127.0.0.1", 10);
	private final JTextField nickname = new JTextField("규민", 7);
	private final JButton connect = new JButton("접속하기");
	private final JLabel status = new JLabel("접속 전 · 그림판을 먼저 사용해 보세요");
	private final JTextArea chat = new JTextArea();
	private final JTextField input = new JTextField();
	private final JButton send = new JButton("보내기");
	private final JLabel[] names = new JLabel[4];
	private final JLabel[] badges = new JLabel[4];
	private final Board board = new Board();
	private final ExecutorService sender = Executors.newSingleThreadExecutor();
	private volatile Socket socket;
	private volatile DataOutputStream out;

	public ClientMain() {
		super("캐치캐치 · 그림으로 말해요!");
		setDefaultCloseOperation(EXIT_ON_CLOSE);
		JPanel root = new JPanel(new BorderLayout(12, 12));
		root.setBackground(BLUE);
		root.setBorder(new EmptyBorder(16, 16, 12, 16));
		setContentPane(root);

		JPanel top = new JPanel(new BorderLayout());
		top.setOpaque(false);
		JLabel logo = new JLabel("CATCH CATCH");
		logo.setFont(new Font("SansSerif", Font.BOLD, 29));
		logo.setForeground(new Color(255, 234, 107));
		top.add(logo, BorderLayout.WEST);
		JPanel login = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
		login.setOpaque(false);
		login.add(new JLabel("서버"));
		login.add(host);
		login.add(new JLabel("닉네임"));
		login.add(nickname);
		login.add(connect);
		top.add(login, BorderLayout.EAST);
		root.add(top, BorderLayout.NORTH);
		root.add(playerColumn(0), BorderLayout.WEST);
		root.add(playerColumn(2), BorderLayout.EAST);

		JPanel center = new JPanel(new BorderLayout(0, 10));
		center.setOpaque(false);
		JPanel canvasBox = box();
		canvasBox.setLayout(new BorderLayout(0, 10));
		JLabel title = new JLabel("PAINT BOARD  ·  자유 그림판", SwingConstants.CENTER);
		title.setFont(new Font("SansSerif", Font.BOLD, 18));
		title.setForeground(NAVY);
		canvasBox.add(title, BorderLayout.NORTH);
		canvasBox.add(board, BorderLayout.CENTER);
		JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		controls.setOpaque(false);
		Color[] colors = { Color.BLACK, new Color(235, 70, 80), new Color(45, 100, 230), new Color(40, 170, 100),
				new Color(250, 190, 40), Color.WHITE };
		String[] labels = { "검정", "빨강", "파랑", "초록", "노랑", "지우개" };
		for (int i = 0; i < colors.length; i++) {
			Color color = colors[i];
			JButton b = new JButton();
			b.setPreferredSize(new Dimension(30, 26));
			b.setBackground(color);
			b.setOpaque(true);
			b.setBorder(new LineBorder(NAVY));
			b.setToolTipText(labels[i]);
			b.addActionListener(e -> board.color = color);
			controls.add(b);
		}
		controls.add(new JLabel("굵기"));
		JComboBox<Integer> widths = new JComboBox<>(new Integer[] { 2, 4, 8, 12 });
		widths.setSelectedItem(4);
		widths.addActionListener(e -> board.width = (Integer) widths.getSelectedItem());
		controls.add(widths);
		JButton clear = new JButton("전체 지우기");
		clear.addActionListener(e -> board.clear());
		controls.add(clear);
		canvasBox.add(controls, BorderLayout.SOUTH);
		center.add(canvasBox, BorderLayout.CENTER);

		JPanel conversation = new JPanel(new BorderLayout(0, 6));
		conversation.setOpaque(false);
		status.setForeground(Color.WHITE);
		conversation.add(status, BorderLayout.NORTH);
		chat.setEditable(false);
		chat.setLineWrap(true);
		chat.setWrapStyleWord(true);
		chat.setMargin(new Insets(8, 8, 8, 8));
		JScrollPane scroll = new JScrollPane(chat);
		scroll.setPreferredSize(new Dimension(580, 115));
		conversation.add(scroll, BorderLayout.CENTER);
		JPanel entry = new JPanel(new BorderLayout(6, 0));
		entry.setOpaque(false);
		entry.add(input);
		entry.add(send, BorderLayout.EAST);
		conversation.add(entry, BorderLayout.SOUTH);
		center.add(conversation, BorderLayout.SOUTH);
		root.add(center);
		JLabel footer = new JLabel("그림으로 말해요!   |   이번 주: 로컬 그림판 + 온라인 채팅", SwingConstants.CENTER);
		footer.setForeground(Color.WHITE);
		root.add(footer, BorderLayout.SOUTH);
		input.setEnabled(false);
		send.setEnabled(false);
		connect.addActionListener(e -> connect());
		send.addActionListener(e -> sendChat());
		input.addActionListener(e -> sendChat());
		addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				close(socket);
				sender.shutdownNow();
			}
		});
		pack();
		setResizable(false);
		setLocationRelativeTo(null);
	}

	private JPanel box() {
		JPanel panel = new JPanel();
		panel.setBackground(PALE);
		panel.setBorder(new CompoundBorder(new LineBorder(NAVY, 3, true), new EmptyBorder(10, 10, 10, 10)));
		return panel;
	}

	private JPanel playerColumn(int start) {
		JPanel column = new JPanel(new GridLayout(2, 1, 0, 12));
		column.setOpaque(false);
		column.setPreferredSize(new Dimension(135, 500));
		Color[] tones = { new Color(255, 180, 190), new Color(255, 216, 125), new Color(155, 221, 188),
				new Color(190, 180, 240) };
		for (int i = start; i < start + 2; i++) {
			JPanel card = box();
			card.setLayout(new BorderLayout(0, 8));
			names[i] = new JLabel("빈 자리", SwingConstants.CENTER);
			names[i].setFont(new Font("SansSerif", Font.BOLD, 16));
			card.add(names[i], BorderLayout.NORTH);
			final Color tone = tones[i];
			JPanel avatar = new JPanel() {
				protected void paintComponent(Graphics graphics) {
					super.paintComponent(graphics);
					Graphics2D g = (Graphics2D) graphics.create();
					g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
					int x = (getWidth() - 78) / 2, y = (getHeight() - 78) / 2;
					g.setColor(tone);
					g.fillRoundRect(x, y, 78, 78, 28, 28);
					g.setColor(NAVY);
					g.fillOval(x + 20, y + 26, 7, 10);
					g.fillOval(x + 51, y + 26, 7, 10);
					g.setStroke(new BasicStroke(3));
					g.drawArc(x + 29, y + 39, 20, 15, 180, 180);
					g.dispose();
				}
			};
			avatar.setOpaque(false);
			card.add(avatar);
			badges[i] = new JLabel("참가 대기", SwingConstants.CENTER);
			card.add(badges[i], BorderLayout.SOUTH);
			column.add(card);
		}
		return column;
	}

	private void connect() {
		String address = host.getText().trim(), name = nickname.getText().trim();
		if (address.isEmpty() || !name.matches("[가-힣a-zA-Z0-9_]{1,12}")) {
			JOptionPane.showMessageDialog(this, "주소와 닉네임을 확인하세요. 닉네임: 한글·영문·숫자·밑줄 1~12자");
			return;
		}
		connect.setEnabled(false);
		host.setEnabled(false);
		nickname.setEnabled(false);
		status.setText("접속 중...");
		sender.execute(() -> {
			Socket s = new Socket();
			try {
				s.connect(new InetSocketAddress(address, 5000), 3000);
				s.setSoTimeout(5000);
				DataInputStream reader = new DataInputStream(s.getInputStream());
				DataOutputStream writer = new DataOutputStream(s.getOutputStream());
				writer.writeUTF("/JOIN " + name);
				writer.flush();
				String response = reader.readUTF();
				if (!response.startsWith("/JOIN_OK "))
					throw new IOException(response);
				s.setSoTimeout(0);
				socket = s;
				out = writer;
				SwingUtilities.invokeLater(() -> {
					status.setText("접속 완료 · " + name);
					input.setEnabled(true);
					send.setEnabled(true);
					input.requestFocusInWindow();
				});
				Thread receiver = new Thread(() -> receive(s, reader), "server-receiver");
				receiver.setDaemon(true);
				receiver.start();
			} catch (IOException e) {
				close(s);
				SwingUtilities.invokeLater(() -> reset("접속 실패: " + e.getMessage()));
			}
		});
	}

	private void receive(Socket s, DataInputStream reader) {
		try {
			while (true) {
				String message = reader.readUTF();
				SwingUtilities.invokeLater(() -> handle(message));
			}
		} catch (IOException e) {
			// 연결이 닫히면 재접속할 수 있도록 화면을 복구한다.
		} finally {
			close(s);
			out = null;
			socket = null;
			SwingUtilities.invokeLater(() -> reset("연결이 종료되었습니다."));
		}
	}

	private void handle(String message) {
		if (message.startsWith("/PLAYERS ")) {
			String list = message.substring(9).trim();
			String[] users = list.isEmpty() ? new String[0] : list.split(" ");
			for (int i = 0; i < 4; i++) {
				names[i].setText(i < users.length ? users[i] : "빈 자리");
				badges[i].setText(i < users.length ? "접속 중" : "참가 대기");
			}
		} else {
			String text = message.startsWith("/CHAT_MSG ") ? message.substring(10)
					: message.startsWith("/NOTICE ") ? "[안내] " + message.substring(8) : message;
			chat.append(text + "\n");
			chat.setCaretPosition(chat.getDocument().getLength());
		}
	}

	private void reset(String reason) {
		status.setText(reason);
		connect.setEnabled(true);
		host.setEnabled(true);
		nickname.setEnabled(true);
		input.setEnabled(false);
		send.setEnabled(false);
		handle("/PLAYERS ");
	}

	private void sendChat() {
		String text = input.getText().trim();
		DataOutputStream writer = out;
		Socket s = socket;
		if (text.isEmpty() || writer == null)
			return;
		if (text.length() > 200) {
			JOptionPane.showMessageDialog(this, "200자 이내로 입력하세요.");
			return;
		}
		input.setText("");
		sender.execute(() -> {
			try {
				writer.writeUTF("/CHAT " + text);
				writer.flush();
			} catch (IOException e) {
				close(s);
			}
		});
	}

	private static void close(Socket s) {
		if (s != null)
			try {
				s.close();
			} catch (IOException ignored) {
			}
	}

	private static class Board extends JPanel {
		final BufferedImage image = new BufferedImage(580, 340, BufferedImage.TYPE_INT_RGB);
		Color color = Color.BLACK;
		int width = 4;
		Point previous;

		Board() {
			setPreferredSize(new Dimension(580, 340));
			clear();
			setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
			MouseAdapter mouse = new MouseAdapter() {
				public void mousePressed(MouseEvent e) {
					if (SwingUtilities.isLeftMouseButton(e)) {
						previous = e.getPoint();
						line(previous, previous);
					}
				}

				public void mouseDragged(MouseEvent e) {
					if (previous != null) {
						line(previous, e.getPoint());
						previous = e.getPoint();
					}
				}

				public void mouseReleased(MouseEvent e) {
					previous = null;
				}
			};
			addMouseListener(mouse);
			addMouseMotionListener(mouse);
		}

		void line(Point a, Point b) {
			Graphics2D g = image.createGraphics();
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setColor(color);
			g.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			g.drawLine(a.x, a.y, b.x, b.y);
			g.dispose();
			repaint();
		}

		void clear() {
			Graphics2D g = image.createGraphics();
			g.setColor(Color.WHITE);
			g.fillRect(0, 0, 580, 340);
			g.dispose();
			repaint();
		}

		protected void paintComponent(Graphics g) {
			super.paintComponent(g);
			g.drawImage(image, 0, 0, null);
		}
	}

	public static void main(String[] args) {
		SwingUtilities.invokeLater(() -> new ClientMain().setVisible(true));
	}
}

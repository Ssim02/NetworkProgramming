package catchmind.client;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineUnavailableException;
import javax.swing.*;
import javax.swing.border.*;

/** 임규민 담당: 기본 화면, 로컬 그림판, TCP 접속과 채팅. */
public class ClientMain extends JFrame {
    private static final int SERVER_PORT = 5000;
    private int round;
    private int playerCount;
    private boolean readySubmitted;
    private boolean active;
    private long roundDeadline;
    private final Timer roundClock = new Timer(200, e -> updateCountdown());
    private final JButton readyButton = new JButton("준비");
    private final JButton clear = new JButton("전체 지우기");
    private final JCheckBox sound = new JCheckBox("효과음", true);
    private final JLabel game = new JLabel("서버에 접속하세요.");
    private final JLabel promptLabel = new JLabel("제시어");
    private final JLabel prompt = new JLabel();
    private final JTextField guessInput = new JTextField();
    private final JButton guessButton = new JButton("정답 제출");
    private final JLabel guessHint = new JLabel("라운드 대기 중");
    private final Timer answerTimer = new Timer(5000, e ->
            showPrompt("제시어", "다음 라운드를 기다리는 중", false));
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
        JPanel root = new JPanel(new BorderLayout(10, 8));
        root.setBackground(BLUE);
        root.setBorder(new EmptyBorder(10, 12, 8, 12));
        setContentPane(root);

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        JLabel logo = new JLabel("CATCH CATCH");
        logo.setFont(new Font("SansSerif", Font.BOLD, 29));
        logo.setForeground(new Color(255, 234, 107));
        top.add(logo, BorderLayout.WEST);
        JPanel login = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        login.setOpaque(false);
        login.add(new JLabel("서버")); login.add(host);
        login.add(new JLabel("닉네임")); login.add(nickname); login.add(connect);
        login.add(readyButton);
        top.add(login, BorderLayout.EAST);
        root.add(top, BorderLayout.NORTH);
        root.add(playerColumn(0), BorderLayout.WEST);
        root.add(playerColumn(2), BorderLayout.EAST);

        JPanel center = new JPanel(new BorderLayout(0, 6));
        center.setOpaque(false);
        JPanel canvasBox = box();
        canvasBox.setLayout(new BorderLayout(0, 6));
        JPanel promptBar = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 4));
        promptBar.setBackground(new Color(255, 248, 220));
        promptBar.setBorder(BorderFactory.createLineBorder(new Color(239, 209, 132)));
        promptLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        promptLabel.setOpaque(true);
        promptLabel.setBackground(NAVY);
        promptLabel.setForeground(Color.WHITE);
        promptLabel.setBorder(new EmptyBorder(4, 10, 4, 10));
        promptBar.add(promptLabel);
        promptBar.add(prompt);
        answerTimer.setRepeats(false);
        showPrompt("제시어", "라운드를 기다리는 중", false);
        canvasBox.add(promptBar, BorderLayout.NORTH);
        canvasBox.add(board, BorderLayout.CENTER);
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        controls.setOpaque(false);
        Color[] colors = {Color.BLACK, new Color(235,70,80), new Color(45,100,230),
                new Color(40,170,100), new Color(250,190,40), Color.WHITE};
        String[] labels = {"검정", "빨강", "파랑", "초록", "노랑", "지우개"};
        for (int i = 0; i < colors.length; i++) {
            Color color = colors[i];
            JButton b = new JButton();
            b.setPreferredSize(new Dimension(30, 26)); b.setBackground(color);
            b.setOpaque(true); b.setBorder(new LineBorder(NAVY)); b.setToolTipText(labels[i]);
            b.addActionListener(e -> board.color = color); controls.add(b);
        }
        controls.add(new JLabel("굵기"));
        JComboBox<Integer> widths = new JComboBox<>(new Integer[]{2, 4, 8, 12});
        widths.setSelectedItem(4);
        widths.addActionListener(e -> board.width = (Integer) widths.getSelectedItem());
        controls.add(widths);
        board.onDraw = this::sendDraw;
        clear.addActionListener(e -> {
            if (!board.drawingAllowed) return;
            board.clear();
            sendRaw("/CLEAR " + round);
        });
        controls.add(clear);
        canvasBox.add(controls, BorderLayout.SOUTH);
        center.add(canvasBox, BorderLayout.CENTER);

        JPanel conversation = new JPanel(new BorderLayout(0, 6));
        conversation.setOpaque(false); status.setForeground(Color.WHITE);
        conversation.add(status, BorderLayout.NORTH);
        chat.setEditable(false); chat.setLineWrap(true); chat.setWrapStyleWord(true);
        chat.setMargin(new Insets(8, 8, 8, 8));
        JScrollPane scroll = new JScrollPane(chat);
        scroll.setPreferredSize(new Dimension(580, 66));
        conversation.add(scroll, BorderLayout.CENTER);
        JPanel entry = new JPanel(new BorderLayout(6, 0));
        entry.setOpaque(false); entry.add(input); entry.add(send, BorderLayout.EAST);
        entry.setBorder(BorderFactory.createTitledBorder("채팅"));

        JPanel guesses = new JPanel(new BorderLayout(6, 0));
        guesses.setBackground(new Color(255, 248, 220));
        guesses.setBorder(BorderFactory.createTitledBorder("정답 입력"));
        guessHint.setForeground(NAVY);
        JPanel guessActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        guessActions.setOpaque(false);
        guessActions.add(guessHint);
        guessActions.add(guessButton);
        guesses.add(guessInput, BorderLayout.CENTER);
        guesses.add(guessActions, BorderLayout.EAST);
        guessInput.addActionListener(e -> submitGuess());
        guessButton.addActionListener(e -> submitGuess());
        setGuessingAllowed(false, "라운드 대기 중");

        JPanel inputRows = new JPanel(new GridLayout(2, 1, 0, 4));
        inputRows.setOpaque(false);
        inputRows.add(guesses);
        inputRows.add(entry);
        conversation.add(inputRows, BorderLayout.SOUTH);
        center.add(conversation, BorderLayout.SOUTH); root.add(center);
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        footer.setOpaque(false);
        game.setForeground(Color.WHITE);
        sound.setOpaque(false);
        sound.setForeground(Color.WHITE);
        footer.add(sound);
        footer.add(game);
        root.add(footer, BorderLayout.SOUTH);
        readyButton.setEnabled(false);
        clear.setEnabled(false);
        readyButton.addActionListener(e -> {
            readySubmitted = true;
            updateReadyButton();
            sendRaw("/READY");
        });
        input.setEnabled(false); send.setEnabled(false);
        connect.addActionListener(e -> connect());
        send.addActionListener(e -> sendChat()); input.addActionListener(e -> sendChat());
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) { close(socket); sender.shutdownNow(); }
        });
        pack();
        Rectangle usableScreen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        setMinimumSize(new Dimension(Math.min(790, usableScreen.width), Math.min(520, usableScreen.height)));
        setSize(Math.min(getWidth(), usableScreen.width), Math.min(getHeight(), usableScreen.height));
        setLocation(usableScreen.x + (usableScreen.width - getWidth()) / 2,
                usableScreen.y + (usableScreen.height - getHeight()) / 2);
    }

    private JPanel box() {
        JPanel panel = new JPanel(); panel.setBackground(PALE);
        panel.setBorder(new CompoundBorder(new LineBorder(NAVY, 3, true), new EmptyBorder(10,10,10,10)));
        return panel;
    }

    private JPanel playerColumn(int start) {
        JPanel column = new JPanel(new GridLayout(2, 1, 0, 12)); column.setOpaque(false);
        column.setPreferredSize(new Dimension(116, 430));
        Color[] tones = {new Color(255,180,190), new Color(255,216,125),
                new Color(155,221,188), new Color(190,180,240)};
        for (int i = start; i < start + 2; i++) {
            JPanel card = box(); card.setLayout(new BorderLayout(0, 8));
            names[i] = new JLabel("빈 자리", SwingConstants.CENTER);
            names[i].setFont(new Font("SansSerif", Font.BOLD, 16));
            card.add(names[i], BorderLayout.NORTH);
            final Color tone = tones[i];
            JPanel avatar = new JPanel() {
                protected void paintComponent(Graphics graphics) {
                    super.paintComponent(graphics);
                    Graphics2D g = (Graphics2D) graphics.create();
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    int x = (getWidth()-78)/2, y = (getHeight()-78)/2;
                    g.setColor(tone); g.fillRoundRect(x,y,78,78,28,28);
                    g.setColor(NAVY); g.fillOval(x+20,y+26,7,10); g.fillOval(x+51,y+26,7,10);
                    g.setStroke(new BasicStroke(3)); g.drawArc(x+29,y+39,20,15,180,180); g.dispose();
                }
            };
            avatar.setOpaque(false); card.add(avatar);
            badges[i] = new JLabel("참가 대기", SwingConstants.CENTER);
            card.add(badges[i], BorderLayout.SOUTH); column.add(card);
        }
        return column;
    }

    private void connect() {
        String address = host.getText().trim(), name = nickname.getText().trim();
        if (address.isEmpty() || !name.matches("[가-힣a-zA-Z0-9_]{1,12}")) {
            JOptionPane.showMessageDialog(this, "주소와 닉네임을 확인하세요. 닉네임: 한글·영문·숫자·밑줄 1~12자"); return;
        }
        connect.setEnabled(false); host.setEnabled(false); nickname.setEnabled(false);
        status.setText("접속 중...");
        sender.execute(() -> {
            Socket s = new Socket();
            try {
                s.connect(new InetSocketAddress(address, SERVER_PORT), 3000);
                s.setSoTimeout(5000);
                DataInputStream reader = new DataInputStream(s.getInputStream());
                DataOutputStream writer = new DataOutputStream(s.getOutputStream());
                writer.writeUTF("/JOIN " + name); writer.flush();
                String response = reader.readUTF();
                if (!response.startsWith("/JOIN_OK ")) throw new IOException(response);
                s.setSoTimeout(0); socket = s; out = writer;
                SwingUtilities.invokeLater(() -> {
                    status.setText("접속 완료 · " + name); input.setEnabled(true); send.setEnabled(true);
                    input.requestFocusInWindow();
                });
                Thread receiver = new Thread(() -> receive(s, reader), "server-receiver");
                receiver.setDaemon(true); receiver.start();
            } catch (IOException e) {
                close(s); SwingUtilities.invokeLater(() -> reset("접속 실패: " + e.getMessage()));
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
            close(s); out = null; socket = null;
            SwingUtilities.invokeLater(() -> reset("연결이 종료되었습니다."));
        }
    }

    private void handle(String message) {
        try {
            if (handleRound(message)) return;
        } catch (NumberFormatException e) {
            return;
        }
        if (handleDrawing(message)) return;
        if (message.startsWith("/PLAYERS ")) {
            String list = message.substring(9).trim();
            String[] users = list.isEmpty() ? new String[0] : list.split(" ");
            if (!active && users.length < playerCount) {
                readySubmitted = false;
            }
            playerCount = users.length;
            for (int i=0; i<4; i++) {
                names[i].setText(i < users.length ? users[i] : "빈 자리");
                badges[i].setText(i < users.length ? "접속 중" : "참가 대기");
            }
            updateReadyButton();
        } else {
            String text = message.startsWith("/CHAT_MSG ") ? message.substring(10)
                    : message.startsWith("/NOTICE ") ? "[안내] " + message.substring(8) : message;
            chat.append(text + "\n"); chat.setCaretPosition(chat.getDocument().getLength());
        }
    }

    private boolean handleRound(String message) {
        String[] p = message.split(" ", 5);
        switch (p[0]) {
            case "/START" -> {
                if (p.length != 4) return true;
                int id = Integer.parseInt(p[1]);
                int seconds = Integer.parseInt(p[3]);
                if (id <= round || seconds <= 0) return true;
                answerTimer.stop();
                board.hideResult();
                round = id;
                active = true;
                readySubmitted = false;
                updateReadyButton();
                board.clear();
                setDrawingAllowed(nickname.getText().trim().equals(p[2]));
                setGuessingAllowed(!board.drawingAllowed, board.drawingAllowed ? "출제 중" : " ");
                if (!board.drawingAllowed) guessInput.requestFocusInWindow();
                game.setText(round + "라운드 · 출제자 " + p[2]);
                showPrompt("제시어",
                        board.drawingAllowed ? "제시어를 기다리는 중" : "아래 정답 입력칸에 적어 보세요", false);
                roundDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
                updateCountdown();
                roundClock.restart();
                if (sound.isSelected()) SoundPlayer.play(SoundPlayer.START);
            }
            case "/WORD" -> {
                if (message.startsWith("/WORD ") && active && board.drawingAllowed) {
                    showPrompt("제시어", message.substring(6), true);
                }
            }
            case "/RESULT" -> {
                String[] resultParts = message.split(" ", 4);
                if (resultParts.length != 4 || !active || !resultParts[1].equals(Integer.toString(round))) return true;
                endRound();
                String winner = resultParts[2];
                String answer = resultParts[3];
                boolean correct = !winner.equals("-");
                String result = correct ? winner + " 정답!" : "시간 초과";
                status.setText(round + "라운드 종료 · " + result);
                showPrompt("정답", answer, true);
                answerTimer.restart();
                board.showResult(correct ? "정답!" : "시간 초과", answer,
                        correct ? winner + " 님이 맞혔어요!" : "맞힌 참가자가 없어요");
                if (sound.isSelected()) {
                    SoundPlayer.play(correct ? SoundPlayer.CORRECT : SoundPlayer.TIMEOUT);
                }
            }
            case "/ROUND_ABORT" -> {
                if (p.length != 2 || !active || !p[1].equals(Integer.toString(round))) return true;
                endRound();
                status.setText("참가자 퇴장으로 라운드가 중단되었습니다.");
                showPrompt("제시어", "다음 라운드를 기다리는 중", false);
            }
            case "/ERROR" -> {
                if (p.length != 2) return true;
                switch (p[1]) {
                    case "ALREADY_PLAYING" -> {
                        readySubmitted = false;
                        updateReadyButton();
                        status.setText("라운드가 끝난 뒤 준비할 수 있어요.");
                    }
                    case "INVALID_CHAT" -> status.setText("한 줄에 1~200자로 입력하세요.");
                    case "ANSWER_LEAK" -> status.setText("출제자는 채팅으로 정답을 보낼 수 없어요.");
                    case "UNKNOWN_COMMAND" -> {
                        if (!readySubmitted || active) return false;
                        readySubmitted = false;
                        updateReadyButton();
                        status.setText("준비 요청을 받는 팀원 서버인지 확인하세요.");
                    }
                    default -> { return false; }
                }
            }
            default -> { return false; }
        }
        return true;
    }

    private void updateReadyButton() {
        readyButton.setText(readySubmitted ? "준비 완료" : "준비");
        readyButton.setEnabled(out != null && !active && !readySubmitted && playerCount >= 2);
        if (out == null || active) return;
        if (playerCount < 2) game.setText("다른 참가자를 기다리는 중");
        else game.setText(readySubmitted ? "다른 참가자의 준비를 기다리는 중" : "모두 준비하면 자동 시작");
    }

    private void updateCountdown() {
        long nanos = roundDeadline - System.nanoTime();
        long seconds = Math.max(0L, (long) Math.ceil(nanos / 1_000_000_000.0));
        status.setText("남은 시간 " + seconds + "초");
    }

    private void endRound() {
        active = false;
        roundClock.stop();
        setDrawingAllowed(false);
        setGuessingAllowed(false, "라운드 종료");
        updateReadyButton();
    }

    private void showPrompt(String label, String value, boolean highlight) {
        promptLabel.setText(label);
        prompt.setText(value);
        prompt.setFont(new Font("SansSerif", Font.BOLD, highlight ? 30 : 16));
        prompt.setForeground(highlight ? NAVY : new Color(66, 81, 106));
    }

    private void setDrawingAllowed(boolean allowed) {
        board.drawingAllowed = allowed;
        board.previous = null;
        clear.setEnabled(allowed);
    }

    private void setGuessingAllowed(boolean allowed, String hint) {
        guessInput.setText("");
        guessInput.setEnabled(allowed);
        guessButton.setEnabled(allowed);
        guessHint.setText(hint);
    }

    private void submitGuess() {
        if (!active || board.drawingAllowed || out == null) return;
        String answer = guessInput.getText().trim();
        if (answer.isEmpty() || answer.length() > 200) {
            guessHint.setText("1~200자로 입력하세요");
            return;
        }
        guessInput.setText("");
        guessHint.setText("제출했어요");
        sendRaw("/CHAT " + answer);
        guessInput.requestFocusInWindow();
    }

    private boolean handleDrawing(String message) {
        if (!message.startsWith("/DRAW") && !message.startsWith("/CLEAR")) return false;
        String[] parts = message.split(" ");
        boolean draw = parts[0].equals("/DRAW");
        if (!draw && !parts[0].equals("/CLEAR")) return false;
        if (parts.length != (draw ? 8 : 2)) return true;

        try {
            if (!active || Integer.parseInt(parts[1]) != round) return true;
            if (!draw) {
                board.clear();
                return true;
            }
            Point a = new Point(Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
            Point b = new Point(Integer.parseInt(parts[4]), Integer.parseInt(parts[5]));
            int width = Integer.parseInt(parts[7]);
            if (a.equals(Board.clamp(a)) && b.equals(Board.clamp(b))
                    && parts[6].matches("[0-9A-Fa-f]{6}") && width >= 1 && width <= 20) {
                board.line(a, b, new Color(Integer.parseInt(parts[6], 16)), width);
            }
        } catch (NumberFormatException e) {
            // 잘못된 그림 메시지만 건너뛰고 다음 메시지를 처리한다.
        }
        return true;
    }

    private void reset(String reason) {
        answerTimer.stop();
        roundClock.stop();
        board.hideResult();
        setGuessingAllowed(false, "라운드 대기 중");
        round = 0;
        active = false;
        setDrawingAllowed(false);
        readySubmitted = false;
        updateReadyButton();
        game.setText("서버에 접속하세요.");
        showPrompt("제시어", "라운드를 기다리는 중", false);
        board.clear();
        status.setText(reason); connect.setEnabled(true); host.setEnabled(true); nickname.setEnabled(true);
        input.setEnabled(false); send.setEnabled(false); handle("/PLAYERS ");
    }

    private void sendChat() {
        String text = input.getText().trim();
        if (text.isEmpty() || out == null) return;
        if (text.length()>200) { JOptionPane.showMessageDialog(this,"200자 이내로 입력하세요."); return; }
        input.setText("");
        sendRaw("/CHAT " + text);
    }

    private void sendDraw(Point a, Point b, Color color, int width) {
        if (out == null) return;
        String rgb = String.format("%06X", color.getRGB() & 0xFFFFFF);
        sendRaw("/DRAW " + round + " " + a.x + " " + a.y + " "
                + b.x + " " + b.y + " " + rgb + " " + width);
    }

    private void sendRaw(String message) {
        // 같은 접속의 전송 작업을 한 스레드에서 순서대로 처리한다.
        Socket s = socket;
        DataOutputStream writer = out;
        if (s == null || writer == null || s.isClosed()) return;
        sender.execute(() -> {
            if (s.isClosed()) return;
            try { writer.writeUTF(message); writer.flush(); }
            catch (IOException e) { close(s); }
        });
    }

    private static void close(Socket s) {
        if (s != null) try { s.close(); } catch (IOException ignored) {}
    }

    @FunctionalInterface
    private interface DrawListener {
        void draw(Point a, Point b, Color color, int width);
    }

    private static class Board extends JPanel {
        private static final int WIDTH = 580;
        private static final int HEIGHT = 340;
        private static final int RESULT_HOLD_MS = 4100;
        private static final int RESULT_FADE_MS = 900;
        private String resultWord;
        private String resultTitle;
        private String resultWinner;
        private long resultStarted;
        private final Timer resultTimer = new Timer(16, e -> {
            if (resultElapsed() >= RESULT_HOLD_MS + RESULT_FADE_MS) hideResult();
            else repaint();
        });
        final BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        DrawListener onDraw;
        boolean drawingAllowed;
        Color color = Color.BLACK; int width = 4; Point previous;
        Board() {
            setPreferredSize(new Dimension(WIDTH, 290)); clear();
            setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
            MouseAdapter mouse = new MouseAdapter() {
                public void mousePressed(MouseEvent e) {
                    if (!drawingAllowed || !SwingUtilities.isLeftMouseButton(e)) return;
                    previous = imagePoint(e.getPoint());
                    if (new Rectangle(WIDTH, HEIGHT).contains(previous)) line(previous, previous);
                    else previous = null;
                }
                public void mouseDragged(MouseEvent e) {
                    if (previous != null) {
                        Point next = clamp(imagePoint(e.getPoint()));
                        line(previous, next);
                        previous = next;
                    }
                }
                public void mouseReleased(MouseEvent e) { previous=null; }
            };
            addMouseListener(mouse); addMouseMotionListener(mouse);
        }

        private double imageScale() {
            return Math.min(getWidth() / (double) WIDTH, getHeight() / (double) HEIGHT);
        }

        private Point imagePoint(Point point) {
            double scale = imageScale();
            return new Point((int) Math.floor((point.x - (getWidth() - WIDTH * scale) / 2) / scale),
                    (int) Math.floor((point.y - (getHeight() - HEIGHT * scale) / 2) / scale));
        }
        void line(Point a, Point b) {
            Point start = clamp(a);
            Point end = clamp(b);
            line(start, end, color, width);
            if (onDraw != null) onDraw.draw(start, end, color, width);
        }

        static Point clamp(Point p) {
            return new Point(Math.max(0, Math.min(WIDTH - 1, p.x)),
                    Math.max(0, Math.min(HEIGHT - 1, p.y)));
        }

        void line(Point a, Point b, Color color, int width) {
            Graphics2D g=image.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(color); g.setStroke(new BasicStroke(width,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
            g.drawLine(a.x,a.y,b.x,b.y); g.dispose(); repaint();
        }
        void clear() {
            Graphics2D g=image.createGraphics(); g.setColor(Color.WHITE);
            g.fillRect(0,0,580,340); g.dispose(); repaint();
        }
        void showResult(String title, String word, String winner) {
            resultTitle = title;
            resultWord = word;
            resultWinner = winner;
            resultStarted = System.nanoTime();
            resultTimer.restart();
            repaint();
        }

        void hideResult() {
            resultTimer.stop();
            resultWord = null;
            repaint();
        }

        private long resultElapsed() {
            return (System.nanoTime() - resultStarted) / 1_000_000;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            double scale = imageScale();
            if (scale <= 0) return;
            Graphics2D canvas = (Graphics2D) g.create();
            canvas.translate((getWidth() - WIDTH * scale) / 2, (getHeight() - HEIGHT * scale) / 2);
            canvas.scale(scale, scale);
            canvas.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            canvas.drawImage(image, 0, 0, null);
            canvas.dispose();
            if (resultWord == null) return;

            float alpha = 1f - Math.max(0, resultElapsed() - RESULT_HOLD_MS) / (float) RESULT_FADE_MS;
            Graphics2D card = (Graphics2D) g.create();
            card.setComposite(AlphaComposite.SrcOver.derive(Math.max(0f, alpha)));
            card.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            card.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int width = Math.min(350, getWidth());
            int top = (getHeight() - 152) / 2;
            card.setColor(NAVY);
            card.fillRoundRect((getWidth() - width) / 2, top, width, 152, 28, 28);
            drawCentered(card, resultTitle, 28, top + 37, new Color(255, 234, 107));
            drawCentered(card, resultWord, 38, top + 87, Color.WHITE);
            drawCentered(card, resultWinner, 16, top + 123, Color.WHITE);
            card.dispose();
        }

        private void drawCentered(Graphics2D g, String text, int size, int baseline, Color color) {
            g.setFont(new Font("SansSerif", Font.BOLD, size));
            g.setColor(color);
            g.drawString(text, (getWidth() - g.getFontMetrics().stringWidth(text)) / 2, baseline);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ClientMain().setVisible(true));
    }

    private static final class SoundPlayer {
        static final int[] START = {523, 659};
        static final int[] CORRECT = {659, 784, 1047};
        static final int[] TIMEOUT = {392, 262};
        private static final int SAMPLE_RATE = 22050;

        private SoundPlayer() {}

        static void play(int[] notes) {
            Thread thread = new Thread(() -> {
                int samplesPerNote = SAMPLE_RATE * 13 / 100;
                byte[] pcm = new byte[notes.length * samplesPerNote];
                for (int n = 0; n < notes.length; n++) {
                    for (int i = 0; i < samplesPerNote; i++) {
                        double volume = Math.min(1.0, Math.min(i, samplesPerNote - 1 - i) / 150.0);
                        double wave = Math.sin(2 * Math.PI * notes[n] * i / SAMPLE_RATE);
                        pcm[n * samplesPerNote + i] = (byte) (48 * volume * wave);
                    }
                }
                try (Clip clip = AudioSystem.getClip()) {
                    clip.open(new AudioFormat(SAMPLE_RATE, 8, 1, true, false), pcm, 0, pcm.length);
                    clip.start();
                    Thread.sleep(130L * notes.length + 80);
                } catch (LineUnavailableException | IllegalArgumentException e) {
                    System.err.println("효과음 재생 불가: " + e.getMessage());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "round-sound");
            thread.setDaemon(true);
            thread.start();
        }
    }
}

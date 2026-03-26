package src;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.*;

import java.util.Map;
import java.util.LinkedHashMap;

/**
 * Interface Gráfica do Chat — inspirada no design do Discord.
 *
 * ─── CONCORRÊNCIA NA GUI (Swing e Thread Safety) ────────────────────────────
 * O Swing NÃO é thread-safe. Todos os componentes devem ser criados e
 * modificados EXCLUSIVAMENTE na Event Dispatch Thread (EDT).
 *
 * Quando a ReceiverThread do ChatClient recebe uma mensagem do servidor
 * (em uma thread de rede separada), ela NÃO pode modificar componentes
 * diretamente. Em vez disso, usa SwingUtilities.invokeLater(), que
 * enfileira a atualização na EDT — equivalente a uma seção crítica
 * do ponto de vista da GUI.
 * ─────────────────────────────────────────────────────────────────────────────
 */
public class ChatGUI extends JFrame {

    // ─── Paleta de cores (Discord Dark Theme) ────────────────────────────────
    private static final Color BG_DARKEST  = new Color(0x20, 0x21, 0x25);
    private static final Color BG_DARK     = new Color(0x2B, 0x2D, 0x31);
    private static final Color BG_MEDIUM   = new Color(0x31, 0x33, 0x38);
    private static final Color BG_INPUT    = new Color(0x40, 0x43, 0x4A);
    private static final Color ACCENT      = new Color(0x58, 0x65, 0xF2);
    private static final Color ACCENT_HOV  = new Color(0x4A, 0x55, 0xD9);
    private static final Color TEXT_BRIGHT = new Color(0xDB, 0xDE, 0xE1);
    private static final Color TEXT_MUTED  = new Color(0x94, 0x96, 0x9D);
    private static final Color TEXT_INPUT  = new Color(0xDC, 0xDD, 0xDE);
    private static final Color CHANNEL_SEL = new Color(0x40, 0x43, 0x4A);
    private static final Color MSG_SYSTEM  = new Color(0x72, 0x76, 0x7D);
    private static final Color GREEN       = new Color(0x23, 0xA5, 0x59);
    private static final Color RED_ERR     = new Color(0xED, 0x43, 0x45);

    // ─── Fontes ───────────────────────────────────────────────────────────────
    private static final Font F_TITLE  = new Font("Segoe UI", Font.BOLD,  16);
    private static final Font F_CHAN   = new Font("Segoe UI", Font.PLAIN, 14);
    private static final Font F_BOLD   = new Font("Segoe UI", Font.BOLD,  13);
    private static final Font F_TEXT   = new Font("Segoe UI", Font.PLAIN, 13);
    private static final Font F_INPUT  = new Font("Segoe UI", Font.PLAIN, 14);
    private static final Font F_HEAD   = new Font("Segoe UI", Font.BOLD,  14);
    private static final Font F_SMALL  = new Font("Segoe UI", Font.PLAIN, 11);
    private static final Font F_LABEL  = new Font("Segoe UI", Font.BOLD,  11);

    // ─── Componentes ─────────────────────────────────────────────────────────
    private JPanel     channelList;
    private JPanel     messageArea;
    private JScrollPane msgScrollPane;
    private JTextArea  inputArea;      // Agora JTextArea (multi-linha)
    private JButton    sendButton;
    private JLabel     channelTitleLabel;
    private JLabel     memberCountLabel;
    private JLabel     channelDescLabel;
    private JPanel     memberListPanel;
    private JLabel     memberTitleLabel; // título do painel de membros (referência direta)
    private JLabel     statusLabel;
    private CircularAvatar footerAvatar;  // avatar do rodapé da sidebar (referência direta)

    // ─── Estado ───────────────────────────────────────────────────────────────
    private ChatClient client;
    private String     currentChannel = "";
    private final Map<String, JButton> channelButtons = new LinkedHashMap<>();

    // Rastreia o último autor para agrupamento de mensagens consecutivas
    private String lastMessageAuthor = "";
    private long   lastMessageTime   = 0;

    // ─────────────────────────────────────────────────────────────────────────

    public ChatGUI() {
        super("DiscordLP2 — Chat Concorrente & Distribuído");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1120, 760);
        setMinimumSize(new Dimension(900, 600));
        setLocationRelativeTo(null);

        // Melhoria: scrollbars mais suaves
        initUI();
        showLoginDialog();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  CONSTRUÇÃO DA UI
    // ═══════════════════════════════════════════════════════════════════════════

    private void initUI() {
        getContentPane().setBackground(BG_DARKEST);
        setLayout(new BorderLayout(0, 0));

        add(buildLeftPanel(),   BorderLayout.WEST);
        add(buildCenterPanel(), BorderLayout.CENTER);
        add(buildRightPanel(),  BorderLayout.EAST);
    }

    // ─── Painel Esquerdo (Sidebar com canais) ─────────────────────────────────

    private JPanel buildLeftPanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setPreferredSize(new Dimension(235, 0));
        p.setBackground(BG_DARK);

        p.add(buildServerHeader(),   BorderLayout.NORTH);
        p.add(buildChannelSection(), BorderLayout.CENTER);
        p.add(buildUserFooter(),     BorderLayout.SOUTH);
        return p;
    }

    private JPanel buildServerHeader() {
        JPanel h = new JPanel(new BorderLayout(12, 0));
        h.setBackground(BG_DARK);
        h.setBorder(new CompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(0x1E, 0x1F, 0x22)),
            new EmptyBorder(14, 16, 14, 16)
        ));

        // Ícone circular com gradiente (melhorado)
        JComponent icon = new CircularAvatar("LP2", ACCENT, 36);

        JPanel info = new JPanel(new GridLayout(2, 1, 0, 0));
        info.setBackground(BG_DARK);

        JLabel name = new JLabel("DiscordLP2");
        name.setFont(F_TITLE);
        name.setForeground(TEXT_BRIGHT);

        JLabel sub = new JLabel("Servidor de Chat");
        sub.setFont(F_SMALL);
        sub.setForeground(TEXT_MUTED);

        info.add(name);
        info.add(sub);

        h.add(icon, BorderLayout.WEST);
        h.add(info, BorderLayout.CENTER);
        return h;
    }

    private JPanel buildChannelSection() {
        JPanel section = new JPanel(new BorderLayout());
        section.setBackground(BG_DARK);

        JLabel label = new JLabel("  ▸  CANAIS DE TEXTO");
        label.setFont(F_LABEL);
        label.setForeground(TEXT_MUTED);
        label.setBorder(new EmptyBorder(16, 12, 8, 0));

        channelList = new JPanel();
        channelList.setLayout(new BoxLayout(channelList, BoxLayout.Y_AXIS));
        channelList.setBackground(BG_DARK);
        channelList.setBorder(new EmptyBorder(0, 8, 8, 8));

        JScrollPane scroll = new JScrollPane(channelList);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(BG_DARK);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        styleScrollBar(scroll);

        section.add(label,  BorderLayout.NORTH);
        section.add(scroll, BorderLayout.CENTER);
        return section;
    }

    private JPanel buildUserFooter() {
        JPanel f = new JPanel(new BorderLayout(10, 0));
        f.setBackground(new Color(0x23, 0x25, 0x28));
        f.setBorder(new EmptyBorder(10, 12, 10, 12));
        f.setPreferredSize(new Dimension(235, 58));

        // Avatar circular com ponto de status animado
        JPanel ap = new JPanel(null);
        ap.setPreferredSize(new Dimension(44, 40));
        ap.setBackground(new Color(0x23, 0x25, 0x28));

        footerAvatar = new CircularAvatar("?", new Color(0x5C, 0x5E, 0x6A), 32);
        footerAvatar.setBounds(0, 0, 32, 32);

        // Ponto verde de status com animação de pulso
        JLabel dot = new JLabel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(GREEN);
                g2.fillOval(0, 0, getWidth(), getHeight());
                g2.dispose();
            }
        };
        dot.setOpaque(false);
        dot.setBounds(22, 22, 10, 10);

        ap.add(footerAvatar);
        ap.add(dot);

        JPanel info = new JPanel(new GridLayout(2, 1, 0, 1));
        info.setBackground(new Color(0x23, 0x25, 0x28));

        statusLabel = new JLabel("Desconectado");
        statusLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        statusLabel.setForeground(TEXT_BRIGHT);
        statusLabel.setName("statusLabel");

        JLabel subStatus = new JLabel("● Online");
        subStatus.setFont(F_SMALL);
        subStatus.setForeground(GREEN);

        info.add(statusLabel);
        info.add(subStatus);

        // Botão de configurações
        JLabel settingsBtn = new JLabel("⚙");
        settingsBtn.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        settingsBtn.setForeground(TEXT_MUTED);
        settingsBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        settingsBtn.setToolTipText("Configurações (em breve)");
        settingsBtn.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { settingsBtn.setForeground(TEXT_BRIGHT); }
            @Override public void mouseExited(MouseEvent e)  { settingsBtn.setForeground(TEXT_MUTED); }
        });

        f.add(ap,          BorderLayout.WEST);
        f.add(info,        BorderLayout.CENTER);
        f.add(settingsBtn, BorderLayout.EAST);
        return f;
    }

    // ─── Painel Central (Chat) ─────────────────────────────────────────────────

    private JPanel buildCenterPanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(BG_MEDIUM);

        p.add(buildChannelHeader(), BorderLayout.NORTH);
        p.add(buildMessageArea(),   BorderLayout.CENTER);
        p.add(buildBottomArea(),    BorderLayout.SOUTH);
        return p;
    }

    private JPanel buildChannelHeader() {
        JPanel h = new JPanel(new BorderLayout());
        h.setBackground(BG_MEDIUM);
        h.setBorder(new CompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(0x1E, 0x1F, 0x22)),
            new EmptyBorder(12, 20, 12, 20)
        ));

        // Lado esquerdo: # + nome + separador + descrição
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left.setBackground(BG_MEDIUM);

        JLabel hash = new JLabel("#");
        hash.setFont(new Font("Segoe UI", Font.BOLD, 20));
        hash.setForeground(ACCENT);  // Melhoria: accent em vez de cinza

        channelTitleLabel = new JLabel("Selecione um canal");
        channelTitleLabel.setFont(F_HEAD);
        channelTitleLabel.setForeground(TEXT_BRIGHT);

        // Separador visual
        JLabel sep = new JLabel("|");
        sep.setFont(F_TEXT);
        sep.setForeground(new Color(0x50, 0x52, 0x58));

        channelDescLabel = new JLabel("Entre em um canal para começar");
        channelDescLabel.setFont(F_SMALL);
        channelDescLabel.setForeground(TEXT_MUTED);

        left.add(hash);
        left.add(channelTitleLabel);
        left.add(sep);
        left.add(channelDescLabel);

        // Lado direito: contador de membros + ações
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        right.setBackground(BG_MEDIUM);

        memberCountLabel = new JLabel("👥 0 membros");
        memberCountLabel.setFont(F_SMALL);
        memberCountLabel.setForeground(TEXT_MUTED);

        JLabel searchBtn = makeHeaderIcon("🔍", "Pesquisar (em breve)");
        JLabel mentionBtn = makeHeaderIcon("@", "Menções (em breve)");

        right.add(memberCountLabel);
        right.add(searchBtn);
        right.add(mentionBtn);

        h.add(left,  BorderLayout.CENTER);
        h.add(right, BorderLayout.EAST);
        return h;
    }

    private JLabel makeHeaderIcon(String text, String tooltip) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("Segoe UI", Font.BOLD, 15));
        l.setForeground(TEXT_MUTED);
        l.setToolTipText(tooltip);
        l.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        l.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { l.setForeground(TEXT_BRIGHT); }
            @Override public void mouseExited(MouseEvent e)  { l.setForeground(TEXT_MUTED); }
        });
        return l;
    }

    private JScrollPane buildMessageArea() {
        messageArea = new JPanel();
        messageArea.setLayout(new BoxLayout(messageArea, BoxLayout.Y_AXIS));
        messageArea.setBackground(BG_MEDIUM);
        messageArea.setBorder(new EmptyBorder(12, 16, 8, 16));

        msgScrollPane = new JScrollPane(messageArea);
        msgScrollPane.setBorder(null);
        msgScrollPane.getViewport().setBackground(BG_MEDIUM);
        msgScrollPane.getVerticalScrollBar().setUnitIncrement(16);
        msgScrollPane.getVerticalScrollBar().setBlockIncrement(60);
        styleScrollBar(msgScrollPane);
        return msgScrollPane;
    }

    private JPanel buildBottomArea() {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(BG_MEDIUM);
        wrapper.setBorder(new EmptyBorder(0, 16, 12, 16));
        wrapper.add(buildInputArea(), BorderLayout.CENTER);
        return wrapper;
    }

    private RoundedPanel buildInputArea() {
        // Painel com bordas arredondadas
        RoundedPanel box = new RoundedPanel(12, BG_INPUT);
        box.setLayout(new BorderLayout(8, 0));
        box.setBorder(new EmptyBorder(10, 16, 10, 10));

        // Botão emoji à esquerda
        JLabel emojiBtn = new JLabel("😀");
        emojiBtn.setFont(new Font("Segoe UI", Font.PLAIN, 18));
        emojiBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        emojiBtn.setToolTipText("Emojis (em breve)");
        emojiBtn.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                JOptionPane.showMessageDialog(ChatGUI.this, "Emojis em breve! 🚀", "Em construção", JOptionPane.INFORMATION_MESSAGE);
            }
        });

        // JTextArea multi-linha com placeholder
        inputArea = new JTextArea(1, 1) {
            private final String placeholder = "Escreva uma mensagem...";
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (getText().isEmpty() && !isFocusOwner()) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setColor(TEXT_MUTED);
                    g2.setFont(getFont());
                    g2.drawString(placeholder, getInsets().left + 2, g2.getFontMetrics().getAscent() + getInsets().top);
                    g2.dispose();
                }
            }
        };
        inputArea.setFont(F_INPUT);
        inputArea.setForeground(TEXT_INPUT);
        inputArea.setBackground(BG_INPUT);
        inputArea.setCaretColor(TEXT_BRIGHT);
        inputArea.setBorder(null);
        inputArea.setEnabled(false);
        inputArea.setLineWrap(true);
        inputArea.setWrapStyleWord(true);

        // Enter envia, Shift+Enter cria nova linha
        inputArea.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER && !e.isShiftDown()) {
                    e.consume();
                    sendMessage();
                }
            }
        });

        // Botão enviar animado
        sendButton = new AnimatedButton("➤", ACCENT, ACCENT_HOV);
        sendButton.setEnabled(false);
        sendButton.addActionListener(e -> sendMessage());

        box.add(emojiBtn,  BorderLayout.WEST);
        box.add(inputArea, BorderLayout.CENTER);
        box.add(sendButton, BorderLayout.EAST);
        return box;
    }

    // ─── Painel Direito (Membros) ─────────────────────────────────────────────

    private JPanel buildRightPanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setPreferredSize(new Dimension(200, 0));
        p.setBackground(BG_DARK);

        memberTitleLabel = new JLabel("  MEMBROS ONLINE — 0");
        memberTitleLabel.setFont(F_LABEL);
        memberTitleLabel.setForeground(TEXT_MUTED);
        memberTitleLabel.setBorder(new EmptyBorder(16, 12, 10, 8));
        JLabel title = memberTitleLabel;

        memberListPanel = new JPanel();
        memberListPanel.setLayout(new BoxLayout(memberListPanel, BoxLayout.Y_AXIS));
        memberListPanel.setBackground(BG_DARK);
        memberListPanel.setBorder(new EmptyBorder(0, 8, 8, 8));

        JScrollPane scroll = new JScrollPane(memberListPanel);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(BG_DARK);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        styleScrollBar(scroll);

        p.add(title,  BorderLayout.NORTH);
        p.add(scroll, BorderLayout.CENTER);
        return p;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  DIÁLOGO DE LOGIN — reformulado
    // ═══════════════════════════════════════════════════════════════════════════

    private void showLoginDialog() {
        JDialog dlg = new JDialog(this, "Conectar ao Servidor", true);
        dlg.setSize(440, 490);
        dlg.setLocationRelativeTo(this);
        dlg.setResizable(false);
        dlg.getContentPane().setBackground(BG_DARK);
        dlg.setLayout(new BorderLayout());

        // ─── Logo animado (pulsante) ───────────────────────────────────────
        JComponent logo = new JComponent() {
            private float scale = 1.0f;
            private boolean growing = false;
            {
                setPreferredSize(new Dimension(56, 56));
                Timer t = new Timer(40, e -> {
                    scale += growing ? 0.008f : -0.008f;
                    if (scale > 1.08f) growing = false;
                    if (scale < 0.92f) growing = true;
                    repaint();
                });
                t.start();
            }
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int cx = getWidth() / 2, cy = getHeight() / 2;
                int r = (int)(22 * scale);
                GradientPaint gp = new GradientPaint(cx - r, cy - r, ACCENT, cx + r, cy + r, ACCENT_HOV);
                g2.setPaint(gp);
                g2.fillOval(cx - r, cy - r, r * 2, r * 2);
                g2.setColor(Color.WHITE);
                g2.setFont(new Font("Segoe UI", Font.BOLD, 13));
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString("LP2", cx - fm.stringWidth("LP2") / 2, cy + fm.getAscent() / 2 - 2);
                g2.dispose();
            }
        };

        JPanel topPanel = new JPanel(new BorderLayout(0, 4));
        topPanel.setBackground(BG_DARK);
        topPanel.setBorder(new EmptyBorder(12, 0, 6, 0));

        JPanel logoWrap = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        logoWrap.setBackground(BG_DARK);
        logoWrap.add(logo);

        JLabel title = new JLabel("Entrar no DiscordLP2", SwingConstants.CENTER);
        title.setFont(new Font("Segoe UI", Font.BOLD, 20));
        title.setForeground(TEXT_BRIGHT);

        JLabel subtitle = new JLabel("Programação Concorrente & Distribuída", SwingConstants.CENTER);
        subtitle.setFont(F_SMALL);
        subtitle.setForeground(TEXT_MUTED);

        topPanel.add(logoWrap, BorderLayout.NORTH);
        topPanel.add(title,    BorderLayout.CENTER);
        topPanel.add(subtitle, BorderLayout.SOUTH);

        // ─── Formulário ────────────────────────────────────────────────────
        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(BG_DARK);
        form.setBorder(new EmptyBorder(8, 40, 0, 40));

        GridBagConstraints g = new GridBagConstraints();
        g.fill = GridBagConstraints.HORIZONTAL;
        g.weightx = 1.0;
        g.gridx = 0;
        g.insets = new Insets(3, 0, 3, 0);

        JTextField hostField = loginField("localhost");
        JTextField portField = loginField("12345");
        JTextField nickField = loginField("");
        hostField.setName("hostField");
        portField.setName("portField");
        nickField.setName("nickField");

        g.gridy = 0; form.add(formLabel("SERVIDOR"),  g);
        g.gridy = 1; form.add(hostField, g);
        g.gridy = 2; form.add(formLabel("PORTA"),     g);
        g.gridy = 3; form.add(portField, g);
        g.gridy = 4; form.add(formLabel("NICKNAME"),  g);
        g.gridy = 5; form.add(nickField,  g);

        // ─── Erro + botão ──────────────────────────────────────────────────
        JLabel errLabel = new JLabel(" ", SwingConstants.CENTER);
        errLabel.setFont(F_SMALL);
        errLabel.setForeground(RED_ERR);

        AnimatedButton btn = new AnimatedButton("Entrar no Servidor", ACCENT, ACCENT_HOV);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btn.setPreferredSize(new Dimension(360, 44));
        btn.setName("loginButton");

        // Badge de versão
        JLabel badge = new JLabel("DiscordLP2 v1.0 • LP2 2025", SwingConstants.CENTER);
        badge.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        badge.setForeground(new Color(0x4A, 0x4C, 0x55));
        badge.setBorder(new EmptyBorder(4, 0, 0, 0));

        JPanel south = new JPanel(new BorderLayout(0, 6));
        south.setBackground(BG_DARK);
        south.setBorder(new EmptyBorder(10, 40, 20, 40));
        south.add(errLabel, BorderLayout.NORTH);
        south.add(btn,      BorderLayout.CENTER);
        south.add(badge,    BorderLayout.SOUTH);

        dlg.add(topPanel, BorderLayout.NORTH);
        dlg.add(form,     BorderLayout.CENTER);
        dlg.add(south,    BorderLayout.SOUTH);

        // ─── Ação do botão ─────────────────────────────────────────────────
        Runnable doConnect = () -> {
            String host = hostField.getText().trim();
            String port = portField.getText().trim();
            String nick = nickField.getText().trim();

            if (host.isEmpty() || port.isEmpty() || nick.isEmpty()) {
                shakeAndError(errLabel, "⚠  Preencha todos os campos.");
                return;
            }
            if (nick.contains(" ") || nick.length() < 2 || nick.length() > 20) {
                shakeAndError(errLabel, "⚠  Nickname inválido (2-20 chars, sem espaços).");
                flashFieldError(nickField);
                return;
            }
            int portNum;
            try { portNum = Integer.parseInt(port); }
            catch (NumberFormatException ex) {
                shakeAndError(errLabel, "⚠  Porta inválida (número entre 1-65535).");
                flashFieldError(portField);
                return;
            }

            // Estado de "loading" no botão
            btn.setText("Conectando...");
            btn.setEnabled(false);
            errLabel.setText(" ");

            int finalPort = portNum;
            new Thread(() -> {
                try {
                    client = new ChatClient();
                    client.connect(host, finalPort, nick,
                        this::onMessageReceived,
                        this::onDisconnected);

                    SwingUtilities.invokeLater(() -> {
                        dlg.dispose();
                        setTitle("DiscordLP2 — " + nick);

                        // Atualiza rodapé da sidebar
                        statusLabel.setText(nick);
                        // Atualiza avatar circular do rodapé
                        updateFooterAvatar(nick);

                        client.sendCommand("/channels");
                        addSystemMessage("🎉 Conectado como " + nick + "! Escolha um canal à esquerda.");
                    });
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> {
                        errLabel.setText("⚠  " + ex.getMessage());
                        btn.setText("Entrar no Servidor");
                        btn.setEnabled(true);
                    });
                }
            }, "ConnectThread").start();
        };

        btn.addActionListener(e -> doConnect.run());
        // Enter nos campos também conecta
        ActionListener enterAction = e -> doConnect.run();
        hostField.addActionListener(enterAction);
        portField.addActionListener(enterAction);
        nickField.addActionListener(enterAction);

        dlg.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { System.exit(0); }
        });

        setVisible(true);
        dlg.setVisible(true);
    }

    /** Atualiza o avatar circular do rodapé com a inicial e cor do nick conectado. */
    private void updateFooterAvatar(String nick) {
        footerAvatar.setLabel(nick.substring(0, 1).toUpperCase());
        footerAvatar.setColor(colorFor(nick));
        footerAvatar.repaint();
    }

    /** Anima o label de erro com um tremor lateral (shake). */
    private void shakeAndError(JLabel label, String msg) {
        label.setText(msg);
        final int[] delta = {-6, 6, -5, 5, -3, 3, 0};
        final int[] i = {0};
        Point orig = label.getLocation();
        Timer t = new Timer(40, null);
        t.addActionListener(e -> {
            if (i[0] < delta.length) {
                label.setLocation(orig.x + delta[i[0]], orig.y);
                i[0]++;
            } else {
                label.setLocation(orig);
                t.stop();
            }
        });
        t.start();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  CALLBACKS DE REDE (chamados por threads de rede)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Chamado pela ReceiverThread do ChatClient quando uma mensagem chega.
     *
     * ⚠ THREAD SAFETY: Este callback roda em uma thread de REDE (não na EDT).
     *   Usamos SwingUtilities.invokeLater() para agendar a atualização de
     *   componentes Swing na EDT — esta é a solução correta para o problema
     *   clássico de concorrência em GUIs.
     */
    private void onMessageReceived(Message msg) {
        SwingUtilities.invokeLater(() -> handleMessage(msg));
    }

    /** Processamento de mensagem — executa SEMPRE na EDT. */
    private void handleMessage(Message msg) {
        switch (msg.getType()) {

            case Message.TYPE_CHAT:
                addChatBubble(msg);
                break;

            case Message.TYPE_SYSTEM:
                if ("__STOP__".equals(msg.getChannel())) return;
                String content = msg.getContent();
                if (content.startsWith("📋 Canais disponíveis:")) {
                    parseChannels(content);
                } else if (content.startsWith("Usuários em #")) {
                    // Parse da lista de usuários para o painel direito
                    parseUsers(content);
                } else {
                    addSystemMessage(content);
                }
                break;

            case Message.TYPE_JOIN:
                if ("SERVER".equals(msg.getAuthor()) && msg.getContent().startsWith("Bem-vindo")) {
                    currentChannel = msg.getChannel();
                    channelTitleLabel.setText(msg.getChannel());
                    String desc = msg.getContent()
                        .replace("Bem-vindo ao #" + msg.getChannel() + "! ", "");
                    channelDescLabel.setText(desc);
                    inputArea.setEnabled(true);
                    sendButton.setEnabled(true);
                    highlightChannel(msg.getChannel());
                    // Pede lista de usuários após entrar
                    if (client != null) client.sendCommand("/users");
                } else {
                    addSystemMessage("👋 " + msg.getContent());
                    if (client != null) client.sendCommand("/users");
                }
                break;

            case Message.TYPE_LEAVE:
                addSystemMessage("🚪 " + msg.getContent());
                if (client != null) client.sendCommand("/users");
                break;
        }
    }

    /** Chamado quando a conexão cai. Executa em thread de rede → invokeLater. */
    private void onDisconnected() {
        SwingUtilities.invokeLater(() -> {
            addSystemMessage("❌ Conexão com o servidor encerrada.");
            statusLabel.setText("Desconectado");
            inputArea.setEnabled(false);
            sendButton.setEnabled(false);
        });
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  ENVIO DE MENSAGENS
    // ═══════════════════════════════════════════════════════════════════════════

    private void sendMessage() {
        String text = inputArea.getText().trim();
        if (text.isEmpty() || client == null || !client.isConnected()) return;

        if (text.startsWith("/")) {
            client.sendCommand(text);
            addSystemMessage("→ " + text);
        } else {
            Message self = new Message(Message.TYPE_CHAT, client.getUsername(), currentChannel, text);
            addChatBubble(self);
            client.sendChatMessage(text);
        }
        inputArea.setText("");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  EXIBIÇÃO DE MENSAGENS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Adiciona uma bolha de chat ao painel de mensagens.
     *
     * MELHORIA: Agrupamento de mensagens do mesmo autor em ≤3 minutos —
     * omite avatar e nome, igual ao Discord real. Economiza espaço e é
     * mais legível em conversas rápidas.
     */
    private void addChatBubble(Message msg) {
        long now = System.currentTimeMillis();
        boolean sameAuthor = msg.getAuthor().equals(lastMessageAuthor)
                && (now - lastMessageTime) < 3 * 60 * 1000L;

        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setBackground(BG_MEDIUM);
        row.setBorder(new EmptyBorder(sameAuthor ? 1 : 6, 4, 1, 8));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        if (!sameAuthor) {
            // Avatar circular ancorado ao TOPO — evita esticamento vertical
            // quando o texto da mensagem ocupa mais de uma linha.
            // BorderLayout.WEST daria ao avatar a altura total do CENTER,
            // por isso envolvemos num painel com NORTH para fixar no topo.
            CircularAvatar avatar = new CircularAvatar(
                msg.getAuthor().substring(0, 1).toUpperCase(),
                colorFor(msg.getAuthor()), 36
            );
            avatar.setPreferredSize(new Dimension(36, 36));
            avatar.setMinimumSize(new Dimension(36, 36));
            avatar.setMaximumSize(new Dimension(36, 36));

            JPanel avatarWrap = new JPanel(new BorderLayout());
            avatarWrap.setBackground(BG_MEDIUM);
            avatarWrap.setPreferredSize(new Dimension(46, 1));
            avatarWrap.add(avatar, BorderLayout.NORTH); // ancora no topo

            JPanel content = new JPanel(new BorderLayout(0, 3));
            content.setBackground(BG_MEDIUM);

            JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            header.setBackground(BG_MEDIUM);

            JLabel name = new JLabel(msg.getAuthor());
            name.setFont(F_BOLD);
            name.setForeground(colorFor(msg.getAuthor()));

            JLabel time = new JLabel(msg.getTime());
            time.setFont(F_SMALL);
            time.setForeground(TEXT_MUTED);

            header.add(name);
            header.add(time);

            JTextArea txt = buildMessageText(msg.getContent());

            content.add(header, BorderLayout.NORTH);
            content.add(txt,    BorderLayout.CENTER);

            row.add(avatarWrap, BorderLayout.WEST);
            row.add(content,    BorderLayout.CENTER);
        } else {
            // Mensagem agrupada: apenas indenta o texto
            JPanel indent = new JPanel();
            indent.setPreferredSize(new Dimension(46, 1));
            indent.setBackground(BG_MEDIUM);

            JTextArea txt = buildMessageText(msg.getContent());
            row.add(indent, BorderLayout.WEST);
            row.add(txt,    BorderLayout.CENTER);
        }

        // Hover com timestamp no canto (quando agrupado aparece a hora)
        Color hov = new Color(0x35, 0x37, 0x3C);
        row.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { deepBg(row, hov); }
            @Override public void mouseExited (MouseEvent e) { deepBg(row, BG_MEDIUM); }
        });

        lastMessageAuthor = msg.getAuthor();
        lastMessageTime   = now;

        // Animação de fade-in
        FadeInPanel fade = new FadeInPanel(row);
        appendToChat(fade);
    }

    private JTextArea buildMessageText(String content) {
        JTextArea txt = new JTextArea(content);
        txt.setFont(F_TEXT);
        txt.setForeground(TEXT_BRIGHT);
        txt.setBackground(BG_MEDIUM);
        txt.setEditable(false);
        txt.setLineWrap(true);
        txt.setWrapStyleWord(true);
        txt.setBorder(null);
        return txt;
    }

    /**
     * Exibe mensagem de sistema com linha horizontal nos dois lados
     * (estilo Discord real: ──── texto ────).
     */
    private void addSystemMessage(String text) {
        // Quebra o agrupamento quando entra msg de sistema
        lastMessageAuthor = "";

        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setBackground(BG_MEDIUM);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        row.setBorder(new EmptyBorder(6, 4, 6, 8));

        JPanel leftLine  = makeSysLine();
        JPanel rightLine = makeSysLine();

        JLabel lbl = new JLabel(text);
        lbl.setFont(F_SMALL);
        lbl.setForeground(MSG_SYSTEM);

        row.add(leftLine,  BorderLayout.WEST);
        row.add(lbl,       BorderLayout.CENTER);
        row.add(rightLine, BorderLayout.EAST);
        appendToChat(row);
    }

    private JPanel makeSysLine() {
        JPanel line = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.setColor(new Color(0x3F, 0x41, 0x47));
                g.drawLine(0, getHeight()/2, getWidth(), getHeight()/2);
            }
        };
        line.setBackground(BG_MEDIUM);
        line.setPreferredSize(new Dimension(30, 14));
        return line;
    }

    private void appendToChat(JComponent c) {
        messageArea.add(c);
        messageArea.revalidate();
        messageArea.repaint();
        SwingUtilities.invokeLater(() -> {
            JScrollBar v = msgScrollPane.getVerticalScrollBar();
            v.setValue(v.getMaximum());
        });
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  GESTÃO DE CANAIS NA SIDEBAR
    // ═══════════════════════════════════════════════════════════════════════════

    private void parseChannels(String text) {
        java.util.List<String> names = new java.util.ArrayList<>();
        for (String line : text.split("\n")) {
            String t = line.trim();
            if (t.startsWith("#")) {
                String name = t.split("\\s+")[0].substring(1);
                if (!name.isEmpty()) names.add(name);
            }
        }
        if (!names.isEmpty()) rebuildChannelList(names);
    }

    private void parseUsers(String content) {
        // Exemplo: "Usuários em #geral: Alice, Bob, Carlos"
        int colon = content.indexOf(": ");
        if (colon < 0) return;
        String[] users = content.substring(colon + 2).split(",\\s*");
        rebuildMemberList(users);
    }

    private void rebuildMemberList(String[] users) {
        memberListPanel.removeAll();

        // Referência direta ao label (evita ClassCastException por traversal hierárquico
        // que passava pelo JScrollPane — que não é JPanel — causando o cast inválido).
        memberTitleLabel.setText("  MEMBROS ONLINE — " + users.length);
        memberCountLabel.setText("👥 " + users.length + (users.length == 1 ? " membro" : " membros"));

        for (String user : users) {
            String u = user.trim();
            if (u.isEmpty()) continue;

            JPanel mrow = new JPanel(new BorderLayout(8, 0));
            mrow.setBackground(BG_DARK);
            mrow.setBorder(new EmptyBorder(4, 4, 4, 8));
            mrow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

            CircularAvatar av = new CircularAvatar(u.substring(0, 1).toUpperCase(), colorFor(u), 28);
            av.setPreferredSize(new Dimension(28, 28));

            JLabel name = new JLabel(u);
            name.setFont(F_SMALL);
            name.setForeground(TEXT_MUTED);

            // Ponto verde de status
            JLabel dot = new JLabel() {
                @Override protected void paintComponent(Graphics g) {
                    ((Graphics2D)g).setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                    g.setColor(GREEN);
                    g.fillOval(0, 2, 8, 8);
                }
            };
            dot.setOpaque(false);
            dot.setPreferredSize(new Dimension(10, 14));

            mrow.add(av,   BorderLayout.WEST);
            mrow.add(name, BorderLayout.CENTER);
            mrow.add(dot,  BorderLayout.EAST);
            mrow.setToolTipText(u);

            // Hover
            mrow.addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) {
                    mrow.setBackground(new Color(0x35, 0x37, 0x3C));
                    name.setForeground(TEXT_BRIGHT);
                }
                @Override public void mouseExited(MouseEvent e) {
                    mrow.setBackground(BG_DARK);
                    name.setForeground(TEXT_MUTED);
                }
            });

            memberListPanel.add(mrow);
            memberListPanel.add(Box.createVerticalStrut(2));
        }
        memberListPanel.revalidate();
        memberListPanel.repaint();
    }

    private void rebuildChannelList(java.util.List<String> names) {
        channelList.removeAll();
        channelButtons.clear();
        for (String name : names) {
            JButton btn = channelButton(name);
            channelButtons.put(name, btn);
            channelList.add(btn);
            channelList.add(Box.createVerticalStrut(2));
        }
        channelList.revalidate();
        channelList.repaint();
    }

    private JButton channelButton(String name) {
        JButton btn = new JButton("# " + name) {
            // Borda roxa à esquerda quando selecionado (igual Discord)
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (name.equals(currentChannel)) {
                    g.setColor(ACCENT);
                    g.fillRect(0, 4, 3, getHeight() - 8);
                }
            }
        };
        btn.setFont(F_CHAN);
        btn.setForeground(TEXT_MUTED);
        btn.setBackground(BG_DARK);
        btn.setBorder(new EmptyBorder(7, 14, 7, 10));
        btn.setHorizontalAlignment(SwingConstants.LEFT);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));

        Color hov = new Color(0x35, 0x37, 0x3C);
        btn.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) {
                if (!name.equals(currentChannel)) { btn.setBackground(hov); btn.setForeground(TEXT_BRIGHT); }
            }
            @Override public void mouseExited(MouseEvent e) {
                if (!name.equals(currentChannel)) { btn.setBackground(BG_DARK); btn.setForeground(TEXT_MUTED); }
            }
        });

        btn.addActionListener(e -> {
            if (!name.equals(currentChannel) && client != null && client.isConnected()) {
                messageArea.removeAll();
                messageArea.revalidate();
                messageArea.repaint();
                memberListPanel.removeAll();
                memberListPanel.revalidate();
                lastMessageAuthor = "";
                addSystemMessage("Entrando em #" + name + "...");
                client.sendCommand("/join " + name);
            }
        });
        return btn;
    }

    private void highlightChannel(String name) {
        for (Map.Entry<String, JButton> e : channelButtons.entrySet()) {
            boolean sel = e.getKey().equals(name);
            e.getValue().setBackground(sel ? CHANNEL_SEL : BG_DARK);
            e.getValue().setForeground(sel ? TEXT_BRIGHT  : TEXT_MUTED);
            e.getValue().repaint();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  COMPONENTES CUSTOMIZADOS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * CircularAvatar — JComponent que desenha uma inicial em círculo.
     * Usa Graphics2D com anti-aliasing para bordas suaves.
     */
    static class CircularAvatar extends JComponent {
        private String label;
        private Color  color;
        private final int size;

        CircularAvatar(String label, Color color, int size) {
            this.label = label;
            this.color = color;
            this.size  = size;
            setPreferredSize(new Dimension(size, size));
            setOpaque(false);
        }

        void setLabel(String l) { this.label = l; }
        void setColor(Color c)  { this.color = c; }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            // Círculo de fundo
            g2.setColor(color);
            g2.fillOval(0, 0, w, h);
            // Inicial centralizada
            g2.setColor(Color.WHITE);
            g2.setFont(new Font("Segoe UI", Font.BOLD, (int)(size * 0.4)));
            FontMetrics fm = g2.getFontMetrics();
            int x = (w - fm.stringWidth(label)) / 2;
            int y = (h - fm.getHeight()) / 2 + fm.getAscent();
            g2.drawString(label, x, y);
            g2.dispose();
        }
    }

    /**
     * RoundedPanel — painel com bordas arredondadas, usado no input.
     */
    static class RoundedPanel extends JPanel {
        private final int arc;
        private final Color bg;

        RoundedPanel(int arc, Color bg) {
            this.arc = arc;
            this.bg  = bg;
            setOpaque(false);
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(bg);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /**
     * AnimatedButton — botão que interpola a cor de fundo ao hover,
     * dando uma transição suave (não instantânea) entre as cores.
     */
    static class AnimatedButton extends JButton {
        private Color current;
        private Color target;
        private Timer animator;

        AnimatedButton(String text, Color base, Color hover) {
            super(text);
            this.current = base;
            this.target  = base;

            setBackground(base);
            setForeground(Color.WHITE);
            setFont(new Font("Segoe UI", Font.PLAIN, 14));
            setBorder(new EmptyBorder(8, 18, 8, 18));
            setFocusPainted(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setOpaque(true);

            animator = new Timer(16, e -> {
                current = blend(current, target, 0.2f);
                setBackground(current);
                if (colorDist(current, target) < 3) {
                    current = target;
                    setBackground(current);
                    ((Timer) e.getSource()).stop();
                }
            });

            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { animateTo(hover); }
                @Override public void mouseExited (MouseEvent e) { animateTo(base);  }
            });
        }

        private void animateTo(Color c) {
            target = c;
            if (!animator.isRunning()) animator.start();
        }

        private Color blend(Color a, Color b, float t) {
            return new Color(
                (int)(a.getRed()   + (b.getRed()   - a.getRed())   * t),
                (int)(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int)(a.getBlue()  + (b.getBlue()  - a.getBlue())  * t)
            );
        }

        private double colorDist(Color a, Color b) {
            int dr = a.getRed() - b.getRed();
            int dg = a.getGreen() - b.getGreen();
            int db = a.getBlue() - b.getBlue();
            return Math.sqrt(dr*dr + dg*dg + db*db);
        }
    }

    /**
     * FadeInPanel — wrapper que anima alpha de 0→1 ao ser adicionado,
     * usando AlphaComposite para simular fade-in suave.
     */
    static class FadeInPanel extends JPanel {
        private float alpha = 0.0f;

        FadeInPanel(JComponent inner) {
            setLayout(new BorderLayout());
            setOpaque(false);
            setMaximumSize(inner.getMaximumSize());
            add(inner, BorderLayout.CENTER);

            Timer t = new Timer(20, null);
            t.addActionListener(e -> {
                alpha = Math.min(1.0f, alpha + 0.12f);
                repaint();
                if (alpha >= 1.0f) ((Timer) e.getSource()).stop();
            });
            t.start();
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            super.paintComponent(g2);
            g2.dispose();
        }

        @Override protected void paintChildren(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            super.paintChildren(g2);
            g2.dispose();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  UTILITÁRIOS
    // ═══════════════════════════════════════════════════════════════════════════

    /** Cor determinística por username (mesma cor em todas as sessões). */
    private Color colorFor(String username) {
        Color[] palette = {
            new Color(0x5C, 0x65, 0xF2), new Color(0x3B, 0xA5, 0x5D),
            new Color(0xE8, 0x67, 0x31), new Color(0xED, 0x43, 0x45),
            new Color(0xF0, 0xB2, 0x32), new Color(0x00, 0x96, 0x9A),
            new Color(0xB5, 0x73, 0xE3), new Color(0xE9, 0x1E, 0x63),
        };
        return palette[Math.abs(username.hashCode()) % palette.length];
    }

    /** Muda fundo de um contêiner e filhos recursivamente. */
    private void deepBg(Container c, Color color) {
        c.setBackground(color);
        for (Component child : c.getComponents()) {
            if (child instanceof JTextArea || child instanceof CircularAvatar) continue;
            if (child instanceof Container) deepBg((Container) child, color);
        }
    }

    private JLabel formLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("Segoe UI", Font.BOLD, 10));
        l.setForeground(TEXT_MUTED);
        l.setBorder(new EmptyBorder(6, 0, 2, 0));
        return l;
    }

    /**
     * Campo de texto para o diálogo de login.
     * Usa JTextField opaco com borda que muda de cinza → roxo no foco.
     * Approach confiável em todos os Look&Feels Linux (GTK, Nimbus, Metal).
     */
    private JTextField loginField(String defaultText) {
        Color borderNormal = new Color(0x1A, 0x1B, 0x1E);
        Color borderFocus  = ACCENT;
        JTextField f = new JTextField(defaultText);
        f.setFont(F_INPUT);
        f.setForeground(TEXT_BRIGHT);
        f.setBackground(BG_INPUT);
        f.setCaretColor(TEXT_BRIGHT);
        f.setOpaque(true);
        f.setBorder(new CompoundBorder(
            new LineBorder(borderNormal, 2, true),
            new EmptyBorder(8, 12, 8, 12)
        ));
        f.setPreferredSize(new Dimension(360, 40));
        f.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) {
                f.setBorder(new CompoundBorder(
                    new LineBorder(borderFocus, 2, true),
                    new EmptyBorder(8, 12, 8, 12)));
            }
            @Override public void focusLost(FocusEvent e) {
                f.setBorder(new CompoundBorder(
                    new LineBorder(borderNormal, 2, true),
                    new EmptyBorder(8, 12, 8, 12)));
            }
        });
        return f;
    }

    /** Pisca a borda do campo de vermelho por 400ms (feedback de erro). */
    private void flashFieldError(JTextField f) {
        Border origBorder = f.getBorder();
        f.setBorder(new CompoundBorder(
            new LineBorder(RED_ERR, 2, true),
            new EmptyBorder(8, 12, 8, 12)));
        new Timer(400, e -> {
            f.setBorder(origBorder);
            ((Timer) e.getSource()).stop();
        }).start();
    }

    /** Estiliza barra de scroll (discreta, estilo escuro). */
    private void styleScrollBar(JScrollPane sp) {
        sp.getVerticalScrollBar().setUI(new javax.swing.plaf.basic.BasicScrollBarUI() {
            @Override protected void configureScrollBarColors() {
                thumbColor = new Color(0x40, 0x43, 0x4A);
                trackColor = BG_DARK;
            }
            @Override protected JButton createDecreaseButton(int o) { return zeroButton(); }
            @Override protected JButton createIncreaseButton(int o) { return zeroButton(); }
            private JButton zeroButton() {
                JButton b = new JButton(); b.setPreferredSize(new Dimension(0, 0)); return b;
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  PONTO DE ENTRADA
    // ═══════════════════════════════════════════════════════════════════════════

    public static void main(String[] args) {
        // Inicia a GUI na Event Dispatch Thread (EDT) — obrigatório em Swing
        SwingUtilities.invokeLater(ChatGUI::new);
    }
}

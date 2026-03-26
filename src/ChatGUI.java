package src;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.ArrayList;
import java.util.*;

/**
 * Interface Gráfica do Chat — inspirada no design do Discord.
 *
 * Implementada em Java Swing (puro, sem bibliotecas externas).
 *
 * ─── CONCORRÊNCIA NA GUI (Swing e Thread Safety) ────────────────────────────
 *
 * O Swing NÃO é thread-safe. Todos os componentes devem ser criados e
 * modificados EXCLUSIVAMENTE na Event Dispatch Thread (EDT), que é a
 * thread especializada do Swing para processar eventos de interface.
 *
 * Quando a ReceiverThread do ChatClient recebe uma mensagem do servidor
 * (em uma thread de rede separada), ela NÃO pode modificar componentes
 * diretamente. Em vez disso, usa SwingUtilities.invokeLater(), que
 * enfileira a atualização na EDT — equivalente a uma seção crítica
 * do ponto de vista da GUI.
 *
 * Analogia com os slides: a EDT é como um monitor com uma única "vaga"
 * de execução; invokeLater() é o equivalente ao V(s) que agenda o acesso.
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
    private JPanel    channelList;
    private JPanel    messageArea;
    private JScrollPane msgScrollPane;
    private JTextField inputField;
    private JButton    sendButton;
    private JLabel     channelTitleLabel;
    private JLabel     channelDescLabel;
    private JPanel     memberListPanel;
    private JLabel     statusLabel;
    private JLabel     avatarLabel;         // avatar no rodapé da sidebar

    // ─── Estado ───────────────────────────────────────────────────────────────
    private ChatClient client;
    private String     currentChannel = "";
    private final Map<String, JButton> channelButtons = new LinkedHashMap<>();

    // ─────────────────────────────────────────────────────────────────────────

    public ChatGUI() {
        super("DiscordLP2 — Chat Concorrente & Distribuído");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1120, 760);
        setMinimumSize(new Dimension(900, 600));
        setLocationRelativeTo(null);

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

        p.add(buildServerHeader(), BorderLayout.NORTH);
        p.add(buildChannelSection(), BorderLayout.CENTER);
        p.add(buildUserFooter(), BorderLayout.SOUTH);
        return p;
    }

    private JPanel buildServerHeader() {
        JPanel h = new JPanel(new BorderLayout(12, 0));
        h.setBackground(BG_DARK);
        h.setBorder(new CompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(0x1E, 0x1F, 0x22)),
            new EmptyBorder(14, 16, 14, 16)
        ));

        JLabel icon = new JLabel("LP2", SwingConstants.CENTER);
        icon.setFont(new Font("Segoe UI", Font.BOLD, 11));
        icon.setForeground(Color.WHITE);
        icon.setBackground(ACCENT);
        icon.setOpaque(true);
        icon.setPreferredSize(new Dimension(36, 36));

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
        styleScrollBar(scroll);

        section.add(label, BorderLayout.NORTH);
        section.add(scroll, BorderLayout.CENTER);
        return section;
    }

    private JPanel buildUserFooter() {
        JPanel f = new JPanel(new BorderLayout(10, 0));
        f.setBackground(new Color(0x23, 0x25, 0x28));
        f.setBorder(new EmptyBorder(10, 12, 10, 12));
        f.setPreferredSize(new Dimension(235, 58));

        // Avatar com ponto verde de "online"
        JPanel ap = new JPanel(null);
        ap.setPreferredSize(new Dimension(40, 40));
        ap.setBackground(new Color(0x23, 0x25, 0x28));

        avatarLabel = new JLabel("?", SwingConstants.CENTER);
        avatarLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        avatarLabel.setForeground(Color.WHITE);
        avatarLabel.setBackground(new Color(0x5C, 0x5E, 0x6A));
        avatarLabel.setOpaque(true);
        avatarLabel.setBounds(0, 0, 32, 32);

        JLabel dot = new JLabel();
        dot.setBackground(GREEN);
        dot.setOpaque(true);
        dot.setBounds(22, 22, 10, 10);

        ap.add(avatarLabel);
        ap.add(dot);

        JPanel info = new JPanel(new GridLayout(2, 1, 0, 1));
        info.setBackground(new Color(0x23, 0x25, 0x28));

        statusLabel = new JLabel("Desconectado");
        statusLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        statusLabel.setForeground(TEXT_BRIGHT);

        JLabel subStatus = new JLabel("Online");
        subStatus.setFont(F_SMALL);
        subStatus.setForeground(GREEN);

        info.add(statusLabel);
        info.add(subStatus);

        f.add(ap,   BorderLayout.WEST);
        f.add(info, BorderLayout.CENTER);
        return f;
    }

    // ─── Painel Central (Chat) ─────────────────────────────────────────────────

    private JPanel buildCenterPanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(BG_MEDIUM);

        p.add(buildChannelHeader(), BorderLayout.NORTH);
        p.add(buildMessageArea(),   BorderLayout.CENTER);
        p.add(buildInputArea(),     BorderLayout.SOUTH);
        return p;
    }

    private JPanel buildChannelHeader() {
        JPanel h = new JPanel(new BorderLayout(0, 3));
        h.setBackground(BG_MEDIUM);
        h.setBorder(new CompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(0x1E, 0x1F, 0x22)),
            new EmptyBorder(14, 20, 14, 20)
        ));

        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        row.setBackground(BG_MEDIUM);

        JLabel hash = new JLabel("#");
        hash.setFont(new Font("Segoe UI", Font.BOLD, 20));
        hash.setForeground(TEXT_MUTED);

        channelTitleLabel = new JLabel("Selecione um canal");
        channelTitleLabel.setFont(F_HEAD);
        channelTitleLabel.setForeground(TEXT_BRIGHT);

        row.add(hash);
        row.add(channelTitleLabel);

        channelDescLabel = new JLabel("Entre em um canal para começar a conversar");
        channelDescLabel.setFont(F_SMALL);
        channelDescLabel.setForeground(TEXT_MUTED);

        h.add(row,              BorderLayout.NORTH);
        h.add(channelDescLabel, BorderLayout.CENTER);
        return h;
    }

    private JScrollPane buildMessageArea() {
        messageArea = new JPanel();
        messageArea.setLayout(new BoxLayout(messageArea, BoxLayout.Y_AXIS));
        messageArea.setBackground(BG_MEDIUM);
        messageArea.setBorder(new EmptyBorder(12, 16, 8, 16));

        msgScrollPane = new JScrollPane(messageArea);
        msgScrollPane.setBorder(null);
        msgScrollPane.getViewport().setBackground(BG_MEDIUM);
        styleScrollBar(msgScrollPane);
        return msgScrollPane;
    }

    private JPanel buildInputArea() {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(BG_MEDIUM);
        wrapper.setBorder(new EmptyBorder(0, 16, 20, 16));

        JPanel box = new JPanel(new BorderLayout(10, 0));
        box.setBackground(BG_INPUT);
        box.setBorder(new CompoundBorder(
            new LineBorder(new Color(0x1E, 0x1F, 0x22), 1),
            new EmptyBorder(10, 16, 10, 10)
        ));

        inputField = new JTextField();
        inputField.setFont(F_INPUT);
        inputField.setForeground(TEXT_MUTED);
        inputField.setBackground(BG_INPUT);
        inputField.setCaretColor(TEXT_BRIGHT);
        inputField.setBorder(null);
        inputField.setEnabled(false);
        inputField.setText("Selecione um canal para escrever...");

        inputField.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) {
                if (inputField.getForeground() == TEXT_MUTED) {
                    inputField.setText("");
                    inputField.setForeground(TEXT_INPUT);
                }
            }
        });
        inputField.addActionListener(e -> sendMessage());

        sendButton = styledButton("➤", ACCENT, ACCENT_HOV);
        sendButton.setEnabled(false);
        sendButton.addActionListener(e -> sendMessage());

        box.add(inputField, BorderLayout.CENTER);
        box.add(sendButton, BorderLayout.EAST);

        wrapper.add(box, BorderLayout.CENTER);
        return wrapper;
    }

    // ─── Painel Direito (Membros) ─────────────────────────────────────────────

    private JPanel buildRightPanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setPreferredSize(new Dimension(200, 0));
        p.setBackground(BG_DARK);

        JLabel title = new JLabel("  MEMBROS ONLINE");
        title.setFont(F_LABEL);
        title.setForeground(TEXT_MUTED);
        title.setBorder(new EmptyBorder(16, 12, 10, 8));

        memberListPanel = new JPanel();
        memberListPanel.setLayout(new BoxLayout(memberListPanel, BoxLayout.Y_AXIS));
        memberListPanel.setBackground(BG_DARK);
        memberListPanel.setBorder(new EmptyBorder(0, 8, 8, 8));

        JScrollPane scroll = new JScrollPane(memberListPanel);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(BG_DARK);
        styleScrollBar(scroll);

        p.add(title, BorderLayout.NORTH);
        p.add(scroll, BorderLayout.CENTER);
        return p;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  DIÁLOGO DE LOGIN
    // ═══════════════════════════════════════════════════════════════════════════

    private void showLoginDialog() {
        JDialog dlg = new JDialog(this, "Conectar ao Servidor", true);
        dlg.setSize(420, 370);
        dlg.setLocationRelativeTo(this);
        dlg.setResizable(false);
        dlg.getContentPane().setBackground(BG_DARK);
        dlg.setLayout(new BorderLayout());

        // Título
        JLabel title = new JLabel("Entrar no DiscordLP2", SwingConstants.CENTER);
        title.setFont(new Font("Segoe UI", Font.BOLD, 22));
        title.setForeground(TEXT_BRIGHT);
        title.setBorder(new EmptyBorder(28, 0, 4, 0));

        JLabel subtitle = new JLabel("Programação Concorrente & Distribuída", SwingConstants.CENTER);
        subtitle.setFont(F_SMALL);
        subtitle.setForeground(TEXT_MUTED);
        subtitle.setBorder(new EmptyBorder(0, 0, 20, 0));

        JPanel titlePanel = new JPanel(new BorderLayout());
        titlePanel.setBackground(BG_DARK);
        titlePanel.add(title, BorderLayout.NORTH);
        titlePanel.add(subtitle, BorderLayout.CENTER);

        // Formulário
        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(BG_DARK);
        form.setBorder(new EmptyBorder(0, 40, 0, 40));

        GridBagConstraints g = new GridBagConstraints();
        g.fill = GridBagConstraints.HORIZONTAL;
        g.weightx = 1.0;
        g.gridx = 0;
        g.insets = new Insets(3, 0, 3, 0);

        JTextField hostField = formField("localhost");
        JTextField portField = formField("12345");
        JTextField nickField = formField("MeuNick");

        g.gridy = 0; form.add(formLabel("SERVIDOR"),  g);
        g.gridy = 1; form.add(hostField, g);
        g.gridy = 2; form.add(formLabel("PORTA"),     g);
        g.gridy = 3; form.add(portField, g);
        g.gridy = 4; form.add(formLabel("NICKNAME"),  g);
        g.gridy = 5; form.add(nickField,  g);

        // Mensagem de erro
        JLabel errLabel = new JLabel(" ", SwingConstants.CENTER);
        errLabel.setFont(F_SMALL);
        errLabel.setForeground(new Color(0xED, 0x43, 0x45));

        // Botão
        JButton btn = styledButton("Entrar no Servidor", ACCENT, ACCENT_HOV);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btn.setPreferredSize(new Dimension(340, 42));

        JPanel south = new JPanel(new BorderLayout(0, 8));
        south.setBackground(BG_DARK);
        south.setBorder(new EmptyBorder(12, 40, 28, 40));
        south.add(errLabel, BorderLayout.NORTH);
        south.add(btn,      BorderLayout.CENTER);

        dlg.add(titlePanel, BorderLayout.NORTH);
        dlg.add(form,       BorderLayout.CENTER);
        dlg.add(south,      BorderLayout.SOUTH);

        btn.addActionListener(e -> {
            String host = hostField.getText().trim();
            String port = portField.getText().trim();
            String nick = nickField.getText().trim();

            if (host.isEmpty() || port.isEmpty() || nick.isEmpty()) {
                errLabel.setText("⚠  Preencha todos os campos.");
                return;
            }
            if (nick.contains(" ") || nick.length() < 2 || nick.length() > 20) {
                errLabel.setText("⚠  Nickname inválido (2-20 chars, sem espaços).");
                return;
            }
            int portNum;
            try { portNum = Integer.parseInt(port); }
            catch (NumberFormatException ex) { errLabel.setText("⚠  Porta inválida."); return; }

            btn.setText("Conectando...");
            btn.setEnabled(false);
            errLabel.setText(" ");

            // Conecta em thread separada para não bloquear a EDT
            int finalPort = portNum;
            new Thread(() -> {
                try {
                    client = new ChatClient();
                    client.connect(host, finalPort, nick, this::onMessageReceived, this::onDisconnected);

                    SwingUtilities.invokeLater(() -> {
                        dlg.dispose();
                        setTitle("DiscordLP2 — " + nick);
                        statusLabel.setText(nick);
                        avatarLabel.setText(nick.substring(0, 1).toUpperCase());
                        avatarLabel.setBackground(colorFor(nick));
                        // Solicita a lista de canais ao servidor
                        client.sendCommand("/channels");
                        addSystemMessage("🎉 Conectado! Clique em um canal para participar.");
                    });
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> {
                        errLabel.setText("⚠  " + ex.getMessage());
                        btn.setText("Entrar no Servidor");
                        btn.setEnabled(true);
                    });
                }
            }, "ConnectThread").start();
        });

        dlg.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { System.exit(0); }
        });

        // Deixa a janela principal visível atrás
        setVisible(true);
        dlg.setVisible(true);
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
     *   clássico de concorrência em GUIs: "apenas a EDT modifica componentes".
     *
     *   Sem isso, duas threads modificariam os componentes ao mesmo tempo,
     *   causando race conditions: tela corrompida, ArrayIndexOutOfBounds etc.
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
                } else {
                    addSystemMessage(content);
                }
                break;

            case Message.TYPE_JOIN:
                if ("SERVER".equals(msg.getAuthor()) && msg.getContent().startsWith("Bem-vindo")) {
                    // É nossa mensagem de boas-vindas = entramos no canal
                    currentChannel = msg.getChannel();
                    channelTitleLabel.setText(msg.getChannel());
                    String desc = msg.getContent()
                        .replace("Bem-vindo ao #" + msg.getChannel() + "! ", "");
                    channelDescLabel.setText(desc);
                    inputField.setEnabled(true);
                    inputField.setForeground(TEXT_INPUT);
                    inputField.setText("");
                    sendButton.setEnabled(true);
                    highlightChannel(msg.getChannel());
                } else {
                    addSystemMessage("👋 " + msg.getContent());
                }
                break;

            case Message.TYPE_LEAVE:
                addSystemMessage("🚪 " + msg.getContent());
                break;
        }
    }

    /** Chamado quando a conexão cai. Executa em thread de rede → invokeLater. */
    private void onDisconnected() {
        SwingUtilities.invokeLater(() -> {
            addSystemMessage("❌ Conexão com o servidor encerrada.");
            statusLabel.setText("Desconectado");
            inputField.setEnabled(false);
            sendButton.setEnabled(false);
        });
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  ENVIO DE MENSAGENS
    // ═══════════════════════════════════════════════════════════════════════════

    private void sendMessage() {
        String text = inputField.getText().trim();
        if (text.isEmpty() || client == null || !client.isConnected()) return;

        if (text.startsWith("/")) {
            client.sendCommand(text);
            addSystemMessage("→ " + text);
        } else {
            // Exibe imediatamente na própria tela (sem esperar eco do servidor)
            Message self = new Message(Message.TYPE_CHAT, client.getUsername(), currentChannel, text);
            addChatBubble(self);
            client.sendChatMessage(text);
        }
        inputField.setText("");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  EXIBIÇÃO DE MENSAGENS
    // ═══════════════════════════════════════════════════════════════════════════

    private void addChatBubble(Message msg) {
        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setBackground(BG_MEDIUM);
        row.setBorder(new EmptyBorder(5, 4, 5, 8));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        // Avatar com a inicial do autor
        JLabel avatar = new JLabel(msg.getAuthor().substring(0, 1).toUpperCase(), SwingConstants.CENTER);
        avatar.setFont(new Font("Segoe UI", Font.BOLD, 13));
        avatar.setForeground(Color.WHITE);
        avatar.setBackground(colorFor(msg.getAuthor()));
        avatar.setOpaque(true);
        avatar.setPreferredSize(new Dimension(36, 36));
        avatar.setMinimumSize(new Dimension(36, 36));
        avatar.setMaximumSize(new Dimension(36, 36));

        // Conteúdo: linha de cabeçalho + texto
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

        JTextArea txt = new JTextArea(msg.getContent());
        txt.setFont(F_TEXT);
        txt.setForeground(TEXT_BRIGHT);
        txt.setBackground(BG_MEDIUM);
        txt.setEditable(false);
        txt.setLineWrap(true);
        txt.setWrapStyleWord(true);
        txt.setBorder(null);

        content.add(header, BorderLayout.NORTH);
        content.add(txt,    BorderLayout.CENTER);

        row.add(avatar,  BorderLayout.WEST);
        row.add(content, BorderLayout.CENTER);

        // Hover sutil
        Color hov = new Color(0x35, 0x37, 0x3C);
        row.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { deepBg(row, hov); }
            @Override public void mouseExited (MouseEvent e) { deepBg(row, BG_MEDIUM); }
        });

        appendToChat(row);
    }

    private void addSystemMessage(String text) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row.setBackground(BG_MEDIUM);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));

        JLabel lbl = new JLabel(text);
        lbl.setFont(F_SMALL);
        lbl.setForeground(MSG_SYSTEM);

        row.add(Box.createHorizontalStrut(46));
        row.add(lbl);
        appendToChat(row);
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
        JButton btn = new JButton("# " + name);
        btn.setFont(F_CHAN);
        btn.setForeground(TEXT_MUTED);
        btn.setBackground(BG_DARK);
        btn.setBorder(new EmptyBorder(7, 10, 7, 10));
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
                // Limpa a área de chat antes de entrar no novo canal
                messageArea.removeAll();
                messageArea.revalidate();
                messageArea.repaint();
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
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  UTILITÁRIOS DE COMPONENTES
    // ═══════════════════════════════════════════════════════════════════════════

    private JButton styledButton(String text, Color bg, Color hov) {
        JButton b = new JButton(text);
        b.setBackground(bg);
        b.setForeground(Color.WHITE);
        b.setFont(F_INPUT);
        b.setBorder(new EmptyBorder(6, 14, 6, 14));
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { b.setBackground(hov); }
            @Override public void mouseExited (MouseEvent e) { b.setBackground(bg);  }
        });
        return b;
    }

    private JTextField formField(String dflt) {
        JTextField f = new JTextField(dflt);
        f.setFont(F_INPUT);
        f.setForeground(TEXT_BRIGHT);
        f.setBackground(BG_INPUT);
        f.setCaretColor(TEXT_BRIGHT);
        f.setBorder(new CompoundBorder(
            new LineBorder(new Color(0x04, 0x06, 0x0F), 1),
            new EmptyBorder(8, 12, 8, 12)
        ));
        f.setPreferredSize(new Dimension(340, 38));
        return f;
    }

    private JLabel formLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("Segoe UI", Font.BOLD, 10));
        l.setForeground(TEXT_MUTED);
        l.setBorder(new EmptyBorder(6, 0, 2, 0));
        return l;
    }

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

    /** Troca a cor de fundo de um contêiner e todos os seus filhos recursivamente. */
    private void deepBg(Container c, Color color) {
        c.setBackground(color);
        for (Component child : c.getComponents()) {
            if (child instanceof Container) deepBg((Container) child, color);
        }
    }

    /** Estiliza a barra de scroll (discreta, no estilo escuro). */
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

package src;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

/**
 * Lógica de rede do lado do cliente.
 * Gerencia a conexão TCP com o servidor e o fluxo de mensagens.
 *
 * ─── CONCEITOS DE CONCORRÊNCIA APLICADOS ───────────────────────────────────
 *
 * 1. DUAS THREADS INDEPENDENTES (Reader + Sender):
 *    Conforme o padrão de Servidor Concorrente dos slides, o cliente também
 *    separa leitura e escrita em threads distintas. Sem essa separação:
 *    - Se o usuário estiver escrevendo uma mensagem, não receberia novas.
 *    - A GUI ficaria bloqueada aguardando I/O de rede (deadlock lógico).
 *
 * 2. PROCESSO COMUNICANTES (Produtor-Consumidor via BlockingQueue):
 *    - Thread de envio produz mensagens na sendQueue quando o usuário digita.
 *    - Thread SenderThread consome da sendQueue e escreve no socket.
 *    Mesma estrutura do BoundedBuffer dos slides de Semáforos.
 *
 * 3. CALLBACK THREAD-SAFE (Consumer<Message>):
 *    A thread de recepção usa um callback para notificar a GUI.
 *    A atualização da GUI ocorre via SwingUtilities.invokeLater() na ChatGUI,
 *    garantindo que modificações de componentes Swing ocorram na Event Dispatch
 *    Thread (EDT), evitando race conditions clássicos de frameworks de GUI.
 *
 * 4. volatile connected: flag de parada visível para todas as threads.
 *    Sem volatile, a JVM poderia cachear o valor e a thread leitora
 *    nunca veria a mudança (problema de visibilidade entre threads).
 * ───────────────────────────────────────────────────────────────────────────
 */
public class ChatClient {

    private Socket             socket;
    private ObjectInputStream  in;
    private ObjectOutputStream out;

    private String  username;
    private volatile boolean connected = false;

    // Fila de mensagens a serem enviadas ao servidor (Produtor-Consumidor)
    private final BlockingQueue<Message> sendQueue = new LinkedBlockingQueue<>(256);

    // Callback invocado quando uma mensagem chega do servidor (notifica a GUI)
    private Consumer<Message> onMessageReceived;

    // Callback para notificar a GUI de desconexão
    private Runnable onDisconnected;

    /**
     * Estabelece conexão TCP com o servidor.
     * new Socket(host, port) realiza bind() + connect() internamente.
     * Conforme slides de Sockets: "Socket(String, int) cria socket no cliente TCP,
     * realiza as operações bind() e connect()".
     */
    public void connect(String host, int port, String username,
                        Consumer<Message> onMsg, Runnable onDisc) throws IOException {

        this.username           = username;
        this.onMessageReceived  = onMsg;
        this.onDisconnected     = onDisc;

        // Cria socket TCP e conecta ao servidor (bind + connect implícitos)
        socket = new Socket(host, port);
        connected = true;

        // ObjectOutputStream ANTES do ObjectInputStream (evita deadlock na negociação)
        out = new ObjectOutputStream(socket.getOutputStream());
        out.flush();
        in  = new ObjectInputStream(socket.getInputStream());

        // Envia mensagem de login (primeira mensagem ao servidor)
        Message loginMsg = new Message(Message.TYPE_COMMAND, username, "", username);
        out.writeObject(loginMsg);
        out.flush();

        // Inicia as duas threads de I/O de rede
        startReceiverThread();
        startSenderThread();
    }

    /**
     * Thread RECEPTORA: fica bloqueada em readObject() esperando mensagens do servidor.
     * Quando recebe, invoca o callback (que atualiza a GUI via EDT).
     *
     * Processo independente: não depende da thread de envio.
     * Comunicante: produz eventos que a GUI consome.
     */
    private void startReceiverThread() {
        Thread receiver = new Thread(() -> {
            try {
                while (connected) {
                    // Bloqueia até receber um objeto do servidor (I/O bloqueante)
                    Message msg = (Message) in.readObject();
                    if (onMessageReceived != null) {
                        onMessageReceived.accept(msg); // notifica a GUI
                    }
                }
            } catch (EOFException | java.net.SocketException e) {
                // Servidor encerrou a conexão — comportamento normal
            } catch (IOException | ClassNotFoundException e) {
                if (connected) System.err.println("[CLIENT] Erro de recepção: " + e.getMessage());
            } finally {
                handleDisconnect();
            }
        }, "Receiver-" + username);

        receiver.setDaemon(true);
        receiver.start();
    }

    /**
     * Thread REMETENTE: consome da sendQueue e escreve no socket.
     * Equivalente à WriterThread do servidor no ClientHandler.
     *
     * take() bloqueia quando a fila está vazia (sem busy-waiting),
     * análogo ao P(full) do Produtor-Consumidor dos slides.
     */
    private void startSenderThread() {
        Thread sender = new Thread(() -> {
            try {
                while (connected) {
                    // P(full): bloqueia até haver mensagem para enviar
                    Message msg = sendQueue.take();

                    // Poison pill para parar a thread
                    if ("__STOP__".equals(msg.getChannel())) break;

                    out.writeObject(msg);
                    out.flush();
                    out.reset(); // reset do cache do ObjectOutputStream
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (IOException e) {
                if (connected) System.err.println("[CLIENT] Erro de envio: " + e.getMessage());
            }
        }, "Sender-" + username);

        sender.setDaemon(true);
        sender.start();
    }

    /**
     * Enfileira uma mensagem de chat para envio ao servidor.
     * Chamado pela GUI (Event Dispatch Thread) — não bloqueia a UI.
     *
     * offer() é thread-safe internamente (equivalente ao V(empty) do Produtor).
     */
    public void sendChatMessage(String text) {
        if (!connected) return;
        Message msg = new Message(Message.TYPE_CHAT, username, "", text);
        sendQueue.offer(msg);
    }

    /**
     * Enfileira um comando para envio ao servidor (ex: /join geral).
     */
    public void sendCommand(String command) {
        if (!connected) return;
        Message msg = new Message(Message.TYPE_COMMAND, username, "", command);
        sendQueue.offer(msg);
    }

    /**
     * Encerra a conexão graciosamente.
     */
    public void disconnect() {
        connected = false;
        // Poison pill para parar a SenderThread
        sendQueue.offer(new Message(Message.TYPE_SYSTEM, "SYSTEM", "__STOP__", ""));
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
    }

    /**
     * Chamado pelas threads quando a conexão é perdida inesperadamente.
     */
    private void handleDisconnect() {
        if (!connected) return;
        connected = false;
        if (onDisconnected != null) onDisconnected.run();
    }

    public boolean isConnected() { return connected; }
    public String  getUsername() { return username; }
}

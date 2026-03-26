package src;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Handler dedicado a um único cliente conectado.
 * Instanciado pelo Server para cada nova conexão aceita via accept().
 *
 * ─── CONCEITOS DE CONCORRÊNCIA APLICADOS ───────────────────────────────────
 *
 * 1. PROCESSOS COMUNICANTES (Producer-Consumer com BlockingQueue):
 *    Este handler usa DUAS threads internas:
 *    - Thread LEITORA (run()): lê mensagens do socket (dados chegando da rede).
 *      Funciona como CONSUMIDORA: consome dados do socket TCP e os processa.
 *    - Thread ESCRITORA (WriterThread): escreve mensagens na fila de saída.
 *      Funciona como PRODUTORA do ponto de vista do socket de saída.
 *
 *    A BlockingQueue funciona como BUFFER LIMITADO (Bounded Buffer) clássico
 *    dos slides de Semáforos / Produtor-Consumidor. Internamente ela usa
 *    locks e condições para bloquear quando cheia ou vazia, evitando
 *    espera ocupada (busy-waiting). É o equivalente moderno dos Semáforos
 *    empty/full do exemplo dos slides.
 *
 * 2. THREAD POR CLIENTE (Servidor Concorrente Multi-Threaded):
 *    Conforme slides de Sockets (Servidor Concorrente), cada cliente tem
 *    sua própria thread de atendimento. O servidor principal apenas aceita
 *    conexões e delega o processamento ao ClientHandler + thread pool.
 *    Sem isso, o servidor seria iterativo e atenderia apenas um cliente por vez.
 *
 * 3. SEÇÃO CRÍTICA (synchronized na variável currentChannel):
 *    O canal atual do usuário é um estado compartilhado que pode ser lido
 *    pela thread de broadcast do Channel e modificado pela thread leitora.
 *    O acesso é sincronizado para evitar race condition.
 * ───────────────────────────────────────────────────────────────────────────
 */
public class ClientHandler implements Runnable {

    private final Socket socket;
    private final Server server;

    private ObjectInputStream  in;
    private ObjectOutputStream out;

    private String  username;
    private Channel currentChannel; // estado compartilhado, acesso sincronizado

    /**
     * BUFFER BOUNDED (Produtor-Consumidor):
     * Fila bloqueante de tamanho limitado para mensagens de saída.
     * sendMessage() (chamado por outras threads) produz na fila.
     * WriterThread consome da fila e escreve no socket.
     *
     * Isso desacopla a escrita no socket da lógica de broadcast:
     * - O broadcast não bloqueia esperando o socket do cliente ficar livre.
     * - Se o cliente ficar lento, a fila enche e a operação retorna imediatamente
     *   (oferta sem bloqueio), protegendo o servidor de clientes lentos.
     *
     * Capacidade 128: semelhante ao BoundedBuffer dos slides (array de tamanho N).
     */
    private final BlockingQueue<Message> outQueue = new LinkedBlockingQueue<>(128);

    // Sinal de parada para a WriterThread (equivalente à interrupção de thread dos slides)
    private volatile boolean running = true;

    public ClientHandler(Socket socket, Server server) {
        this.socket = socket;
        this.server = server;
    }

    /**
     * Método run() da thread LEITORA.
     * Lê mensagens do socket (ObjectInputStream) e processa em loop.
     * Bloqueia em readObject() enquanto não há dados (I/O bloqueante, sem busy-waiting).
     *
     * Conforme slides de Threads: o método run() é executado quando t.start() é invocado.
     */
    @Override
    public void run() {
        try {
            // ObjectOutputStream DEVE ser criado antes do ObjectInputStream
            // para evitar deadlock na negociação inicial de streams Java.
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush(); // envia o cabeçalho do stream imediatamente
            in  = new ObjectInputStream(socket.getInputStream());

            // Primeira mensagem: autenticação / escolha de username
            Message loginMsg = (Message) in.readObject();
            this.username = loginMsg.getContent().trim();

            // Registra o cliente no servidor e inicia a WriterThread
            server.registerClient(this);

            // Inicia a THREAD ESCRITORA (consumidora da outQueue)
            Thread writer = new Thread(new WriterThread(), "Writer-" + username);
            writer.setDaemon(true); // daemon: não impede o encerramento da JVM
            writer.start();

            // Notifica o servidor da chegada de um novo usuário
            server.systemMessage(username + " entrou no servidor.");

            // ─── Loop principal de leitura ───────────────────────────────
            Message msg;
            while (running && (msg = (Message) in.readObject()) != null) {
                processMessage(msg);
            }

        } catch (EOFException | java.net.SocketException e) {
            // Conexão encerrada pelo cliente — comportamento normal (não é erro)
        } catch (IOException | ClassNotFoundException e) {
            if (running) System.err.println("[SERVER] Erro com " + username + ": " + e.getMessage());
        } finally {
            disconnect();
        }
    }

    /**
     * Processa uma mensagem recebida do cliente.
     * Decide se é um comando ou uma mensagem de chat e age adequadamente.
     */
    private void processMessage(Message msg) {
        switch (msg.getType()) {

            case Message.TYPE_COMMAND:
                handleCommand(msg.getContent());
                break;

            case Message.TYPE_CHAT:
                // Delega o broadcast ao canal atual (monitor do Channel)
                synchronized (this) {
                    if (currentChannel != null) {
                        currentChannel.broadcast(msg, this);
                    } else {
                        sendMessage(new Message(
                            Message.TYPE_SYSTEM, "SERVER", "",
                            "Você não está em nenhum canal. Use /join <canal>"
                        ));
                    }
                }
                break;
        }
    }

    /**
     * Interpreta e executa comandos enviados pelo cliente (ex: /join, /leave, /users).
     */
    private void handleCommand(String cmd) {
        String[] parts = cmd.split(" ", 2);
        String command = parts[0].toLowerCase();

        switch (command) {

            case "/join": {
                // Comando para entrar em um canal
                if (parts.length < 2) {
                    sendMessage(new Message(Message.TYPE_SYSTEM, "SERVER", "", "Uso: /join <canal>"));
                    return;
                }
                String channelName = parts[1].trim();
                server.joinChannel(this, channelName);
                break;
            }

            case "/leave": {
                // Comando para sair do canal atual
                synchronized (this) {
                    if (currentChannel != null) {
                        server.leaveChannel(this, currentChannel.getName());
                    } else {
                        sendMessage(new Message(Message.TYPE_SYSTEM, "SERVER", "", "Você não está em nenhum canal."));
                    }
                }
                break;
            }

            case "/channels": {
                // Lista todos os canais disponíveis
                String list = server.getChannelList();
                sendMessage(new Message(Message.TYPE_SYSTEM, "SERVER", "", list));
                break;
            }

            case "/users": {
                // Lista usuários do canal atual
                synchronized (this) {
                    if (currentChannel != null) {
                        String users = String.join(", ", currentChannel.getMemberNames());
                        sendMessage(new Message(Message.TYPE_SYSTEM, "SERVER", currentChannel.getName(),
                            "Usuários em #" + currentChannel.getName() + ": " + users));
                    } else {
                        sendMessage(new Message(Message.TYPE_SYSTEM, "SERVER", "", "Você não está em nenhum canal."));
                    }
                }
                break;
            }

            default:
                sendMessage(new Message(Message.TYPE_SYSTEM, "SERVER", "", "Comando desconhecido: " + command));
        }
    }

    /**
     * Enfileira uma mensagem para envio ao cliente (não bloqueia o chamador).
     * É chamado por outras threads (e.g., thread de broadcast do Channel).
     *
     * SEÇÃO CRÍTICA INTERNA da BlockingQueue: o offer() é thread-safe,
     * implementado com lock interno, equivalente ao P(empty) do Produtor.
     *
     * Se a fila estiver cheia (cliente lento), a mensagem é descartada
     * para proteger o servidor de um cliente lento travando outros.
     */
    public void sendMessage(Message msg) {
        if (!outQueue.offer(msg)) {
            // Fila cheia: cliente não está consumindo (lento ou travado)
            System.err.println("[WARN] Fila cheia para " + username + ", descartando msg.");
        }
    }

    /**
     * Encerra a conexão e remove o cliente do servidor.
     * volatile 'running' garante visibilidade desta mudança para a WriterThread
     * (sem volatile, a JVM poderia cachear o valor no registrador da thread).
     */
    public void disconnect() {
        if (!running) return; // já desconectado
        running = false;

        // Sai do canal atual, se houver
        synchronized (this) {
            if (currentChannel != null) {
                server.leaveChannel(this, currentChannel.getName());
            }
        }

        server.unregisterClient(this);
        if (username != null) {
            server.systemMessage(username + " saiu do servidor.");
        }

        // Envenena a WriterThread com sinal de parada (padrão "poison pill")
        outQueue.offer(new Message(Message.TYPE_SYSTEM, "SYSTEM", "__STOP__", ""));

        try { socket.close(); } catch (IOException ignored) {}
    }

    // ─── Getters e setters com synchronization ──────────────────────────────

    public String getUsername() { return username; }

    public synchronized Channel getCurrentChannel() { return currentChannel; }

    public synchronized void setCurrentChannel(Channel ch) { this.currentChannel = ch; }

    // ─── Thread Escritora (Consumidora da outQueue) ──────────────────────────

    /**
     * WriterThread: thread dedicada à ESCRITA no socket deste cliente.
     *
     * PROCESSSOS COMUNICANTES (slides): esta thread e as threads de broadcast
     * são processos comunicantes — o broadcast PRODUZ mensagens na fila,
     * e a WriterThread as CONSOME. A dependência temporal é: a mensagem
     * deve ser depositada na fila ANTES de ser consumida e enviada.
     *
     * Usa blocking take() que bloqueia quando a fila está vazia,
     * equivalente a P(full) no padrão Produtor-Consumidor dos slides.
     * Isso evita busy-waiting: a thread dorme jusqu'à há dados disponíveis.
     */
    private class WriterThread implements Runnable {
        @Override
        public void run() {
            try {
                while (running) {
                    // P(full): bloqueia até haver mensagem na fila.
                    // take() libera a CPU enquanto espera (sem busy-waiting),
                    // equivalente ao sleep() do consumidor nos slides.
                    Message msg = outQueue.take();

                    // Poison pill: padrão para encerrar threads consumidoras
                    // de forma segura. Em vez de interromper abruptamente a
                    // thread, inserimos um objeto "veneno" na fila que sinaliza
                    // encerramento. A thread processa normalmente até achar o
                    // veneno e sai do loop de forma limpa.
                    if ("__STOP__".equals(msg.getChannel())) break;

                    // Serializa o objeto Message e envia pelo stream TCP
                    out.writeObject(msg);
                    out.flush();

                    // out.reset() limpa o cache interno do ObjectOutputStream.
                    // Java reutiliza referências a objetos já enviados para economizar
                    // banda. Como enviamos muitas mensagens do mesmo tipo, sem reset()
                    // o receptor receberia a MESMA referência e o conteúdo nunca mudaria.
                    out.reset();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (IOException e) {
                // Conexão fechou — encerra silenciosamente
            }
        }
    }
}

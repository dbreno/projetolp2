package src;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Servidor de Chat Concorrente — coração do sistema distribuído.
 *
 * ─── CONCEITOS DE CONCORRÊNCIA APLICADOS ───────────────────────────────────
 *
 * 1. SERVIDOR CONCORRENTE MULTI-THREADED (slides de Sockets, Servidor Concorrente):
 *    O loop principal apenas faz accept() e delega cada cliente a uma thread
 *    separada. Sem isso, seria um "Servidor Iterativo" que atende um cliente
 *    por vez — inaceitável para um chat multiusuário.
 *
 * 2. POOL DE THREADS FIXO (slides de Pool de Threads):
 *    Usamos Executors.newFixedThreadPool(MAX_THREADS) em vez de criar uma
 *    new Thread() a cada cliente. Benefícios:
 *    - Reutilização de threads (reduz overhead de criação/destruição)
 *    - Limitação de recursos: no máximo MAX_THREADS clientes simultâneos
 *    - Fila de espera automática para novos clientes se o pool estiver cheio
 *    Diretamente do exemplo newFixedThreadPool dos slides de Pool de Threads.
 *
 * 3. ESTRUTURAS THREAD-SAFE (ConcurrentHashMap):
 *    Mapas de canais e clientes são acessados por múltiplas threads.
 *    ConcurrentHashMap garante consistência interna sem bloquear todo o mapa
 *    (segmentação interna), diferente de Collections.synchronizedMap.
 *
 * 4. MONITOR (synchronized) nos métodos de gerenciamento:
 *    joinChannel/leaveChannel são sincronizados para evitar que dois clientes
 *    entrem no mesmo canal ao mesmo tempo de forma inconsistente
 *    (região crítica sobre o estado dos canais).
 *
 * 5. PROCESSO INDEPENDENTE: O loop de accept() é independente das threads
 *    de ClientHandler. Cada ClientHandler é um processo independente que
 *    não afeta os outros (exceto via memória compartilhada protegida).
 * ───────────────────────────────────────────────────────────────────────────
 */
public class Server {

    public static final int    PORT        = 12345;
    public static final int    MAX_THREADS = 100; // max de clientes concorrentes

    // Mapa thread-safe: nome do canal → objeto Channel
    private final Map<String, Channel> channels = new ConcurrentHashMap<>();

    // Set thread-safe: todos os clientes conectados
    private final Set<ClientHandler> clients = ConcurrentHashMap.newKeySet();

    /**
     * POOL DE THREADS FIXO: conforme slides de Pool de Threads.
     * Cada ClientHandler.run() é submetido como tarefa Runnable.
     * Se o pool está cheio, novas tarefas aguardam na fila interna do Executor.
     */
    private final ExecutorService threadPool = Executors.newFixedThreadPool(MAX_THREADS);

    public Server() {
        // Cria os canais padrão do servidor (inspirado no Discord)
        createChannel("geral",      "Canal principal para todos os assuntos 💬");
        createChannel("tecnologia", "Discussões sobre tecnologia e programação 💻");
        createChannel("jogos",      "Fale sobre seus jogos favoritos 🎮");
        createChannel("musica",     "Compartilhe e discuta música 🎵");
        createChannel("off-topic",  "Qualquer coisa que não se encaixa nos outros canais 🎲");
    }

    /**
     * Inicia o servidor: cria o ServerSocket e entra no loop de accept().
     *
     * ServerSocket realiza internamente bind() + listen() (conforme slides de Sockets).
     * accept() bloqueia até um cliente conectar — operação bloqueante de I/O.
     * Quando desbloqueia, retorna um novo Socket representando a conexão estabelecida.
     */
    public void start() throws IOException {
        // ServerSocket: bind na porta + listen (análogo ao socket+bind+listen do C)
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("╔════════════════════════════════════╗");
            System.out.println("║   DiscordLP2 Server — Porta " + PORT + "   ║");
            System.out.println("║   Pool de threads: " + MAX_THREADS + " threads        ║");
            System.out.println("╚════════════════════════════════════╝");

            // Loop infinito: servidor executa continuamente (conforme slides de C/S)
            while (!serverSocket.isClosed()) {
                // accept() bloqueia até receber uma requisição de conexão TCP
                // (equivalente a accept() do C nos slides de Sockets)
                Socket clientSocket = serverSocket.accept();

                System.out.println("[SERVER] Nova conexão: " + clientSocket.getRemoteSocketAddress());

                // Cria handler e submete ao pool de threads (não cria nova thread diretamente)
                // Conforme slides de Pool de Threads: exec.execute(new RunnableTask(...))
                ClientHandler handler = new ClientHandler(clientSocket, this);
                threadPool.execute(handler);
            }
        }
    }

    /**
     * Registra um novo cliente no set global de clientes.
     * ConcurrentHashMap.newKeySet() é thread-safe, não precisa de synchronized aqui.
     */
    public void registerClient(ClientHandler client) {
        clients.add(client);
        System.out.println("[SERVER] Cliente registrado: " + client.getUsername() +
                           " (total: " + clients.size() + ")");
    }

    /**
     * Remove um cliente desconectado do set global.
     */
    public void unregisterClient(ClientHandler client) {
        clients.remove(client);
        System.out.println("[SERVER] Cliente removido: " + client.getUsername() +
                           " (total: " + clients.size() + ")");
    }

    /**
     * Faz um cliente entrar em um canal.
     *
     * SEÇÃO CRÍTICA: synchronized evita que duas threads modifiquem o estado
     * do canal e do cliente simultaneamente (race condition no join + setCurrentChannel).
     */
    public synchronized void joinChannel(ClientHandler client, String channelName) {
        Channel channel = channels.get(channelName);

        if (channel == null) {
            client.sendMessage(new Message(
                Message.TYPE_SYSTEM, "SERVER", "",
                "Canal #" + channelName + " não existe. Use /channels para ver a lista."
            ));
            return;
        }

        // Se já está no canal, ignora
        if (channel.equals(client.getCurrentChannel())) {
            client.sendMessage(new Message(
                Message.TYPE_SYSTEM, "SERVER", channelName,
                "Você já está no canal #" + channelName
            ));
            return;
        }

        // Sai do canal atual, se houver
        Channel prev = client.getCurrentChannel();
        if (prev != null) {
            prev.leave(client);
            Message leaveMsg = new Message(
                Message.TYPE_LEAVE, "SERVER", prev.getName(),
                client.getUsername() + " saiu do canal."
            );
            prev.broadcastAll(leaveMsg);
            client.setCurrentChannel(null);
        }

        // Tenta entrar no novo canal (semáforo interno do Channel)
        try {
            boolean joined = channel.join(client);
            if (!joined) {
                client.sendMessage(new Message(
                    Message.TYPE_SYSTEM, "SERVER", "",
                    "Canal #" + channelName + " está cheio (" + Channel.MAX_MEMBERS + " membros máx)."
                ));
                return;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        client.setCurrentChannel(channel);

        // Envia histórico do canal ao novo membro
        for (Message histMsg : channel.getHistory()) {
            client.sendMessage(histMsg);
        }

        // Notifica entrada ao novo membro
        client.sendMessage(new Message(
            Message.TYPE_JOIN, "SERVER", channelName,
            "Bem-vindo ao #" + channelName + "! " + channel.getDescription()
        ));

        // Notifica os outros membros
        Message joinNotify = new Message(
            Message.TYPE_JOIN, "SERVER", channelName,
            client.getUsername() + " entrou no canal. 👋"
        );
        channel.broadcast(joinNotify, client);
    }

    /**
     * Faz um cliente sair de um canal.
     */
    public synchronized void leaveChannel(ClientHandler client, String channelName) {
        Channel channel = channels.get(channelName);
        if (channel == null) return;

        channel.leave(client);
        client.setCurrentChannel(null);

        Message leaveMsg = new Message(
            Message.TYPE_LEAVE, "SERVER", channelName,
            client.getUsername() + " saiu do canal."
        );
        channel.broadcastAll(leaveMsg);

        client.sendMessage(new Message(
            Message.TYPE_SYSTEM, "SERVER", "",
            "Você saiu do canal #" + channelName
        ));
    }

    /**
     * Envia uma mensagem de sistema para todos os clientes conectados.
     * Usado para notificações globais (entrada/saída do servidor).
     */
    public void systemMessage(String text) {
        System.out.println("[SISTEMA] " + text);
        Message msg = new Message(Message.TYPE_SYSTEM, "SERVER", "", "🔔 " + text);
        for (ClientHandler client : clients) {
            client.sendMessage(msg);
        }
    }

    /**
     * Retorna uma string formatada com a lista de canais disponíveis.
     */
    public String getChannelList() {
        StringBuilder sb = new StringBuilder("📋 Canais disponíveis:\n");
        for (Channel ch : channels.values()) {
            sb.append("  #").append(ch.getName())
              .append(" (").append(ch.getMemberCount()).append("/").append(Channel.MAX_MEMBERS).append(")")
              .append(" — ").append(ch.getDescription()).append("\n");
        }
        return sb.toString().trim();
    }

    /**
     * Cria um novo canal e o registra no mapa de canais.
     */
    private void createChannel(String name, String description) {
        channels.put(name, new Channel(name, description));
        System.out.println("[SERVER] Canal criado: #" + name);
    }

    // ─── Ponto de entrada ────────────────────────────────────────────────────

    public static void main(String[] args) {
        Server server = new Server();
        try {
            server.start();
        } catch (IOException e) {
            System.err.println("[SERVER] Erro fatal: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

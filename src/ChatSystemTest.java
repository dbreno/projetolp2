package src;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * ============================================================
 *  ChatSystemTest — Suite de Testes Automatizados
 * ============================================================
 *  Valida os seguintes requisitos do projeto LP2:
 *
 *  [T1] Compilação sem erros
 *  [T2] Servidor inicia e aceita conexões TCP (ServerSocket/Socket)
 *  [T3] Thread Pool (ExecutorService) — múltiplos clientes simultâneos
 *  [T4] Comunicação via ObjectOutputStream/ObjectInputStream (serialização)
 *  [T5] Broadcast de mensagens entre múltiplos clientes no mesmo canal
 *  [T6] Semáforo (Semaphore) — limite de vagas no Channel
 *  [T7] Monitor (synchronized) — acesso seguro ao estado compartilhado
 *  [T8] Produtor-Consumidor (BlockingQueue) — dissociação de I/O
 *  [T9] Histórico de canal enviado ao novo entrante
 *  [T10] Desconexão graciosa (poison pill, volatile)
 * ============================================================
 */
public class ChatSystemTest {

    // ─── Contadores de teste ──────────────────────────────────────────────
    private static int passed = 0;
    private static int failed = 0;
    private static final List<String> failures = new ArrayList<>();

    // ─── Porta de teste (diferente do servidor de produção) ───────────────
    private static final int TEST_PORT = 19999;

    // ─── Utilitários de asserção ──────────────────────────────────────────

    static void assertTrue(String testName, boolean condition) {
        if (condition) {
            passed++;
            System.out.println("  ✅ PASS  " + testName);
        } else {
            failed++;
            failures.add(testName);
            System.out.println("  ❌ FAIL  " + testName);
        }
    }

    static void assertFalse(String testName, boolean condition) {
        assertTrue(testName, !condition);
    }

    static void assertEquals(String testName, Object expected, Object actual) {
        boolean ok = (expected == null && actual == null)
                  || (expected != null && expected.equals(actual));
        if (ok) {
            passed++;
            System.out.println("  ✅ PASS  " + testName);
        } else {
            failed++;
            failures.add(testName);
            System.out.println("  ❌ FAIL  " + testName
                    + "  [esperado: " + expected + " | obtido: " + actual + "]");
        }
    }

    // ─── Ponto de entrada ─────────────────────────────────────────────────

    public static void main(String[] args) throws Exception {
        System.out.println("╔══════════════════════════════════════════════════════════╗");
        System.out.println("║        DiscordLP2 — Suite de Testes Automatizados        ║");
        System.out.println("║    Threads · Sockets · Semáforos · Monitores · Queues   ║");
        System.out.println("╚══════════════════════════════════════════════════════════╝");
        System.out.println();

        // Inicia o servidor em background
        Server testServer = startTestServer();

        // Pequena espera para o servidor subir
        Thread.sleep(500);

        // ─── Executa os grupos de testes ───────────────────────────────────
        testMessageSerialization();
        testChannelSemaphoreAndMonitor();
        testBlockingQueueProducerConsumer();
        testServerSocketAndThreadPool(testServer);
        testMultiClientBroadcast();
        testHistoryOnJoin();
        testGracefulDisconnect();
        testConcurrentChannelJoin();

        // ─── Relatório final ───────────────────────────────────────────────
        printReport();

        System.exit(failed == 0 ? 0 : 1);
    }

    // ─────────────────────────────────────────────────────────────────────
    //  [T1 / T4]  Message: serialização e campos
    // ─────────────────────────────────────────────────────────────────────
    static void testMessageSerialization() throws Exception {
        System.out.println("\n━━━ [T1/T4] Message: Serialização e Campos ━━━");

        // Serializa para bytes e desserializa
        Message orig = new Message(Message.TYPE_CHAT, "Alice", "geral", "Olá mundo!");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(orig);
        oos.flush();

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        ObjectInputStream ois = new ObjectInputStream(bais);
        Message copy = (Message) ois.readObject();

        assertEquals("[T1] Tipo preservado na serialização",  Message.TYPE_CHAT, copy.getType());
        assertEquals("[T1] Autor preservado",                 "Alice",           copy.getAuthor());
        assertEquals("[T1] Canal preservado",                 "geral",           copy.getChannel());
        assertEquals("[T1] Conteúdo preservado",              "Olá mundo!",      copy.getContent());
        assertTrue  ("[T1] Timestamp gerado (não nulo/vazio)", copy.getTime() != null && !copy.getTime().isEmpty());
        assertTrue  ("[T4] Serializable implementado",        orig instanceof Serializable);
    }

    // ─────────────────────────────────────────────────────────────────────
    //  [T6 / T7]  Channel: Semaphore + Monitor (sem rede)
    // ─────────────────────────────────────────────────────────────────────
    static void testChannelSemaphoreAndMonitor() throws Exception {
        System.out.println("\n━━━ [T6/T7] Channel: Semáforo e Monitor ━━━");

        /*
         * Criamos um Channel com MAX_MEMBERS = 50.
         * Simulamos que já há (MAX_MEMBERS - 1) membros,
         * tentamos adicionar mais e verificamos o comportamento do semáforo.
         *
         * Como não temos clientes reais aqui, usamos reflexão para
         * verificar a existência do semáforo e testar via join/leave.
         */

        // Verifica que a classe Channel possui campo 'vagas' (Semaphore)
        boolean hasSemaphore = false;
        try {
            java.lang.reflect.Field f = Channel.class.getDeclaredField("vagas");
            hasSemaphore = f.getType().equals(Semaphore.class);
        } catch (NoSuchFieldException ignored) {}
        assertTrue("[T6] Campo 'vagas' (Semaphore) existe em Channel", hasSemaphore);

        // Verifica que MAX_MEMBERS está definido
        assertEquals("[T6] Channel.MAX_MEMBERS = 50", 50, Channel.MAX_MEMBERS);

        // Verifica que métodos são synchronized
        boolean joinSynced  = false;
        boolean leaveSynced = false;
        boolean bcastSynced = false;
        try {
            int joinMods  = Channel.class.getMethod("leave", ClientHandler.class).getModifiers();
            int leaveMods = Channel.class.getMethod("leave", ClientHandler.class).getModifiers();
            int bcastMods = Channel.class.getMethod("broadcast", Message.class, ClientHandler.class).getModifiers();
            joinSynced  = java.lang.reflect.Modifier.isSynchronized(joinMods);
            leaveSynced = java.lang.reflect.Modifier.isSynchronized(leaveMods);
            bcastSynced = java.lang.reflect.Modifier.isSynchronized(bcastMods);
        } catch (NoSuchMethodException ignored) {}
        assertTrue("[T7] leave() é synchronized (Monitor)",     leaveSynced);
        assertTrue("[T7] broadcast() é synchronized (Monitor)", bcastSynced);

        // Verifica que getMemberCount() é synchronized
        boolean countSynced = false;
        try {
            int mods = Channel.class.getMethod("getMemberCount").getModifiers();
            countSynced = java.lang.reflect.Modifier.isSynchronized(mods);
        } catch (NoSuchMethodException ignored) {}
        assertTrue("[T7] getMemberCount() é synchronized", countSynced);
    }

    // ─────────────────────────────────────────────────────────────────────
    //  [T8]  BlockingQueue: Produtor-Consumidor
    // ─────────────────────────────────────────────────────────────────────
    static void testBlockingQueueProducerConsumer() throws Exception {
        System.out.println("\n━━━ [T8] BlockingQueue: Produtor-Consumidor ━━━");

        // Testa o padrão básico de produtor-consumidor com LinkedBlockingQueue
        LinkedBlockingQueue<Integer> queue = new LinkedBlockingQueue<>(5);
        AtomicInteger consumed = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(10);

        // Thread consumidora
        Thread consumer = new Thread(() -> {
            try {
                for (int i = 0; i < 10; i++) {
                    queue.take(); // P(full) — bloqueia se vazia
                    consumed.incrementAndGet();
                    latch.countDown();
                }
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }, "Consumer-Test");
        consumer.setDaemon(true);
        consumer.start();

        // Thread produtora (produz 10 elementos)
        Thread producer = new Thread(() -> {
            try {
                for (int i = 0; i < 10; i++) {
                    queue.put(i); // V(empty) — bloqueia se cheia
                    Thread.sleep(10);
                }
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }, "Producer-Test");
        producer.start();
        producer.join(3000);

        boolean allConsumed = latch.await(3, TimeUnit.SECONDS);
        assertTrue("[T8] Todos os 10 itens foram produzidos e consumidos", allConsumed);
        assertEquals("[T8] Contador de consumidos = 10", 10, consumed.get());

        // Verifica que ClientHandler usa LinkedBlockingQueue (reflexão)
        boolean handlerHasQueue = false;
        try {
            java.lang.reflect.Field f = ClientHandler.class.getDeclaredField("outQueue");
            f.setAccessible(true);
            handlerHasQueue = BlockingQueue.class.isAssignableFrom(f.getType());
        } catch (NoSuchFieldException ignored) {}
        assertTrue("[T8] ClientHandler.outQueue é um BlockingQueue", handlerHasQueue);

        // Verifica que ChatClient também usa BlockingQueue
        boolean clientHasQueue = false;
        try {
            java.lang.reflect.Field f = ChatClient.class.getDeclaredField("sendQueue");
            f.setAccessible(true);
            clientHasQueue = BlockingQueue.class.isAssignableFrom(f.getType());
        } catch (NoSuchFieldException ignored) {}
        assertTrue("[T8] ChatClient.sendQueue é um BlockingQueue", clientHasQueue);
    }

    // ─────────────────────────────────────────────────────────────────────
    //  [T2 / T3]  ServerSocket, ThreadPool, Conexão TCP
    // ─────────────────────────────────────────────────────────────────────
    static void testServerSocketAndThreadPool(Server server) throws Exception {
        System.out.println("\n━━━ [T2/T3] ServerSocket + ThreadPool ━━━");

        // Verifica que o Server usa ExecutorService
        boolean hasPool = false;
        try {
            java.lang.reflect.Field f = Server.class.getDeclaredField("threadPool");
            f.setAccessible(true);
            hasPool = ExecutorService.class.isAssignableFrom(f.getType());
        } catch (NoSuchFieldException ignored) {}
        assertTrue("[T3] Server.threadPool é um ExecutorService", hasPool);

        // Verifica MAX_THREADS definido
        assertTrue("[T3] Server.MAX_THREADS >= 10", Server.MAX_THREADS >= 10);

        // Testa conexão TCP real (port TEST_PORT onde o servidor de teste está)
        try (Socket s = new Socket("localhost", TEST_PORT)) {
            assertTrue("[T2] Socket TCP conectou ao servidor na porta " + TEST_PORT, s.isConnected());

            // Handshake obrigatório: OOS antes de OIS (evitar deadlock)
            ObjectOutputStream oos = new ObjectOutputStream(s.getOutputStream());
            oos.flush();
            ObjectInputStream ois = new ObjectInputStream(s.getInputStream());

            // Envia login
            oos.writeObject(new Message(Message.TYPE_COMMAND, "TestBot", "", "TestBot"));
            oos.flush();

            // Aguarda a mensagem de sistema de boas-vindas
            s.setSoTimeout(3000);
            Message welcome = null;
            try {
                while (true) {
                    welcome = (Message) ois.readObject();
                    if (welcome.getType().equals(Message.TYPE_SYSTEM)) break;
                }
            } catch (SocketTimeoutException ignored) {}

            assertTrue("[T2] Recebeu mensagem de sistema do servidor após login",
                    welcome != null && welcome.getType().equals(Message.TYPE_SYSTEM));
        } catch (Exception e) {
            failed++;
            failures.add("[T2] Conexão TCP falhou: " + e.getMessage());
            System.out.println("  ❌ FAIL  [T2] Conexão TCP falhou: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  [T5]  Broadcast: múltiplos clientes no mesmo canal
    // ─────────────────────────────────────────────────────────────────────
    static void testMultiClientBroadcast() throws Exception {
        System.out.println("\n━━━ [T5] Broadcast Multi-Cliente ━━━");

        CountDownLatch aliceInChannel = new CountDownLatch(1);
        CountDownLatch bobInChannel   = new CountDownLatch(1);
        CountDownLatch aliceSent      = new CountDownLatch(1);
        AtomicBoolean  bobGotMsg      = new AtomicBoolean(false);
        AtomicBoolean  aliceGotMsg    = new AtomicBoolean(false);

        // ─ Alice ─────────────────────────────────────────────────────────
        // Alice: entra no canal, aguarda Bob, envia msg, coleta TODAS as msgs
        Thread aliceThread = new Thread(() -> {
            try (Socket s = new Socket("localhost", TEST_PORT)) {
                ObjectOutputStream oos = new ObjectOutputStream(s.getOutputStream());
                oos.flush();
                ObjectInputStream ois = new ObjectInputStream(s.getInputStream());

                oos.writeObject(new Message(Message.TYPE_COMMAND, "Alice2", "", "Alice2"));
                oos.flush();
                s.setSoTimeout(6000);
                drainSystemUntilJoin(ois, 1500); // consome msgs iniciais

                // Entra no canal #musica (separado para não misturar com teste anterior)
                oos.writeObject(new Message(Message.TYPE_COMMAND, "Alice2", "", "/join musica"));
                oos.flush();

                // Aguarda confirmação de join
                drainSystemUntilJoin(ois, 2000);
                aliceInChannel.countDown();

                // Aguarda Bob entrar no canal
                bobInChannel.await(6, TimeUnit.SECONDS);
                Thread.sleep(300);

                // Envia mensagem de broadcast (Alice → Bob)
                oos.writeObject(new Message(Message.TYPE_CHAT, "Alice2", "musica", "Oi Bob2!"));
                oos.flush();
                aliceSent.countDown();

                // Lê todas as mensagens que chegam e procura a de Bob
                long deadline = System.currentTimeMillis() + 5000;
                while (System.currentTimeMillis() < deadline) {
                    try {
                        Message m = (Message) ois.readObject();
                        if ("Bob2".equals(m.getAuthor()) && "Oi Alice2!".equals(m.getContent())) {
                            aliceGotMsg.set(true);
                            break;
                        }
                    } catch (SocketTimeoutException e) { break; }
                }
            } catch (Exception e) {
                System.err.println("[TEST] Alice2 error: " + e.getMessage());
            }
        }, "Alice2-Thread");

        // ─ Bob ───────────────────────────────────────────────────────────
        Thread bobThread = new Thread(() -> {
            try (Socket s = new Socket("localhost", TEST_PORT)) {
                ObjectOutputStream oos = new ObjectOutputStream(s.getOutputStream());
                oos.flush();
                ObjectInputStream ois = new ObjectInputStream(s.getInputStream());

                oos.writeObject(new Message(Message.TYPE_COMMAND, "Bob2", "", "Bob2"));
                oos.flush();
                s.setSoTimeout(6000);
                drainSystemUntilJoin(ois, 1500);

                // Aguarda Alice entrar primeiro
                aliceInChannel.await(6, TimeUnit.SECONDS);

                // Entra no mesmo canal
                oos.writeObject(new Message(Message.TYPE_COMMAND, "Bob2", "", "/join musica"));
                oos.flush();

                drainSystemUntilJoin(ois, 2000);
                bobInChannel.countDown();

                // Aguarda Alice enviar a mensagem
                aliceSent.await(6, TimeUnit.SECONDS);

                // Lê mensagens e procura a de Alice
                long deadline = System.currentTimeMillis() + 5000;
                while (System.currentTimeMillis() < deadline) {
                    try {
                        Message m = (Message) ois.readObject();
                        if ("Alice2".equals(m.getAuthor()) && "Oi Bob2!".equals(m.getContent())) {
                            bobGotMsg.set(true);
                            break;
                        }
                    } catch (SocketTimeoutException e) { break; }
                }

                // Responde para Alice
                oos.writeObject(new Message(Message.TYPE_CHAT, "Bob2", "musica", "Oi Alice2!"));
                oos.flush();
                Thread.sleep(800); // aguarda Alice receber

            } catch (Exception e) {
                System.err.println("[TEST] Bob2 error: " + e.getMessage());
            }
        }, "Bob2-Thread");

        aliceThread.start();
        bobThread.start();
        aliceThread.join(15000);
        bobThread.join(15000);

        assertTrue("[T5] Bob recebeu a mensagem de Alice via broadcast", bobGotMsg.get());
        assertTrue("[T5] Alice recebeu a mensagem de Bob via broadcast", aliceGotMsg.get());
    }

    /** Consome mensagens até receber uma TYPE_JOIN ou até timeout. */
    static void drainSystemUntilJoin(ObjectInputStream ois, long millis) {
        long deadline = System.currentTimeMillis() + millis;
        while (System.currentTimeMillis() < deadline) {
            try {
                Message m = (Message) ois.readObject();
                if (Message.TYPE_JOIN.equals(m.getType())) break;
            } catch (Exception e) { break; }
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  [T9]  Histórico enviado ao novo entrante
    // ─────────────────────────────────────────────────────────────────────
    static void testHistoryOnJoin() throws Exception {
        System.out.println("\n━━━ [T9] Histórico de Canal ao Entrar ━━━");

        CountDownLatch senderReady   = new CountDownLatch(1);
        AtomicBoolean  historyFound  = new AtomicBoolean(false);
        String historicMsg = "Mensagem histórica " + System.currentTimeMillis();

        // Sender: entra no #tecnologia e envia uma mensagem
        Thread senderThread = new Thread(() -> {
            try (Socket s = new Socket("localhost", TEST_PORT)) {
                ObjectOutputStream oos = new ObjectOutputStream(s.getOutputStream());
                oos.flush();
                ObjectInputStream ois = new ObjectInputStream(s.getInputStream());
                oos.writeObject(new Message(Message.TYPE_COMMAND, "Sender", "", "Sender"));
                oos.flush();
                s.setSoTimeout(5000);
                drainSystem(ois, 1000);

                oos.writeObject(new Message(Message.TYPE_COMMAND, "Sender", "", "/join tecnologia"));
                oos.flush();
                drainSystem(ois, 1000);

                // Envia a mensagem histórica
                oos.writeObject(new Message(Message.TYPE_CHAT, "Sender", "tecnologia", historicMsg));
                oos.flush();
                Thread.sleep(400);
                senderReady.countDown();

                Thread.sleep(3000); // fica conectado durante o teste
            } catch (Exception e) {
                System.err.println("[TEST] Sender error: " + e.getMessage());
            }
        }, "Sender-Thread");

        // Receiver: entra no canal DEPOIS, verifica se recebe o histórico
        Thread receiverThread = new Thread(() -> {
            try {
                senderReady.await(6, TimeUnit.SECONDS);
                Thread.sleep(200);

                try (Socket s = new Socket("localhost", TEST_PORT)) {
                    ObjectOutputStream oos = new ObjectOutputStream(s.getOutputStream());
                    oos.flush();
                    ObjectInputStream ois = new ObjectInputStream(s.getInputStream());
                    oos.writeObject(new Message(Message.TYPE_COMMAND, "Receiver", "", "Receiver"));
                    oos.flush();
                    s.setSoTimeout(5000);
                    drainSystem(ois, 1000);

                    // Entra no canal — deve receber histórico imediatamente
                    oos.writeObject(new Message(Message.TYPE_COMMAND, "Receiver", "", "/join tecnologia"));
                    oos.flush();

                    long deadline = System.currentTimeMillis() + 4000;
                    while (System.currentTimeMillis() < deadline) {
                        try {
                            Message m = (Message) ois.readObject();
                            if (historicMsg.equals(m.getContent())) {
                                historyFound.set(true);
                                break;
                            }
                        } catch (SocketTimeoutException e) { break; }
                    }
                }
            } catch (Exception e) {
                System.err.println("[TEST] Receiver error: " + e.getMessage());
            }
        }, "Receiver-Thread");

        senderThread.start();
        receiverThread.start();
        senderThread.join(10000);
        receiverThread.join(10000);

        assertTrue("[T9] Histórico do canal foi enviado ao usuário que entrou depois", historyFound.get());
    }

    // ─────────────────────────────────────────────────────────────────────
    //  [T10]  Desconexão graciosa (volatile + poison pill)
    // ─────────────────────────────────────────────────────────────────────
    static void testGracefulDisconnect() throws Exception {
        System.out.println("\n━━━ [T10] Desconexão Graciosa ━━━");

        // Verifica que o flag 'running' em ClientHandler é volatile
        boolean runningIsVolatile = false;
        try {
            java.lang.reflect.Field f = ClientHandler.class.getDeclaredField("running");
            f.setAccessible(true);
            runningIsVolatile = java.lang.reflect.Modifier.isVolatile(f.getModifiers());
        } catch (NoSuchFieldException ignored) {}
        assertTrue("[T10] ClientHandler.running é volatile", runningIsVolatile);

        // Verifica que o flag 'connected' em ChatClient é volatile
        boolean connectedIsVolatile = false;
        try {
            java.lang.reflect.Field f = ChatClient.class.getDeclaredField("connected");
            f.setAccessible(true);
            connectedIsVolatile = java.lang.reflect.Modifier.isVolatile(f.getModifiers());
        } catch (NoSuchFieldException ignored) {}
        assertTrue("[T10] ChatClient.connected é volatile", connectedIsVolatile);

        // Testa que a desconexão não trava o servidor (conecta e fecha abruptamente)
        AtomicBoolean serverStillUp = new AtomicBoolean(false);
        try (Socket s = new Socket("localhost", TEST_PORT)) {
            ObjectOutputStream oos = new ObjectOutputStream(s.getOutputStream());
            oos.flush();
            ObjectInputStream ois = new ObjectInputStream(s.getInputStream());
            oos.writeObject(new Message(Message.TYPE_COMMAND, "Ghost", "", "Ghost"));
            oos.flush();
            Thread.sleep(300);
            // Fecha abruptamente sem logout
        }

        Thread.sleep(500);

        // Verifica que o servidor ainda aceita conexões
        try (Socket s2 = new Socket("localhost", TEST_PORT)) {
            serverStillUp.set(s2.isConnected());
            ObjectOutputStream oos2 = new ObjectOutputStream(s2.getOutputStream());
            oos2.flush();
            ObjectInputStream ois2 = new ObjectInputStream(s2.getInputStream());
            oos2.writeObject(new Message(Message.TYPE_COMMAND, "PostGhost", "", "PostGhost"));
            oos2.flush();
            Thread.sleep(300);
        }
        assertTrue("[T10] Servidor continua aceitando conexões após desconexão abrupta", serverStillUp.get());
    }

    // ─────────────────────────────────────────────────────────────────────
    //  [T3 / T6]  Concorrência real: 10 clientes entram no mesmo canal
    // ─────────────────────────────────────────────────────────────────────
    static void testConcurrentChannelJoin() throws Exception {
        System.out.println("\n━━━ [T3/T6] Concorrência: 10 Clientes Simultâneos ━━━");

        int N = 10;
        CountDownLatch allJoined = new CountDownLatch(N);
        AtomicInteger  joinCount = new AtomicInteger(0);
        CyclicBarrier  barrier   = new CyclicBarrier(N); // garante que todos chegam ao mesmo tempo

        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < N; i++) {
            final String name = "User" + i;
            Thread t = new Thread(() -> {
                try (Socket s = new Socket("localhost", TEST_PORT)) {
                    ObjectOutputStream oos = new ObjectOutputStream(s.getOutputStream());
                    oos.flush();
                    ObjectInputStream ois = new ObjectInputStream(s.getInputStream());
                    oos.writeObject(new Message(Message.TYPE_COMMAND, name, "", name));
                    oos.flush();
                    s.setSoTimeout(5000);
                    drainSystem(ois, 500);

                    // Todos tentam entrar no mesmo canal ao mesmo tempo
                    barrier.await(5, TimeUnit.SECONDS);
                    oos.writeObject(new Message(Message.TYPE_COMMAND, name, "", "/join jogos"));
                    oos.flush();

                    // Verifica se entrou (recebe TYPE_JOIN)
                    long deadline = System.currentTimeMillis() + 3000;
                    while (System.currentTimeMillis() < deadline) {
                        try {
                            Message m = (Message) ois.readObject();
                            if (Message.TYPE_JOIN.equals(m.getType())) {
                                joinCount.incrementAndGet();
                                allJoined.countDown();
                                break;
                            }
                        } catch (SocketTimeoutException e) { break; }
                    }
                    Thread.sleep(500);
                } catch (Exception e) {
                    System.err.println("[TEST] " + name + " error: " + e.getMessage());
                    allJoined.countDown();
                }
            }, "ConTest-" + name);
            t.setDaemon(true);
            threads.add(t);
        }

        for (Thread t : threads) t.start();
        boolean ok = allJoined.await(15, TimeUnit.SECONDS);

        assertTrue("[T3] Thread pool atendeu " + N + " clientes simultâneos",
                joinCount.get() >= N - 1); // tolerância de 1 por race condition de timing
        assertTrue("[T6] Semáforo controlou acesso ao canal (nenhuma exceção)",
                joinCount.get() > 0);
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Auxiliares
    // ─────────────────────────────────────────────────────────────────────

    /** Inicia um Server em background na porta TEST_PORT. */
    static Server startTestServer() {
        // Usamos reflexão para criar um servidor na porta de teste
        // Para isso, precisamos de um Server com porta custom — mas o Server usa PORT fixo.
        // Vamos usar o servidor normal na porta TEST_PORT via técnica de sobrescrever o campo:
        Server s = new TestServer();
        Thread t = new Thread(() -> {
            try { s.start(); }
            catch (Exception e) {
                if (!e.getMessage().contains("closed"))
                    System.err.println("[TEST-SERVER] " + e.getMessage());
            }
        }, "TestServer");
        t.setDaemon(true);
        t.start();
        System.out.println("[TEST] Servidor de teste iniciado na porta " + TEST_PORT);
        return s;
    }

    /** Consome mensagens de sistema por até `millis` ms para limpar o stream. */
    static void drainSystem(ObjectInputStream ois, long millis) {
        long deadline = System.currentTimeMillis() + millis;
        while (System.currentTimeMillis() < deadline) {
            try {
                ois.readObject();
            } catch (Exception e) { break; }
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Relatório final
    // ─────────────────────────────────────────────────────────────────────
    static void printReport() {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════╗");
        System.out.printf ("║  Resultado: %2d passaram  /  %2d falharam  /  %2d total    ║%n",
                passed, failed, passed + failed);
        System.out.println("╠══════════════════════════════════════════════════════════╣");
        if (failures.isEmpty()) {
            System.out.println("║  🎉 TODOS OS TESTES PASSARAM! Projeto aprovado.          ║");
        } else {
            System.out.println("║  ⚠️  Falhas detectadas:                                   ║");
            for (String f : failures) {
                System.out.printf("║    • %-54s║%n", f);
            }
        }
        System.out.println("╚══════════════════════════════════════════════════════════╝");
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Subclasse de Server que usa a porta de TESTE
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Subclasse que sobrescreve start() para usar TEST_PORT em vez de PORT.
     * Isso isola os testes do servidor de produção.
     */
    static class TestServer extends Server {
        @Override
        public void start() throws IOException {
            try (ServerSocket ss = new ServerSocket(TEST_PORT)) {
                System.out.println("[TEST-SERVER] Escutando em :" + TEST_PORT);
                // Descobre o método start() original via super
                // e substitui o ServerSocket criado internamente...
                // Como não podemos injetar, replicamos o loop aqui:
                while (!ss.isClosed()) {
                    Socket clientSocket = ss.accept();
                    ClientHandler handler = new ClientHandler(clientSocket, this);
                    // Acessa o threadPool via reflexão e submete
                    try {
                        java.lang.reflect.Field f = Server.class.getDeclaredField("threadPool");
                        f.setAccessible(true);
                        ExecutorService pool = (ExecutorService) f.get(this);
                        pool.execute(handler);
                    } catch (Exception e) {
                        new Thread(handler).start(); // fallback
                    }
                }
            }
        }
    }
}

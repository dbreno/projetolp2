package src;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Semaphore;

/**
 * Representa um canal de chat (inspirado nos canais do Discord).
 * Um canal é o LOCAL onde múltiplos usuários se comunicam simultaneamente.
 *
 * ─── CONCEITOS DE CONCORRÊNCIA APLICADOS ───────────────────────────────────
 *
 * 1. MONITOR (synchronized): Todos os métodos que leem ou escrevem o estado
 *    compartilhado (lista de membros, histórico) são sincronizados. Isso cria
 *    um MONITOR no sentido clássico de Dijkstra/Hoare: apenas UMA thread
 *    executa dentro do monitor por vez. Sem isso, duas ClientHandler-threads
 *    poderiam corromper a lista ao adicionarem membros ao mesmo tempo (race condition).
 *
 * 2. SEMÁFORO CONTADOR (java.util.concurrent.Semaphore): Limita quantos
 *    usuários podem estar no canal simultaneamente. Modela o recurso "vagas no canal".
 *    P(s) = acquire(), V(s) = release(). Conceito direto dos slides de Semáforos
 *    (Dijkstra, operações P/V sobre semáforo contador).
 *
 * 3. COLEÇÃO THREAD-SAFE: A lista de membros é acessada via bloco synchronized,
 *    equivalente a uma seção crítica protegida pelo monitor do objeto Channel.
 * ───────────────────────────────────────────────────────────────────────────
 */
public class Channel {

    public static final int MAX_MEMBERS = 50; // capacidade máxima do canal

    private final String name;
    private final String description;

    // Lista de handlers dos clientes conectados neste canal (seção crítica)
    private final List<ClientHandler> members = new ArrayList<>();

    // Histórico de até 100 mensagens (seção crítica)
    private final List<Message> history = new ArrayList<>();
    private static final int MAX_HISTORY = 100;

    /**
     * SEMÁFORO CONTADOR: inicializado com MAX_MEMBERS "permissões" (vagas).
     * Cada usuário que entra faz P(s) = acquire().
     * Cada usuário que sai faz V(s) = release().
     * Se s = 0, o canal está lotado; novos entrantes bloqueiam até uma vaga abrir.
     *
     * fair = true: garante política FIFO de liberação (sem starvation),
     * conforme discutido nos slides de Semáforos ("política de liberação da fila").
     */
    private final Semaphore vagas = new Semaphore(MAX_MEMBERS, true);

    public Channel(String name, String description) {
        this.name        = name;
        this.description = description;
    }

    /**
     * Tenta adicionar um usuário ao canal.
     *
     * POR QUE dois mecanismos (Semáforo + synchronized)?
     * São responsáveis por coisas DIFERENTES e se complementam:
     *
     * - Semáforo (vagas): controla QUANTOS usuários podem estar no canal.
     *   P(s) = tryAcquire(): decrementa a contagem de vagas.
     *   Se vagas == 0 (canal cheio), retorna false IMEDIATAMENTE sem bloquear.
     *   Isso é preferido ao acquire() bloqueante porque não queremos que o
     *   cliente fique preso esperando uma vaga — é melhor retornar um erro rápido.
     *
     * - synchronized (monitor): controla acesso à LISTA de membros.
     *   Mesmo que duas threads passem pelo tryAcquire() quase simultaneamente,
     *   só uma pode modificar a lista por vez, evitando corrupção de dados.
     *
     * @return true se adicionado com sucesso, false se o canal estiver cheio
     */
    public boolean join(ClientHandler client) throws InterruptedException {
        // P(s): tenta adquirir uma vaga — retorna false se não houver
        if (!vagas.tryAcquire()) {
            return false; // canal cheio, rejeita imediatamente
        }

        // MONITOR: seção crítica para modificar a lista de membros com segurança
        synchronized (this) {
            if (!members.contains(client)) {
                members.add(client);
            }
        }
        return true;
    }

    /**
     * Remove um usuário do canal.
     * release() implementa V(s): incrementa o semáforo e libera quem espera.
     */
    public synchronized void leave(ClientHandler client) {
        if (members.remove(client)) {
            // V(s): libera uma vaga no semáforo
            vagas.release();
        }
    }

    /**
     * Difunde (broadcast) uma mensagem para TODOS os membros do canal,
     * exceto o remetente (para não ecoar a própria mensagem de volta).
     *
     * POR QUE fazemos uma SNAPSHOT (cópia) da lista antes de iterar?
     * O método está sincronizado, mas sendMessage() pode demorar se a fila
     * do cliente estiver quase cheia. Manter o lock durante todo o envio
     * bloquearia outros métodos (join, leave) por mais tempo que o necessário.
     * Copiamos a lista em O(n) e liberamos o lock imediatamente, fazendo
     * os envios fora do período crítico — equilíbrio entre segurança e performance.
     */
    public synchronized void broadcast(Message msg, ClientHandler sender) {
        addToHistory(msg);

        // Copia a lista com o lock; depois itera na cópia sem segurar o monitor
        List<ClientHandler> snapshot = new ArrayList<>(members);
        for (ClientHandler member : snapshot) {
            if (member != sender) {
                member.sendMessage(msg); // enfileira na BlockingQueue do cliente (não bloqueia)
            }
        }
    }

    /**
     * Versão de broadcast que inclui o remetente (usada para comandos de sistema).
     */
    public synchronized void broadcastAll(Message msg) {
        addToHistory(msg);
        List<ClientHandler> snapshot = new ArrayList<>(members);
        for (ClientHandler member : snapshot) {
            member.sendMessage(msg);
        }
    }

    /**
     * Adiciona mensagem ao histórico circular, descartando a mais antiga
     * quando o limite é atingido. DEVE ser chamado dentro de bloco synchronized.
     */
    private void addToHistory(Message msg) {
        if (history.size() >= MAX_HISTORY) {
            history.remove(0); // descarta a mais antiga
        }
        history.add(msg);
    }

    /**
     * Retorna uma cópia imutável do histórico para exibição quando
     * um novo usuário entra no canal.
     */
    public synchronized List<Message> getHistory() {
        return Collections.unmodifiableList(new ArrayList<>(history));
    }

    // ─── Getters ────────────────────────────────────────────────────────────
    public String getName()        { return name; }
    public String getDescription() { return description; }

    public synchronized int getMemberCount() { return members.size(); }

    public synchronized List<String> getMemberNames() {
        List<String> names = new ArrayList<>();
        for (ClientHandler h : members) names.add(h.getUsername());
        return names;
    }

    @Override
    public String toString() {
        return "#" + name;
    }
}

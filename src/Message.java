package src;

import java.io.Serializable;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Objeto de transferência de dados (DTO) que representa uma mensagem trocada
 * no sistema. É serializado/desserializado via ObjectInputStream/ObjectOutputStream
 * sobre o socket TCP, que garante entrega confiável e ordenada (stream de bytes).
 *
 * Conceito aplicado: Comunicação via TCP (sockets de stream), conforme slides de Sockets.
 * O TCP garante que os objetos chegam na mesma ordem e sem perdas, ao contrário do UDP.
 */
public class Message implements Serializable {

    // Versão para compatibilidade de serialização entre JVMs
    private static final long serialVersionUID = 1L;

    // Tipos de mensagem que definem o comportamento no servidor e no cliente
    public static final String TYPE_CHAT    = "CHAT";    // mensagem normal de texto
    public static final String TYPE_JOIN    = "JOIN";    // usuário entrou num canal
    public static final String TYPE_LEAVE   = "LEAVE";   // usuário saiu de um canal
    public static final String TYPE_SYSTEM  = "SYSTEM";  // mensagem do sistema (informativa)
    public static final String TYPE_COMMAND = "CMD";     // comando do cliente para o servidor

    private final String type;    // tipo da mensagem (constantes acima)
    private final String author;  // nome do usuário que enviou
    private final String channel; // canal de destino ou contexto
    private final String content; // conteúdo textual
    private final String time;    // horário formatado de envio

    public Message(String type, String author, String channel, String content) {
        this.type    = type;
        this.author  = author;
        this.channel = channel;
        this.content = content;
        // Carimbo de tempo gerado no momento da criação da mensagem
        this.time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
    }

    public String getType()    { return type; }
    public String getAuthor()  { return author; }
    public String getChannel() { return channel; }
    public String getContent() { return content; }
    public String getTime()    { return time; }

    @Override
    public String toString() {
        return "[" + time + "] <" + author + "> " + content;
    }
}

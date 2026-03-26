# DiscordLP2 — Sistema de Chat Concorrente e Distribuído

> Projeto desenvolvido para a disciplina de **Linguagens de Programação 2**,
> demonstrando os conceitos de **concorrência** e **sistemas distribuídos** em Java puro.

---

## 📋 Visão Geral

O **DiscordLP2** é um sistema de chat multiusuário em tempo real, inspirado na arquitetura de canais do Discord. A comunicação entre cliente e servidor é feita via **Java Sockets TCP**, e a concorrência é gerenciada com **Threads**, **Semáforos**, **Monitores (synchronized)** e o padrão **Produtor-Consumidor** com `BlockingQueue`.

A interface gráfica é construída em **Java Swing** seguindo o dark theme do Discord, com avatares circulares animados, agrupamento de mensagens e fade-in de novas mensagens.

---

## 🏗️ Arquitetura

```
┌─────────────────────────────────────────────────────────┐
│                    SERVIDOR (Server.java)                │
│                                                          │
│  ServerSocket + Pool de Threads (ExecutorService)        │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐   │
│  │ ClientHandler│  │ ClientHandler│  │ ClientHandler│   │
│  │  (Thread 1)  │  │  (Thread 2)  │  │  (Thread N)  │   │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘   │
│         │                 │                 │            │
│         └─────────────┬───┘─────────────────┘            │
│                  ┌────▼────┐                             │
│                  │ Channel │  ← Semáforo + Monitor       │
│                  └─────────┘                             │
└─────────────────────────────────────────────────────────┘
          ▲ TCP Socket          ▲ TCP Socket
          │                    │
┌─────────┴──────┐   ┌─────────┴──────┐
│  ChatClient    │   │  ChatClient    │
│  (Thread recv) │   │  (Thread recv) │
│  + ChatGUI     │   │  + ChatGUI     │
│  (Swing EDT)   │   │  (Swing EDT)   │
└────────────────┘   └────────────────┘
```

### Conceitos de Concorrência Aplicados

| Conceito | Onde é Aplicado |
|---|---|
| **Pool de Threads** (`ExecutorService`) | `Server.java` — cada cliente vai para uma thread do pool |
| **Semáforo Contador** (`Semaphore`) | `Channel.java` — limita a N usuários simultâneos por canal |
| **Monitor** (`synchronized`) | `Channel.java`, `ClientHandler.java` — acesso à lista de membros e `currentChannel` |
| **Produtor-Consumidor** (`BlockingQueue`) | `ClientHandler.java` — fila de saída de mensagens por cliente |
| **Poison Pill** | `ClientHandler.java` — sinaliza encerramento da `WriterThread` |
| **Thread Safety na GUI** (`invokeLater`) | `ChatGUI.java` — atualizações de componentes Swing sempre na EDT |
| **Volatile** | `ClientHandler.java` — flag `running` visível entre threads |

---

## 📁 Estrutura de Arquivos

```
ProjetoLP2/
├── src/
│   ├── Server.java          # Servidor: ServerSocket + pool de threads
│   ├── ClientHandler.java   # Handler por cliente: leitura + WriterThread
│   ├── Channel.java         # Canal de chat: semáforo + monitor + histórico
│   ├── ChatClient.java      # Cliente de rede: conexão + ReceiverThread
│   ├── Message.java         # Modelo de mensagem (Serializable)
│   ├── ChatGUI.java         # Interface gráfica (Java Swing, dark theme)
│   └── ChatSystemTest.java  # Suite de testes automatizados (27 testes)
├── out/                     # Classes compiladas (.class) — gerado automaticamente
├── compile.sh               # Script de compilação
├── run_server.sh            # Script para iniciar o servidor
└── run_client.sh            # Script para iniciar um cliente
```

---

## ✅ Pré-requisitos

- **Java JDK 8 ou superior** instalado e no `PATH`

Para verificar:

```bash
java -version
javac -version
```

A saída deve ser algo como `java version "17.0.x"` ou superior.

---

## 🚀 Como Rodar

### 1. Compilar o projeto

Execute **uma única vez** a partir da raiz do projeto:

```bash
chmod +x compile.sh run_server.sh run_client.sh
./compile.sh
```

Ou manualmente:

```bash
mkdir -p out
javac -d out src/Message.java src/Channel.java src/ClientHandler.java src/Server.java src/ChatClient.java src/ChatGUI.java
```

---

### 2. Iniciar o Servidor

Abra um terminal e rode:

```bash
./run_server.sh
```

Você verá:

```
╔════════════════════════════════════╗
║   DiscordLP2 Server — Porta 12345   ║
║   Pool de threads: 100 threads      ║
╚════════════════════════════════════╝
[SERVER] Canal criado: #geral
[SERVER] Canal criado: #tecnologia
...
```

O servidor ficará escutando na porta **12345** até ser encerrado com `Ctrl+C`.

> **Múltiplas máquinas:** basta que os clientes apontem o IP correto da máquina do servidor na tela de login. O servidor precisa estar acessível na rede local.

---

### 3. Iniciar um Cliente

Em outro terminal (ou outra máquina), rode:

```bash
./run_client.sh
```

A janela gráfica abrirá com a tela de login. Preencha:

| Campo | Valor padrão | Descrição |
|---|---|---|
| **Servidor** | `localhost` | IP ou hostname do servidor |
| **Porta** | `12345` | Porta TCP do servidor |
| **Nickname** | — | Seu nome no chat (2–20 chars, sem espaços) |

Clique em **Entrar no Servidor**.

> Para testar com vários usuários, abra múltiplos terminais e execute `./run_client.sh` em cada um.

---

### 4. Usando o Chat

Após conectar:

1. **Escolha um canal** na barra lateral esquerda (ex: `# geral`)
2. **Digite uma mensagem** no campo inferior e pressione `Enter` para enviar
3. Use `Shift + Enter` para quebrar linha sem enviar

#### Comandos disponíveis

| Comando | Descrição |
|---|---|
| `/join <canal>` | Entra em um canal específico |
| `/leave` | Sai do canal atual |
| `/channels` | Lista todos os canais disponíveis |
| `/users` | Lista os membros do canal atual |

---

## 🧪 Rodando os Testes

O projeto inclui uma suite com **27 testes automatizados** que cobrem o servidor, os canais e a lógica de mensagens sem depender de interface gráfica:

```bash
java -cp out src.ChatSystemTest
```

Saída esperada:

```
════════════════════════════════════════
  DiscordLP2 — Suite de Testes
════════════════════════════════════════
✓ Canal criado com nome e descrição corretos
✓ Usuário entra no canal com sucesso
✓ Canal cheio rejeita novo usuário
...
════════════════════════════════════════
  Resultado: 27/27 testes passaram ✔
════════════════════════════════════════
```

---

## 🎮 Canais Padrão

O servidor inicia com 5 canais pré-configurados:

| Canal | Descrição |
|---|---|
| `#geral` | Canal principal para todos os assuntos 💬 |
| `#tecnologia` | Discussões sobre tecnologia e programação 💻 |
| `#jogos` | Fale sobre seus jogos favoritos 🎮 |
| `#musica` | Compartilhe e discuta música 🎵 |
| `#off-topic` | Qualquer coisa que não se encaixa nos outros canais 🎲 |

Cada canal suporta até **50 usuários simultâneos** (controlado por semáforo).

---

## 🔧 Solução de Problemas

**"Connection refused" ao conectar:**
- Verifique se o servidor está rodando antes de iniciar o cliente.
- Confirme que a porta 12345 não está bloqueada por firewall.

**Interface gráfica não abre (Linux headless):**
- Certifique-se de que há um servidor de display ativo (`echo $DISPLAY`).
- Em ambientes sem GUI, rode apenas o servidor e teste com `ChatSystemTest`.

**"Address already in use":**
- Algum processo já está usando a porta 12345. Encerre-o com `kill $(lsof -t -i:12345)` ou reinicie o terminal.

---

## 👥 Equipe

Projeto desenvolvido por alunos da disciplina **Linguagens de Programação 2**.

---

## 📄 Licença

Uso acadêmico — Disciplina de LP2.

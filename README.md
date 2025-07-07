# 🧩 P2P File Sharing com Java Sockets

Este projeto simula o funcionamento de uma rede P2P (como o BitTorrent), utilizando Java e comunicação via **UDP (para Tracker)** e **TCP (entre peers)**.

## 📁 Estrutura do Projeto

```
p2p-sockets/
├── src/
│   ├── tracker/
│   │   └── TrackerServer.java
│   └── peer/
│       ├── Peer.java
│       └── PeerInfo.java
├── pieces/
│   ├── 1.txt
│   ├── 2.txt
│   └── ... até 10.txt
├── out/                  
├── peer_data_<porta>/    
└── README.md
```

## 🧱 Pré-requisitos

- Java 11+ instalado
- Terminal (Linux/macOS) ou Prompt de Comando (Windows)

---

## ⚙️ Compilação

No diretório raiz do projeto, execute:

```bash
javac -d out src/tracker/TrackerServer.java src/peer/Peer.java src/peer/PeerInfo.java
```

Isso criará os arquivos `.class` no diretório `out/`.

---

## 🚀 Execução

### 1. Inicie o Tracker

```bash
java -cp out tracker.TrackerServer
```

Isso inicia o servidor na porta `8888` (fixa por padrão).

---

### 2. Inicie os peers

Cada peer deve ser iniciado **com uma porta diferente**. Os pedaços que ele possui devem estar em `peer_data_<porta>/`.

#### Exemplo:

##### 🌐 Rodando em Rede Local

- Certifique-se de que todos os dispositivos estão na **mesma rede**
- Use o **IP local da máquina do Tracker** ao iniciar os peers:

```bash
java -cp out peer.Peer 192.168.0.10 5001
java -cp out peer.Peer 192.168.0.10 5002
```

(onde `192.168.0.10` é o IP do Tracker)

---

## 🛠️ Funcionalidades

- ✅ Envio de `JOIN` ao Tracker
- ✅ Registro e atualização de pedaços (`UPDATE`)
- ✅ Consulta periódica (`GETPEERS`) para obter peers atualizados
- ✅ Download do pedaço mais raro via TCP
- ✅ Salvamento de pedaços como `.txt`
- ✅ Reconstrução do arquivo final quando todos os pedaços são recebidos

---

## ✅ Reconstrução do Arquivo Final

Assim que o peer possuir os pedaços `1.txt` a `10.txt`, ele irá reconstruir automaticamente o arquivo completo:

```
peer_data_<porta>/arquivo_final.txt
```

---

## 📌 Observações

- O `Peer` só tenta baixar pedaços **que ele sabe que existem em outros peers**
- A lista de peers é atualizada automaticamente com `GETPEERS` a cada 10 segundos
- Após reconstrução, o peer continua servindo pedaços para os outros (modo seeder)

---

## 👩‍💻 Equipe

- Giovana Niehues
- Inara Ribeiro

---

Projeto acadêmico da disciplina **Redes de Computadores** — UDESC Joinville

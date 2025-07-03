package peer;

import tracker.PeerInfo;

import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

// javac -d out src/peer/Peer.java src/tracker/PeerInfo.java
// java peer.Peer 5001

public class Peer {
    private final String trackerIp;
    private final int trackerPort;
    private final int localPort;
    private DatagramSocket socket;
    private File peerFolder;

    private final List<PeerInfo> knownPeers = new ArrayList<>();
    private final Map<PeerInfo, Set<Integer>> peerPieces = new HashMap<>();
    private final Set<Integer> myPieces = new HashSet<>();

    public Peer(String trackerIp, int trackerPort, int localPort) {
        this.trackerIp = trackerIp;
        this.trackerPort = trackerPort;
        this.localPort = localPort;
        this.peerFolder = new File("peer_data_" + localPort);
        if (!peerFolder.exists()) peerFolder.mkdirs();
    }

    public void start() throws Exception {
        initializeWithRandomPieces(2);
        loadLocalPieces();

        socket = new DatagramSocket();

        sendJoin();
        receivePeersList();
        startTCPServer();

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(this::sendUpdateToTracker, 0, 10, TimeUnit.SECONDS);

        Integer rarest = chooseRarestPiece(myPieces);

        PeerInfo target = null;
        for (Map.Entry<PeerInfo, Set<Integer>> entry : peerPieces.entrySet()) {
            if (entry.getValue().contains(rarest)) {
                target = entry.getKey();
                break;
            }
        }

        if (target != null && rarest != null) {
            String content = requestPieceFromPeer(target, rarest);
            if (content != null) {
                System.out.println("Recebido pedaço " + rarest + ": " + content);
                savePieceToFile(rarest, content);
                myPieces.add(rarest);
            }
        }
    }

    private void sendJoin() throws Exception {
        String joinMessage = "JOIN:" + localPort;
        byte[] buffer = joinMessage.getBytes();

        InetAddress trackerAddress = InetAddress.getByName(trackerIp);
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length, trackerAddress, trackerPort);
        socket.send(packet);
        System.out.println("Enviado JOIN para Tracker");
    }

    private void sendUpdateToTracker() {
        try {
            String piecesStr = myPieces.isEmpty()
                    ? "none"
                    : myPieces.toString().replaceAll("[\\[\\] ]", "");

            String ip = InetAddress.getLocalHost().getHostAddress();
            String updateMessage = "UPDATE:" + ip + ":" + localPort + ":" + piecesStr;

            byte[] buffer = updateMessage.getBytes();
            InetAddress trackerAddress = InetAddress.getByName(trackerIp);

            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, trackerAddress, trackerPort);
            socket.send(packet);

            System.out.println("Enviado UPDATE para Tracker: " + updateMessage);

        } catch (Exception e) {
            System.err.println("Erro ao enviar UPDATE: " + e.getMessage());
        }
    }

    private void savePieceToFile(int pieceNumber, String content) {
        try {
            File outFile = new File(peerFolder, pieceNumber + ".txt");
            Files.write(outFile.toPath(), content.getBytes());
            System.out.println("Salvou pedaço " + pieceNumber + " em " + outFile.getPath());
        } catch (IOException e) {
            System.err.println("Erro ao salvar pedaço: " + e.getMessage());
        }
    }

    private void initializeWithRandomPieces(int quantity) {
        File sourceFolder = new File("pieces");
        File[] files = sourceFolder.listFiles((dir, name) -> name.endsWith(".txt"));

        if (files == null || files.length < quantity) return;

        List<File> fileList = Arrays.asList(files);
        Collections.shuffle(fileList);

        for (int i = 0; i < quantity; i++) {
            File piece = fileList.get(i);
            File destination = new File(peerFolder, piece.getName());

            try {
                Files.copy(piece.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        System.out.println("🔄 Pedaços iniciais copiados para " + peerFolder.getName());
    }

    private void loadLocalPieces() {
        if (!peerFolder.exists()) return;

        File[] files = peerFolder.listFiles((dir, name) -> name.endsWith(".txt"));
        if (files == null) return;

        for (File file : files) {
            String name = file.getName().replace(".txt", "");
            try {
                int pieceNumber = Integer.parseInt(name);
                myPieces.add(pieceNumber);
            } catch (NumberFormatException ignored) {
            }
        }

        System.out.println("📦 Pedaços locais carregados: " + myPieces);
    }

    private void receivePeersList() throws Exception {
        byte[] buffer = new byte[1024];
        DatagramPacket response = new DatagramPacket(buffer, buffer.length);
        socket.receive(response);

        String message = new String(response.getData(), 0, response.getLength());
        System.out.println("Recebido do Tracker: " + message);

        if (message.startsWith("PEERS")) {
            parsePeersList(message);
        }
    }

    private void parsePeersList(String message) {
        // Exemplo: PEERS|127.0.0.1:5001:1,2,3|127.0.0.1:5002:none
        String[] parts = message.split("\\|");

        for (int i = 1; i < parts.length; i++) {
            String[] peerData = parts[i].split(":");
            String ip = peerData[0];
            int port = Integer.parseInt(peerData[1]);

            PeerInfo peer = new PeerInfo(ip, port);

            Set<Integer> pieces = new HashSet<>();
            if (!peerData[2].equalsIgnoreCase("none")) {
                String[] pieceList = peerData[2].split(",");
                for (String p : pieceList) {
                    try {
                        pieces.add(Integer.parseInt(p.trim()));
                    } catch (NumberFormatException ignored) {}
                }
            }

            knownPeers.add(peer);
            peerPieces.put(peer, pieces);
        }

        System.out.println("Peers conhecidos:");
        for (PeerInfo peer : knownPeers) {
            System.out.println("- " + peer + " → " + peerPieces.get(peer));
        }
    }

    public Integer chooseRarestPiece(Set<Integer> myPieces) {
        Map<Integer, Integer> pieceCount = new HashMap<>();

        for (Set<Integer> pieces : peerPieces.values()) {
            for (Integer piece : pieces) {
                if (myPieces.contains(piece)) continue;
                pieceCount.put(piece, pieceCount.getOrDefault(piece, 0) + 1);
            }
        }

        Integer rarestPiece = null;
        int minCount = Integer.MAX_VALUE;

        for (Map.Entry<Integer, Integer> entry : pieceCount.entrySet()) {
            if (entry.getValue() < minCount) {
                minCount = entry.getValue();
                rarestPiece = entry.getKey();
            }
        }

        return rarestPiece;
    }

    public void startTCPServer() {
        new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(localPort)) {
                System.out.println("TCP Server rodando na porta: " + localPort);
                while (true) {
                    Socket clientSocket = serverSocket.accept();
                    new Thread(() -> handlePeerConnection(clientSocket)).start();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void handlePeerConnection(Socket socket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()))) {

            String request = in.readLine();
            System.out.println("Recebida requisição TCP: " + request);

            if (request != null && request.startsWith("GET:")) {
                int pieceNumber = Integer.parseInt(request.substring(4).trim());

                File pieceFile = new File("pieces/" + pieceNumber + ".txt");

                if (pieceFile.exists()) {
                    String content = new String(Files.readAllBytes(pieceFile.toPath()));
                    out.write("OK:" + content + "\n");
                } else {
                    out.write("ERROR: Pedaço não encontrado\n");
                }
                out.flush();
            }

        } catch (IOException | NumberFormatException e) {
            System.err.println("Erro ao lidar com requisição TCP: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public String requestPieceFromPeer(PeerInfo peer, int pieceNumber) {
        try (Socket socket = new Socket(peer.getIp(), peer.getPort());
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()))) {

            out.write("GET:" + pieceNumber + "\n");
            out.flush();

            String response = in.readLine();
            if (response != null && response.startsWith("OK:")) {
                return response.substring(3); // Retorna conteúdo do pedaço
            }

        } catch (IOException e) {
            System.err.println("Erro ao requisitar pedaço " + pieceNumber + " de " + peer);
        }
        return null;
    }

    public static void main(String[] args) {
        try {
            int port = Integer.parseInt(args[0]);
            Peer peer = new Peer("127.0.0.1", 8888, port);
            peer.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

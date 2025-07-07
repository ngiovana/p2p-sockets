package tracker;

import java.io.IOException;
import java.net.*;
import java.util.*;

public class TrackerServer {
    private final int port;
    private DatagramSocket socket;
    private final Map<PeerInfo, Set<Integer>> peersMap;

    public TrackerServer(int port) throws SocketException {
        this.port = port;
        this.socket = new DatagramSocket(port);
        this.peersMap = new HashMap<>();

        System.out.println("Tracker iniciado na porta " + port);
    }

    public void startServer() throws IOException {
        byte[] buffer = new byte[1024];

        while (true) {
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            socket.receive(packet);

            String message = new String(packet.getData(), 0, packet.getLength());
            InetAddress senderIp = packet.getAddress();
            int senderPort = packet.getPort();

            System.out.println("Recebido: " + message + " de " + senderIp + ":" + senderPort);

            if (message.startsWith("JOIN:")) {
                processJoin(message, senderIp, senderPort);
            } else if (message.startsWith("UPDATE:")) {
                processUpdate(message);
            } else if (message.startsWith("GETPEERS:")) {
                String portStr = message.split(":")[1];
                int port = Integer.parseInt(portStr);
                InetAddress requesterIp = packet.getAddress();

                StringBuilder response = new StringBuilder("PEERS");
                for (Map.Entry<PeerInfo, Set<Integer>> entry : peersMap.entrySet()) {
                    PeerInfo peer = entry.getKey();
                    if (peer.getIp().equals(requesterIp.getHostAddress()) && peer.getPort() == port) {
                        continue;
                    }

                    Set<Integer> pieces = entry.getValue();
                    String pieceStr = pieces == null || pieces.isEmpty()
                            ? "none"
                            : pieces.toString().replaceAll("[\\[\\] ]", "");

                    response.append("|").append(peer.getIp()).append(":").append(peer.getPort()).append(":").append(pieceStr);
                }

                byte[] sendData = response.toString().getBytes();
                DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, packet.getAddress(), packet.getPort());
                socket.send(sendPacket);
            }
        }
    }

    private void processJoin(String msg, InetAddress ip, int port) throws IOException {
        // Ex: JOIN:8888
        String[] parts = msg.split(":");
        int peerPort = Integer.parseInt(parts[1]);
        PeerInfo peer = new PeerInfo(ip.getHostAddress(), peerPort);

        peersMap.putIfAbsent(peer, new HashSet<>());

        String peerList = generatePeerList();
        byte[] response = peerList.getBytes();
        DatagramPacket responsePacket = new DatagramPacket(response, response.length, ip, port);
        socket.send(responsePacket);
    }

    private void processUpdate(String msg) {
        // Ex: UPDATE:192.168.0.10:5001:1,2,5
        String[] parts = msg.split(":");
        String ip = parts[1];
        int port = Integer.parseInt(parts[2]);
        String[] piecesStr = parts[3].split(",");

        Set<Integer> pieces = new HashSet<>();

        for (String p : piecesStr) {
            pieces.add(Integer.parseInt(p.trim()));
        }

        PeerInfo peer = new PeerInfo(ip, port);
        peersMap.put(peer, pieces);
        System.out.println("Atualizado: " + peer + " > " + pieces);
    }

    private String generatePeerList() {
        StringBuilder sb = new StringBuilder("PEERS");

        int count = 0;
        for (Map.Entry<PeerInfo, Set<Integer>> entry : peersMap.entrySet()) {
            PeerInfo peer = entry.getKey();
            Set<Integer> pieces = entry.getValue();

            sb.append("|").append(peer.getIp()).append(":").append(peer.getPort()).append(":");
            sb.append(pieces.isEmpty() ? "none" : pieces.toString().replaceAll("[\\[\\] ]", ""));

            if (++count >= 3) break;
        }

        return sb.toString();
    }

    public static void main(String[] args) {
        try {
            TrackerServer tracker = new TrackerServer(8888);
            tracker.startServer();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

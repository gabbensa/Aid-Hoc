package com.test.demibluetoothchatting;

import android.util.Log;
import android.os.Handler;
import android.os.Looper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

public class ChatSocketHandler {
    private static ChatSocketHandler instance;
    private Socket socket;
    private ServerSocket serverSocket;
    private ReadWriteThread readWriteThread;
    private boolean connected = false;
    private ChatFragment chatFragment;
    private InetAddress groupOwnerAddress;
    private static final String TAG = "ChatSocketHandler";

    private ChatSocketHandler() {}

    public static synchronized ChatSocketHandler getInstance() {
        if (instance == null) {
            instance = new ChatSocketHandler();
        }
        return instance;
    }

    public void setChatFragment(ChatFragment fragment) {
        this.chatFragment = fragment;
    }

    public boolean isConnected() {
        return connected && isSocketConnected();
    }

    private void setConnected(boolean connected) {
        this.connected = connected;
        Log.d(TAG, "Connection state changed to: " + connected);
    }

    public void startServerSocket() {
        // Si le serverSocket existe déjà et n'est pas fermé, ne rien faire.
        if (serverSocket != null && !serverSocket.isClosed()) {
            Log.d(TAG, "Server socket already running, not restarting.");
            return;
        }

        // Fermer toute connexion existante
        closeExistingConnections();

        new Thread(() -> {
            try {
                Log.d(TAG, "Starting server socket on port 8888");
                serverSocket = new ServerSocket(8888);
                Log.d(TAG, "Server socket created, waiting for client...");
                socket = serverSocket.accept();
                Log.d(TAG, "Client connected from: " + socket.getInetAddress().getHostAddress());

                readWriteThread = new ReadWriteThread(socket);
                readWriteThread.start();
                setConnected(true);

                // Envoyer un message de test pour vérifier la connexion
                sendTestMessage();

            } catch (IOException e) {
                Log.e(TAG, "Error starting server socket", e);
                setConnected(false);
            }
        }).start();
    }

    public void startClientSocket(InetAddress hostAddress) {
        // Fermer toute connexion existante
        closeExistingConnections();

        new Thread(() -> {
            try {
                Log.d(TAG, "Starting client socket to " + hostAddress.getHostAddress());
                socket = new Socket();
                socket.connect(new InetSocketAddress(hostAddress, 8888), 10000); // Timeout de 10 secondes
                Log.d(TAG, "Connected to server: " + hostAddress.getHostAddress());

                readWriteThread = new ReadWriteThread(socket);
                readWriteThread.start();
                setConnected(true);

                // Envoyer un message de test pour vérifier la connexion
                sendTestMessage();

            } catch (IOException e) {
                Log.e(TAG, "Error connecting to server: " + e.getMessage(), e);
                setConnected(false);
            }
        }).start();
    }

    private void sendTestMessage() {
        new Thread(() -> {
            try {
                Log.d(TAG, "Sending test message");
                sendMessage("TEST_CONNECTION");
            } catch (Exception e) {
                Log.e(TAG, "Error sending test message", e);
            }
        }).start();
    }

    private void closeExistingConnections() {
        try {
            if (socket != null && !socket.isClosed()) {
                Log.d(TAG, "Closing existing socket");
                socket.close();
            }
            if (serverSocket != null && !serverSocket.isClosed()) {
                Log.d(TAG, "Closing existing server socket");
                serverSocket.close();
            }
            if (readWriteThread != null) {
                Log.d(TAG, "Interrupting existing read/write thread");
                readWriteThread.interrupt();
            }
        } catch (IOException e) {
            Log.e(TAG, "Error closing existing connections", e);
        }
        socket = null;
        serverSocket = null;
        readWriteThread = null;
    }

    public boolean sendMessage(String message) {
        if (readWriteThread != null) {
            try {
                Log.d("ChatSocketHandler", "Sending message: " + message);
                // Créer un nouveau thread pour envoyer le message
                new Thread(() -> {
                    try {
                        readWriteThread.write(message.getBytes());
                    } catch (Exception e) {
                        Log.e("ChatSocketHandler", "Error in thread sending message: " + e.getMessage(), e);
                        setConnected(false);
                    }
                }).start();
                return true;
            } catch (Exception e) {
                Log.e("ChatSocketHandler", "Error sending message: " + e.getMessage(), e);
                setConnected(false);
                return false;
            }
        } else {
            Log.e("ChatSocketHandler", "Cannot send message: readWriteThread is null");
            return false;
        }
    }

    public class ReadWriteThread extends Thread {
        private final Socket socket;
        private final InputStream inputStream;
        private final OutputStream outputStream;
        private final Handler mainHandler;

        public ReadWriteThread(Socket socket) throws IOException {
            this.socket = socket;
            this.inputStream = socket.getInputStream();
            this.outputStream = socket.getOutputStream();
            this.mainHandler = new Handler(Looper.getMainLooper());
            Log.d(TAG, "ReadWriteThread initialized");
        }

        @Override
        public void run() {
            Log.d(TAG, "ReadWriteThread started");
            byte[] buffer = new byte[1024];
            int bytes;

            while (!isInterrupted() && socket != null && !socket.isClosed()) {
                try {
                    // Vérifier si le socket est toujours connecté
                    if (!socket.isConnected()) {
                        Log.e(TAG, "Socket disconnected");
                        break;
                    }

                    // Lire les données
                    bytes = inputStream.read(buffer);
                    Log.d(TAG, "Read " + bytes + " bytes from socket");

                    if (bytes > 0) {
                        final String received = new String(buffer, 0, bytes);
                        Log.d(TAG, "Message received: " + received);

                        // Notifier le ChatFragment du message reçu
                        if (chatFragment != null) {
                            mainHandler.post(() -> {
                                try {
                                    chatFragment.onMessageReceived(received);
                                } catch (Exception e) {
                                    Log.e(TAG, "Error notifying ChatFragment: " + e.getMessage(), e);
                                }
                            });
                        } else {
                            Log.w(TAG, "ChatFragment is null, cannot deliver message");
                        }
                    } else if (bytes < 0) {
                        // -1 indique la fin du flux (connexion fermée)
                        Log.d(TAG, "End of stream reached, connection closed");
                        break;
                    }
                } catch (IOException e) {
                    if (!isInterrupted()) {
                        Log.e(TAG, "Error reading from socket: " + e.getMessage(), e);
                    }
                    break;
                }
            }

            // Si on sort de la boucle, c'est que la connexion est perdue
            Log.d(TAG, "ReadWriteThread exiting, connection lost or closed");
            setConnected(false);

            // Notifier le ChatFragment de la perte de connexion
            if (chatFragment != null) {
                mainHandler.post(() -> {
                    try {
                        // Vous devrez ajouter cette méthode à ChatFragment
                        // chatFragment.onConnectionLost();
                    } catch (Exception e) {
                        Log.e(TAG, "Error notifying ChatFragment of connection loss", e);
                    }
                });
            }
        }

        public void write(byte[] bytes) {
            try {
                if (socket != null && socket.isConnected() && !socket.isClosed() && outputStream != null) {
                    outputStream.write(bytes);
                    outputStream.flush(); // Assurez-vous que les données sont envoyées immédiatement
                    Log.d(TAG, "Message written to socket: " + bytes.length + " bytes");
                } else {
                    Log.e(TAG, "Cannot write to socket: socket is null, closed or disconnected");
                    setConnected(false);
                }
            } catch (IOException e) {
                Log.e(TAG, "Error writing to socket: " + e.getMessage(), e);
                setConnected(false);
            }
        }
    }

    public boolean isSocketConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    public void setGroupOwnerAddress(InetAddress address) {
        this.groupOwnerAddress = address;
        Log.d(TAG, "Group owner address set to: " + address.getHostAddress());
    }

    public void closeConnection() {
        Log.d(TAG, "Closing all connections");
        closeExistingConnections();
        setConnected(false);
    }
}

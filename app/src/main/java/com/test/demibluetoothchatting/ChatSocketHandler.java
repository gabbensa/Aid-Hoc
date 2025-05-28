package com.test.demibluetoothchatting;

import android.util.Log;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import android.content.Context;
import android.content.SharedPreferences;



import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;



public class ChatSocketHandler {
    private static ChatSocketHandler instance;
    private Socket socket;
    private ServerSocket serverSocket;
    private ReadWriteThread readWriteThread;
    private boolean connected = false;
    private ChatFragment chatFragment;
    private InetAddress groupOwnerAddress;
    private static final String TAG = "ChatSocketHandler";
    private InputStream inputStream;
    private OutputStream outputStream;

    // Modifier le type de pendingMessages
    private List<String> pendingMessages = new ArrayList<>();
    private List<ChatMessage> pendingChatMessages = new ArrayList<>();

    // Ajoutez ces variables de classe
    private List<String> undeliveredMessages = new ArrayList<>();
    private boolean otherDeviceInChat = true;

    private Context appContext;

    // Ajout d'une file d'attente pour les messages à envoyer quand la socket n'est pas prête
    private final List<String> pendingSystemMessages = new ArrayList<>();

    private ChatSocketHandler() {}

    public static synchronized ChatSocketHandler getInstance() {
        if (instance == null) {
            instance = new ChatSocketHandler();
        }
        return instance;
    }
    

    public void setChatFragment(ChatFragment fragment) {
        this.chatFragment = fragment;

        // If a new fragment is set and we have pending messages, deliver them
        if (fragment != null && !pendingMessages.isEmpty()) {
            Log.d(TAG, "Delivering " + pendingMessages.size() + " pending messages to new fragment");


            // Create a copy of pending messages to avoid concurrent modification
            List<String> messagesToDeliver = new ArrayList<>(pendingMessages);
            pendingMessages.clear();

            // Deliver each message with a small delay to ensure proper order
            for (int i = 0; i < messagesToDeliver.size(); i++) {
                final String message = messagesToDeliver.get(i);
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    if (fragment != null) {
                        fragment.onMessageReceived(message, true); // Mark as delayed message
                    }
                }, i * 100); // 100ms delay between messages
            }
        }
    }

    public boolean isConnected() {
        return connected && isSocketConnected();
    }

    private void setConnected(boolean connected) {
        this.connected = connected;
        Log.d(TAG, "Connection state changed to: " + connected);
    }

    public void startServerSocket() {
        if (serverSocket != null && !serverSocket.isClosed()) {
            Log.d(TAG, "Server socket already running, not restarting.");
            return;
        }
        if (isSocketConnected() && readWriteThread != null && readWriteThread.isAlive()) {
            Log.d(TAG, "Socket already connected, not starting server.");
            return;
        }
        new Thread(() -> {
            try {
                Log.d(TAG, "Starting server socket on port 8888");
                serverSocket = new ServerSocket(8888);
                Log.d(TAG, "Server socket created, waiting for client...");
                socket = serverSocket.accept();
                Log.d(TAG, "Client connected from: " + socket.getInetAddress().getHostAddress());
                
                // Initialize streams
                inputStream = socket.getInputStream();
                outputStream = socket.getOutputStream();
                
                readWriteThread = new ReadWriteThread(socket);
                readWriteThread.start();
                setConnected(true);
                // Envoyer les messages système en attente (dont REQUEST_USER_INFO)
                flushPendingSystemMessages();
                // Envoyer les informations d'utilisateur
                sendUserInfo();
            } catch (IOException e) {
                Log.e(TAG, "Error starting server socket", e);
                setConnected(false);
            }
        }).start();
    }

    public void startClientSocket(InetAddress hostAddress) {
        if (isSocketConnected() && readWriteThread != null && readWriteThread.isAlive()) {
            Log.d(TAG, "Socket already connected, not reconnecting.");
            return;
        }
        new Thread(() -> {
            try {
                Log.d(TAG, "Starting client socket to " + hostAddress.getHostAddress());
                socket = new Socket();
                socket.connect(new InetSocketAddress(hostAddress, 8888), 10000);
                Log.d(TAG, "Connected to server: " + hostAddress.getHostAddress());
                
                // Initialize streams
                inputStream = socket.getInputStream();
                outputStream = socket.getOutputStream();
                
                readWriteThread = new ReadWriteThread(socket);
                readWriteThread.start();
                setConnected(true);
                // Envoyer les messages système en attente (dont REQUEST_USER_INFO)
                flushPendingSystemMessages();
                // Envoyer les informations d'utilisateur
                sendUserInfo();
            } catch (IOException e) {
                Log.e(TAG, "Error connecting to server: " + e.getMessage(), e);
                setConnected(false);
            }
        }).start();
    }



    private void flushPendingSystemMessages() {
        if (readWriteThread != null && isConnected() && !pendingSystemMessages.isEmpty()) {
            Log.d(TAG, "Flushing " + pendingSystemMessages.size() + " pending system messages");
            for (String msg : pendingSystemMessages) {
                sendMessage(msg);
            }
            pendingSystemMessages.clear();
        }
    }

    // Rendre la méthode publique et améliorer la gestion des erreurs
    public void sendUserInfo() {
        if (appContext == null) {
            Log.e(TAG, "Cannot send user info: appContext is null");
            return;
        }

        SharedPreferences prefs = appContext.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        String username = prefs.getString("username", "");
        String deviceName = prefs.getString("deviceName", "");
        String deviceAddress = prefs.getString("deviceAddress", "");

        Log.d(TAG, "Preparing to send user info - username: " + username + 
                   ", deviceName: " + deviceName + 
                   ", deviceAddress: " + deviceAddress);

        if (username.isEmpty() || deviceName.isEmpty() || deviceAddress.isEmpty() || 
            deviceAddress.equals("02:00:00:00:00:00")) {
            Log.e(TAG, "Cannot send user info: missing or invalid data");
            return;
        }

        if (!isConnected()) {
            Log.e(TAG, "Cannot send user info: socket is not connected");
            return;
        }

        try {
            String userInfo = String.format("USER_INFO:%s:%s:%s", username, deviceName, deviceAddress);
            Log.d(TAG, "Sending user info: " + userInfo);

        } catch (Exception e) {
            Log.e(TAG, "Error sending user info: " + e.getMessage(), e);
        }
    }



    // Modifié pour ne pas fermer les connexions existantes
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

    // Ajoutez une méthode pour marquer l'état de l'autre appareil
    public void setOtherDeviceInChat(boolean inChat) {
        this.otherDeviceInChat = inChat;
        Log.d(TAG, "Other device in chat: " + inChat);
        
        // Si l'autre appareil revient dans le chat, envoyez les messages non livrés
        if (inChat && !undeliveredMessages.isEmpty()) {
            sendUndeliveredMessages();
        }
    }

    // Méthode pour envoyer les messages non livrés
    private void sendUndeliveredMessages() {
        if (isConnected() && !undeliveredMessages.isEmpty()) {
            Log.d(TAG, "Sending " + undeliveredMessages.size() + " undelivered messages");
            
            for (String message : undeliveredMessages) {
                // Préfixez le message pour indiquer qu'il s'agit d'un message différé
                sendMessage("DELAYED_MESSAGE:" + message);
            }
            undeliveredMessages.clear();
        }
    }

    // Modifiez la méthode sendMessage pour gérer les messages non livrés
    public boolean sendMessage(String message) {
        // Si le message commence par "DELAYED_MESSAGE:", c'est un message différé
        // que nous envoyons nous-mêmes, donc pas besoin de le stocker à nouveau
        boolean isDelayedMessage = message.startsWith("DELAYED_MESSAGE:");
        
        // Si l'autre appareil n'est pas dans le chat et ce n'est pas un message différé,
        // stockez le message pour une livraison ultérieure
        if (!otherDeviceInChat && !isDelayedMessage) {
            Log.d(TAG, "Other device not in chat, storing message for later delivery: " + message);
            undeliveredMessages.add(message);
            return true; // Retournez true car le message a été correctement traité
        }
        
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
                        
                        // Si le message n'est pas un message différé, stockez-le pour une livraison ultérieure
                        if (!isDelayedMessage) {
                            undeliveredMessages.add(message);
                        }
                    }
                }).start();
                return true;
            } catch (Exception e) {
                Log.e("ChatSocketHandler", "Error sending message: " + e.getMessage(), e);
                setConnected(false);
                
                // Si le message n'est pas un message différé, stockez-le pour une livraison ultérieure
                if (!isDelayedMessage) {
                    undeliveredMessages.add(message);
                }
                return false;
            }
        } else {
            Log.e("ChatSocketHandler", "Cannot send message: readWriteThread is null");
            
            // Si le message n'est pas un message différé, stockez-le pour une livraison ultérieure
            if (!isDelayedMessage) {
                undeliveredMessages.add(message);
            }
            return false;
        }
    }

    private class ReadWriteThread extends Thread {
        private final Socket socket;
        private final InputStream inputStream;
        private final OutputStream outputStream;
        private final Handler mainHandler;
        private boolean isRunning = true;

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

            while (isRunning && !isInterrupted() && socket != null && !socket.isClosed()) {
                try {
                    if (!socket.isConnected()) {
                        Log.e(TAG, "Socket disconnected");
                        handleDisconnection("Connection lost");
                        break;
                    }

                    bytes = inputStream.read(buffer);
                    if (bytes > 0) {
                        String message = new String(buffer, 0, bytes);
                        Log.d(TAG, "Received message: " + message);

                        // Handle disconnection request
                        if (message.equals("DISCONNECT_REQUEST")) {
                            handleDisconnection("Other device disconnected");
                            break;
                        }

                        // Handle delayed message
                        boolean isDelayed = message.startsWith("DELAYED:");
                        String messageToDeliver = isDelayed ? message.substring(8) : message;

                        mainHandler.post(() -> {
                            if (chatFragment != null) {
                                try {
                                    chatFragment.onMessageReceived(messageToDeliver, isDelayed);
                                } catch (Exception e) {
                                    Log.e(TAG, "Error notifying ChatFragment: " + e.getMessage(), e);
                                    pendingMessages.add(isDelayed ? "DELAYED:" + messageToDeliver : messageToDeliver);
                                }
                            } else {
                                Log.w(TAG, "ChatFragment is null, storing message for later delivery");
                                pendingMessages.add(isDelayed ? "DELAYED:" + messageToDeliver : messageToDeliver);
                            }
                        });
                    } else if (bytes < 0) {
                        Log.d(TAG, "End of stream reached, connection closed");
                        handleDisconnection("Connection closed");
                        break;
                    }
                } catch (IOException e) {
                    if (!isInterrupted()) {
                        Log.e(TAG, "Error reading from socket: " + e.getMessage(), e);
                        handleDisconnection("Connection error: " + e.getMessage());
                    }
                    break;
                }
            }

            Log.d(TAG, "ReadWriteThread exiting");
            setConnected(false);
        }

        private void handleDisconnection(String reason) {
            Log.d(TAG, "Handling disconnection: " + reason);
            isRunning = false;
            setConnected(false);
            
            mainHandler.post(() -> {
                if (chatFragment != null) {
                    // Update the status text to show disconnected
                    chatFragment.setStatus("Disconnected");
                    Toast.makeText(chatFragment.getContext(), reason, Toast.LENGTH_LONG).show();
                }
            });
        }

        public void write(byte[] bytes) {
            try {
                if (socket != null && socket.isConnected() && !socket.isClosed() && outputStream != null) {
                    outputStream.write(bytes);
                    outputStream.flush();
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

    // Modifié pour ne pas fermer les connexions
    public void closeConnection() {
        Log.d(TAG, "closeConnection called - keeping socket open for future use");
        // Ne fermez pas les connexions ici, juste marquer que le fragment n'est plus disponible
        chatFragment = null;
    }

    public void setAppContext(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public void disconnect() {
        Log.d(TAG, "Disconnecting all connections");
        setConnected(false);
        
        try {
            if (readWriteThread != null) {
                readWriteThread.interrupt();
                readWriteThread = null;
            }
            
            if (socket != null && !socket.isClosed()) {
                socket.close();
                socket = null;
            }
            
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
                serverSocket = null;
            }
            
            if (inputStream != null) {
                inputStream.close();
                inputStream = null;
            }
            
            if (outputStream != null) {
                outputStream.close();
                outputStream = null;
            }
            
            // Clear message lists
            pendingMessages.clear();
            undeliveredMessages.clear();
            pendingSystemMessages.clear();
            
            Log.d(TAG, "All connections closed successfully");
        } catch (IOException e) {
            Log.e(TAG, "Error during disconnection", e);
        }
    }

    public void setPendingMessages(ArrayList<ChatMessage> messages) {
        this.pendingChatMessages = messages;
        Log.d(TAG, "Set " + messages.size() + " pending chat messages");
    }

    public ArrayList<ChatMessage> getPendingMessages() {
        return new ArrayList<>(pendingChatMessages);
    }

    public void clearPendingMessages() {
        pendingChatMessages.clear();
    }
}

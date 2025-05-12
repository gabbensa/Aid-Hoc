package com.test.demibluetoothchatting;

import android.util.Log;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import android.content.Context;
import android.content.SharedPreferences;

import com.test.demibluetoothchatting.Database.DatabaseHelper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONObject;

public class ChatSocketHandler {
    private static ChatSocketHandler instance;
    private Socket socket;
    private ServerSocket serverSocket;
    private ReadWriteThread readWriteThread;
    private boolean connected = false;
    private ChatFragment chatFragment;
    private InetAddress groupOwnerAddress;
    private static final String TAG = "ChatSocketHandler";
    
    // Ajouter une liste pour stocker les messages en attente
    private List<String> pendingMessages = new ArrayList<>();

    // Ajoutez ces variables de classe
    private List<String> undeliveredMessages = new ArrayList<>();
    private boolean otherDeviceInChat = true; // Supposons initialement que l'autre appareil est dans le chat

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
        
        // Si un nouveau fragment est défini et que nous avons des messages en attente, les livrer
        if (fragment != null && !pendingMessages.isEmpty()) {
            Log.d(TAG, "Delivering " + pendingMessages.size() + " pending messages to new fragment");
            
            // Afficher une notification Toast
            int messageCount = pendingMessages.size();
            new Handler(Looper.getMainLooper()).post(() -> {
                Toast.makeText(fragment.getContext(), 
                    messageCount + " New messages received",
                    Toast.LENGTH_LONG).show();
            });
            
            for (String message : pendingMessages) {
                fragment.onMessageReceived(message);
            }
            pendingMessages.clear();
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

    // Nouvelle méthode pour envoyer un message système (ex: REQUEST_USER_INFO)
    public void sendSystemMessage(String message) {
        if (readWriteThread != null && isConnected()) {
            sendMessage(message);
        } else {
            Log.d(TAG, "Socket not ready, queuing system message: " + message);
            pendingSystemMessages.add(message);
        }
    }

    // Vider la file d'attente des messages système dès que la socket est prête
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
            sendMessage(userInfo);
        } catch (Exception e) {
            Log.e(TAG, "Error sending user info: " + e.getMessage(), e);
        }
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
                    if (!socket.isConnected()) {
                        Log.e(TAG, "Socket disconnected");
                        break;
                    }

                    bytes = inputStream.read(buffer);
                    if (bytes > 0) {
                        String received = new String(buffer, 0, bytes);
                        Log.d(TAG, "Message received: " + received);

                        if (received.startsWith("USER_INFO:")) {
                            Log.d(TAG, "Received USER_INFO: " + received);

                            String[] parts = received.split(":", 4);
                            if (parts.length == 4) {
                                String username = parts[1];
                                String deviceName = parts[2];
                                String deviceAddress = parts[3];

                                Log.d(TAG, "Saving user info to DB: " + username + " | " + deviceName + " | " + deviceAddress);

                                DatabaseHelper db = new DatabaseHelper(appContext);
                                db.associateUsernameWithDevice(username, deviceName, deviceAddress);
                            }

                        } else if (received.equals("REQUEST_USER_INFO")) {
                            Log.d(TAG, "Received REQUEST_USER_INFO, sending USER_INFO...");
                            sendUserInfo(); // renvoyer automatiquement

                        } else {
                            if (chatFragment != null) {
                                chatFragment.onMessageReceived(received);
                            } else {
                                pendingMessages.add(received);
                            }
                        }
                    }

                } catch (IOException e) {
                    Log.e(TAG, "Error reading from input stream", e);
                    break;
                }
            }

            Log.d(TAG, "ReadWriteThread exiting loop");
        }




        private void handleUserInfo(String userInfoJson) {
            try {
                // Parser le JSON
                JSONObject json = new JSONObject(userInfoJson);
                String username = json.getString("username");
                String deviceName = json.getString("deviceName");
                String deviceAddress = json.getString("deviceAddress");

                // Sauvegarder les informations dans la base de données
                DatabaseHelper dbHelper = new DatabaseHelper(appContext);
                dbHelper.associateUsernameWithDevice(username, deviceName, deviceAddress);

                Log.d(TAG, "User info saved: username=" + username + ", deviceName=" + deviceName + ", deviceAddress=" + deviceAddress);
            } catch (Exception e) {
                Log.e(TAG, "Error handling user info: " + e.getMessage(), e);
            }
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
}

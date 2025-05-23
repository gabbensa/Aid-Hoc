package com.test.demibluetoothchatting;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.test.demibluetoothchatting.Adapters.ChatAdapter;
import com.test.demibluetoothchatting.Database.DatabaseHelper;
import com.test.demibluetoothchatting.Receivers.NetworkChangeReceiver;

import java.util.ArrayList;
import java.util.List;

public class ChatFragment extends Fragment {
    private WifiP2pDevice connectingDevice;
    private WifiP2pManager manager;
    private WifiP2pManager.Channel channel;
    private Controller controller;
    private DatabaseHelper dbHelper;
    private NetworkChangeReceiver networkChangeReceiver;

    private TextView status;

    private RecyclerView recyclerView;
    private ChatAdapter chatAdapter;
    private ArrayList<ChatMessage> chatMessages;
    private TextView txtDeviceName;

    private String deviceAddress;
    private String deviceName;

    // Variable pour suivre si nous avons déjà ajouté un séparateur
    private boolean separatorAdded = false;

    // Liste pour stocker les nouveaux messages
    private List<String> newMessages = new ArrayList<>();
    // Handler pour effacer la surbrillance après un délai
    private Handler highlightHandler = new Handler();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.chat_fragment, container, false);

        manager = (WifiP2pManager) requireActivity().getSystemService(Context.WIFI_P2P_SERVICE);
        channel = manager.initialize(requireActivity(), requireActivity().getMainLooper(), null);
        dbHelper = new DatabaseHelper(getActivity());

        status = rootView.findViewById(R.id.status);
        EditText inputLayout = rootView.findViewById(R.id.input_message);
        recyclerView = rootView.findViewById(R.id.list);
        recyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        chatMessages = new ArrayList<>();
        chatAdapter = new ChatAdapter(chatMessages, "This Device");
        recyclerView.setAdapter(chatAdapter);
        txtDeviceName = rootView.findViewById(R.id.txt_device_name);

        rootView.findViewById(R.id.btn_back).setOnClickListener(v -> {
            if (getFragmentManager() != null) getFragmentManager().popBackStack();
        });

        // Add disconnect button handler
        rootView.findViewById(R.id.btn_disconnect).setOnClickListener(v -> {
            new AlertDialog.Builder(requireContext())
                .setTitle("Disconnect")
                .setMessage("Are you sure you want to disconnect from the chat?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    disconnectFromChat();
                })
                .setNegativeButton("No", null)
                .show();
        });

        controller = new Controller(handler);
        ChatSocketHandler.getInstance().setChatFragment(this);

        Bundle args = getArguments();
        if (args != null) {
            deviceName = args.getString("device_name");
            deviceAddress = args.getString("device_address");
            txtDeviceName.setText(deviceName);
            
            // Always create a new WifiP2pDevice when returning to chat
            connectingDevice = new WifiP2pDevice();
            connectingDevice.deviceName = deviceName;
            connectingDevice.deviceAddress = deviceAddress;
            
            loadMessagesFromDatabase(deviceName);
        }

        rootView.findViewById(R.id.btn_send).setOnClickListener(v -> {
            if (inputLayout.getText().toString().isEmpty()) {
                Toast.makeText(getActivity(), "Please input your message", Toast.LENGTH_SHORT).show();
            } else {
                sendMessage(inputLayout.getText().toString());
                inputLayout.setText("");
            }
        });

        // In ChatFragment, for example in onCreateView or onResume
        if (!ChatSocketHandler.getInstance().isConnected()) {
            manager.requestConnectionInfo(channel, info -> {
                if (info.groupFormed) {
                    if (info.isGroupOwner) {
                        ChatSocketHandler.getInstance().startServerSocket();
                    } else {
                        ChatSocketHandler.getInstance().startClientSocket(info.groupOwnerAddress);
                        setStatus("Joining chat...");
                    }
                } else {
                    setStatus("Waiting for group formation...");
                }
            });
        } else {
            Log.d("ChatFragment", "Connection already established. Not reinitializing.");
            setStatus("Connected");
        }

        return rootView;
    }

    @SuppressLint("MissingPermission")
    private void connectToSelectedDevice(String deviceAddress) {
        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = deviceAddress;

        manager.connect(channel, config, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Toast.makeText(getActivity(), "Connecting to " + deviceName, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(int reason) {
                Toast.makeText(getActivity(), "Connection failed: " + reason, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadMessagesFromDatabase(String deviceName) {
        String currentUserName = getUserName();
        chatMessages = dbHelper.getMessagesForDevice(currentUserName, deviceName);
        chatAdapter = new ChatAdapter(chatMessages, currentUserName);
        recyclerView.setAdapter(chatAdapter);
        recyclerView.scrollToPosition(chatMessages.size() - 1);
    }

    private void sendMessage(String message) {
        // Vérifier d'abord si ChatSocketHandler est connecté
        if (!ChatSocketHandler.getInstance().isConnected()) {
            Log.d("ChatFragment", "Socket not connected, trying to reconnect...");

            // Tenter de reconnecter
            manager.requestConnectionInfo(channel, info -> {
                if (info.groupFormed) {
                    if (info.isGroupOwner) {
                        ChatSocketHandler.getInstance().startServerSocket();
                    } else {
                        ChatSocketHandler.getInstance().startClientSocket(info.groupOwnerAddress);
                    }

                    // Réessayer d'envoyer après un court délai
                    new Handler().postDelayed(() -> {
                        if (ChatSocketHandler.getInstance().isConnected()) {
                            sendMessageViaSocket(message);
                        } else {
                            Toast.makeText(getActivity(), "Connection was lost!", Toast.LENGTH_SHORT).show();
                        }
                    }, 2000);
                } else {
                    Toast.makeText(getActivity(), "Connection was lost!", Toast.LENGTH_SHORT).show();
                }
            });
            return;
        }

        sendMessageViaSocket(message);
    }

    private void sendMessageViaSocket(String message) {
        boolean sent = ChatSocketHandler.getInstance().sendMessage(message);

        if (sent) {
            // Enregistrer le message dans la base de données
            if (connectingDevice != null) {
                // Obtenir le nom d'utilisateur actuel au lieu d'utiliser "This Device"
                String currentUserName = getUserName();

                dbHelper.insertMessage(currentUserName, connectingDevice.deviceName, message, getCurrentTimestamp(), 0);
                loadMessagesFromDatabase(connectingDevice.deviceName);

                // Déclencher une synchronisation immédiate après l'envoi d'un message
                triggerImmediateSync();
            }
        } else {
            Toast.makeText(getActivity(), "Failed to send message", Toast.LENGTH_SHORT).show();
        }
    }

    private String getUserName() {
        // Essayer d'obtenir le nom d'utilisateur des préférences partagées
        SharedPreferences prefs = requireActivity().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        String userName = prefs.getString("userName", null);

        // Si aucun nom d'utilisateur n'est défini, utiliser le nom du modèle de l'appareil
        if (userName == null || userName.isEmpty()) {
            userName = android.os.Build.MODEL;
            // Sauvegarder ce nom pour une utilisation future
            prefs.edit().putString("userName", userName).apply();
        }

        return userName;
    }

    private void triggerImmediateSync() {
        // Créer une contrainte pour s'assurer que l'appareil est connecté à Internet
        androidx.work.Constraints constraints = new androidx.work.Constraints.Builder()
                .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                .build();

        // Créer une demande de travail unique avec les contraintes
        androidx.work.OneTimeWorkRequest syncWork = new androidx.work.OneTimeWorkRequest.Builder(com.test.demibluetoothchatting.Service.MessageSyncWorker.class)
                .setConstraints(constraints)
                .build();

        // Envoyer la demande de travail au WorkManager
        androidx.work.WorkManager.getInstance(requireContext()).enqueueUniqueWork(
                "immediate_sync",
                androidx.work.ExistingWorkPolicy.REPLACE,
                syncWork
        );
    }

    private final Handler handler = new Handler(msg -> {
        switch (msg.what) {
            case MainActivity.state_change:
                switch (msg.arg1) {
                    case Controller.CONNECTED:
                        setStatus("Connected to: " + (connectingDevice != null ? connectingDevice.deviceName : "device"));
                        if (connectingDevice != null)
                            loadMessagesFromDatabase(connectingDevice.deviceName);
                        break;
                    case Controller.CONNECTING:
                        setStatus("Connecting...");
                        break;
                    case Controller.LISTEN:
                    case Controller.NONE:
                        setStatus("Not connected");
                        break;
                }
                break;

            case MainActivity.message_write:
                String writeMessage = new String((byte[]) msg.obj);
                if (connectingDevice != null) {
                    dbHelper.insertMessage("This Device", connectingDevice.deviceName, writeMessage, getCurrentTimestamp(), 0);
                    loadMessagesFromDatabase(connectingDevice.deviceName);
                }
                break;

            case MainActivity.message_read:
                if (msg.arg1 > 0) {
                    try {
                        String readMessage = new String((byte[]) msg.obj, 0, msg.arg1);
                        if (connectingDevice != null) {
                            dbHelper.insertMessage(connectingDevice.deviceName, "This Device", readMessage, getCurrentTimestamp(), 0);
                            loadMessagesFromDatabase(connectingDevice.deviceName);
                        }
                    } catch (Exception e) {
                        Log.e("ChatFragment", "Error decoding received message: " + e.getMessage());
                    }
                } else {
                    Log.w("ChatFragment", "Received message with invalid length: " + msg.arg1);
                }
                break;

            case MainActivity.MESSAGE_DEVICE_OBJECT:
                connectingDevice = (WifiP2pDevice) msg.obj;
                if (connectingDevice != null) {
                    loadMessagesFromDatabase(connectingDevice.deviceName);
                    Toast.makeText(getActivity(), "Connected to " + connectingDevice.deviceName, Toast.LENGTH_SHORT).show();
                }
                break;

            case MainActivity.message_toast:
                Toast.makeText(getActivity(), "Error: " + msg.getData().getString("toast"), Toast.LENGTH_SHORT).show();
                break;
        }
        return false;
    });

    public void onMessageReceived(String message, boolean isDelayed) {
        requireActivity().runOnUiThread(() -> {
            if (connectingDevice != null) {
                // Add visual indication for delayed messages
                String displayMessage = message;
                if (isDelayed) {
                    displayMessage =  message; // Add an envelope emoji for delayed messages
                }

                // Get current username
                String currentUserName = getUserName();

                // Insert message with timestamp
                String timestamp = getCurrentTimestamp();
                dbHelper.insertMessage(connectingDevice.deviceName, currentUserName, displayMessage, timestamp, isDelayed ? 1 : 0);
                
                // Reload messages and scroll to bottom
                loadMessagesFromDatabase(connectingDevice.deviceName);
                recyclerView.scrollToPosition(chatMessages.size() - 1);
                
                // Show toast for delayed messages
                if (isDelayed) {
                    Toast.makeText(getActivity(), "Received delayed message", Toast.LENGTH_SHORT).show();
                }
            } else {
                Log.w("ChatFragment", "No connected device, cannot save message.");
            }
        });
    }

    // Gardez la méthode originale pour la compatibilité
    public void onMessageReceived(String message) {
        onMessageReceived(message, false);
    }

    private String getCurrentTimestamp() {
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(new java.util.Date());
    }

    private void setStatus(String s) {
        status.setText(s);
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (controller != null) controller.stop();
        if (networkChangeReceiver != null) requireActivity().unregisterReceiver(networkChangeReceiver);

        // Nettoyer le handler
        highlightHandler.removeCallbacksAndMessages(null);

        // Informer ChatSocketHandler que ce fragment est détruit
        ChatSocketHandler.getInstance().setChatFragment(null);
    }

    private void attemptReconnection() {
        if (connectingDevice != null) {
            Log.d("ChatFragment", "Attempting to reconnect...");
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                connectToSelectedDevice(connectingDevice.deviceAddress);
            }, 5000); // Attendre 5 secondes avant de réessayer
        }
    }

    private void disconnectFromChat() {
        // Notify the other device about disconnection
        if (ChatSocketHandler.getInstance().isConnected()) {
            ChatSocketHandler.getInstance().sendMessage("DISCONNECT_REQUEST");
        }

        // Clean up the socket connection
        ChatSocketHandler.getInstance().disconnect();
        
        // Disconnect from WiFi Direct
        if (manager != null && channel != null) {
            manager.removeGroup(channel, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    Log.d("ChatFragment", "Successfully removed from WiFi Direct group");
                    // After removing from group, cancel the connection
                    manager.cancelConnect(channel, new WifiP2pManager.ActionListener() {
                        @Override
                        public void onSuccess() {
                            Log.d("ChatFragment", "Successfully cancelled WiFi Direct connection");
                            requireActivity().runOnUiThread(() -> {
                                Toast.makeText(getActivity(), "Disconnected from chat", Toast.LENGTH_SHORT).show();
                                // Return to previous screen
                                if (getFragmentManager() != null) {
                                    getFragmentManager().popBackStack();
                                }
                            });
                        }

                        @Override
                        public void onFailure(int reason) {
                            Log.e("ChatFragment", "Failed to cancel WiFi Direct connection: " + reason);
                            requireActivity().runOnUiThread(() -> {
                                Toast.makeText(getActivity(), "Error disconnecting from WiFi Direct", Toast.LENGTH_SHORT).show();
                            });
                        }
                    });
                }

                @Override
                public void onFailure(int reason) {
                    Log.e("ChatFragment", "Failed to remove from WiFi Direct group: " + reason);
                    requireActivity().runOnUiThread(() -> {
                        Toast.makeText(getActivity(), "Error removing from group", Toast.LENGTH_SHORT).show();
                    });
                }
            });
        } else {
            // If manager or channel is null, just show the disconnection message and return
            Toast.makeText(getActivity(), "Disconnected from chat", Toast.LENGTH_SHORT).show();
            if (getFragmentManager() != null) {
                getFragmentManager().popBackStack();
            }
        }
    }
}





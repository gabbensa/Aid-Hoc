package com.test.demibluetoothchatting;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
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
import androidx.core.app.ActivityCompat;
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
    private boolean isDisconnecting = false;

    // Variable pour suivre si nous avons déjà ajouté un séparateur
    private boolean separatorAdded = false;

    // Liste pour stocker les nouveaux messages
    private List<String> newMessages = new ArrayList<>();
    // Handler pour effacer la surbrillance après un délai
    private Handler highlightHandler = new Handler();

    private String remoteUsername = null;

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
        String currentUserName = getUserName();
        chatAdapter = new ChatAdapter(chatMessages, currentUserName);
        recyclerView.setAdapter(chatAdapter);
        recyclerView.setItemAnimator(null);
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
                    disconnect();
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
            connectingDevice = new WifiP2pDevice();
            connectingDevice.deviceName = deviceName;
            connectingDevice.deviceAddress = deviceAddress;
            // Load the remote username before loading messages
            loadRemoteUsername();
            // If ChatSocketHandler has a pending remote username, set it now
            ChatSocketHandler handler = ChatSocketHandler.getInstance();
            java.lang.reflect.Field field = null;
            try {
                field = handler.getClass().getDeclaredField("pendingRemoteUsername");
                field.setAccessible(true);
                String pendingRemoteUsername = (String) field.get(handler);
                if (pendingRemoteUsername != null) {
                    setRemoteUsername(pendingRemoteUsername);
                    field.set(handler, null);
                    Log.d("ChatFragment", "Set pending remote username from handler: " + pendingRemoteUsername);
                }
            } catch (Exception e) {
                Log.e("ChatFragment", "Reflection error accessing pendingRemoteUsername: " + e.getMessage());
            }
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
        // First, check if we're already in a group
        manager.requestGroupInfo(channel, group -> {
            if (group != null && group.isGroupOwner()) {
                // We're already a group owner, don't try to connect
                Log.d("ChatFragment", "Already a group owner, waiting for client to connect");
                return;
            }

            // Check if we're already connected to this device
            if (group != null && group.getClientList().contains(connectingDevice)) {
                Log.d("ChatFragment", "Already connected to this device");
                return;
            }

            // Remove any existing group first
            manager.removeGroup(channel, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    // Now try to connect
                    WifiP2pConfig config = new WifiP2pConfig();
                    config.deviceAddress = deviceAddress;
                    
                    // Add a small random delay to prevent simultaneous connection attempts
                    new Handler().postDelayed(() -> {
                        manager.connect(channel, config, new WifiP2pManager.ActionListener() {
                            @Override
                            public void onSuccess() {
                                Log.d("ChatFragment", "Connection request sent to " + deviceName);
                                setStatus("Connecting to " + deviceName);
                            }

                            @Override
                            public void onFailure(int reason) {
                                Log.e("ChatFragment", "Connection failed: " + reason);
                                setStatus("Connection failed");
                                // Try to reconnect after a delay
                                new Handler().postDelayed(() -> {
                                    if (isAdded() && !isDisconnecting) {
                                        connectToSelectedDevice(deviceAddress);
                                    }
                                }, 3000);
                            }
                        });
                    }, (long) (Math.random() * 1000)); // Random delay between 0-1 second
                }

                @Override
                public void onFailure(int reason) {
                    Log.e("ChatFragment", "Failed to remove group: " + reason);
                    // Try to connect anyway
                    WifiP2pConfig config = new WifiP2pConfig();
                    config.deviceAddress = deviceAddress;
                    manager.connect(channel, config, null);
                }
            });
        });
    }

    private void loadMessagesFromDatabase(String deviceName) {
        String currentUserName = getUserName();
        String remoteName = (remoteUsername != null) ? remoteUsername : deviceName;
        
        Log.d("ChatFragment", "Loading messages for: " + currentUserName + " and " + remoteName);
        
        // Get messages from database using usernames
        ArrayList<ChatMessage> newMessages = dbHelper.getMessagesForDevice(currentUserName, remoteName);
        
        // Update the chat view
        chatMessages.clear();
        chatMessages.addAll(newMessages);
        chatAdapter.notifyDataSetChanged();
        if (!chatMessages.isEmpty()) {
            recyclerView.scrollToPosition(chatMessages.size() - 1);
        }
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
        if (remoteUsername == null) {
            Log.w("ChatFragment", "Attempted to send message but remoteUsername is null!");
            Toast.makeText(getActivity(), "Not ready, please wait for connection.", Toast.LENGTH_SHORT).show();
            return;
        }
        boolean sent = ChatSocketHandler.getInstance().sendMessage(message);
        if (sent) {
            if (connectingDevice != null) {
                String currentUserName = getUserName();
                String receiverName = remoteUsername;
                String timestamp = getCurrentTimestamp();
                Log.d("ChatFragment", "Saving message: sender=" + currentUserName + ", receiver=" + receiverName);
                ChatMessage chatMessage = new ChatMessage(0, currentUserName, receiverName, message, timestamp);
                chatMessages.add(chatMessage);
                chatAdapter.notifyItemInserted(chatMessages.size() - 1);
                recyclerView.scrollToPosition(chatMessages.size() - 1);
                dbHelper.insertMessage(currentUserName, receiverName, message, timestamp, 0);
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
                    String currentUserName = getUserName();
                    String receiverName = remoteUsername;
                    String timestamp = getCurrentTimestamp();
                    dbHelper.insertMessage(currentUserName, receiverName, writeMessage, timestamp, 0);
                    loadMessagesFromDatabase(connectingDevice.deviceName);
                }
                break;

            case MainActivity.message_read:
                if (msg.arg1 > 0) {
                    try {
                        String readMessage = new String((byte[]) msg.obj, 0, msg.arg1);
                        if (connectingDevice != null) {
                            String currentUserName = getUserName();
                            String senderName = remoteUsername;
                            String timestamp = getCurrentTimestamp();
                            dbHelper.insertMessage(senderName, currentUserName, readMessage, timestamp, 0);
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
            if (remoteUsername == null) {
                Log.w("ChatFragment", "Attempted to receive message but remoteUsername is null!");
                Toast.makeText(getActivity(), "Not ready, please wait for connection.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (connectingDevice != null) {
                String currentUserName = getUserName();
                String senderName = remoteUsername;
                String timestamp = getCurrentTimestamp();
                Log.d("ChatFragment", "Saving message: sender=" + senderName + ", receiver=" + currentUserName);
                dbHelper.insertMessage(senderName, currentUserName, message, timestamp, isDelayed ? 1 : 0);
                ChatMessage chatMessage = new ChatMessage(0, senderName, currentUserName, message, timestamp);
                chatMessages.add(chatMessage);
                chatAdapter.notifyItemInserted(chatMessages.size() - 1);
                recyclerView.scrollToPosition(chatMessages.size() - 1);
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

    public void setStatus(String s) {
        if (status != null) {
            status.setText(s);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        ChatSocketHandler.getInstance().setChatFragment(this);
        // Reload messages when resuming the fragment
        if (connectingDevice != null) {
            loadMessagesFromDatabase(connectingDevice.deviceName);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        // Save the current state
        if (connectingDevice != null) {
            loadMessagesFromDatabase(connectingDevice.deviceName);
        }
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

        if (isDisconnecting) {
            // If we're disconnecting, make sure we clean up
            ChatSocketHandler.getInstance().disconnect();
            if (manager != null && channel != null) {
                try {
                    if (ActivityCompat.checkSelfPermission(requireContext(),
                            Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        manager.removeGroup(channel, null);
                    }
                } catch (SecurityException e) {
                    Log.e("ChatFragment", "Security exception in onDestroy: " + e.getMessage());
                }
            }
        }
    }

    private void attemptReconnection() {
        if (connectingDevice != null) {
            Log.d("ChatFragment", "Attempting to reconnect...");
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                connectToSelectedDevice(connectingDevice.deviceAddress);
            }, 5000); // Attendre 5 secondes avant de réessayer
        }
    }

    private void disconnect() {
        Log.d("ChatFragment", "Disconnecting...");
        isDisconnecting = true;
        
        // First close the socket connection
        ChatSocketHandler.getInstance().disconnect();
        
        // Then remove the group
        if (manager != null && channel != null) {
            try {
                if (ActivityCompat.checkSelfPermission(requireContext(), 
                        Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    manager.removeGroup(channel, new WifiP2pManager.ActionListener() {
                        @Override
                        public void onSuccess() {
                            Log.d("ChatFragment", "Successfully removed group");
                            navigateBackToMessages();
                        }

                        @Override
                        public void onFailure(int reason) {
                            Log.e("ChatFragment", "Failed to remove group: " + reason);
                            // Force disconnect by removing the group again
                            manager.removeGroup(channel, null);
                            navigateBackToMessages();
                        }
                    });
                }
            } catch (SecurityException e) {
                Log.e("ChatFragment", "Security exception during disconnect: " + e.getMessage());
                navigateBackToMessages();
            }
        } else {
            navigateBackToMessages();
        }
    }

    private void navigateBackToMessages() {
        if (isAdded() && getActivity() != null) {
            requireActivity().runOnUiThread(() -> {
                try {
                    // Clear any pending messages or states
                    ChatSocketHandler.getInstance().disconnect();
                    
                    // Navigate back to messages tab
                    requireActivity().getSupportFragmentManager().popBackStack();
                    
                    // Show a success message
                    Toast.makeText(requireContext(), 
                        "Disconnected successfully", 
                        Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Log.e("ChatFragment", "Error navigating back: " + e.getMessage());
                }
            });
        }
    }

    public void setRemoteUsername(String username) {
        Log.d("ChatFragment", "Remote username set to: " + username + ", deviceAddress: " + deviceAddress);
        this.remoteUsername = username;
        if (deviceAddress != null) {
            SharedPreferences prefs = requireActivity().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
            prefs.edit().putString("remoteUsername_" + deviceAddress, username).apply();
            Log.d("ChatFragment", "Saved remoteUsername_" + deviceAddress + " = " + username);
        } else {
            Log.w("ChatFragment", "deviceAddress is null when trying to save remote username!");
        }
        // Reload messages with the new username
        if (connectingDevice != null) {
            loadMessagesFromDatabase(connectingDevice.deviceName);
        }
    }

    private void loadRemoteUsername() {
        if (deviceAddress != null) {
            SharedPreferences prefs = requireActivity().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
            this.remoteUsername = prefs.getString("remoteUsername_" + deviceAddress, null);
            Log.d("ChatFragment", "Loaded remote username: " + this.remoteUsername);
        }
    }

    public String getRemoteUsername() {
        return remoteUsername;
    }

    // Helper to get username from prefs for a device
    private String getUserNameFromPrefsForDevice(String deviceAddress, String fallback) {
        if (deviceAddress == null) return fallback;
        SharedPreferences prefs = requireActivity().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        String username = prefs.getString("remoteUsername_" + deviceAddress, null);
        return username != null ? username : fallback;
    }

    public String getDeviceAddress() {
        return deviceAddress;
    }
}





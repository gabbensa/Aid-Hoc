package com.test.demibluetoothchatting.Tabs;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager.ActionListener;
import android.net.wifi.p2p.WifiP2pManager.Channel;
import android.net.wifi.p2p.WifiP2pManager.PeerListListener;
import android.net.wifi.p2p.WifiP2pManager.ConnectionInfoListener;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.ExistingWorkPolicy;

import com.test.demibluetoothchatting.ChatFragment;
import com.test.demibluetoothchatting.ChatMessage;
import com.test.demibluetoothchatting.ChatSocketHandler;
import com.test.demibluetoothchatting.DevicesActivity;
import com.test.demibluetoothchatting.R;
import com.test.demibluetoothchatting.WiFiDirectBroadcastReceiver;
import com.test.demibluetoothchatting.Database.DatabaseHelper;
import com.test.demibluetoothchatting.Service.MessageSyncWorker;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo;
import android.net.wifi.p2p.nsd.WifiP2pServiceInfo;

import java.util.HashSet;
import java.util.Set;

public class messages_tab extends Fragment implements WiFiDirectBroadcastReceiver.WiFiDirectHandler
{

    private WifiP2pManager manager;
    private WifiP2pManager.Channel channel;
    private WifiP2pManager.PeerListListener peerListListener;
    private IntentFilter intentFilter;
    private WiFiDirectBroadcastReceiver wifiDirectReceiver;

    private ArrayAdapter<String> availableDevicesAdapter;
    private ArrayAdapter<String> connectedDevicesAdapter;
    private List<WifiP2pDevice> peers = new ArrayList<>();
    private List<WifiP2pDevice> connectedPeers = new ArrayList<>();
    private static List<String> connectedDeviceAddresses = new ArrayList<>();

    private WifiP2pDevice localDevice;
    private static final int PERMISSION_REQUEST_CODE = 100;

    private boolean isFieldUser = false;
    private Handler autoSearchHandler = new Handler();
    private static final long AUTO_SEARCH_INTERVAL = 10000; // 10 secondes entre chaque recherche

    private List<WifiP2pDevice> appDevices = new ArrayList<>();

    private boolean isConnecting = false;
    private String lastAttemptedConnection = null;
    private static final long CONNECTION_COOLDOWN = 5000; // 5 seconds cooldown between connection attempts
    private long lastConnectionAttempt = 0;

    // Handler and Runnable for connection timeout
    private Handler connectionTimeoutHandler = new Handler(Looper.getMainLooper());
    private Runnable connectionTimeoutRunnable;
    private static final int CONNECTION_TIMEOUT_MS = 7000; // 7 seconds
    private int currentDeviceIndex = 0;

    private Set<String> attemptedDeviceAddresses = new HashSet<>();
    private List<WifiP2pDevice> lastAvailableDevices = new ArrayList<>();
    private boolean isTryingDevices = false;

    public messages_tab() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View main_view = inflater.inflate(R.layout.messages_tab, container, false);


        // Vérifier si nous sommes un utilisateur "field"
        SharedPreferences prefs = getActivity().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        String userType = prefs.getString("userType", "");
        isFieldUser = "field".equals(userType);


        // Initialize ListView and adapter for available devices
        ListView deviceList = main_view.findViewById(R.id.discoveredDeviceList);
        availableDevicesAdapter = new ArrayAdapter<>(requireActivity(),
                android.R.layout.simple_list_item_1, new ArrayList<>());
        deviceList.setAdapter(availableDevicesAdapter);
        availableDevicesAdapter.add("No devices found");

        deviceList.setOnItemClickListener((parent, view, position, id) -> {
            String item = availableDevicesAdapter.getItem(position);


            if (item != null && item.contains("\n")) {
                String[] parts = item.split("\n");
                if (parts.length == 2) {
                    String selectedAddress = parts[1].trim();

                    for (WifiP2pDevice device : peers) {
                        if (device.deviceAddress.equals(selectedAddress)) {
                            Log.d("MessagesTab", "Appareil trouvé, tentative de connexion à : " + device.deviceName);
                            connectToDevice(device.deviceAddress);
                            return;
                        }
                    }


                }
            }
        });



        // Initialize ListView and adapter for connected devices
        ListView connectedDeviceList = main_view.findViewById(R.id.connectedDeviceList);
        connectedDevicesAdapter = new ArrayAdapter<>(requireActivity(),
                android.R.layout.simple_list_item_1, new ArrayList<>());
        connectedDeviceList.setAdapter(connectedDevicesAdapter);
        connectedDevicesAdapter.add("No connected devices");

        // Set up click listener for connected devices
        connectedDeviceList.setOnItemClickListener((parent, view, position, id) -> {
            String deviceInfo = connectedDevicesAdapter.getItem(position);
            if (deviceInfo != null) {
                Log.d("MessagesTab", "Selected connected device: " + deviceInfo);
                // Extract device info from the item string
                String[] parts = deviceInfo.split("\n");
                if (parts.length > 1) {
                    String deviceName = parts[0];
                    String deviceAddress = parts[1];
                    openChatFragment(deviceName, deviceAddress);
                }
            }
        });

        // Initialize WiFi P2P
        try {
            manager = (WifiP2pManager) requireActivity().getSystemService(Context.WIFI_P2P_SERVICE);
            if (manager != null) {
                channel = manager.initialize(requireActivity(), requireActivity().getMainLooper(), null);
                if (channel != null) {
                    Log.d("MessagesTab", "Channel initialized successfully");
                    // Setup service discovery
                    setupServiceDiscovery();
                } else {
                    Log.e("MessagesTab", "Failed to initialize channel");
                }
            } else {
                Log.e("MessagesTab", "Failed to get WifiP2pManager");
            }

            // Initialize intent filter for WiFi P2P actions
            intentFilter = new IntentFilter();
            intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
            intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
            intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
            intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

        } catch (Exception e) {
            Log.e("MessagesTab", "Error initializing WiFi Direct: " + e.getMessage());
            Toast.makeText(getContext(), "Error initializing WiFi Direct", Toast.LENGTH_LONG).show();
        }

        // Si c'est un utilisateur field, démarrer la recherche automatique
        if (isFieldUser) {
            startAutoDeviceDiscovery();
        }

        Log.e("MessagesTab", "***** STARTING SYNC CONFIGURATION *****");
        scheduleMessageSync();
        Log.e("MessagesTab", "***** SYNC CONFIGURATION COMPLETE *****");

        return main_view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Lancer automatiquement la recherche d'appareils après un court délai
        // pour s'assurer que tous les composants sont initialisés
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isAdded() && getActivity() != null) {
                    Log.d("MessagesTab", "Starting automatic device discovery");
                    startAutoDeviceDiscovery();
                }
            }
        }, 1000); // Délai de 1 seconde pour s'assurer que tout est bien initialisé
    }

    private boolean isWifiDirectSupported() {
        PackageManager packageManager = requireActivity().getPackageManager();
        return packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT);
    }



    private void startDeviceDiscovery() {
        Log.d("MessagesTab", "Starting device discovery");

        // Vérifier les permissions avant de démarrer la découverte

        boolean allPermissionsGranted = true;

        String[] requiredPermissions = {
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.CHANGE_WIFI_STATE,
                Manifest.permission.INTERNET
        };

        for (String permission : requiredPermissions) {
            int status = ContextCompat.checkSelfPermission(requireContext(), permission);
            Log.d("MessagesTab", "Permission " + permission + " status: " + (status == PackageManager.PERMISSION_GRANTED ? "GRANTED" : "DENIED"));
            if (status != PackageManager.PERMISSION_GRANTED) {
                allPermissionsGranted = false;
            }
        }

        if (!allPermissionsGranted) {
            Log.e("MessagesTab", "Missing required permissions");
            Toast.makeText(getActivity(), "Missing required permissions", Toast.LENGTH_SHORT).show();
            return;
        }

        Log.d("MessagesTab", "All permissions are granted");

        // Vérifier si le Wi-Fi est activé
        WifiManager wifiManager = (WifiManager) requireContext().getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager != null && !wifiManager.isWifiEnabled()) {
            Log.e("MessagesTab", "Wi-Fi is disabled");
            Toast.makeText(getActivity(), "Please enable Wi-Fi to discover devices", Toast.LENGTH_LONG).show();

            // Ouvrir les paramètres Wi-Fi
            Intent intent = new Intent(Settings.ACTION_WIFI_SETTINGS);
            startActivity(intent);
            return;
        }


        // Arrêter toute découverte en cours avant d'en démarrer une nouvelle
        manager.stopPeerDiscovery(channel, new WifiP2pManager.ActionListener() {
            @SuppressLint("MissingPermission")
            @Override
            public void onSuccess() {
                // Attendre un court instant avant de démarrer une nouvelle découverte
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    // Démarrer la découverte après avoir arrêté la précédente
                    manager.discoverPeers(channel, new WifiP2pManager.ActionListener() {
                        @Override
                        public void onSuccess() {
                            Log.d("MessagesTab", "Discovery initiated successfully");

                        }

                        @Override
                        public void onFailure(int reason) {
                            String errorMsg = getErrorMessage(reason);
                            Log.e("MessagesTab", "Discovery failed: " + errorMsg);
                            Toast.makeText(getActivity(), "Discovery failed: " + errorMsg, Toast.LENGTH_SHORT).show();
                        }
                    });
                }, 500); // Délai de 500ms
            }

            @SuppressLint("MissingPermission")
            @Override
            public void onFailure(int reason) {
                // Même si l'arrêt échoue, essayer de démarrer une nouvelle découverte
                manager.discoverPeers(channel, new WifiP2pManager.ActionListener() {
                    @Override
                    public void onSuccess() {
                        Log.d("MessagesTab", "Discovery initiated successfully");
                        Toast.makeText(getActivity(), "Discovery started", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onFailure(int reason) {
                        String errorMsg = getErrorMessage(reason);
                        Log.e("MessagesTab", "Discovery failed: " + errorMsg);
                        Toast.makeText(getActivity(), "Discovery failed: " + errorMsg, Toast.LENGTH_SHORT).show();

                    }
                });
            }
        });
    }



    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int i = 0; i < permissions.length; i++) {
                String permission = permissions[i];
                boolean granted = grantResults[i] == PackageManager.PERMISSION_GRANTED;
                if (!granted) {
                    allGranted = false;
                }
            }

            if (allGranted) {
                startDeviceDiscovery();
            } else {
                Log.e("MessagesTab", "Some permissions were denied");
                Toast.makeText(getContext(),
                        "WiFi Direct requires all permissions to function. Please grant them in Settings.",
                        Toast.LENGTH_LONG).show();

                // Show settings dialog if permissions are permanently denied
                if (!shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
                    showSettingsDialog();
                }
            }
        }
    }

    private void showSettingsDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Permissions Required")
                .setMessage("WiFi Direct requires location and nearby devices permissions to function. " +
                        "Please grant these permissions in Settings.")
                .setPositiveButton("Settings", (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    Uri uri = Uri.fromParts("package", requireContext().getPackageName(), null);
                    intent.setData(uri);
                    startActivity(intent);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private String getErrorMessage(int reason) {
        switch (reason) {
            case WifiP2pManager.P2P_UNSUPPORTED:
                return "P2P is not supported on this device";
            case WifiP2pManager.BUSY:
                return "System is busy, please try again";
            case WifiP2pManager.ERROR:
                return "Internal error occurred";
            default:
                return String.valueOf(reason);
        }
    }

    private void connectToDevice(String deviceAddress) {
        Log.d("MessagesTab", "Attempting to connect to device: " + deviceAddress);

        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = deviceAddress;
        try {
            if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) {
                manager.connect(channel, config, new WifiP2pManager.ActionListener() {
                    @Override
                    public void onSuccess() {
                        Log.d("MessagesTab", "Connection initiated successfully");
                        Toast.makeText(getContext(), "Connecting to device...", Toast.LENGTH_SHORT).show();
                    }
                    @Override
                    public void onFailure(int reason) {
                        Log.e("MessagesTab", "Connection failed: " + getDetailedErrorMessage(reason));
                        Toast.makeText(getContext(),
                                "Failed to connect: " + getDetailedErrorMessage(reason),
                                Toast.LENGTH_LONG).show();
                        isConnecting = false;
                    }
                });
            }
        } catch (SecurityException e) {
            Log.e("MessagesTab", "Security exception during connection: " + e.getMessage());
            Toast.makeText(getContext(), "Permission denied for connection", Toast.LENGTH_SHORT).show();
            isConnecting = false;
        }
    }


    private void openChatFragment(String deviceName, String deviceAddress) {
        // Vérifiez d'abord si la connexion socket est établie
        if (!ChatSocketHandler.getInstance().isSocketConnected()) {
            Log.d("MessagesTab", "Socket not connected, attempting to reconnect before opening chat");
            Toast.makeText(getContext(), "Establishing connection...", Toast.LENGTH_SHORT).show();

            // Demandez les informations de connexion à nouveau
            try {
                if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED) {
                    manager.requestConnectionInfo(channel, connectionInfoListener);
                }
            } catch (SecurityException e) {
                Log.e("MessagesTab", "Security exception: " + e.getMessage());
            }

            // Attendez un peu avant d'ouvrir le fragment de chat
            new Handler().postDelayed(() -> {
                proceedToOpenChatFragment(deviceName, deviceAddress);
            }, 1500);
        } else {
            proceedToOpenChatFragment(deviceName, deviceAddress);
        }
    }

    private void proceedToOpenChatFragment(String deviceName, String deviceAddress) {
        ChatFragment chatFragment = new ChatFragment();

        Bundle args = new Bundle();
        args.putString("device_name", deviceName);
        args.putString("device_address", deviceAddress);
        chatFragment.setArguments(args);

        FragmentTransaction transaction = requireActivity().getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.root_layout, chatFragment, "chat_fragment_tag");
        transaction.addToBackStack(null);
        transaction.commit();

        Log.d("MessagesTab", "ChatFragment opened with: " + deviceName + " | " + deviceAddress);
    }



    @Override
    public void onResume() {
        super.onResume();
        Log.d("MessagesTab", "onResume: Registering broadcast receiver");

        // Initialize the broadcast receiver if it's null
        if (wifiDirectReceiver == null) {
            wifiDirectReceiver = new WiFiDirectBroadcastReceiver(manager, channel, this);
        }

        requireActivity().registerReceiver(wifiDirectReceiver, intentFilter);

        // Démarrer la recherche automatique lorsque le fragment reprend
        startAutoDeviceDiscovery();
    }

    @Override
    public void onPause() {
        super.onPause();
        try {
            if (wifiDirectReceiver != null) {
                requireActivity().unregisterReceiver(wifiDirectReceiver);
            }
        } catch (Exception e) {
            Log.e("WiFiDirect", "Error in onPause: " + e.getMessage());
        }

        // Arrêter la recherche automatique lorsque le fragment est en pause
        stopAutoDeviceDiscovery();
    }

    private void logDeviceStatus(WifiP2pDevice device) {
        String status;
        switch (device.status) {
            case WifiP2pDevice.AVAILABLE:
                status = "Available";
                break;
            case WifiP2pDevice.INVITED:
                status = "Invited";
                break;
            case WifiP2pDevice.CONNECTED:
                status = "Connected";
                break;
            case WifiP2pDevice.FAILED:
                status = "Failed";
                break;
            case WifiP2pDevice.UNAVAILABLE:
                status = "Unavailable";
                break;
            default:
                status = "Unknown";
        }

        Log.d("WiFiDirect", "Device: " + device.deviceName);
        Log.d("WiFiDirect", "Status: " + status);
        Log.d("WiFiDirect", "Address: " + device.deviceAddress);
        Log.d("WiFiDirect", "------------------------");
    }

    // Add this method to implement WifiP2pManager.PeerListListener directly in the fragment
    @Override
    public void onPeersAvailable(WifiP2pDeviceList peerList) {
        Log.d("WiFiDirect", "onPeersAvailable called. Peer count: " + peerList.getDeviceList().size());
        peers.clear();
        peers.addAll(peerList.getDeviceList());
        requireActivity().runOnUiThread(() -> {
            availableDevicesAdapter.clear();
            // Remove or hide the noAvailableDevicesText TextView if present
            TextView noAvailableDevicesText = requireView().findViewById(R.id.noAvailableDevicesText);
            if (noAvailableDevicesText != null) {
                noAvailableDevicesText.setVisibility(View.GONE);
            }
            List<WifiP2pDevice> availableDevices = new ArrayList<>();
            for (WifiP2pDevice device : peers) {
                // Skip local device
                if (localDevice != null && device.deviceAddress.equals(localDevice.deviceAddress)) {
                    Log.d("WiFiDirect", "Skipping local device: " + device.deviceName);
                    continue;
                }
                boolean alreadyConnected = false;
                for (WifiP2pDevice connected : connectedPeers) {
                    if (connected.deviceAddress.equals(device.deviceAddress)) {
                        alreadyConnected = true;
                        break;
                    }
                }
                if (!alreadyConnected) {
                    String deviceInfo = device.deviceName + "\n" + device.deviceAddress;
                    Log.d("WiFiDirect", "Found app device: " + deviceInfo);
                    availableDevicesAdapter.add(deviceInfo);
                    logDeviceStatus(device);
                    if (device.status == WifiP2pDevice.AVAILABLE) {
                        availableDevices.add(device);
                    }
                } else {
                    Log.d("WiFiDirect", "Skipping device " + device.deviceName + " (already connected).");
                }
            }
            lastAvailableDevices = availableDevices;
            // Show 'No available devices' if the list is empty
            if (availableDevicesAdapter.isEmpty()) {
                availableDevicesAdapter.add("No available devices");
            }
            availableDevicesAdapter.notifyDataSetChanged();
            // Deterministic initiator logic: only the device with the smallest MAC address initiates
            if (!availableDevices.isEmpty() && !isConnecting && localDevice != null && !isTryingDevices) {
                attemptedDeviceAddresses.clear(); // Reset attempts for new round
                WifiP2pDevice firstDevice = availableDevices.get(0);
                String myMac = localDevice.deviceAddress;
                String peerMac = firstDevice.deviceAddress;
                if (myMac == null || peerMac == null) {
                    Log.w("WiFiDirect", "MAC address missing, skipping initiator logic");
                    return;
                }
                int cmp = myMac.compareToIgnoreCase(peerMac);
                if (cmp < 0) {
                    // This device initiates connection
                    long currentTime = System.currentTimeMillis();
                    if (currentTime - lastConnectionAttempt > CONNECTION_COOLDOWN) {
                        Log.d("MessagesTab", "[INITIATOR] My MAC (" + myMac + ") < Peer MAC (" + peerMac + "). Initiating connection to all available devices.");
                        lastConnectionAttempt = currentTime;
                        isConnecting = true;
                        isTryingDevices = true;
                        tryConnectToNextDevice();
                    } else {
                        Log.d("MessagesTab", "[INITIATOR] Skipping connection attempt - in cooldown");
                    }
                } else if (cmp > 0) {
                    // This device waits
                    Log.d("MessagesTab", "[WAITER] My MAC (" + myMac + ") > Peer MAC (" + peerMac + "). Waiting for peer to connect.");
                    Toast.makeText(getContext(), "Waiting for the other device to connect...", Toast.LENGTH_SHORT).show();
                } else {
                    // MACs are equal (should not happen)
                    Log.w("MessagesTab", "[EQUAL MACS] Both devices have the same MAC address! Skipping connection.");
                }
            } else {
                Log.d("MessagesTab", "No available app devices found for connection or already connecting");
            }
        });
    }





    private String getDeviceStatus(int status) {
        switch (status) {
            case WifiP2pDevice.AVAILABLE:
                return "Available";
            case WifiP2pDevice.INVITED:
                return "Invited";
            case WifiP2pDevice.CONNECTED:
                return "Connected";
            case WifiP2pDevice.FAILED:
                return "Failed";
            case WifiP2pDevice.UNAVAILABLE:
                return "Unavailable";
            default:
                return "Unknown";
        }
    }

    private String getDetailedErrorMessage(int reason) {
        switch (reason) {
            case WifiP2pManager.P2P_UNSUPPORTED:
                return "WiFi Direct is not supported on this device";
            case WifiP2pManager.ERROR:
                return "Internal error occurred";
            case WifiP2pManager.BUSY:
                return "System is busy, please try again";
            default:
                return "Unknown error code: " + reason;
        }
    }

    // Update the connectionInfoListener to add connected devices to the list
    private final ConnectionInfoListener connectionInfoListener = new ConnectionInfoListener() {
        @Override
        public void onConnectionInfoAvailable(WifiP2pInfo info) {
            Log.d("MessagesTab", "Connection info available");

            if (info.groupFormed) {
                Log.d("MessagesTab", "Group formed");
                Toast.makeText(getContext(), "Connected! Establishing chat connection...", Toast.LENGTH_SHORT).show();

                if (info.isGroupOwner) {
                    Log.d("MessagesTab", "Device is group owner, starting server socket");
                    ChatSocketHandler.getInstance().setGroupOwnerAddress(info.groupOwnerAddress);
                    ChatSocketHandler.getInstance().startServerSocket();
                } else {
                    Log.d("MessagesTab", "Device is client, starting client socket to " + info.groupOwnerAddress);
                    ChatSocketHandler.getInstance().setGroupOwnerAddress(info.groupOwnerAddress);
                    ChatSocketHandler.getInstance().startClientSocket(info.groupOwnerAddress);
                }

                // Increase delay to ensure socket connection is established
                new Handler().postDelayed(() -> {
                    Log.d("MessagesTab", "Checking socket connection status");
                    if (ChatSocketHandler.getInstance().isSocketConnected()) {
                        Log.d("MessagesTab", "Socket connection is established");

                        // Find and open chat with connected device
                        if (!peers.isEmpty()) {
                            for (WifiP2pDevice device : peers) {
                                if (device.status == WifiP2pDevice.CONNECTED) {
                                    Log.d("MessagesTab", "Auto-opening chat with: " + device.deviceName);
                                    // Add a small delay before opening chat
                                    new Handler().postDelayed(() -> {
                                    openChatFragment(device.deviceName, device.deviceAddress);
                                    }, 500);
                                    break;
                                }
                            }
                        }
                    } else {
                        Log.d("MessagesTab", "Socket connection failed to establish, retrying...");
                        // Retry socket connection
                        if (info.isGroupOwner) {
                            ChatSocketHandler.getInstance().startServerSocket();
                        } else {
                            ChatSocketHandler.getInstance().startClientSocket(info.groupOwnerAddress);
                        }
                        
                        // Check again after retry
                        new Handler().postDelayed(() -> {
                            if (ChatSocketHandler.getInstance().isSocketConnected()) {
                                if (!peers.isEmpty()) {
                                    for (WifiP2pDevice device : peers) {
                                        if (device.status == WifiP2pDevice.CONNECTED) {
                                            openChatFragment(device.deviceName, device.deviceAddress);
                                            break;
                                        }
                                    }
                                }
                            } else {
                                Toast.makeText(getContext(), "Connection failed. Please try again.", Toast.LENGTH_LONG).show();
                    }
                        }, 2000);
                    }
                }, 3000); // Increased delay to 3 seconds
            }
        }
    };

    // Method to update the connected devices list
    private void updateConnectedDevicesList(WifiP2pDevice device) {
        if (device == null) return;
        boolean deviceExists = false;
        for (WifiP2pDevice existingDevice : connectedPeers) {
            if (existingDevice.deviceAddress.equals(device.deviceAddress)) {
                deviceExists = true;
                break;
            }
        }
        if (!deviceExists) {
            connectedPeers.add(device);
            // Ajout dans la liste statique
            if (!connectedDeviceAddresses.contains(device.deviceAddress)) {
                connectedDeviceAddresses.add(device.deviceAddress);
            }
            // Retirer cet appareil de la liste des Available Devices
            for (int i = 0; i < availableDevicesAdapter.getCount(); i++) {
                String item = availableDevicesAdapter.getItem(i);
                if (item != null && item.contains(device.deviceAddress)) {
                    availableDevicesAdapter.remove(item);
                    break;
                }
            }
            availableDevicesAdapter.notifyDataSetChanged();
            requireActivity().runOnUiThread(() -> {
                connectedDevicesAdapter.clear();
                for (WifiP2pDevice connectedDevice : connectedPeers) {
                    String deviceInfo = connectedDevice.deviceName + "\n" + connectedDevice.deviceAddress;
                    connectedDevicesAdapter.add(deviceInfo);
                }
                connectedDevicesAdapter.notifyDataSetChanged();
            });
        }
    }


    // Method to remove a device from connected list
    private void removeFromConnectedList(String deviceAddress) {
        WifiP2pDevice deviceToRemove = null;
        for (WifiP2pDevice device : connectedPeers) {
            if (device.deviceAddress.equals(deviceAddress)) {
                deviceToRemove = device;
                break;
            }
        }
        if (deviceToRemove != null) {
            connectedPeers.remove(deviceToRemove);
            requireActivity().runOnUiThread(() -> {
                connectedDevicesAdapter.clear();
                if (connectedPeers.isEmpty()) {
                    connectedDevicesAdapter.add("No connected devices");
                } else {
                    for (WifiP2pDevice device : connectedPeers) {
                        connectedDevicesAdapter.add(device.deviceName + "\n" + device.deviceAddress);
                    }
                }
                connectedDevicesAdapter.notifyDataSetChanged();
            });
        }
    }



    // Update your broadcast receiver to handle connection changes
    // You'll need to modify your WiFiDirectBroadcastReceiver to call this method
    public void handleConnectionChanged(NetworkInfo networkInfo) {
        if (networkInfo.isConnected()) {
            manager.requestConnectionInfo(channel, connectionInfoListener);
            isConnecting = false;
            isTryingDevices = false;
            if (connectionTimeoutRunnable != null) connectionTimeoutHandler.removeCallbacks(connectionTimeoutRunnable);
        } else {
            connectedPeers.clear();
            isConnecting = false;
            isTryingDevices = false;
            requireActivity().runOnUiThread(() -> {
                connectedDevicesAdapter.clear();
                connectedDevicesAdapter.add("No connected devices");
                connectedDevicesAdapter.notifyDataSetChanged();
            });
        }
    }

    @Override
    public void updateLocalDevice(WifiP2pDevice device) {
        localDevice = device;
        Log.d("MessagesTab", "updateLocalDevice: Local device updated: " + device.deviceName
                + " (" + device.deviceAddress + ")");
    }

    @Override
    public void updateConnectedDevice(WifiP2pDevice device) {
        // Ajoutez des logs pour comparer localDevice et le device reçu
        if (localDevice != null) {
            Log.d("MessagesTab", "updateConnectedDevice: Local device address: " + localDevice.deviceAddress);
        } else {
            Log.d("MessagesTab", "updateConnectedDevice: localDevice is null");
        }
        Log.d("MessagesTab", "updateConnectedDevice: Connected device address: " + device.deviceAddress);

        // Si le device mis à jour correspond à notre device local, on l'ignore
        if (localDevice != null && device.deviceAddress.equals(localDevice.deviceAddress)) {
            Log.d("MessagesTab", "updateConnectedDevice: Ignoring local device ("
                    + device.deviceName + ")");
            return;
        }
        requireActivity().runOnUiThread(() -> {
            boolean exists = false;
            for (WifiP2pDevice d : connectedPeers) {
                if (d.deviceAddress.equals(device.deviceAddress)) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                connectedPeers.add(device);
            }
            // Mettre à jour la liste des Connected Devices
            connectedDevicesAdapter.clear();
            for (WifiP2pDevice d : connectedPeers) {
                connectedDevicesAdapter.add(d.deviceName + "\n" + d.deviceAddress);
            }
            connectedDevicesAdapter.notifyDataSetChanged();

            for (int i = 0; i < availableDevicesAdapter.getCount(); i++) {
                String item = availableDevicesAdapter.getItem(i);
                if (item != null && item.contains(device.deviceAddress)) {
                    availableDevicesAdapter.remove(item);
                    break;
                }
            }
            availableDevicesAdapter.notifyDataSetChanged();
        });
    }



    @Override
    public void clearConnectedDevices() {
        connectedPeers.clear();
        requireActivity().runOnUiThread(() -> {
            connectedDevicesAdapter.clear();
            connectedDevicesAdapter.add("No connected devices");
            connectedDevicesAdapter.notifyDataSetChanged();
        });
    }

    // Méthode pour démarrer la recherche automatique des appareils
    @SuppressLint("MissingPermission")
    private void startAutoDeviceDiscovery() {
        Log.d("MessagesTab", "Starting automatic device discovery");

        // Arrêter toute découverte en cours
        manager.stopPeerDiscovery(channel, null);

        // Attendre un court instant avant de démarrer une nouvelle découverte
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Toast.makeText(getActivity(), "Auto-discovering peers...", Toast.LENGTH_SHORT).show();

            manager.discoverPeers(channel, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    Log.d("MessagesTab", "Auto-discovery initiated successfully");
                }

                @Override
                public void onFailure(int reason) {
                    Log.d("MessagesTab", "Discovery failed: " + reason);
                    Toast.makeText(getActivity(), "Discovery failed: " + getErrorMessage(reason), Toast.LENGTH_SHORT).show();


                    // Réessayer après un délai plus long en cas d'échec
                    new Handler(Looper.getMainLooper()).postDelayed(() -> startAutoDeviceDiscovery(), 5000);
                }
            });
        }, 1000); // Délai de 1 seconde
    }

    // Méthode pour arrêter la recherche automatique
    private void stopAutoDeviceDiscovery() {
        autoSearchHandler.removeCallbacksAndMessages(null);
    }

    // Méthode existante pour découvrir les appareils
    @SuppressLint("MissingPermission")
    public void discoverPeers() {
        manager.discoverPeers(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d("MessagesTab", "Discovery initiated");
                Toast.makeText(getContext(), "Discovery initiated", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(int reasonCode) {
                Log.d("MessagesTab", "Discovery failed: " + reasonCode);
                Toast.makeText(getContext(), "Discovery failed", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void scheduleMessageSync() {
        Log.e("MessagesTab", "scheduleMessageSync called");

        // Vérifier si le réseau est disponible
        ConnectivityManager connectivityManager = (ConnectivityManager) getActivity().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        boolean isNetworkAvailable = activeNetworkInfo != null && activeNetworkInfo.isConnected();

        // Récupérer les messages non synchronisés
        DatabaseHelper dbHelper = new DatabaseHelper(getActivity());
        ArrayList<ChatMessage> unsyncedMessages = dbHelper.getUnsyncedMessages();
        Log.e("MessagesTab", "Found " + unsyncedMessages.size() + " unsynced messages at startup");

        if (isNetworkAvailable) {
            Log.e("MessagesTab", "Network available at startup - running immediate sync");
            OneTimeWorkRequest syncWork = new OneTimeWorkRequest.Builder(MessageSyncWorker.class)
                    .build();
            WorkManager.getInstance(getActivity()).enqueue(syncWork);
        } else {
            Log.e("MessagesTab", "No network at startup - scheduling periodic sync");
            // Configurer une synchronisation périodique
            PeriodicWorkRequest periodicSyncWork = new PeriodicWorkRequest.Builder(
                    MessageSyncWorker.class, 15, TimeUnit.MINUTES)
                    .setConstraints(new Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build())
                    .build();

            WorkManager.getInstance(getActivity()).enqueueUniquePeriodicWork(
                    "message_sync_work",
                    ExistingPeriodicWorkPolicy.REPLACE,
                    periodicSyncWork);
        }

        Log.e("MessagesTab", "Scheduling message sync work completed");
    }

    public static void triggerImmediateSync(Context context) {
        Log.d("MessagesTab", "Triggering immediate sync after message send");

        // Créer une contrainte pour s'assurer que l'appareil est connecté à Internet
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        // Créer une demande de travail unique avec les contraintes
        OneTimeWorkRequest syncWork = new OneTimeWorkRequest.Builder(MessageSyncWorker.class)
                .setConstraints(constraints)
                .build();

        // Envoyer la demande de travail au WorkManager
        WorkManager.getInstance(context).enqueueUniqueWork(
                "immediate_sync",
                ExistingWorkPolicy.REPLACE,
                syncWork
        );
    }

    @Override
    public void onAppDeviceFound(WifiP2pDevice device) {
        Log.d("MessagesTab", "App device found: " + device.deviceName);
        if (!appDevices.contains(device)) {
            appDevices.add(device);
            requireActivity().runOnUiThread(() -> {
                availableDevicesAdapter.clear();
                for (WifiP2pDevice appDevice : appDevices) {
                    String deviceInfo = appDevice.deviceName + "\n" + appDevice.deviceAddress;
                    availableDevicesAdapter.add(deviceInfo);
                }
                availableDevicesAdapter.notifyDataSetChanged();
            });
        }
    }

    private void setupServiceDiscovery() {
        if (manager != null && channel != null) {
            // Check for required permissions
            if (ActivityCompat.checkSelfPermission(requireContext(), 
                    Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                Log.e("MessagesTab", "Location permission not granted for service discovery");
                return;
            }

            // Register our service
            WifiP2pDnsSdServiceInfo serviceInfo = WifiP2pDnsSdServiceInfo.newInstance(
                "com.test.demibluetoothchatting",
                "_presence._tcp",
                null
            );

            try {
                manager.addLocalService(channel, serviceInfo, new WifiP2pManager.ActionListener() {
                    @Override
                    public void onSuccess() {
                        Log.d("MessagesTab", "Service registered successfully");
                    }

                    @Override
                    public void onFailure(int reason) {
                        Log.e("MessagesTab", "Failed to register service: " + reason);
                    }
                });
            } catch (SecurityException e) {
                Log.e("MessagesTab", "Security exception while registering service: " + e.getMessage());
            }
        }
    }

    private void tryConnectToNextDevice() {
        // Always use the latest available devices and skip already attempted ones
        List<WifiP2pDevice> untriedDevices = new ArrayList<>();
        for (WifiP2pDevice device : lastAvailableDevices) {
            if (!attemptedDeviceAddresses.contains(device.deviceAddress)) {
                untriedDevices.add(device);
            }
        }
        if (untriedDevices.isEmpty()) {
            Log.d("MessagesTab", "No more untried devices to try or device list exhausted. Restarting auto-discovery in 2 seconds.");
            isConnecting = false;
            isTryingDevices = false;
            attemptedDeviceAddresses.clear();
            // Wait 2 seconds, then restart auto-discovery
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (isAdded() && getActivity() != null) {
                    startAutoDeviceDiscovery();
                }
            }, 2000);
            return;
        }
        WifiP2pDevice device = untriedDevices.get(0);
        Log.d("MessagesTab", "Trying to connect to device: " + device.deviceName + " (" + device.deviceAddress + ")");
        isConnecting = true;
        isTryingDevices = true;
        attemptedDeviceAddresses.add(device.deviceAddress);
        connectToDeviceWithTimeout(device.deviceAddress);
    }

    private void connectToDeviceWithTimeout(String deviceAddress) {
        connectToDevice(deviceAddress);
        // Cancel any previous timeout
        if (connectionTimeoutRunnable != null) connectionTimeoutHandler.removeCallbacks(connectionTimeoutRunnable);
        connectionTimeoutRunnable = () -> {
            Log.d("MessagesTab", "Connection attempt timed out. Cancelling and trying next device.");
            manager.cancelConnect(channel, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    Log.d("MessagesTab", "Cancelled connection attempt successfully.");
                    tryConnectToNextDevice();
                }
                @Override
                public void onFailure(int reason) {
                    Log.e("MessagesTab", "Failed to cancel connection: " + reason);
                    tryConnectToNextDevice();
                }
            });
        };
        connectionTimeoutHandler.postDelayed(connectionTimeoutRunnable, CONNECTION_TIMEOUT_MS);
    }

}


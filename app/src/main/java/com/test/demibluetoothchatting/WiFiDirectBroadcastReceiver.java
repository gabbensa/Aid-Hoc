package com.test.demibluetoothchatting;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.NetworkInfo;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest;
import android.net.wifi.p2p.nsd.WifiP2pServiceInfo;
import android.util.Log;
import android.widget.Toast;
import androidx.core.app.ActivityCompat;
import android.content.SharedPreferences;

import com.test.demibluetoothchatting.Database.DatabaseHelper;

public class WiFiDirectBroadcastReceiver extends BroadcastReceiver {
    private static final String TAG = "WiFiDirectBR";
    private WifiP2pManager manager;
    private WifiP2pManager.Channel channel;
    private WiFiDirectHandler handler;

    public WiFiDirectBroadcastReceiver(WifiP2pManager manager, WifiP2pManager.Channel channel,
                                       WiFiDirectHandler handler) {
        this.manager = manager;
        this.channel = channel;
        this.handler = handler;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.d(TAG, "Broadcast received: " + action);

        if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {
            int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
            Log.d(TAG, "P2P state changed - " + state);
            if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                Log.d(TAG, "Wifi P2P is enabled");
                // Setup service discovery when WiFi P2P is enabled
                setupServiceDiscovery(context);
            } else {
                Log.d(TAG, "Wifi P2P is disabled");
                Toast.makeText(context, "⚠️ Please enable WiFi Direct", Toast.LENGTH_LONG).show();
            }
        } else if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {
            Log.d(TAG, "P2P peers changed");
            if (manager != null) {
                if (ActivityCompat.checkSelfPermission(context,
                        Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    manager.requestPeers(channel, handler::onPeersAvailable);
                }
            }
        } else if (WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.equals(action)) {
            WifiP2pDevice device = intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE);
            if (device != null) {
                String newAddress = device.deviceAddress;
                Log.d(TAG, "Device changed - Name: " + device.deviceName + ", Address: " + newAddress);

                // Vérifier si l'adresse MAC a changé et est valide
                if (!newAddress.equals("02:00:00:00:00:00") && !newAddress.isEmpty()) {
                    Log.d(TAG, "Valid device address received: " + newAddress);

                    // Mettre à jour les préférences avec la nouvelle adresse MAC
                    SharedPreferences prefs = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
                    String oldAddress = prefs.getString("deviceAddress", "");

                    if (!newAddress.equals(oldAddress)) {
                        Log.d(TAG, "Updating stored device address from " + oldAddress + " to " + newAddress);
                        SharedPreferences.Editor editor = prefs.edit();
                        editor.putString("deviceAddress", newAddress);
                        editor.apply();
                    }
                } else {
                    Log.w(TAG, "Invalid device address received: " + newAddress);
                }
            }

            // Notifier le handler
            handler.updateLocalDevice(device);
            Log.d(TAG, "Local device updated: " + device.deviceName);

        } else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
            Log.d(TAG, "P2P connection changed");
            if (manager != null) {
                NetworkInfo networkInfo = intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);
                if (networkInfo != null) {
                    Log.d(TAG, "NetworkInfo state: " + networkInfo.getState());
                    handler.handleConnectionChanged(networkInfo);

                    if (networkInfo.isConnected()) {
                        Log.d(TAG, "Device connected");
                        if (ActivityCompat.checkSelfPermission(context,
                                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                            manager.requestGroupInfo(channel, group -> {
                                if (group != null) {
                                    // Notifier le propriétaire du groupe
                                    handler.updateConnectedDevice(group.getOwner());
                                    // Notifier chaque client du groupe
                                    for (WifiP2pDevice client : group.getClientList()) {
                                        handler.updateConnectedDevice(client);
                                    }

                                    // Vérifier si nous devons envoyer le USER_INFO
                                    SharedPreferences prefs = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
                                    String deviceAddress = prefs.getString("deviceAddress", "");
                                    if (deviceAddress != null && !deviceAddress.equals("02:00:00:00:00:00") && !deviceAddress.isEmpty()) {
                                        ChatSocketHandler socketHandler = ChatSocketHandler.getInstance();
                                        socketHandler.setAppContext(context);
                                        if (socketHandler.isConnected()) {
                                            Log.d(TAG, "Connection established, sending USER_INFO");
                                            socketHandler.sendUserInfo();
                                        }
                                    }

                                    Toast.makeText(context, "Connected to group", Toast.LENGTH_LONG).show();
                                }
                            });
                        }
                    } else {
                        Log.d(TAG, "Device disconnected");
                        handler.clearConnectedDevices();
                    }
                }
            }
        }
    }

    private void setupServiceDiscovery(Context context) {
        if (manager != null && channel != null) {
            // Check for required permissions
            if (ActivityCompat.checkSelfPermission(context,
                    Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "Location permission not granted for service discovery");
                return;
            }

            try {
                // Add service discovery
                manager.setDnsSdResponseListeners(channel,
                    (instanceName, registrationType, deviceInfo) -> {
                        if (instanceName.equals("com.test.demibluetoothchatting")) {
                            Log.d(TAG, "Found app device: " + deviceInfo.deviceName);
                            handler.onAppDeviceFound(deviceInfo);
                        }
                    },
                    (fullDomainName, record, deviceInfo) -> {
                        // Handle TXT record if needed
                    }
                );

                // Add service discovery request
                WifiP2pDnsSdServiceRequest serviceRequest = WifiP2pDnsSdServiceRequest.newInstance();
                manager.addServiceRequest(channel, serviceRequest,
                    new WifiP2pManager.ActionListener() {
                        @Override
                        public void onSuccess() {
                            Log.d(TAG, "Service discovery request added successfully");
                        }

                        @Override
                        public void onFailure(int reason) {
                            Log.e(TAG, "Failed to add service discovery request: " + reason);
                        }
                    }
                );

                // Start service discovery
                manager.discoverServices(channel, new WifiP2pManager.ActionListener() {
                    @Override
                    public void onSuccess() {
                        Log.d(TAG, "Service discovery started successfully");
                    }

                    @Override
                    public void onFailure(int reason) {
                        Log.e(TAG, "Failed to start service discovery: " + reason);
                    }
                });
            } catch (SecurityException e) {
                Log.e(TAG, "Security exception during service discovery setup: " + e.getMessage());
            }
        }
    }

    public interface WiFiDirectHandler {
        void updateLocalDevice(WifiP2pDevice device);
        void updateConnectedDevice(WifiP2pDevice device);
        void clearConnectedDevices();
        void onPeersAvailable(WifiP2pDeviceList peerList);
        void handleConnectionChanged(NetworkInfo networkInfo);
        void onAppDeviceFound(WifiP2pDevice device);
    }
}


package com.test.demibluetoothchatting;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.NetworkInfo;
import android.net.wifi.p2p.*;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.*;
import android.app.AlertDialog;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.util.ArrayList;
import java.util.List;

public class DevicesActivity extends AppCompatActivity implements
        WifiP2pManager.PeerListListener,
        WiFiDirectBroadcastReceiver.WiFiDirectHandler {

    private static final String TAG = "DevicesActivity";

    private WifiP2pManager manager;
    private WifiP2pManager.Channel channel;
    private BroadcastReceiver receiver;
    private IntentFilter intentFilter;

    private ListView discoveredDeviceList;
    private ListView pairedDeviceList;
    private TextView paired_devices_title;
    private TextView discovered_devices_title;

    private ArrayAdapter<String> availableDevicesAdapter;
    private ArrayAdapter<String> connectedDevicesAdapter;
    private List<WifiP2pDevice> peers = new ArrayList<>();
    private WifiP2pDevice localDevice;


    private WifiP2pDevice lastConnectedDevice = null;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_devices);

        discoveredDeviceList = findViewById(R.id.discoveredDeviceList);
        pairedDeviceList = findViewById(R.id.pairedDeviceList);
        discovered_devices_title = findViewById(R.id.discovered_devices_title);
        paired_devices_title = findViewById(R.id.paired_devices_title);

        availableDevicesAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new ArrayList<>());
        connectedDevicesAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new ArrayList<>());

        discoveredDeviceList.setAdapter(availableDevicesAdapter);
        pairedDeviceList.setAdapter(connectedDevicesAdapter);

        // Démarrer avec la liste des devices connectés masquée
        pairedDeviceList.setVisibility(View.GONE);
        paired_devices_title.setVisibility(View.GONE);

        manager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        channel = manager.initialize(this, getMainLooper(), null);

        intentFilter = new IntentFilter();
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

        discoveredDeviceList.setOnItemClickListener((parent, view, position, id) -> {
            String item = availableDevicesAdapter.getItem(position);
            Log.d(TAG, "Item clicked: " + item);
            if (item != null && item.contains("\n")) {
                String[] parts = item.split("\n");
                if (parts.length == 2) {
                    String selectedAddress = parts[1].trim();
                    for (WifiP2pDevice device : peers) {
                        Log.d(TAG, "Comparing with: " + device.deviceAddress);
                        if (device.deviceAddress.equals(selectedAddress)) {
                            connectToDevice(device);
                            break;
                        }
                    }
                }
            }
        });

        discoverPeers();
    }

    @Override
    protected void onResume() {
        super.onResume();
        receiver = new WiFiDirectBroadcastReceiver(manager, channel, this);
        registerReceiver(receiver, intentFilter);
        Log.d(TAG, "Receiver registered");

        // Resynchroniser l’état de connexion
        manager.requestConnectionInfo(channel, info -> {
            if (info.groupFormed) {
                Log.d(TAG, "Reconnected on resume to: " + info.groupOwnerAddress);
                startChat();
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            if (receiver != null) {
                unregisterReceiver(receiver);
                Log.d(TAG, "Receiver unregistered");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in onPause: " + e.getMessage());
        }
    }

    private void discoverPeers() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Missing location permission");
            return;
        }
        manager.discoverPeers(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Discovery started");
            }

            @Override
            public void onFailure(int reason) {
                Log.d(TAG, "Discovery failed: " + reason);
            }
        });
    }

    private void connectToDevice(WifiP2pDevice device) {
        Log.d(TAG, "Attempting to connect to device: " + device.deviceName);
        lastConnectedDevice = device;

        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = device.deviceAddress;
        config.groupOwnerIntent = 0;

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        manager.stopPeerDiscovery(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Discovery stopped before connection attempt");
                initiateConnection(config, device);
            }

            @Override
            public void onFailure(int reason) {
                Log.e(TAG, "Failed to stop discovery: " + reason);
                initiateConnection(config, device);
            }
        });
    }

    private void initiateConnection(WifiP2pConfig config, WifiP2pDevice device) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        manager.connect(channel, config, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Connection initiated to " + device.deviceName);
                Toast.makeText(DevicesActivity.this,
                        "Connecting to " + device.deviceName,
                        Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(int reason) {
                Log.e(TAG, "Connection failed: " + reason);
                Toast.makeText(DevicesActivity.this,
                        "Connection failed: " + reason,
                        Toast.LENGTH_LONG).show();
                discoverPeers();
            }
        });
    }

    // Méthode implémentée avec la bonne signature attendue par WiFiDirectHandler
    @Override
    public void updateConnectedDevice(WifiP2pDevice device) {
        // Si le device correspond au localDevice, on ne l'ajoute pas
        if (localDevice != null && device.deviceAddress.equals(localDevice.deviceAddress)) {
            Log.d(TAG, "Skipping update for local device: " + device.deviceName);
            return;
        }
        runOnUiThread(() -> {
            connectedDevicesAdapter.clear();
            // Affichage simple : vous pouvez adapter selon vos besoins
            connectedDevicesAdapter.add("🔵 " + device.deviceName + "\n(" + device.deviceAddress + ")");
            connectedDevicesAdapter.notifyDataSetChanged();

            pairedDeviceList.setVisibility(View.VISIBLE);
            paired_devices_title.setVisibility(View.VISIBLE);

            // Supprimez l'élément correspondant dans l'adapter des available devices
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
        runOnUiThread(() -> {
            connectedDevicesAdapter.clear();
            connectedDevicesAdapter.notifyDataSetChanged();
            pairedDeviceList.setVisibility(View.GONE);
            paired_devices_title.setVisibility(View.GONE);
        });
    }

    @Override
    public void handleConnectionChanged(NetworkInfo networkInfo) {
        if (networkInfo != null && networkInfo.isConnected()) {
            Log.d(TAG, "Connection re-established");
            manager.requestConnectionInfo(channel, info -> {
                if (info.groupFormed) {
                    startChat();
                }
            });
        } else {
            Log.w(TAG, "WiFi P2P temporarily disconnected (probably tab switch)");
            // Vous pouvez éventuellement mettre à jour l'UI ici si nécessaire
        }
    }

    @Override
    public void onPeersAvailable(WifiP2pDeviceList peerList) {
        peers.clear();
        peers.addAll(peerList.getDeviceList());

        List<String> displayList = new ArrayList<>();
        for (WifiP2pDevice device : peers) {
            // Filtrer le device local
            if (localDevice != null && device.deviceAddress.equals(localDevice.deviceAddress)) {
                Log.d(TAG, "Skipping local device: " + device.deviceName);
                continue;
            }
            displayList.add(device.deviceName + "\n" + device.deviceAddress);
        }

        availableDevicesAdapter.clear();
        availableDevicesAdapter.addAll(displayList);
        availableDevicesAdapter.notifyDataSetChanged();

        discovered_devices_title.setText(peers.isEmpty() ? "No devices found" : "Discovered Devices");
    }


    public void startChat() {
        runOnUiThread(() -> {
            new AlertDialog.Builder(this)
                    .setTitle("✅ Connected!")
                    .setMessage("You are now connected and ready to chat.")
                    .setPositiveButton("OK", null)
                    .show();
        });
    }

    @Override
    public void updateLocalDevice(WifiP2pDevice device) {
        localDevice = device;
        Log.d(TAG, "Local device set to: " + device.deviceName + " (" + device.deviceAddress + ")");
    }


}

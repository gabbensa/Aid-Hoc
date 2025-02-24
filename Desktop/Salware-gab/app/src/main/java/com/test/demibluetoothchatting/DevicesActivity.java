package com.test.demibluetoothchatting;


import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pInfo;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.app.AlertDialog;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.test.demibluetoothchatting.R;
import com.test.demibluetoothchatting.WiFiDirectBroadcastReceiver;

import java.util.ArrayList;
import java.util.List;

public class DevicesActivity extends AppCompatActivity implements WifiP2pManager.PeerListListener {

    private static final String TAG = "DevicesActivity";

    // WiFi-Direct components
    private WifiP2pManager manager;
    private WifiP2pManager.Channel channel;
    private BroadcastReceiver receiver;
    private IntentFilter intentFilter;

    private ListView discoveredDeviceList;
    private ListView pairedDeviceList;
    private TextView paired_devices_title;
    private TextView discovered_devices_title;

    private List<WifiP2pDevice> peers = new ArrayList<>();
    private ArrayAdapter<String> discoveredDevicesAdapter;
    private ArrayAdapter<String> availableDevicesAdapter;
    private ArrayAdapter<String> connectedDevicesAdapter;
    private ListView availableDevicesList;
    private ListView connectedDevicesList;
    private TextView connectedDevicesTitle;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_devices);

        // Initialize lists with your existing IDs
        discoveredDeviceList = findViewById(R.id.discoveredDeviceList);
        pairedDeviceList = findViewById(R.id.pairedDeviceList);
        discovered_devices_title = findViewById(R.id.discovered_devices_title);
        paired_devices_title = findViewById(R.id.paired_devices_title);

        availableDevicesAdapter = new ArrayAdapter<>(this, 
            android.R.layout.simple_list_item_1, new ArrayList<>());
        connectedDevicesAdapter = new ArrayAdapter<>(this, 
            android.R.layout.simple_list_item_1, new ArrayList<>());

        discoveredDeviceList.setAdapter(availableDevicesAdapter);
        pairedDeviceList.setAdapter(connectedDevicesAdapter);

        // Initially hide paired devices section
        pairedDeviceList.setVisibility(View.GONE);
        paired_devices_title.setVisibility(View.GONE);

        // Initialize UI components
        connectedDevicesList = findViewById(R.id.pairedDeviceList);
        connectedDevicesTitle = findViewById(R.id.paired_devices_title);

        // Initialize WiFi-Direct manager and channel
        manager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        channel = manager.initialize(this, getMainLooper(), null);

        // Initialize intent filter
        intentFilter = new IntentFilter();
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

        // Set up adapters for the device lists
        discoveredDevicesAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        discoveredDeviceList.setAdapter(discoveredDevicesAdapter);
        pairedDeviceList.setAdapter(connectedDevicesAdapter);

        // Handle device selection for discovered devices
        discoveredDeviceList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                WifiP2pDevice device = peers.get(position);
                connectToDevice(device);
            }
        });

        // Start peer discovery
        discoverPeers();
    }

    @Override
    protected void onResume() {
        super.onResume();
        receiver = new WiFiDirectBroadcastReceiver(manager, channel, this);
        registerReceiver(receiver, intentFilter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(receiver);
    }

    private void discoverPeers() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
            != PackageManager.PERMISSION_GRANTED) {
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

    public void updatePeerList(WifiP2pDeviceList peerList) {
        peers.clear();
        peers.addAll(peerList.getDeviceList());

        List<String> deviceNames = new ArrayList<>();
        for (WifiP2pDevice device : peers) {
            deviceNames.add(device.deviceName);
        }

        discoveredDevicesAdapter.clear();
        discoveredDevicesAdapter.addAll(deviceNames);
        discoveredDevicesAdapter.notifyDataSetChanged();

        if (peers.isEmpty()) {
            discovered_devices_title.setText("No devices found");
        } else {
            discovered_devices_title.setText("Discovered Devices");
        }
    }

    public void startChat() {
        runOnUiThread(() -> {
            // Show a dialog to make the connection more obvious
            new AlertDialog.Builder(this)
                .setTitle("Connection Successful! 🎉")
                .setMessage("You are now connected and ready to chat!")
                .setPositiveButton("Start Chatting", (dialog, which) -> {
                    // TODO: Start your chat activity
                    Toast.makeText(this, "Chat feature coming soon!", 
                                 Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Later", null)
                .show();
        });
    }

    private void connectToDevice(WifiP2pDevice device) {
        Log.d(TAG, "Attempting to connect to device: " + device.deviceName);
        
        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = device.deviceAddress;
        config.groupOwnerIntent = 0; // Let the other device be group owner
        
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
            != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Missing location permission");
            return;
        }

        // First, cancel any ongoing discovery
        manager.stopPeerDiscovery(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Discovery stopped before connection attempt");
                // Now try to connect
                initiateConnection(config, device);
            }

            @Override
            public void onFailure(int reason) {
                Log.e(TAG, "Failed to stop discovery: " + reason);
                // Try to connect anyway
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
                Log.d(TAG, "Connection initiated successfully to " + device.deviceName);
                // Show clear connection attempt message
                Toast.makeText(DevicesActivity.this, 
                    "🔄 Connecting to " + device.deviceName + 
                    "\n⏳ Waiting for other device to accept...", 
                    Toast.LENGTH_LONG).show();
            }

            @Override
            public void onFailure(int reason) {
                Log.e(TAG, "Connection failed with reason code: " + reason);
                String failureMessage = "❌ Connection failed: ";
                switch (reason) {
                    case WifiP2pManager.P2P_UNSUPPORTED:
                        failureMessage += "WiFi Direct not supported";
                        break;
                    case WifiP2pManager.ERROR:
                        failureMessage += "Please try again";
                        break;
                    case WifiP2pManager.BUSY:
                        failureMessage += "System busy, try again";
                        break;
                    default:
                        failureMessage += "Error code " + reason;
                }
                Toast.makeText(DevicesActivity.this, failureMessage, 
                             Toast.LENGTH_LONG).show();

                // If we get an error, try to rediscover peers
                discoverPeers();
            }
        });
    }

    @Override
    public void onPeersAvailable(WifiP2pDeviceList peerList) {
        // Existing implementation
    }

    public void updateConnectedDevice(String deviceName) {
        runOnUiThread(() -> {
            // Clear and update connected devices list
            connectedDevicesAdapter.clear();
            connectedDevicesAdapter.add("🔵 " + deviceName);
            connectedDevicesAdapter.notifyDataSetChanged();

            // Show paired devices section
            pairedDeviceList.setVisibility(View.VISIBLE);
            paired_devices_title.setVisibility(View.VISIBLE);

            // Update available devices
            availableDevicesAdapter.remove(deviceName);
            availableDevicesAdapter.notifyDataSetChanged();
        });
    }

    public void clearConnectedDevices() {
        runOnUiThread(() -> {
            connectedDevicesAdapter.clear();
            connectedDevicesAdapter.notifyDataSetChanged();
            pairedDeviceList.setVisibility(View.GONE);
            paired_devices_title.setVisibility(View.GONE);
        });
    }
}


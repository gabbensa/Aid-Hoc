package com.test.demibluetoothchatting;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.NetworkInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.util.Log;
import android.widget.Toast;
import androidx.core.app.ActivityCompat;

public class WiFiDirectBroadcastReceiver extends BroadcastReceiver {
    private static final String TAG = "WiFiDirectBR";
    private WifiP2pManager manager;
    private WifiP2pManager.Channel channel;
    private DevicesActivity activity;

    public WiFiDirectBroadcastReceiver(WifiP2pManager manager, WifiP2pManager.Channel channel,
                                     DevicesActivity activity) {
        this.manager = manager;
        this.channel = channel;
        this.activity = activity;
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
            } else {
                Log.d(TAG, "Wifi P2P is disabled");
                Toast.makeText(context, "⚠️ Please enable WiFi Direct", Toast.LENGTH_LONG).show();
            }
        } else if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {
            Log.d(TAG, "P2P peers changed");
            if (manager != null) {
                if (ActivityCompat.checkSelfPermission(context, 
                    Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    manager.requestPeers(channel, activity);
                }
            }
        } else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
            Log.d(TAG, "P2P connection changed");
            if (manager != null) {
                NetworkInfo networkInfo = intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);
                if (networkInfo != null) {
                    Log.d(TAG, "NetworkInfo state: " + networkInfo.getState());
                    if (networkInfo.isConnected()) {
                        Log.d(TAG, "Device connected");
                        
                        // Get connection info to show connected device
                        if (ActivityCompat.checkSelfPermission(context, 
                            Manifest.permission.ACCESS_FINE_LOCATION) == 
                            PackageManager.PERMISSION_GRANTED) {
                            
                            manager.requestGroupInfo(channel, group -> {
                                if (group != null) {
                                    String connectedDevice = group.getClientList().isEmpty() ? 
                                        group.getOwner().deviceName : 
                                        group.getClientList().iterator().next().deviceName;
                                    
                                    activity.updateConnectedDevice(connectedDevice);
                                    
                                    Toast.makeText(context, 
                                        "🔵 Connected to " + connectedDevice, 
                                        Toast.LENGTH_LONG).show();
                                }
                            });
                        }
                    } else {
                        Log.d(TAG, "Device disconnected");
                        activity.clearConnectedDevices();
                        Toast.makeText(context, 
                            "🔴 Disconnected from peer", 
                            Toast.LENGTH_LONG).show();
                    }
                }
            }
        }
    }
}

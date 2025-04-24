package com.test.demibluetoothchatting;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.NetworkInfo;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pManager;
import android.util.Log;
import android.widget.Toast;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.test.demibluetoothchatting.Tabs.messages_tab;

public class WiFiDirectBroadcastReceiver extends BroadcastReceiver {

    private final WifiP2pManager manager;
    private final WifiP2pManager.Channel channel;
    private final FragmentActivity activity;

    public WiFiDirectBroadcastReceiver(WifiP2pManager manager, WifiP2pManager.Channel channel,
                                      FragmentActivity activity) {
        this.manager = manager;
        this.channel = channel;
        this.activity = activity;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.d("WiFiDirectBR", "Received action: " + action);
        
        if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {
            int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
            if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                Log.d("WiFiDirectBR", "Wifi P2P is enabled");
            } else {
                Log.d("WiFiDirectBR", "Wifi P2P is not enabled");
                Toast.makeText(context, "WiFi Direct is not enabled", Toast.LENGTH_SHORT).show();
            }
        } else if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {
            Log.d("WiFiDirectBR", "P2P peers changed");
            
            Fragment messagesFragment = null;
            for (Fragment f : activity.getSupportFragmentManager().getFragments()) {
                if (f instanceof messages_tab) {
                    messagesFragment = f;
                    break;
                }
            }
            
            if (messagesFragment != null && messagesFragment instanceof messages_tab) {
                messages_tab fragment = (messages_tab) messagesFragment;
                if (fragment.peerListListener != null) {
                    manager.requestPeers(channel, fragment.peerListListener);
                }
            }
        } else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
            NetworkInfo networkInfo = intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);
            Log.d("WiFiDirectBR", "Connection changed: " + (networkInfo != null ? 
                "Connected: " + networkInfo.isConnected() : "NetworkInfo is null"));
            
            Fragment messagesFragment = null;
            for (Fragment f : activity.getSupportFragmentManager().getFragments()) {
                if (f instanceof messages_tab) {
                    messagesFragment = f;
                    break;
                }
            }
            
            if (messagesFragment != null) {
                Log.d("WiFiDirectBR", "Found messages_tab fragment");
                ((messages_tab) messagesFragment).handleConnectionChanged(networkInfo);
            } else {
                Log.d("WiFiDirectBR", "Could not find messages_tab fragment");
            }
        } else if (WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.equals(action)) {
            Log.d("WiFiDirectBR", "This device changed");
            // Handle device change if needed
        }
    }
} 
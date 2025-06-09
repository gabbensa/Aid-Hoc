package com.test.demibluetoothchatting;

import android.content.Context;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GroupManager {
    private static final String TAG = "GroupManager";
    private static GroupManager instance;
    private WifiP2pManager manager;
    private WifiP2pManager.Channel channel;
    private Context context;
    private boolean isGroupOwner = false;
    private List<WifiP2pDevice> groupMembers = new ArrayList<>();
    private Map<String, ChatSocketHandler> memberSockets = new HashMap<>();
    private GroupChatListener listener;

    public interface GroupChatListener {
        void onGroupFormed(boolean isGroupOwner);
        void onMemberJoined(WifiP2pDevice device);
        void onMemberLeft(WifiP2pDevice device);
        void onGroupMessageReceived(String sender, String message);
    }

    private GroupManager(Context context) {
        this.context = context.getApplicationContext();
        manager = (WifiP2pManager) context.getSystemService(Context.WIFI_P2P_SERVICE);
        channel = manager.initialize(context, context.getMainLooper(), null);
    }

    public static synchronized GroupManager getInstance(Context context) {
        if (instance == null) {
            instance = new GroupManager(context);
        }
        return instance;
    }

    public void setGroupChatListener(GroupChatListener listener) {
        this.listener = listener;
    }

    public void createGroup() {
        manager.createGroup(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Group created successfully");
                isGroupOwner = true;
                if (listener != null) {
                    listener.onGroupFormed(true);
                }
            }

            @Override
            public void onFailure(int reason) {
                Log.e(TAG, "Failed to create group: " + reason);
            }
        });
    }

    public void joinGroup(WifiP2pDevice groupOwner) {
        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = groupOwner.deviceAddress;

        manager.connect(channel, config, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Connected to group owner");
                isGroupOwner = false;
                if (listener != null) {
                    listener.onGroupFormed(false);
                }
            }

            @Override
            public void onFailure(int reason) {
                Log.e(TAG, "Failed to join group: " + reason);
            }
        });
    }

    public void leaveGroup() {
        manager.removeGroup(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Left group successfully");
                groupMembers.clear();
                memberSockets.clear();
                isGroupOwner = false;
            }

            @Override
            public void onFailure(int reason) {
                Log.e(TAG, "Failed to leave group: " + reason);
            }
        });
    }

    public void broadcastMessage(String message) {
        if (isGroupOwner) {
            // As group owner, send to all connected clients
            for (ChatSocketHandler socket : memberSockets.values()) {
                socket.sendMessage(message);
            }
        } else {
            // As client, send only to group owner
            ChatSocketHandler groupOwnerSocket = memberSockets.get("GROUP_OWNER");
            if (groupOwnerSocket != null) {
                groupOwnerSocket.sendMessage(message);
            }
        }
    }

    public void handleNewConnection(WifiP2pDevice device, ChatSocketHandler socket) {
        groupMembers.add(device);
        memberSockets.put(device.deviceAddress, socket);
        
        if (listener != null) {
            listener.onMemberJoined(device);
        }
    }

    public void handleDisconnection(WifiP2pDevice device) {
        groupMembers.remove(device);
        memberSockets.remove(device.deviceAddress);
        
        if (listener != null) {
            listener.onMemberLeft(device);
        }
    }

    public boolean isGroupOwner() {
        return isGroupOwner;
    }

    public List<WifiP2pDevice> getGroupMembers() {
        return new ArrayList<>(groupMembers);
    }
} 
package com.test.demibluetoothchatting;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.test.demibluetoothchatting.Adapters.ChatAdapter;
import com.test.demibluetoothchatting.Database.DatabaseHelper;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {
    private WifiP2pManager manager;
    private WifiP2pManager.Channel channel;
    private Controller controller;
    private DatabaseHelper dbHelper;

    public static final int state_change = 1;
    public static final int message_read = 2;
    public static final int message_write = 3;
    public static final int MESSAGE_DEVICE_OBJECT = 4;
    public static final int message_toast = 5;

    private TextView status;
    private Button btnConnect;
    private RecyclerView recyclerView;
    private ChatAdapter chatAdapter;
    private ArrayList<com.test.demibluetoothchatting.ChatMessage> chatMessages;

    private WifiP2pDevice connectingDevice;
    private String deviceName;
    private TextView txtDeviceName;
    private String deviceAddress; // Pour stocker l'adresse MAC du device

    private WifiP2pDevice localDevice;

    @SuppressLint({"MissingInflatedId"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize UI components
        dbHelper = new DatabaseHelper(MainActivity.this);
        status = findViewById(R.id.status);
        btnConnect = findViewById(R.id.btn_connect);
        EditText inputLayout = findViewById(R.id.input_message);
        recyclerView = findViewById(R.id.list);
        txtDeviceName = findViewById(R.id.txt_device_name);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        chatMessages = new ArrayList<>();
        chatAdapter = new ChatAdapter(chatMessages, "This Device");
        recyclerView.setAdapter(chatAdapter);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        // Initialize WiFi-Direct components
        manager = (WifiP2pManager) getSystemService(WIFI_P2P_SERVICE);
        channel = manager.initialize(this, getMainLooper(), null);
        controller = new Controller(handler);

        Intent intent = getIntent();
        if (intent != null && intent.hasExtra("device_name") && intent.hasExtra("device_address")) {
            deviceName = intent.getStringExtra("device_name");
            deviceAddress = intent.getStringExtra("device_address");

            if (deviceName != null && deviceAddress != null) {
                // Récupérer le username associé au device
                String username = dbHelper.getUsernameForDevice(deviceAddress);
                String displayName = (username != null) ? username : deviceName;
                txtDeviceName.setText(displayName);
                loadMessagesFromDatabase(deviceAddress);
            }
        }

        btnConnect.setOnClickListener(v -> {
            if (deviceName != null) {
                connectToSelectedDevice(deviceAddress);
            } else {
                Toast.makeText(MainActivity.this, "No device selected!", Toast.LENGTH_SHORT).show();
            }
        });

        findViewById(R.id.btn_send).setOnClickListener(v -> {
            if (inputLayout.getText().toString().isEmpty()) {
                Toast.makeText(MainActivity.this, "Please input your message", Toast.LENGTH_SHORT).show();
            } else {
                sendMessage(inputLayout.getText().toString());
                inputLayout.setText("");
            }
        });
    }

    private void connectToSelectedDevice(String deviceAddress) {
        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = deviceAddress;

        manager.connect(channel, config, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Toast.makeText(MainActivity.this, "Connecting to " + deviceAddress, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(int reason) {
                Toast.makeText(MainActivity.this, "Connection failed: " + reason, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setStatus(String s) {
        status.setText(s);
    }

    private void loadMessagesFromDatabase(String deviceAddress) {
        String senderUsername = dbHelper.getUsernameForDevice(getLocalDeviceAddress());
        String receiverUsername = dbHelper.getUsernameForDevice(deviceAddress);
        
        // Fallback sur les noms de devices si pas de username
        if (senderUsername == null) senderUsername = getLocalDeviceName();
        if (receiverUsername == null) receiverUsername = deviceName;

        if (!senderUsername.isEmpty() && !receiverUsername.isEmpty()) {
            chatMessages.clear();
            chatMessages = dbHelper.getMessagesForDevice(senderUsername, receiverUsername);
            chatAdapter = new ChatAdapter(chatMessages, senderUsername);
            recyclerView.setAdapter(chatAdapter);
            recyclerView.scrollToPosition(chatMessages.size() - 1);
        }
    }

    private void sendMessage(String message) {
        if (controller.getState() != Controller.CONNECTED) {
            Toast.makeText(this, "Connection was lost!", Toast.LENGTH_SHORT).show();
            return;
        }

        if (message.length() > 0) {
            byte[] send = message.getBytes();
            controller.write(send);
            
            // Récupérer les usernames
            String senderUsername = dbHelper.getUsernameForDevice(getLocalDeviceAddress());
            String receiverUsername = dbHelper.getUsernameForDevice(deviceAddress);
            
            // Fallback sur les noms de devices si pas de username
            if (senderUsername == null) senderUsername = getLocalDeviceName();
            if (receiverUsername == null) receiverUsername = deviceName;
            
            dbHelper.insertMessage(senderUsername, receiverUsername, message, getCurrentTimestamp(), 0);
            loadMessagesFromDatabase(deviceAddress);
        }
    }

    private final Handler handler = new Handler(msg -> {
        switch (msg.what) {
            case state_change:
                switch (msg.arg1) {
                    case Controller.CONNECTED:
                        setStatus("Connected to: " + connectingDevice.deviceName);
                        loadMessagesFromDatabase(deviceAddress);
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

            case message_write:
                String writeMessage = new String((byte[]) msg.obj);
                String senderUsername = dbHelper.getUsernameForDevice(getLocalDeviceAddress());
                if (senderUsername == null) senderUsername = getLocalDeviceName();
                dbHelper.insertMessage(senderUsername, connectingDevice.deviceName, writeMessage, getCurrentTimestamp(), 0);
                loadMessagesFromDatabase(deviceAddress);
                break;

            case message_read:
                String readMessage = new String((byte[]) msg.obj, 0, msg.arg1);
                String receiverUsername = dbHelper.getUsernameForDevice(getLocalDeviceAddress());
                if (receiverUsername == null) receiverUsername = getLocalDeviceName();
                dbHelper.insertMessage(connectingDevice.deviceName, receiverUsername, readMessage, getCurrentTimestamp(), 0);
                loadMessagesFromDatabase(deviceAddress);
                break;

            case MESSAGE_DEVICE_OBJECT:
                connectingDevice = (WifiP2pDevice) msg.obj;
                loadMessagesFromDatabase(deviceAddress);
                Toast.makeText(getApplicationContext(), "Connected to " + connectingDevice.deviceName, Toast.LENGTH_SHORT).show();
                break;

            case message_toast:
                Toast.makeText(getApplicationContext(), "Error: " + msg.getData().getString("toast"), Toast.LENGTH_SHORT).show();
                break;
        }
        return false;
    });

    private String getCurrentTimestamp() {
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(new java.util.Date());
    }

    private String getLocalDeviceAddress() {
        if (localDevice != null) {
            return localDevice.deviceAddress;
        }
        return null;
    }

    private String getLocalDeviceName() {
        if (localDevice != null) {
            return localDevice.deviceName;
        }
        return null;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (controller != null) {
            controller.stop();
        }
    }
}

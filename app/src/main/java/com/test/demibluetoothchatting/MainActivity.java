
package com.test.demibluetoothchatting;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.RequiresPermission;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
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
        if (intent != null && intent.hasExtra("device_name")) {
            deviceName = intent.getStringExtra("device_name");

            if (deviceName != null) {
                loadMessagesFromDatabase(deviceName);
                txtDeviceName.setText(deviceName);
            }
        }

        btnConnect.setOnClickListener(v -> {
            if (deviceName != null) {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED) {
                    // TODO: Consider calling
                    //    ActivityCompat#requestPermissions
                    // here to request the missing permissions, and then overriding
                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                    //                                          int[] grantResults)
                    // to handle the case where the user grants the permission. See the documentation
                    // for ActivityCompat#requestPermissions for more details.
                    return;
                }
                connectToSelectedDevice(deviceName);
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

    @RequiresPermission(allOf = {Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.NEARBY_WIFI_DEVICES})
    private void connectToSelectedDevice(String deviceName) {
        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = deviceName; // Use the address from discovered peers

        manager.connect(channel, config, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Toast.makeText(MainActivity.this, "Connecting to " + deviceName, Toast.LENGTH_SHORT).show();
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

    private void loadMessagesFromDatabase(String deviceName) {
        String senderName = "This Device";
        String receiverName = deviceName;

        if (!senderName.isEmpty() && !receiverName.isEmpty()) {
            chatMessages.clear();
            chatMessages = dbHelper.getMessagesForDevice(senderName, receiverName);
            chatAdapter = new ChatAdapter(chatMessages, senderName);
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
        }
    }

    private final Handler handler = new Handler(msg -> {
        switch (msg.what) {
            case state_change:
                switch (msg.arg1) {
                    case Controller.CONNECTED:
                        setStatus("Connected to: " + connectingDevice.deviceName);
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

            case message_write:
                String writeMessage = new String((byte[]) msg.obj);
                dbHelper.insertMessage("This Device", connectingDevice.deviceName, writeMessage, getCurrentTimestamp(), 0);
                loadMessagesFromDatabase(connectingDevice.deviceName);
                break;

            case message_read:
                String readMessage = new String((byte[]) msg.obj, 0, msg.arg1);
                dbHelper.insertMessage(connectingDevice.deviceName, "This Device", readMessage, getCurrentTimestamp(), 0);
                loadMessagesFromDatabase(connectingDevice.deviceName);
                break;

            case MESSAGE_DEVICE_OBJECT:
                connectingDevice = (WifiP2pDevice) msg.obj;
                loadMessagesFromDatabase(connectingDevice.deviceName);
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (controller != null) {
            controller.stop();
        }
    }
}

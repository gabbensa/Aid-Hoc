package com.test.demibluetoothchatting;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
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
import com.test.demibluetoothchatting.Receivers.NetworkChangeReceiver;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {
    private boolean isReceiverRegistered = false;
    private NetworkChangeReceiver networkChangeReceiver;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothDevice connectingDevice;
    private Controller _chat_helper;
    private DatabaseHelper dbHelper;
    public static final int state_change = 1;
    public static final int message_read = 2;
    public static final int message_write = 3;
    public static final int message_device_object = 4;
    public static final int message_toast = 5;
    public static final String device_object = "device_name";
    private TextView status;
    private Button btnConnect;
    private RecyclerView recyclerView;
    private ChatAdapter chatAdapter;
    private ArrayList<com.test.demibluetoothchatting.ChatMessage> chatMessages;

    private String deviceAddress;
    private String deviceName;
    private TextView txt_device_name;

    @SuppressLint({"MissingPermission", "MissingInflatedId"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        dbHelper = new DatabaseHelper(MainActivity.this);
        status = findViewById(R.id.status);
        btnConnect = findViewById(R.id.btn_connect);
        EditText inputLayout = findViewById(R.id.input_message);
        recyclerView = findViewById(R.id.list);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        chatMessages = new ArrayList<>();
        chatAdapter = new ChatAdapter(chatMessages, bluetoothAdapter.getName());
        recyclerView.setAdapter(chatAdapter);
        txt_device_name=findViewById(R.id.txt_device_name);
        findViewById(R.id.btn_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available!", Toast.LENGTH_SHORT).show();
            finish();
        }

        _chat_helper = new Controller(this, handler);

        Intent intent = getIntent();
        if (intent != null && intent.hasExtra("device_name") && intent.hasExtra("device_address")) {
            deviceName = intent.getStringExtra("device_name");
            deviceAddress = intent.getStringExtra("device_address");

            if (deviceName != null) {
                loadMessagesFromDatabase(deviceName);
                txt_device_name.setText(deviceName);
            }
        }

        btnConnect.setOnClickListener(v -> {
            if (deviceAddress != null) {
                connectToSelectedDevice(deviceAddress);
            } else {
                Toast.makeText(MainActivity.this, "No device selected!", Toast.LENGTH_SHORT).show();
            }
        });

        findViewById(R.id.btn_send).setOnClickListener(v -> {
            if (inputLayout.getText().toString().isEmpty()) {
                Toast.makeText(MainActivity.this, "Please input your message", Toast.LENGTH_SHORT).show();
            } else {
                message_send(inputLayout.getText().toString());
                inputLayout.setText("");
            }
        });
    }

    @SuppressLint("MissingPermission")
    private void connectToSelectedDevice(String deviceAddress) {
        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);
        _chat_helper.connect(device);
    }

    private void setStatus(String s) {
        status.setText(s);
    }

    private void loadMessagesFromDatabase(String deviceName) {
        String senderName = bluetoothAdapter.getName();
        String receiverName = deviceName;

        if (!senderName.isEmpty() && !receiverName.isEmpty()) {
            chatMessages.clear();
            chatMessages = dbHelper.getMessagesForDevice(senderName, receiverName);
            chatAdapter = new ChatAdapter(chatMessages, senderName);
            recyclerView.setAdapter(chatAdapter);
            recyclerView.scrollToPosition(chatMessages.size() - 1);
        }
    }

    private void message_send(String message) {
        if (_chat_helper.getState() != Controller.conneceed) {
            Toast.makeText(this, "Connection was lost!", Toast.LENGTH_SHORT).show();
            return;
        }

        if (message.length() > 0) {
            byte[] send = message.getBytes();
            _chat_helper.write(send);
        }
    }

    private Handler handler = new Handler(msg -> {
        switch (msg.what) {
            case MainActivity.state_change:
                switch (msg.arg1) {
                    case Controller.conneceed:
                        setStatus("Connected to: " + connectingDevice.getName());
                        loadMessagesFromDatabase(connectingDevice.getName());
                        break;
                    case Controller.connecting:
                        setStatus("Connecting...");
                        break;
                    case Controller.listen:
                    case Controller.none:
                        setStatus("Not connected");
                        break;
                }
                break;

            case MainActivity.message_write:
                String writeMessage = new String((byte[]) msg.obj);
                String writeTimestamp = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(new java.util.Date());
                String senderName = bluetoothAdapter.getName();
                String receiverName = connectingDevice.getName();
                dbHelper.insertMessage(senderName, receiverName, writeMessage, writeTimestamp, 0);
                loadMessagesFromDatabase(receiverName);
                break;

            case MainActivity.message_read:
                String readMessage = new String((byte[]) msg.obj, 0, msg.arg1);
                String readTimestamp = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(new java.util.Date());
                String readSenderName = connectingDevice.getName();
                String readReceiverName = bluetoothAdapter.getName();
                dbHelper.insertMessage(readSenderName, readReceiverName, readMessage, readTimestamp, 0);
                loadMessagesFromDatabase(readSenderName);
                break;

            case MainActivity.message_device_object:
                connectingDevice = msg.getData().getParcelable(MainActivity.device_object);
                loadMessagesFromDatabase(connectingDevice.getName());
                Toast.makeText(getApplicationContext(), "Connected to " + connectingDevice.getName(), Toast.LENGTH_SHORT).show();
                break;

            case MainActivity.message_toast:
                String errorMessage = msg.getData().getString("toast");
                Toast.makeText(getApplicationContext(), "Error: " + errorMessage, Toast.LENGTH_SHORT).show();

                break;
        }
        return false;
    });


    @SuppressLint("MissingPermission")
    @Override
    protected void onStart() {
        super.onStart();
        if (_chat_helper != null) {
            _chat_helper.start();
        }
    }




    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        if (connectivityManager != null) {
            NetworkInfo activeNetwork = connectivityManager.getActiveNetworkInfo();

            if (activeNetwork != null && activeNetwork.isConnected()) {
                if (activeNetwork.getType() == ConnectivityManager.TYPE_WIFI) {
                    return true;
                } else if (activeNetwork.getType() == ConnectivityManager.TYPE_MOBILE) {
                    return true;
                } else {
                }
            }
        }
        return false;
    }


    @Override
    protected void onPause() {
        super.onPause();
        if (networkChangeReceiver != null) {
            unregisterReceiver(networkChangeReceiver);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isReceiverRegistered) {
            isReceiverRegistered = false;
        }
        if (_chat_helper != null) {
            _chat_helper.stop();
        }
    }


    @Override
    protected void onResume() {
        super.onResume();
        networkChangeReceiver = new NetworkChangeReceiver();
        IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(networkChangeReceiver, filter);
    }



}

// Updated ChatFragment.java: Replacing Bluetooth Logic with WiFi-Direct Logic

package com.test.demibluetoothchatting;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.IntentFilter;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.test.demibluetoothchatting.Adapters.ChatAdapter;
import com.test.demibluetoothchatting.Database.DatabaseHelper;
import com.test.demibluetoothchatting.Receivers.NetworkChangeReceiver;

import java.util.ArrayList;

public class ChatFragment extends Fragment {

    private WifiP2pDevice connectingDevice; // Device currently being connected
    private WifiP2pManager manager; // WiFi-Direct Manager
    private WifiP2pManager.Channel channel; // WiFi-Direct Communication Channel
    private Controller controller; // Controller to manage WiFi-Direct communication
    private DatabaseHelper dbHelper; // DatabaseHelper to interact with SQLite database
    private NetworkChangeReceiver networkChangeReceiver; // Receiver to monitor network changes

    // UI components
    private TextView status;
    private Button btnConnect;
    private RecyclerView recyclerView;
    private ChatAdapter chatAdapter;
    private ArrayList<com.test.demibluetoothchatting.ChatMessage> chatMessages;
    private TextView txtDeviceName;

    private String deviceAddress;
    private String deviceName;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.chat_fragment, container, false);

        // Initialize WiFi-Direct manager and channel
        manager = (WifiP2pManager) requireActivity().getSystemService(Context.WIFI_P2P_SERVICE);
        channel = manager.initialize(requireActivity(), requireActivity().getMainLooper(), null);

        // Initialize database helper
        dbHelper = new DatabaseHelper(getActivity());

        // Find and initialize UI components
        status = rootView.findViewById(R.id.status);
        btnConnect = rootView.findViewById(R.id.btn_connect);
        EditText inputLayout = rootView.findViewById(R.id.input_message);
        recyclerView = rootView.findViewById(R.id.list);
        recyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));

        chatMessages = new ArrayList<>();
        chatAdapter = new ChatAdapter(chatMessages, "This Device");
        recyclerView.setAdapter(chatAdapter);
        txtDeviceName = rootView.findViewById(R.id.txt_device_name);

        // Handle back button click
        rootView.findViewById(R.id.btn_back).setOnClickListener(v -> {
            if (getFragmentManager() != null) {
                getFragmentManager().popBackStack();
            }
        });

        // Initialize controller
        controller = new Controller(handler);

        // Retrieve arguments passed to the fragment
        // Retrieve arguments passed to the fragment
        Bundle args = getArguments();
        if (args != null) {
            deviceName = args.getString("device_name");
            deviceAddress = args.getString("device_address");
            String uri = args.getString("uri"); // Retrieve the URI if passed

            if (deviceName != null) {
                loadMessagesFromDatabase(deviceName);
                txtDeviceName.setText(deviceName);
            }

            if (uri != null) {
                // Add any logic needed for handling the URI, or simply log it for now
                Log.d("ChatFragment", "Received URI: " + uri);
            }
        }


        // Handle connect button click
        btnConnect.setOnClickListener(v -> {
            if (deviceAddress != null) {
                connectToSelectedDevice(deviceAddress);
            } else {
                Toast.makeText(getActivity(), "No device selected!", Toast.LENGTH_SHORT).show();
            }
        });

        // Handle send button click
        rootView.findViewById(R.id.btn_send).setOnClickListener(v -> {
            if (inputLayout.getText().toString().isEmpty()) {
                Toast.makeText(getActivity(), "Please input your message", Toast.LENGTH_SHORT).show();
            } else {
                sendMessage(inputLayout.getText().toString());
                inputLayout.setText("");
            }
        });

        return rootView;
    }

    private void connectToSelectedDevice(String deviceAddress) {
        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = deviceAddress;

        manager.connect(channel, config, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Toast.makeText(getActivity(), "Connecting to " + deviceName, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(int reason) {
                Toast.makeText(getActivity(), "Connection failed: " + reason, Toast.LENGTH_SHORT).show();
            }
        });
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
            Toast.makeText(getActivity(), "Connection was lost!", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!message.isEmpty()) {
            byte[] send = message.getBytes();
            controller.write(send);
        }
    }

    private final Handler handler = new Handler(msg -> {
        switch (msg.what) {
            case MainActivity.state_change:
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

            case MainActivity.message_write:
                String writeMessage = new String((byte[]) msg.obj);
                dbHelper.insertMessage("This Device", connectingDevice.deviceName, writeMessage, getCurrentTimestamp(), 0);
                loadMessagesFromDatabase(connectingDevice.deviceName);
                break;

            case MainActivity.message_read:
                String readMessage = new String((byte[]) msg.obj, 0, msg.arg1);
                dbHelper.insertMessage(connectingDevice.deviceName, "This Device", readMessage, getCurrentTimestamp(), 0);
                loadMessagesFromDatabase(connectingDevice.deviceName);
                break;

            case MainActivity.MESSAGE_DEVICE_OBJECT:
                connectingDevice = (WifiP2pDevice) msg.obj;
                loadMessagesFromDatabase(connectingDevice.deviceName);
                Toast.makeText(getActivity(), "Connected to " + connectingDevice.deviceName, Toast.LENGTH_SHORT).show();
                break;

            case MainActivity.message_toast:
                Toast.makeText(getActivity(), "Error: " + msg.getData().getString("toast"), Toast.LENGTH_SHORT).show();
                break;
        }
        return false;
    });

    private String getCurrentTimestamp() {
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(new java.util.Date());
    }

    private void setStatus(String s) {
        status.setText(s);
    }

    @Override
    public void onResume() {
        super.onResume();
        networkChangeReceiver = new NetworkChangeReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        filter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        filter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        filter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
        requireActivity().registerReceiver(networkChangeReceiver, filter);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (controller != null) {
            controller.stop();
        }
        if (networkChangeReceiver != null) {
            requireActivity().unregisterReceiver(networkChangeReceiver);
        }
    }
}

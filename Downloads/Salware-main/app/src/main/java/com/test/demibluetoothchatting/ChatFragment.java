package com.test.demibluetoothchatting;

// Import necessary Android and Bluetooth components
import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
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

// ChatFragment class to handle Bluetooth chat functionality within the app
public class ChatFragment extends Fragment {

    private String deviceAddress; // Address of the selected Bluetooth device
    private boolean isReceiverRegistered = false; // Flag to track if the network receiver is registered
    private NetworkChangeReceiver networkChangeReceiver; // Receiver to handle network changes
    private BluetoothAdapter bluetoothAdapter; // Adapter to manage Bluetooth functions
    private BluetoothDevice connectingDevice; // Device currently being connected
    private Controller _chat_helper; // Chat controller to manage Bluetooth communication
    private DatabaseHelper dbHelper; // DatabaseHelper to interact with the SQLite database

    // Constants for message states
    public static final int state_change = 1;
    public static final int message_read = 2;
    public static final int message_write = 3;
    public static final int message_device_object = 4;
    public static final int message_toast = 5;
    public static final String device_object = "device_name";

    // UI components
    private TextView status; // TextView to display connection status
    private Button btnConnect; // Button to trigger Bluetooth connection
    private RecyclerView recyclerView; // RecyclerView to display chat messages
    private ChatAdapter chatAdapter; // Adapter for displaying messages in the RecyclerView
    private ArrayList<com.test.demibluetoothchatting.ChatMessage> chatMessages; // List of chat messages
    private TextView txt_device_name; // TextView to display the device's name

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    // Called when the view is created, used to inflate and set up the UI
    @SuppressLint("MissingPermission")
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.chat_fragment, container, false);

        // Initialize the Bluetooth adapter
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // Initialize the database helper
        dbHelper = new DatabaseHelper(getActivity());

        // Find and initialize UI components
        status = rootView.findViewById(R.id.status);
        btnConnect = rootView.findViewById(R.id.btn_connect);
        EditText inputLayout = rootView.findViewById(R.id.input_message);
        recyclerView = rootView.findViewById(R.id.list);
        recyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));

        // Initialize chat messages and set up the RecyclerView adapter
        chatMessages = new ArrayList<>();
        chatAdapter = new ChatAdapter(chatMessages, bluetoothAdapter.getName());
        recyclerView.setAdapter(chatAdapter);
        txt_device_name = rootView.findViewById(R.id.txt_device_name);

        // Handle back button click
        rootView.findViewById(R.id.btn_back).setOnClickListener(v -> {
            if (getFragmentManager() != null) {
                getFragmentManager().popBackStack();
            }
        });

        // Handle Bluetooth unavailability
        if (bluetoothAdapter == null) {
            if (getActivity() != null) {
                Toast.makeText(getActivity(), "Bluetooth is not available!", Toast.LENGTH_SHORT).show();
            } else {
                Log.e("ChatFragment", "Activity is null. Cannot show Toast.");
            }
//            Toast.makeText(getActivity(), "Bluetooth is not available!", Toast.LENGTH_SHORT).show();
            if (getFragmentManager() != null) {
                getFragmentManager().popBackStack();
            }
        }

        // Initialize the chat controller
        _chat_helper = new Controller(getActivity(), handler);

        // Retrieve arguments (device name and address) passed to the fragment
        Bundle args = getArguments();
        if (args != null) {
            String deviceName = args.getString("device_name");
            deviceAddress = args.getString("device_address");
            String uri = args.getString("uri");

            if (deviceName != null) {
                // Load chat messages for the selected device
                loadMessagesFromDatabase(deviceName);
                txt_device_name.setText(deviceName);
            }

            // Additional logic for handling "uri" if required
            if (uri != null) {
                // Handle URI (if applicable)
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
                // Send the message
                message_send(inputLayout.getText().toString());
                inputLayout.setText(""); // Clear the input field after sending the message
            }
        });

        return rootView;
    }

    // Connect to the selected Bluetooth device
    @SuppressLint("MissingPermission")
    private void connectToSelectedDevice(String deviceAddress) {
        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);
        _chat_helper.connect(device); // Start the Bluetooth connection
    }

    // Set the status message (e.g., connected, connecting, etc.)
    private void setStatus(String s) {
        status.setText(s);
    }

    // Load chat messages from the database based on the device name
    private void loadMessagesFromDatabase(String deviceName) {
        @SuppressLint("MissingPermission") String senderName = bluetoothAdapter.getName();
        String receiverName = deviceName;

        if (!senderName.isEmpty() && !receiverName.isEmpty()) {
            chatMessages.clear();
            // Get messages between the sender and receiver from the database
            chatMessages = dbHelper.getMessagesForDevice(senderName, receiverName);
            chatAdapter = new ChatAdapter(chatMessages, senderName);
            recyclerView.setAdapter(chatAdapter);
            recyclerView.scrollToPosition(chatMessages.size() - 1); // Scroll to the latest message
        }
    }

    // Send a message via Bluetooth
    private void message_send(String message) {
        if (_chat_helper.getState() != Controller.conneceed) {
            Toast.makeText(getActivity(), "Connection was lost!", Toast.LENGTH_SHORT).show();
            return;
        }

        if (message.length() > 0) {
            byte[] send = message.getBytes(); // Convert message to bytes
            _chat_helper.write(send); // Send the message via Bluetooth
        }
    }

    // Handler to manage message and connection state changes
    @SuppressLint("MissingPermission")
    private Handler handler = new Handler(msg -> {
        switch (msg.what) {
            case MainActivity.state_change:
                switch (msg.arg1) {
                    case Controller.conneceed:
                        setStatus("Connected to: " + connectingDevice.getName());
                        loadMessagesFromDatabase(connectingDevice.getName()); // Load messages on connection
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
                // Handle message write events (when a message is successfully written)
                String writeMessage = new String((byte[]) msg.obj);
                String writeTimestamp = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(new java.util.Date());
                String senderName = bluetoothAdapter.getName();
                String receiverName = connectingDevice.getName();
                // Insert the sent message into the local database
                dbHelper.insertMessage(senderName, receiverName, writeMessage, writeTimestamp, 0);
                loadMessagesFromDatabase(receiverName);
                break;

            case MainActivity.message_read:
                // Handle message read events (when a message is received)
                String readMessage = new String((byte[]) msg.obj, 0, msg.arg1);
                String readTimestamp = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(new java.util.Date());
                String readSenderName = connectingDevice.getName();
                String readReceiverName = bluetoothAdapter.getName();
                // Insert the received message into the local database
                dbHelper.insertMessage(readSenderName, readReceiverName, readMessage, readTimestamp, 0);
                loadMessagesFromDatabase(readSenderName);
                break;

            case MainActivity.message_device_object:
                // Handle device object messages (when a device is connected)
                connectingDevice = msg.getData().getParcelable(MainActivity.device_object);
                loadMessagesFromDatabase(connectingDevice.getName());
                Toast.makeText(getActivity(), "Connected to " + connectingDevice.getName(), Toast.LENGTH_SHORT).show();
                break;

            case MainActivity.message_toast:
                // Handle toast messages (for displaying errors)
                String errorMessage = msg.getData().getString("toast");
                Toast.makeText(getActivity(), "Error: " + errorMessage, Toast.LENGTH_SHORT).show();
                break;
        }
        return false;
    });

    // Called when the fragment is started
    @SuppressLint("MissingPermission")
    @Override
    public void onStart() {
        super.onStart();
        if (_chat_helper != null) {
            _chat_helper.start(); // Start the chat helper to manage Bluetooth connection
        }
    }

    // Check if the network is available
    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getActivity().getSystemService(Context.CONNECTIVITY_SERVICE);

        if (connectivityManager != null) {
            NetworkInfo activeNetwork = connectivityManager.getActiveNetworkInfo();
            if (activeNetwork != null && activeNetwork.isConnected()) {
                return activeNetwork.getType() == ConnectivityManager.TYPE_WIFI || activeNetwork.getType() == ConnectivityManager.TYPE_MOBILE;
            }
        }
        return false;
    }

    // Called when the fragment is paused
    @Override
    public void onPause() {
        super.onPause();
        if (networkChangeReceiver != null) {
            getActivity().unregisterReceiver(networkChangeReceiver); // Unregister the network change receiver
        }
    }

    // Called when the fragment is destroyed
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (isReceiverRegistered) {
            isReceiverRegistered = false; // Reset the receiver registration flag
        }
        if (_chat_helper != null) {
            _chat_helper.stop(); // Stop the chat helper
        }
    }

    // Called when the fragment is resumed
    @Override
    public void onResume() {
        super.onResume();
        networkChangeReceiver = new NetworkChangeReceiver(); // Initialize the network change receiver
        IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        getActivity().registerReceiver(networkChangeReceiver, filter); // Register the network change receiver
    }
}

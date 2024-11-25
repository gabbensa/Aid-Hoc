package com.test.demibluetoothchatting.Tabs;

// Import necessary Android and Bluetooth components
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.test.demibluetoothchatting.ChatFragment;
import com.test.demibluetoothchatting.MainActivity;
import com.test.demibluetoothchatting.R;

import java.util.Set;

// messages_tab fragment displays a list of paired and discovered Bluetooth devices, allowing the user to initiate chats
public class messages_tab extends Fragment {

    private boolean isChatListenerAttached = false;
    private boolean isFragmentVisible = false;
    private LinearLayoutManager linearLayoutManager;
    RecyclerView recyclerView;
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";
    private String mParam1;
    private String mParam2;
    private BluetoothAdapter bluetoothAdapter; // Bluetooth adapter for managing Bluetooth functions
    private ArrayAdapter<String> pairedDevicesAdapter; // Adapter for paired devices list
    private ArrayAdapter<String> discoveredDevicesAdapter; // Adapter for discovered devices list
    private boolean isReceiverRegistered = false; // Flag to check if the BroadcastReceiver is registered

    // Default constructor
    public messages_tab() {
    }

    // Static method to create a new instance of messages_tab with parameters
    public static messages_tab newInstance(String param1, String param2) {
        messages_tab fragment = new messages_tab();
        Bundle args = new Bundle();
        args.putString(ARG_PARAM1, param1);
        args.putString(ARG_PARAM2, param2);
        fragment.setArguments(args);
        return fragment;
    }

    // Called when the fragment is created
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mParam1 = getArguments().getString(ARG_PARAM1);
            mParam2 = getArguments().getString(ARG_PARAM2);
        }
    }

    // Called to create the view for the fragment
    @SuppressLint("MissingInflatedId")
    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View main_view = inflater.inflate(R.layout.messages_tab, container, false);

        // ListView for paired and discovered devices
        ListView pairedDeviceList = main_view.findViewById(R.id.pairedDeviceList);
        ListView discoveredDeviceList = main_view.findViewById(R.id.discoveredDeviceList);

        // Initialize ArrayAdapters for paired and discovered devices
        pairedDevicesAdapter = new ArrayAdapter<>(getActivity(), android.R.layout.simple_list_item_1);
        discoveredDevicesAdapter = new ArrayAdapter<>(getActivity(), android.R.layout.simple_list_item_1);

        pairedDeviceList.setAdapter(pairedDevicesAdapter);
        discoveredDeviceList.setAdapter(discoveredDevicesAdapter);

        // Get the Bluetooth adapter
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            // Display a message if Bluetooth is not available and finish the activity
            Toast.makeText(getActivity(), "Bluetooth is not available!", Toast.LENGTH_SHORT).show();
            getActivity().finish();
        }

        // Show the list of paired devices
        showPairedDevices();

        // Cancel Bluetooth discovery if it's already running and start a new discovery
        if (bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
        }
        bluetoothAdapter.startDiscovery();

        // Register for broadcasts when a new device is discovered
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        getActivity().registerReceiver(discoveryFinishReceiver, filter);
        isReceiverRegistered = true; // Mark the receiver as registered

        // Set click listener for paired devices
        pairedDeviceList.setOnItemClickListener((parent, view, position, id) -> {
            bluetoothAdapter.cancelDiscovery(); // Stop discovery
            String info = ((TextView) view).getText().toString();
            String deviceName = info.substring(0, info.length() - 18); // Extract device name
            String deviceAddress = info.substring(info.length() - 17); // Extract device address
            openChatFragment(deviceName, deviceAddress); // Open chat fragment with selected device
        });

        // Set click listener for discovered devices
        discoveredDeviceList.setOnItemClickListener((parent, view, position, id) -> {
            bluetoothAdapter.cancelDiscovery(); // Stop discovery
            String info = ((TextView) view).getText().toString();
            String deviceName = info.substring(0, info.length() - 18); // Extract device name
            String deviceAddress = info.substring(info.length() - 17); // Extract device address
            openChatFragment(deviceName, deviceAddress); // Open chat fragment with selected device
        });

        return main_view;
    }

    // Called when the fragment becomes visible
    @Override
    public void onStart() {
        super.onStart();
    }

    // Show the list of paired Bluetooth devices
    @SuppressLint("MissingPermission")
    private void showPairedDevices() {
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices(); // Get the list of paired devices
        if (pairedDevices.size() > 0) {
            // Add each paired device to the adapter
            for (BluetoothDevice device : pairedDevices) {
                pairedDevicesAdapter.add(device.getName() + "\n" + device.getAddress());
            }
        } else {
            // If no devices are paired, show a message indicating none are paired
            pairedDevicesAdapter.add(getString(R.string.none_paired));
        }
    }

    // BroadcastReceiver for Bluetooth device discovery
    private final BroadcastReceiver discoveryFinishReceiver = new BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            // Check if a new Bluetooth device was found
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                // Add the discovered device to the list if it is not already paired
                if (device != null && device.getBondState() != BluetoothDevice.BOND_BONDED) {
                    discoveredDevicesAdapter.add(device.getName() + "\n" + device.getAddress());
                }
            }
        }
    };

    // Open the ChatFragment for a selected device
    private void openChatFragment(String deviceName, String deviceAddress) {
        ChatFragment chatFragment = new ChatFragment(); // Create a new instance of ChatFragment

        // Pass the selected device's name and address as arguments to the ChatFragment
        Bundle args = new Bundle();
        args.putString("device_name", deviceName);
        args.putString("device_address", deviceAddress);
        chatFragment.setArguments(args);

        // Use FragmentTransaction to replace the current fragment with the ChatFragment
        FragmentTransaction transaction = ((AppCompatActivity) getActivity()).getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.root_layout, chatFragment, "chat_fragment_tag");

        // Add the transaction to the back stack for proper navigation
        transaction.addToBackStack(null);
        transaction.commit();
    }

    // Called when the fragment is destroyed
    @Override
    public void onDestroy() {
        super.onDestroy();
        // Unregister the BroadcastReceiver if it was registered
        if (isReceiverRegistered) {
            getActivity().unregisterReceiver(discoveryFinishReceiver);
        }
    }
}

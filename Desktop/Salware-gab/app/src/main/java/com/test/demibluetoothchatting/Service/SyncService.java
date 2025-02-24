package com.test.demibluetoothchatting.Service;

// Import necessary Android and Firebase components
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.test.demibluetoothchatting.ChatMessage;
import com.test.demibluetoothchatting.Controller;
import com.test.demibluetoothchatting.Database.DatabaseHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

// SyncService class extends Android's Service class and is responsible for syncing unsynced messages with Firebase
public class SyncService extends Service {

    private DatabaseHelper db; // Local SQLite database helper
    private DatabaseReference firebaseDb; // Firebase database reference

    // Called when the service is created
    @Override
    public void onCreate() {
        super.onCreate();
        // Initialize the SQLite database helper
        db = new DatabaseHelper(this);
        // Initialize the Firebase database reference to the "chatting_data" node
        firebaseDb = FirebaseDatabase.getInstance().getReference("chatting_data");
    }

    // Called when the service is started
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Get the list of unsynced messages from the local database
        ArrayList<ChatMessage> unsyncedMessages = db.getUnsyncedMessages();

        // Retrieve additional data like latitude, longitude, and device name from the controller
        String latitude = Controller.GetData(getApplicationContext(),"lat");
        String longitude = Controller.GetData(getApplicationContext(),"lon");
        String deviceName = Controller.GetData(getApplicationContext(),"device_name");

        // Loop through each unsynced message and sync it to Firebase
        for (ChatMessage message : unsyncedMessages) {
            syncMessageToFirebase(message, latitude, longitude, deviceName);
        }

        return START_NOT_STICKY; // The service will not restart if it gets terminated
    }

    // Method to sync individual messages to Firebase
    private void syncMessageToFirebase(ChatMessage message, String latitude, String longitude, String deviceAddress) {
        // Generate the chat node using the sender and receiver names
        String chatNode = generateChatNode(message.getSender(), message.getReceiver());
        // Generate a unique message ID in Firebase
        String messageId = firebaseDb.child(chatNode).push().getKey();

        if (messageId != null) {
            // Store the message data in Firebase under the generated chat node
            firebaseDb.child(chatNode).child(messageId).setValue(message).addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    // Mark the message as synced in the local database if the sync was successful
                    db.markMessageAsSynced(message.getId());
                    Log.d("SyncService", "Message synced: " + message.getMessage());

                    // Save the device's location to Firebase after syncing the message
                    saveDeviceLocationToFirebase(chatNode, deviceAddress, latitude, longitude);
                } else {
                    // Log an error if the sync fails
                    Log.d("SyncService", "Failed to sync message: " + message.getMessage());
                }
            });
        }
    }

    // Helper method to generate a consistent chat node name based on the sender and receiver
    private String generateChatNode(String senderName, String receiverName) {
        // Ensures that the chat node name is always in lexicographical order, so it's the same for both sender and receiver
        if (senderName.compareTo(receiverName) < 0) {
            return senderName + "_" + receiverName;
        } else {
            return receiverName + "_" + senderName;
        }
    }

    // Method to save the device's location to Firebase after syncing the message
//    private void saveDeviceLocationToFirebase(String chatNode, String deviceAddress, String latitude, String longitude) {
//        // Reference to the "locations" node in Firebase under the specific chat node
//        DatabaseReference locationRef = firebaseDb.child(chatNode).child("locations").child(deviceAddress);
//
//        // Create a map containing the latitude and longitude values
//        Map<String, Object> locationData = new HashMap<>();
//        locationData.put("latitude", latitude);
//        locationData.put("longitude", longitude);
//
//        // Update the location data in Firebase and log the result
//        locationRef.updateChildren(locationData).addOnCompleteListener(task -> {
//            if (task.isSuccessful()) {
//                Log.d("SyncService", "Device location synced: " + latitude + ", " + longitude);
//            } else {
//                Log.d("SyncService", "Failed to sync device location");
//            }
//        });
//    }
    private void saveDeviceLocationToFirebase(String chatNode, String deviceAddress, String latitude, String longitude) {
        if (chatNode == null || deviceAddress == null || latitude == null || longitude == null) {
            Log.e("SyncService", "Invalid parameters for saving device location: " +
                    "chatNode=" + chatNode + ", deviceAddress=" + deviceAddress +
                    ", latitude=" + latitude + ", longitude=" + longitude);
            return; // Stop execution if any parameter is null
        }

        // Reference to the "locations" node in Firebase under the specific chat node
        DatabaseReference locationRef = firebaseDb.child(chatNode).child("locations").child(deviceAddress);

        // Create a map containing the latitude and longitude values
        Map<String, Object> locationData = new HashMap<>();
        locationData.put("latitude", latitude);
        locationData.put("longitude", longitude);

        // Update the location data in Firebase and log the result
        locationRef.updateChildren(locationData).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Log.d("SyncService", "Device location synced: " + latitude + ", " + longitude);
            } else {
                Log.d("SyncService", "Failed to sync device location");
            }
        });
    }


    // This method is required by the Service class, but we're returning null since we don't need binding
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

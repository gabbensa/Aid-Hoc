package com.test.demibluetoothchatting.Service;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.test.demibluetoothchatting.ChatMessage;
import com.test.demibluetoothchatting.Controller;
import com.test.demibluetoothchatting.Database.DatabaseHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class MessageSyncWorker extends Worker {
    private static final String TAG = "MessageSyncWorker";
    private DatabaseHelper db;
    private DatabaseReference firebaseDb;

    public MessageSyncWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        db = new DatabaseHelper(context);
        firebaseDb = FirebaseDatabase.getInstance().getReference("chatting_data");
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "==== SYNC WORKER STARTED ====");

        try {
            // Get unsynced messages
            ArrayList<ChatMessage> unsyncedMessages = db.getUnsyncedMessages();
            Log.d(TAG, "Found " + unsyncedMessages.size() + " unsynced messages");

            if (unsyncedMessages.isEmpty()) {
                Log.d(TAG, "No messages to sync.");
                return Result.success();
            }

            String latitude = Controller.GetData(getApplicationContext(), "lat");
            String longitude = Controller.GetData(getApplicationContext(), "lon");
            String userName = getUserName(getApplicationContext());

            // Initialize Firebase
            firebaseDb = FirebaseDatabase.getInstance().getReference("chatting_data");

            final CountDownLatch latch = new CountDownLatch(unsyncedMessages.size());
            final boolean[] allSuccessful = {true};

            for (ChatMessage message : unsyncedMessages) {
                syncMessageToFirebase(message, latitude, longitude, userName, latch, allSuccessful);
            }

            try {
                // Wait for all sync operations to complete or timeout after 30 seconds
                boolean completed = latch.await(30, TimeUnit.SECONDS);
                if (!completed) {
                    Log.e(TAG, "Sync timed out");
                    return Result.retry();
                }
            } catch (InterruptedException e) {
                Log.e(TAG, "Sync interrupted", e);
                return Result.retry();
            }

            if (allSuccessful[0]) {
                Log.d(TAG, "All messages synced successfully");
                return Result.success();
            } else {
                Log.e(TAG, "Some messages failed to sync");
                return Result.retry();
            }

        } catch (Exception e) {
            Log.e(TAG, "Error during sync", e);
            return Result.retry();
        }
    }

    private String getUserName(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        String userName = prefs.getString("userName", null);

        if (userName == null || userName.isEmpty()) {
            userName = android.os.Build.MODEL;
            prefs.edit().putString("userName", userName).apply();
        }

        return userName;
    }

    private void syncMessageToFirebase(ChatMessage message, String latitude, String longitude, String userName, CountDownLatch latch, boolean[] allSuccessful) {
        // Only sync messages that were sent by the current user
        if (!message.getSender().equals(userName)) {
            // If this message was received (not sent by us), mark it as synced locally
            // since it was already synced by the sender
            db.markMessageAsSynced(message.getId());
            Log.d(TAG, "Skipping sync of received message: " + message.getMessage());
            latch.countDown();
            return;
        }

        String sender = message.getSender();
        String receiver = message.getReceiver();
        String sanitizedSender = sanitizeForFirebase(sender);
        String sanitizedReceiver = sanitizeForFirebase(receiver);
        String chatNode = generateChatNode(sanitizedSender, sanitizedReceiver);

        Log.d(TAG, "Syncing message to chat node: " + chatNode);

        // Check if this message already exists in Firebase
        firebaseDb.child(chatNode)
                .orderByChild("timestamp")
                .equalTo(message.getTimestamp())
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        if (dataSnapshot.exists()) {
                            // Message already exists in Firebase, mark as synced locally
                            db.markMessageAsSynced(message.getId());
                            Log.d(TAG, "Message already exists in Firebase, marked as synced locally: " + message.getMessage());
                            latch.countDown();
                        } else {
                            // Message doesn't exist, sync it
                            String messageId = firebaseDb.child(chatNode).push().getKey();
                            if (messageId != null) {
                                firebaseDb.child(chatNode).child(messageId).setValue(message)
                                        .addOnCompleteListener(task -> {
                                            if (task.isSuccessful()) {
                                                db.markMessageAsSynced(message.getId());
                                                Log.d(TAG, "Synced new message: " + message.getMessage());
                                                if (latitude != null && longitude != null) {
                                                    saveDeviceLocation(chatNode, sanitizedSender, latitude, longitude);
                                                }
                                            } else {
                                                Log.e(TAG, "Sync failed: " + message.getMessage(), task.getException());
                                                allSuccessful[0] = false;
                                            }
                                            latch.countDown();
                                        });
                            } else {
                                Log.e(TAG, "Failed to generate messageId");
                                allSuccessful[0] = false;
                                latch.countDown();
                            }
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {
                        Log.e(TAG, "Firebase query cancelled", databaseError.toException());
                        allSuccessful[0] = false;
                        latch.countDown();
                    }
                });
    }

    private void saveDeviceLocation(String chatNode, String deviceName, String latitude, String longitude) {
        if (chatNode == null || deviceName == null || latitude == null || longitude == null) {
            Log.e(TAG, "Invalid parameters for saving device location");
            return;
        }

        DatabaseReference locationRef = firebaseDb.child(chatNode).child("locations").child(deviceName);
        Map<String, Object> locationData = new HashMap<>();
        locationData.put("latitude", latitude);
        locationData.put("longitude", longitude);

        locationRef.updateChildren(locationData).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Log.d(TAG, "Device location synced");
            } else {
                Log.e(TAG, "Failed to sync device location", task.getException());
            }
        });
    }

    private String sanitizeForFirebase(String input) {
        if (input == null) {
            return "unknown";
        }
        // Replace characters that are not allowed in Firebase keys
        return input.replaceAll("[.#$\\[\\]]", "_");
    }

    private String generateChatNode(String sender, String receiver) {
        // Sort sender and receiver to ensure consistent chat node names
        if (sender.compareTo(receiver) < 0) {
            return sender + "_" + receiver;
        } else {
            return receiver + "_" + sender;
        }
    }
}


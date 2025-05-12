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
        Log.e("MessageSyncWorker", "==== SYNC WORKER STARTED ====");

        try {
            // Get unsynced messages
            ArrayList<ChatMessage> unsyncedMessages = db.getUnsyncedMessages();
            Log.e("MessageSyncWorker", "Found " + unsyncedMessages.size() + " unsynced messages");

            String latitude = Controller.GetData(getApplicationContext(), "lat");
            String longitude = Controller.GetData(getApplicationContext(), "lon");

            // Obtenir le nom d'utilisateur au lieu du nom de l'appareil
            String userName = getUserName(getApplicationContext());

            if (unsyncedMessages.isEmpty()) {
                Log.d("MessageSyncWorker", "No messages to sync.");
                return Result.success();
            }

            // Initialize Firebase
            firebaseDb = FirebaseDatabase.getInstance().getReference("chatting_data");

            final CountDownLatch latch = new CountDownLatch(unsyncedMessages.size());
            final boolean[] allSuccessful = {true};

            for (ChatMessage message : unsyncedMessages) {
                syncMessageToFirebase(message, latitude, longitude, userName, latch, allSuccessful);
            }

            try {
                latch.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Log.e("MessageSyncWorker", "Sync interrupted", e);
                return Result.retry();
            }

            if (allSuccessful[0]) {
                Log.d("MessageSyncWorker", "All messages synced successfully");
                Log.e("MessageSyncWorker", "==== SYNC WORKER COMPLETED SUCCESSFULLY ====");
                return Result.success();
            } else {
                Log.e("MessageSyncWorker", "Some messages failed to sync");
                return Result.retry();
            }

        } catch (Exception e) {
            Log.e("MessageSyncWorker", "Error during sync", e);
            return Result.retry();
        }
    }

    // Ajouter cette méthode pour obtenir le nom d'utilisateur
    private String getUserName(Context context) {
        // Essayer d'obtenir le nom d'utilisateur des préférences partagées
        SharedPreferences prefs = context.getSharedPreferences("UserPrefs", Context.MODE_PRIVATE);
        String userName = prefs.getString("userName", null);

        // Si aucun nom d'utilisateur n'est défini, utiliser le nom du modèle de l'appareil
        if (userName == null || userName.isEmpty()) {
            userName = android.os.Build.MODEL;
            // Sauvegarder ce nom pour une utilisation future
            prefs.edit().putString("userName", userName).apply();
        }

        return userName;
    }

    private void syncMessageToFirebase(ChatMessage message, String latitude, String longitude, String userName, CountDownLatch latch, boolean[] allSuccessful) {
        // Utiliser le nom d'utilisateur pour le sender si c'est notre appareil
        if (message.getSender().contains("This Device") || message.getSender().equals(android.os.Build.MODEL)) {
            message.setSender(userName);
        }

        // Sanitize device names and other identifiers for Firebase path
        String sanitizedSender = sanitizeForFirebase(message.getSender());
        String sanitizedReceiver = sanitizeForFirebase(message.getReceiver());

        // Generate chat node with sanitized values
        String chatNode = generateChatNode(sanitizedSender, sanitizedReceiver);

        Log.d("MessageSyncWorker", "Using chat node: " + chatNode);

        // Vérifier d'abord si ce message existe déjà sur Firebase
        firebaseDb.child(chatNode).orderByChild("timestamp").equalTo(message.getTimestamp())
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        if (dataSnapshot.exists()) {
                            // Le message existe déjà, marquer comme synchronisé localement
                            db.markMessageAsSynced(message.getId());
                            Log.d("MessageSyncWorker", "Message already exists in Firebase, marked as synced locally: " + message.getMessage());
                            latch.countDown();
                        } else {
                            // Le message n'existe pas encore, le synchroniser
                            String messageId = firebaseDb.child(chatNode).push().getKey();
                            if (messageId != null) {
                                firebaseDb.child(chatNode).child(messageId).setValue(message)
                                        .addOnCompleteListener(task -> {
                                            if (task.isSuccessful()) {
                                                db.markMessageAsSynced(message.getId());
                                                Log.d("MessageSyncWorker", "Synced: " + message.getMessage());
                                                saveDeviceLocation(chatNode, sanitizedSender, latitude, longitude);
                                            } else {
                                                Log.e("MessageSyncWorker", "Sync failed: " + message.getMessage(), task.getException());
                                                allSuccessful[0] = false;
                                            }
                                            latch.countDown();
                                        });
                            } else {
                                Log.e("MessageSyncWorker", "Failed to generate messageId");
                                allSuccessful[0] = false;
                                latch.countDown();
                            }
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {
                        Log.e("MessageSyncWorker", "Firebase query cancelled", databaseError.toException());
                        allSuccessful[0] = false;
                        latch.countDown();
                    }
                });
    }

    private void saveDeviceLocation(String chatNode, String deviceName, String latitude, String longitude) {
        if (chatNode == null || deviceName == null || latitude == null || longitude == null) {
            Log.e("MessageSyncWorker", "Invalid parameters for saving device location");
            return;
        }

        DatabaseReference locationRef = firebaseDb.child(chatNode).child("locations").child(deviceName);
        Map<String, Object> locationData = new HashMap<>();
        locationData.put("latitude", latitude);
        locationData.put("longitude", longitude);

        locationRef.updateChildren(locationData).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Log.d("MessageSyncWorker", "Device location synced");
            } else {
                Log.e("MessageSyncWorker", "Failed to sync device location", task.getException());
            }
        });
    }

    private String sanitizeForFirebase(String input) {
        if (input == null) {
            return "unknown";
        }
        // Remove Firebase invalid characters: ., #, $, [, ]
        return input.replaceAll("[.#$\\[\\]]", "_");
    }

    private String generateChatNode(String sender, String receiver) {
        return (sender.compareTo(receiver) < 0) ? sender + "_" + receiver : receiver + "_" + sender;
    }
}


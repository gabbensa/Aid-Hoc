package com.test.demibluetoothchatting.Database;

// Import necessary Android and Java components
import static android.content.ContentValues.TAG;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import com.test.demibluetoothchatting.ChatMessage;
import com.test.demibluetoothchatting.User;

import java.util.ArrayList;

// DatabaseHelper class for handling SQLite database operations such as user management and storing chat messages
public class DatabaseHelper extends SQLiteOpenHelper {

    // Constants for database name and table structures
    public static final String DATABASE_NAME = "bchat_app.db"; // Database name

    // Constants for the Users table
    public static final String USER_TABLE_NAME = "users_table";
    public static final String USER_COL_1 = "ID";              // Primary Key
    public static final String USER_COL_2 = "FULLNAME";        // Full name of the user
    public static final String USER_COL_3 = "USERNAME";        // Username for login
    public static final String USER_COL_4 = "PASSWORD";        // Password for login

    // Constants for the Messages table
    public static final String MESSAGE_TABLE_NAME = "messages_table";
    public static final String MESSAGE_COL_1 = "ID";           // Primary Key
    public static final String MESSAGE_COL_2 = "SENDER";       // Sender of the message
    public static final String MESSAGE_COL_3 = "RECEIVER";     // Receiver of the message
    public static final String MESSAGE_COL_4 = "MESSAGE";      // Content of the message
    public static final String MESSAGE_COL_5 = "TIMESTAMP";    // Timestamp when the message was sent
    public static final String MESSAGE_COL_6 = "SYNCED";       // Sync status (0 = unsynced, 1 = synced)

    // Dans DatabaseHelper.java, ajouter après les constantes existantes
    public static final String USER_DEVICES_TABLE_NAME = "user_devices_table";
    public static final String USER_DEVICES_COL_1 = "ID";              // Primary Key
    public static final String USER_DEVICES_COL_2 = "USERNAME";        // Username
    public static final String USER_DEVICES_COL_3 = "DEVICE_NAME";     // Nom de l'appareil
    public static final String USER_DEVICES_COL_4 = "DEVICE_ADDRESS";  // Adresse MAC
    public static final String USER_DEVICES_COL_5 = "LAST_UPDATED";    // Dernière mise à jour

    // Constructor for initializing the database helper with the context and database name
    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, 5);
    }

    // onCreate is called when the database is created for the first time
    @Override
    public void onCreate(SQLiteDatabase db) {
        // Tables existantes
        db.execSQL("CREATE TABLE " + USER_TABLE_NAME + " (ID INTEGER PRIMARY KEY AUTOINCREMENT, FULLNAME TEXT, USERNAME TEXT, PASSWORD TEXT, USERTYPE TEXT)");
        db.execSQL("CREATE TABLE " + MESSAGE_TABLE_NAME + " (ID INTEGER PRIMARY KEY AUTOINCREMENT, SENDER TEXT, RECEIVER TEXT, MESSAGE TEXT, TIMESTAMP DATETIME DEFAULT CURRENT_TIMESTAMP, SYNCED INTEGER DEFAULT 0)");
        
        // Nouvelle table pour l'association username-device
        db.execSQL("CREATE TABLE " + USER_DEVICES_TABLE_NAME + " (" + USER_DEVICES_COL_1 + " INTEGER PRIMARY KEY AUTOINCREMENT, " + USER_DEVICES_COL_2 + " TEXT, " + USER_DEVICES_COL_3 + " TEXT, " + USER_DEVICES_COL_4 + " TEXT, " + USER_DEVICES_COL_5 + " DATETIME DEFAULT CURRENT_TIMESTAMP)");
    }

    // onUpgrade is called when the database needs to be upgraded, such as when the schema changes
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 4) {
            // Ajouter la colonne USERTYPE si nécessaire
            db.execSQL("ALTER TABLE " + USER_TABLE_NAME + " ADD COLUMN USERTYPE TEXT");
        }
        
        if (oldVersion < 5) {
            // Créer la nouvelle table user_devices_table si elle n'existe pas
            db.execSQL("CREATE TABLE IF NOT EXISTS " + USER_DEVICES_TABLE_NAME + " (" 
                + USER_DEVICES_COL_1 + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + USER_DEVICES_COL_2 + " TEXT, "
                + USER_DEVICES_COL_3 + " TEXT, "
                + USER_DEVICES_COL_4 + " TEXT, "
                + USER_DEVICES_COL_5 + " DATETIME DEFAULT CURRENT_TIMESTAMP)");
        }
    }

    // Insert a new message into the messages table
    public boolean insertMessage(String sender, String receiver, String message, String timestamp, int synced) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues contentValues = new ContentValues();
        // Set values for sender, receiver, message content, timestamp, and sync status
        contentValues.put(MESSAGE_COL_2, sender);
        contentValues.put(MESSAGE_COL_3, receiver);
        contentValues.put(MESSAGE_COL_4, message);
        contentValues.put(MESSAGE_COL_5, timestamp);
        contentValues.put(MESSAGE_COL_6, synced);  // Sync status (0 = unsynced, 1 = synced)

        long result = db.insert(MESSAGE_TABLE_NAME, null, contentValues);
        return result != -1; // Return true if insertion was successful, false otherwise
    }

    // Fetch all messages between a specific sender and receiver
    @SuppressLint("Range")
    public ArrayList<ChatMessage> getMessagesForDevice(String sender, String receiver) {
        ArrayList<ChatMessage> messages = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        // Query to get messages where the sender and receiver match the provided values
        Cursor cursor = db.rawQuery("SELECT * FROM " + MESSAGE_TABLE_NAME + " WHERE SENDER = ? AND RECEIVER = ? OR SENDER = ? AND RECEIVER = ?",
                new String[]{sender, receiver, receiver, sender});

        // Loop through the results and populate the messages list
        if (cursor.moveToFirst()) {
            do {
                int messageId = cursor.getInt(cursor.getColumnIndex(MESSAGE_COL_1));
                String messageContent = cursor.getString(cursor.getColumnIndex(MESSAGE_COL_4));
                String messageSender = cursor.getString(cursor.getColumnIndex(MESSAGE_COL_2));
                String messageReceiver = cursor.getString(cursor.getColumnIndex(MESSAGE_COL_3));
                String timestamp = cursor.getString(cursor.getColumnIndex(MESSAGE_COL_5));

                // Create a new ChatMessage object with the retrieved data
                ChatMessage message = new ChatMessage(messageId, messageSender, messageReceiver, messageContent, timestamp);
                messages.add(message); // Add message to the list
            } while (cursor.moveToNext());
        }

        cursor.close(); // Close the cursor
        return messages; // Return the list of messages
    }

    // Fetch all unsynced messages (where SYNCED column is 0)
    @SuppressLint("Range")
    public ArrayList<ChatMessage> getUnsyncedMessages() {
        ArrayList<ChatMessage> messageList = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;

        try {
            // Query to get unsynced messages
            cursor = db.rawQuery("SELECT * FROM " + MESSAGE_TABLE_NAME + " WHERE SYNCED = 0", null);

            // Loop through the results and add unsynced messages to the list
            if (cursor.moveToFirst()) {
                do {
                    int messageId = cursor.getInt(cursor.getColumnIndex(MESSAGE_COL_1));
                    String sender = cursor.getString(cursor.getColumnIndex(MESSAGE_COL_2));
                    String receiver = cursor.getString(cursor.getColumnIndex(MESSAGE_COL_3));
                    String message = cursor.getString(cursor.getColumnIndex(MESSAGE_COL_4));
                    String timestamp = cursor.getString(cursor.getColumnIndex(MESSAGE_COL_5));

                    // Create a new ChatMessage object and add to the list
                    ChatMessage chatMessage = new ChatMessage(messageId, sender, receiver, message, timestamp);
                    messageList.add(chatMessage);
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            e.printStackTrace(); // Log any exceptions
        } finally {
            // Ensure the cursor is closed after use
            if (cursor != null) {
                cursor.close();
            }
        }

        return messageList; // Return the list of unsynced messages
    }

    // Insert a new user into the users table
    public boolean insertUser(String fullname, String username, String password, String userType) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues contentValues = new ContentValues();
        // Set values for user details
        contentValues.put(USER_COL_2, fullname);
        contentValues.put(USER_COL_3, username);
        contentValues.put(USER_COL_4, password);
        contentValues.put("USERTYPE", userType);

        long result = db.insert(USER_TABLE_NAME, null, contentValues);
        return result != -1; // Return true if insertion was successful, false otherwise
    }

    // Check if a user with the given username and password exists
    @SuppressLint("Range")
    public int checkUser(String username, String password) {
        SQLiteDatabase db = this.getReadableDatabase();
        // Query to check if user exists with the provided username and password
        Cursor cursor = db.rawQuery("SELECT ID FROM " + USER_TABLE_NAME + " WHERE USERNAME = ? AND PASSWORD = ?", new String[]{username, password});

        int userId = -1; // Default value if user is not found
        if (cursor.moveToFirst()) {
            userId = cursor.getInt(cursor.getColumnIndex("ID"));  // Get the user ID
        }
        cursor.close(); // Close the cursor
        return userId;  // Return the user ID or -1 if not found
    }

    // Mark a message as synced in the database by updating the SYNCED column
    public void markMessageAsSynced(int messageId) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(MESSAGE_COL_6, 1); // Set SYNCED to 1 (synced)
        db.update(MESSAGE_TABLE_NAME, values, "ID = ?", new String[]{String.valueOf(messageId)});
    }

    // Fetch a user by their ID
    @SuppressLint("Range")
    public User getUserById(int userId) {
        User user = null;
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = null;

        try {
            // Query to fetch the user where the ID matches
            cursor = db.rawQuery("SELECT * FROM " + USER_TABLE_NAME + " WHERE ID = ?", new String[]{String.valueOf(userId)});

            // If a user with the provided ID exists, retrieve their data
            if (cursor.moveToFirst()) {
                String fullName = cursor.getString(cursor.getColumnIndex(USER_COL_2));
                String username = cursor.getString(cursor.getColumnIndex(USER_COL_3));
                String password = cursor.getString(cursor.getColumnIndex(USER_COL_4));
                String userType = cursor.getString(cursor.getColumnIndex("USERTYPE"));

                // Create a new User object with the retrieved data
                user = new User(userId, fullName, username, password, userType);
            }
        } catch (Exception e) {
            e.printStackTrace(); // Log any exceptions
        } finally {
            // Ensure the cursor is closed after use
            if (cursor != null) {
                cursor.close();
            }
        }

        return user; // Return the user object or null if not found
    }

    public String getUsernameByMac(String macAddress) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT FULLNAME FROM " + USER_TABLE_NAME + " WHERE MAC_ADDRESS = ?", new String[]{macAddress});

        if (cursor.moveToFirst()) {
            String username = cursor.getString(0);
            cursor.close();
            return username;
        }
        cursor.close();
        return null;
    }

    // Méthode pour associer un username à un device
    public boolean associateUsernameWithDevice(String username, String deviceName, String deviceAddress) {
        Log.d(TAG, "Associating username/device - username: " + username + ", deviceName: " + deviceName + ", deviceAddress: " + deviceAddress);
        
        if (username == null || username.isEmpty() || deviceName == null || deviceName.isEmpty() || deviceAddress == null || deviceAddress.isEmpty()) {
            Log.e(TAG, "Invalid parameters for association");
            return false;
        }

        SQLiteDatabase db = this.getWritableDatabase();
        boolean success = false;

        try {
            // Vérifier si une association existe déjà pour cette adresse
            Cursor cursor = db.query(USER_DEVICES_TABLE_NAME,
                    new String[]{"id", "username"},
                    USER_DEVICES_COL_4 + " = ?",
                    new String[]{deviceAddress},
                    null, null, null);

            if (cursor != null && cursor.moveToFirst()) {
                // Mettre à jour l'association existante
                int id = cursor.getInt(cursor.getColumnIndexOrThrow("ID"));
                String oldUsername = cursor.getString(cursor.getColumnIndexOrThrow("USERNAME"));
                
                Log.d(TAG, "Updating existing association for device " + deviceAddress + 
                          " - Old username: " + oldUsername + ", New username: " + username);
                
                ContentValues values = new ContentValues();
                values.put(USER_DEVICES_COL_2, username);
                values.put(USER_DEVICES_COL_3, deviceName);
                values.put(USER_DEVICES_COL_4, deviceAddress);
                values.put(USER_DEVICES_COL_5, System.currentTimeMillis());

                success = db.update(USER_DEVICES_TABLE_NAME, values, USER_DEVICES_COL_1 + " = ?", new String[]{String.valueOf(id)}) > 0;
            } else {
                // Créer une nouvelle association
                Log.d(TAG, "Creating new association for device " + deviceAddress);
                
                ContentValues values = new ContentValues();
                values.put(USER_DEVICES_COL_2, username);
                values.put(USER_DEVICES_COL_3, deviceName);
                values.put(USER_DEVICES_COL_4, deviceAddress);
                values.put(USER_DEVICES_COL_5, System.currentTimeMillis());

                success = db.insert(USER_DEVICES_TABLE_NAME, null, values) != -1;
            }

            if (cursor != null) {
                cursor.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error associating username with device: " + e.getMessage(), e);
            success = false;
        }

        Log.d(TAG, "Association result: " + (success ? "success" : "failed"));
        return success;
    }

    // Méthode pour obtenir le username associé à un device
    public String getUsernameForDevice(String deviceAddress) {
        SQLiteDatabase db = this.getReadableDatabase();
        Log.d("DatabaseHelper", "Recherche du username pour deviceAddress: " + deviceAddress);
        
        Cursor cursor = db.query(USER_DEVICES_TABLE_NAME,
                new String[]{USER_DEVICES_COL_2},
                USER_DEVICES_COL_4 + " = ?",
                new String[]{deviceAddress},
                null, null, null);
        
        Log.d("DatabaseHelper", "Nombre de résultats trouvés: " + cursor.getCount());
        
        if (cursor.moveToFirst()) {
            String username = cursor.getString(0);
            Log.d("DatabaseHelper", "Username trouvé: " + username);
            cursor.close();
            return username;
        }
        Log.d("DatabaseHelper", "Aucun username trouvé pour ce device");
        cursor.close();
        return null;
    }

    // Méthode pour obtenir le device associé à un username
    public String getDeviceForUsername(String username) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(USER_DEVICES_TABLE_NAME,
                new String[]{USER_DEVICES_COL_4},
                USER_DEVICES_COL_2 + " = ?",
                new String[]{username},
                null, null, null);
        
        if (cursor.moveToFirst()) {
            String deviceAddress = cursor.getString(0);
            cursor.close();
            return deviceAddress;
        }
        cursor.close();
        return null;
    }

}

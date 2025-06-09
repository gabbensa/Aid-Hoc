package com.test.demibluetoothchatting;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.test.demibluetoothchatting.Database.DatabaseHelper;

public class LoginActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 101;
    private DatabaseHelper db;

    // UI elements
    private EditText editTextUsername, editTextPassword;
    private Button buttonLogin;
    private TextView buttonSignupRedirect;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Disable night mode
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);

        setContentView(R.layout.activity_login);

        // Initialize database helper
        db = new DatabaseHelper(this);

        // Check if the user is already logged in
        if (Controller.GetData(LoginActivity.this, "logged") != null) {
            proceedToMainActivity();
        }

        // Initialize UI elements
        editTextUsername = findViewById(R.id.txt_username);
        editTextPassword = findViewById(R.id.txt_password);
        buttonLogin = findViewById(R.id.loginbutton);
        buttonSignupRedirect = findViewById(R.id.textviewsignup);

        // Check and request necessary permissions
        if (!hasNecessaryPermissions()) {
            requestNecessaryPermissions();
        }

        // Login button click handler
        buttonLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String username = editTextUsername.getText().toString();
                String password = editTextPassword.getText().toString();

                // Validate credentials
                int userId = db.checkUser(username, password);
                if (userId != -1) {
                    User user = db.getUserById(userId);
                    String userType = user.getUserType();

                    // Sauvegarder les informations d'utilisateur dans les SharedPreferences
                    SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putString("userName", username);
                    editor.putString("deviceName", Build.MANUFACTURER + " " + Build.MODEL);
                    editor.apply();

                    Log.d("LOGIN_ASSOC", "username=" + username + " | deviceName=" + Build.MANUFACTURER + " " + Build.MODEL);

                    Toast.makeText(LoginActivity.this, "Login successful", Toast.LENGTH_SHORT).show();

                    // Store login information (garder toutes les informations existantes)
                    Controller.PutData(LoginActivity.this, "uid", String.valueOf(userId));
                    Controller.PutData(LoginActivity.this, "userType", userType);
                    Controller.PutData(LoginActivity.this, "username", username);
                    Controller.PutData(LoginActivity.this, "device_name", Build.MANUFACTURER + " " + Build.MODEL);

                    redirectUser(userType);
                } else {
                    Toast.makeText(LoginActivity.this, "Invalid username or password", Toast.LENGTH_SHORT).show();
                }
            }
        });

        // Signup redirect click handler
        buttonSignupRedirect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(LoginActivity.this, SignupActivity.class);
                startActivity(intent);
            }
        });

        // Set app context for ChatSocketHandler
        ChatSocketHandler.getInstance().setAppContext(getApplicationContext());
    }

    private boolean hasNecessaryPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestNecessaryPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.NEARBY_WIFI_DEVICES
            }, PERMISSION_REQUEST_CODE);
        } else {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION
            }, PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (!allGranted) {
                Toast.makeText(this, "Permissions are required to proceed", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void redirectUser(String userType) {
        Intent intent;
        if (userType.equals("field")) {
            intent = new Intent(LoginActivity.this, ActivityDashboard.class);
        } else if (userType.equals("room_management")) {
            intent = new Intent(LoginActivity.this, RoomManagementActivity.class);
        } else {
            Toast.makeText(LoginActivity.this, "Invalid user type", Toast.LENGTH_SHORT).show();
            return;
        }
        startActivity(intent);
        finish();
    }

    private void proceedToMainActivity() {
        String userId = Controller.GetData(LoginActivity.this, "uid");
        if (userId != null) {
            User user = db.getUserById(Integer.parseInt(userId));
            if (user != null) {
                redirectUser(user.getUserType());
            } else {
                Toast.makeText(this, "User not found. Please log in again.", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "Session expired. Please log in again.", Toast.LENGTH_SHORT).show();
        }
    }

    private String getMacAddress() {
        try {
            WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            return wifiInfo.getMacAddress();
        } catch (Exception e) {
            Log.e("LoginActivity", "Error getting MAC address: " + e.getMessage());
            return "00:00:00:00:00:00";
        }
    }
}

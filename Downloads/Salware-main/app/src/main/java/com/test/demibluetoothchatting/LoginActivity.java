package com.test.demibluetoothchatting;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
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

    // Constants for request codes
    private static final int REQUEST_ENABLE_BLUETOOTH = 102;
    private static final int PERMISSION_REQUEST_CODE = 101;

    // Database helper to interact with the local database
    DatabaseHelper db;

    // UI elements
    EditText editTextUsername, editTextPassword;
    Button buttonLogin;
    TextView buttonSignupRedirect;

    // Bluetooth adapter to manage Bluetooth functionality
    BluetoothAdapter bluetoothAdapter;

    @SuppressLint("MissingInflatedId")  // Suppresses warnings about missing ID inflation
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Disable night mode for this activity
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);

        // Set the content view to the layout file activity_login.xml
        setContentView(R.layout.activity_login);

        // Initialize database helper
        db = new DatabaseHelper(this);

        if(Controller.GetData(LoginActivity.this,"logged")!=null){
            proceedToMainActivity();
        }

        // Get the Bluetooth adapter for this device
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // Initialize the UI elements
        editTextUsername = findViewById(R.id.txt_username);
        editTextPassword = findViewById(R.id.txt_password);
        buttonLogin = findViewById(R.id.loginbutton);
        buttonSignupRedirect = findViewById(R.id.textviewsignup);

        // Check if Bluetooth permissions are granted, and request if not
        if (!hasBluetoothPermissions()) {
            requestBluetoothPermissions();
        }

        // Set a click listener for the login button
        // Set a click listener for the login button
        buttonLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Get the username and password from the input fields
                String username = editTextUsername.getText().toString();
                String password = editTextPassword.getText().toString();

                // Check if the user exists in the database and fetch their user ID
                int userId = db.checkUser(username, password);

                // If the user exists (valid userId), proceed with login
                if (userId != -1) {
                    // Fetch the user type from the database
                    User user = db.getUserById(userId); // Ensure `getUserById` is implemented
                    String userType = user.getUserType(); // Get the userType from the User object

                    Toast.makeText(LoginActivity.this, "Login successful", Toast.LENGTH_SHORT).show();

                    // Clear the input fields after successful login
                    editTextUsername.setText("");
                    editTextPassword.setText("");

                    // Store the user ID and type using a helper class
                    Controller.PutData(LoginActivity.this, "uid", String.valueOf(userId));
                    Controller.PutData(LoginActivity.this, "userType", userType);

                    // Redirect based on user type
                    if (userType.equals("field")) {
                        // Redirect to ActivityDashboard for field users
                        Intent intent = new Intent(LoginActivity.this, ActivityDashboard.class);
                        startActivity(intent);
                    } else if (userType.equals("room_management")) {
                        // Redirect to RoomManagementActivity for room management users
                        Intent intent = new Intent(LoginActivity.this, RoomManagementActivity.class);
                        startActivity(intent);
                    } else {
                        // Handle unexpected user types
                        Toast.makeText(LoginActivity.this, "Invalid user type", Toast.LENGTH_SHORT).show();
                    }

                    finish(); // Close the login activity
                } else {
                    // Display a toast message if the credentials are invalid
                    Toast.makeText(LoginActivity.this, "Invalid credentials", Toast.LENGTH_SHORT).show();
                }
            }
        });


        // Set a click listener for the sign-up redirect TextView
        buttonSignupRedirect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Start the SignupActivity if the user clicks on the "Sign Up" text
                Intent intent = new Intent(LoginActivity.this, SignupActivity.class);
                startActivity(intent);
            }
        });
    }

    // Method to check if the app has the necessary Bluetooth permissions
    private boolean hasBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Check Bluetooth permissions for devices running Android 12 (S) and above
            return ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        } else {
            // Check Bluetooth permissions for devices running below Android 12
            return ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }
    }

    // Method to request Bluetooth permissions if they are not already granted
    private void requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Request Bluetooth permissions for Android 12 (S) and above
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.ACCESS_FINE_LOCATION
            }, PERMISSION_REQUEST_CODE);
        } else {
            // Request Bluetooth permissions for Android versions below 12
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN,
                    Manifest.permission.ACCESS_FINE_LOCATION
            }, PERMISSION_REQUEST_CODE);
        }
    }

    // Callback method for handling the result of permission requests
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            // Check if all requested permissions have been granted
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            // Show a message depending on whether the permissions were granted or denied
            if (allGranted) {
                Toast.makeText(this, "Bluetooth permissions granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Bluetooth permissions denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // Method to check the Bluetooth status and request enabling Bluetooth if needed
    private void checkBluetoothStatus() {
        if (bluetoothAdapter == null) {
            // If the device doesn't support Bluetooth, show a message
            Toast.makeText(this, "Device doesn't support Bluetooth", Toast.LENGTH_SHORT).show();
        } else {
            // If Bluetooth is not enabled, request the user to enable it
            if (!bluetoothAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BLUETOOTH);
            } else {
                // If Bluetooth is already enabled, proceed to the main activity
                proceedToMainActivity();
            }
        }
    }

    // Handle the result of the request to enable Bluetooth
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ENABLE_BLUETOOTH) {
            // If Bluetooth was successfully enabled, proceed to the main activity
            if (resultCode == RESULT_OK) {
                proceedToMainActivity();
            } else {
                // If Bluetooth was not enabled, show a message
                Toast.makeText(this, "Bluetooth is required to proceed", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // Method to navigate to the main activity after successful login and Bluetooth check
    private void proceedToMainActivity() {
        // Fetch the logged-in user ID and userType
        String userId = Controller.GetData(LoginActivity.this, "uid");
        User user = db.getUserById(Integer.parseInt(userId));
        String userType = user.getUserType();

        if (userType.equals("field")) {
            // Redirect field users to ActivityDashboard
            Intent intent = new Intent(LoginActivity.this, ActivityDashboard.class);
            startActivity(intent);
        } else if (userType.equals("room_management")) {
            // Redirect room management users to RoomManagementActivity
            Intent intent = new Intent(LoginActivity.this, RoomManagementActivity.class);
            startActivity(intent);
        } else {
            // Handle unexpected user types
            Toast.makeText(LoginActivity.this, "Invalid user type", Toast.LENGTH_SHORT).show();
        }

        finish(); // Close the login activity
    }

}

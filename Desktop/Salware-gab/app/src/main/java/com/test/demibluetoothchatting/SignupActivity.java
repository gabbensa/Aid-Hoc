package com.test.demibluetoothchatting;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.test.demibluetoothchatting.Database.DatabaseHelper;

public class SignupActivity extends AppCompatActivity {

    // DatabaseHelper instance to interact with the local database
    DatabaseHelper db;

    // UI elements
    EditText editTextFullname, editTextUsername, editTextPassword;
    Button buttonSignup;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set the layout to the activity_signup.xml file
        setContentView(R.layout.activity_signup);

        // Initialize the DatabaseHelper instance
        db = new DatabaseHelper(this);

        // Link UI elements to the corresponding views in the layout
        editTextFullname = findViewById(R.id.txt_fullname);  // Full name input field
        editTextUsername = findViewById(R.id.txt_username);  // Username input field
        editTextPassword = findViewById(R.id.txt_password);  // Password input field
        buttonSignup = findViewById(R.id.signupbutton);      // Signup button

        RadioGroup radioGroupUserType = findViewById(R.id.radioGroupUserType);
        // Set an OnClickListener for the signup button
        buttonSignup.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Get the values entered by the user in the input fields
                String fullname = editTextFullname.getText().toString();
                String username = editTextUsername.getText().toString();
                String password = editTextPassword.getText().toString();
                int selectedId = radioGroupUserType.getCheckedRadioButtonId();
                String userType = (selectedId == R.id.radioButtonFieldUser) ? "field" : "room_management";

                // Check if any of the fields are empty
                if (fullname.isEmpty() || username.isEmpty() || password.isEmpty()) {
                    // Show a Toast message if any field is empty
                    Toast.makeText(SignupActivity.this, "Please enter all the details", Toast.LENGTH_SHORT).show();
                } else {
                    // Insert the user details into the database
                    boolean isInserted = db.insertUser(fullname, username, password, userType);


                    // If the user is successfully inserted, show a success message
                    if (isInserted) {
                        Toast.makeText(SignupActivity.this, "Signup successful", Toast.LENGTH_SHORT).show();
                        // Redirect the user to the LoginActivity after successful signup
                        Intent intent = new Intent(SignupActivity.this, LoginActivity.class);
                        startActivity(intent);
                        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                        finish(); // Close SignupActivity

                    } else {
                        // Show a failure message if user insertion failed
                        Toast.makeText(SignupActivity.this, "Signup failed", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });
    }
}

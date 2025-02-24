package com.test.demibluetoothchatting.Tabs;

// Import necessary Android components
import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.test.demibluetoothchatting.ActivityDashboard;
import com.test.demibluetoothchatting.Controller;
import com.test.demibluetoothchatting.Database.DatabaseHelper;
import com.test.demibluetoothchatting.LoginActivity;
import com.test.demibluetoothchatting.R;
import com.test.demibluetoothchatting.User;

import java.io.IOException;
import java.util.ArrayList;

// profile_tab fragment displays user profile information (name and username)
public class profile_tab extends Fragment {

    private DatabaseHelper db; // DatabaseHelper to interact with the SQLite database
    private TextView txt_name; // TextView for displaying the user's full name
    private TextView txt_username; // TextView for displaying the user's username

    // Parameters to pass to the fragment
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";
    private String mParam1;
    private String mParam2;
    CardView btn_ad; // Example CardView button (could be used for adding an action)

    // Default constructor
    public profile_tab() {

    }

    // Static method to create a new instance of profile_tab with parameters
    public static profile_tab newInstance(String param1, String param2) {
        profile_tab fragment = new profile_tab();
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
        // Initialize DatabaseHelper to interact with the database
        db = new DatabaseHelper(getContext());
    }

    // Called when the fragment is stopped
    @Override
    public void onStop() {
        super.onStop();
    }

    // Called to create the view for the fragment
    @SuppressLint("MissingInflatedId")
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.profile_tab, container, false); // Inflate the profile_tab layout

        // Initialize the TextViews for name and username
        txt_name = view.findViewById(R.id.txt_name);
        txt_username = view.findViewById(R.id.txt_username);

        // Fetch user ID from SharedPreferences or any other source
        int userId = Integer.parseInt(Controller.GetData(getActivity(), "uid"));

        if (userId != -1) {
            // Fetch user details from the database using the user ID
            User user = db.getUserById(userId);
            if (user != null) {
                // Display the user's full name and username in the respective TextViews
                txt_name.setText(user.getFullName());
                txt_username.setText(user.getUsername());
            } else {
                // Show a message if the user is not found in the database
                Toast.makeText(getContext(), "User not found", Toast.LENGTH_SHORT).show();
            }
        } else {
            // Show a message if the user ID is not found
            Toast.makeText(getContext(), "No user ID found", Toast.LENGTH_SHORT).show();
        }
        view.findViewById(R.id.btn_logout).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getActivity(), LoginActivity.class);
                startActivity(intent);
                Controller.PutData(getActivity(),"logged",null);
                getActivity().finish();
            }
        });

        return view; // Return the view
    }

    // Called when the fragment is started
    @Override
    public void onStart() {
        super.onStart();
    }

    // Called to handle menu visibility in the fragment
    @Override
    public void setMenuVisibility(final boolean visible) {
        super.setMenuVisibility(visible);
    }

    // Called when the fragment becomes visible to the user
    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        if (isVisibleToUser) {
            // Add code here if you want to do something when the fragment becomes visible
        } else {
            // Add code here if you want to do something when the fragment is not visible
        }
    }
}

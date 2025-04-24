package com.test.demibluetoothchatting.Receivers;

// Import necessary Android and Java components
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.widget.Toast;

import com.test.demibluetoothchatting.Service.SyncService;

// NetworkChangeReceiver listens for changes in network connectivity
public class NetworkChangeReceiver extends BroadcastReceiver {

    // Debounce delay (5 seconds) to prevent rapid triggering of network change events
    private static final long DEBOUNCE_DELAY = 5000;
    private boolean isDebouncing = false; // Flag to indicate if debouncing is in progress
    private static boolean previousIsConnected = false; // Stores the previous network connection state

    // This method is called whenever the network state changes
    @Override
    public void onReceive(Context context, Intent intent) {
        // Check if the network is available
        boolean isConnected = isNetworkAvailable(context);

        // Only proceed if the network connection state has changed (from disconnected to connected or vice versa)
        if (isConnected != previousIsConnected) {
            previousIsConnected = isConnected; // Update the previous connection state

            // If the network is now connected and debouncing is not in progress
            if (isConnected && !isDebouncing) {
                isDebouncing = true; // Set the debouncing flag to true

                // Start the SyncService to sync data when network becomes available
                Intent syncIntent = new Intent(context, SyncService.class);
                context.startService(syncIntent);

                // Display a Toast message to inform the user that the sync has started
                Toast.makeText(context, "Network Available - Sync Started", Toast.LENGTH_LONG).show();

                // Use a handler to reset the debouncing flag after a delay (DEBOUNCE_DELAY)
                new Handler().postDelayed(() -> isDebouncing = false, DEBOUNCE_DELAY);
            }
        }
    }

    // Helper method to check if the network is available
    private boolean isNetworkAvailable(Context context) {
        // Get the ConnectivityManager from the system service
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        // Check if the active network is connected or not
        if (connectivityManager != null) {
            NetworkInfo activeNetwork = connectivityManager.getActiveNetworkInfo();
            return activeNetwork != null && activeNetwork.isConnected(); // Return true if connected
        }
        return false; // Return false if not connected
    }
}

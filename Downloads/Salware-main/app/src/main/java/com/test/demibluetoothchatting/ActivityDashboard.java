package com.test.demibluetoothchatting;

// Import necessary Android and view components
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.test.demibluetoothchatting.Tabs.messages_tab;
import com.test.demibluetoothchatting.Tabs.profile_tab;

// Main dashboard activity that contains tab navigation and fragment management
public class ActivityDashboard extends AppCompatActivity implements TabLayout.OnTabSelectedListener {

    // Declare views and layout variables
    private TabLayout tabLayout; // TabLayout for navigation between fragments
    private ViewPager2 viewPager; // ViewPager2 for displaying fragments
    private LinearLayout linearLayout_main; // Main layout
    private RelativeLayout root_layout; // Root layout for the dashboard
    private boolean isDropdownVisible = false; // Flag to manage dropdown visibility (if applicable)
    private String name, image_url; // Variables for name and image URL (if needed for user profile)
    private View adContainer; // Container for displaying ads (if applicable)
    private FrameLayout frameLayout; // FrameLayout to contain fragments
    RelativeLayout layout_no_internet; // Layout to display when there is no internet connection
    private boolean isAdLoaded = false; // Flag to check if an ad is loaded

    // onCreate is called when the activity is first created
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        setContentView(R.layout.activity_dashboard); // Set the layout for the activity

        // Initialize views by finding them in the layout
        linearLayout_main = findViewById(R.id.main_layout);
        viewPager = findViewById(R.id.pager);
        root_layout = findViewById(R.id.root_layout);
        tabLayout = findViewById(R.id.tabLayout);

        // Add tabs to the TabLayout with custom icons
        tabLayout.addTab(tabLayout.newTab().setIcon(R.drawable.ic_baseline_message_24)); // Tab for messages
        tabLayout.addTab(tabLayout.newTab().setIcon(R.drawable.ic_baseline_supervised_user_circle_24)); // Tab for profile

        // Set custom view for each tab
        for (int i = 0; i < tabLayout.getTabCount(); i++) {
            TabLayout.Tab tab = tabLayout.getTabAt(i);
            if (tab != null) tab.setCustomView(R.layout.view_home_tab);
        }

        // Set the gravity for the tabs to fill the layout
        tabLayout.setTabGravity(TabLayout.GRAVITY_FILL);

        // Initialize the adapter for managing fragments and add the fragments to it
        FragmentsAdapter myAdapter = new FragmentsAdapter(getSupportFragmentManager(), getLifecycle());
        myAdapter.addFragment(new messages_tab()); // Add messages tab fragment
        myAdapter.addFragment(new profile_tab()); // Add profile tab fragment

        // Configure the ViewPager2 (used for swiping between fragments)
        viewPager.setOrientation(ViewPager2.ORIENTATION_HORIZONTAL); // Set the orientation to horizontal
        viewPager.setAdapter(myAdapter); // Set the adapter to manage the fragments
        viewPager.setUserInputEnabled(false); // Disable user swiping
        viewPager.setOffscreenPageLimit(tabLayout.getTabCount() - 1); // Keep fragments loaded in memory

        // Add listener for tab selection events
        tabLayout.addOnTabSelectedListener(this);

        // Set the default selected tab
        viewPager.setCurrentItem(0, false); // Set the first tab (messages) as default
        tabLayout.selectTab(tabLayout.getTabAt(0)); // Highlight the first tab
    }

    // Save the selected tab position in case the activity needs to be recreated
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("selectedTab", tabLayout.getSelectedTabPosition()); // Save the currently selected tab
    }

    // Handle tab selection events
    @Override
    public void onTabSelected(TabLayout.Tab tab) {
        viewPager.setCurrentItem(tab.getPosition()); // Set the ViewPager to display the selected tab
        SharedPreferences sharedPreferences = getSharedPreferences("AppPrefs", MODE_PRIVATE); // Get shared preferences
        SharedPreferences.Editor editor = sharedPreferences.edit(); // Edit shared preferences
        editor.putInt("lastSelectedTab", tab.getPosition()); // Save the last selected tab position
        editor.apply(); // Apply changes to shared preferences
    }

    // Called when a tab is unselected (not needed for this implementation)
    @Override
    public void onTabUnselected(TabLayout.Tab tab) {}

    // Called when a tab is reselected (not needed for this implementation)
    @Override
    public void onTabReselected(TabLayout.Tab tab) {}

    // Handle action bar menu item selection (e.g., back button)
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home: // Handle back button press
                finish(); // Close the activity
                return true;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    // Called when the activity is started
    @Override
    protected void onStart() {
        super.onStart();
    }

    // Called when the activity is destroyed
    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    // Called when the activity is resumed
    @Override
    protected void onResume() {
        super.onResume();
    }

    // Called when the activity is paused
    @Override
    protected void onPause() {
        super.onPause();
    }

    // Called when the activity is stopped
    @Override
    protected void onStop() {
        super.onStop();
    }

    // Handle back button press
    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }
}

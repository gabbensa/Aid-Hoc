// Updated ActivityDashboard.java: Ensuring Compatibility with WiFi-Direct

package com.test.demibluetoothchatting;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.test.demibluetoothchatting.Tabs.messages_tab;
import com.test.demibluetoothchatting.Tabs.profile_tab;

public class ActivityDashboard extends AppCompatActivity implements TabLayout.OnTabSelectedListener {

    private TabLayout tabLayout;
    private ViewPager2 viewPager;
    private LinearLayout linearLayout_main;
    private RelativeLayout root_layout;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_dashboard);

        // Initialize views
        linearLayout_main = findViewById(R.id.main_layout);
        viewPager = findViewById(R.id.pager);
        root_layout = findViewById(R.id.root_layout);
        tabLayout = findViewById(R.id.tabLayout);

        // Add tabs to TabLayout
        tabLayout.addTab(tabLayout.newTab().setIcon(R.drawable.ic_baseline_message_24));
        tabLayout.addTab(tabLayout.newTab().setIcon(R.drawable.ic_baseline_supervised_user_circle_24));

        for (int i = 0; i < tabLayout.getTabCount(); i++) {
            TabLayout.Tab tab = tabLayout.getTabAt(i);
            if (tab != null) tab.setCustomView(R.layout.view_home_tab);
        }

        tabLayout.setTabGravity(TabLayout.GRAVITY_FILL);

        // Set up ViewPager2 with FragmentsAdapter
        FragmentsAdapter myAdapter = new FragmentsAdapter(getSupportFragmentManager(), getLifecycle());

        // Pass necessary data to messages_tab
        messages_tab messagesTab = new messages_tab();
        Bundle args = new Bundle();
        args.putString("device_name", Controller.GetData(this, "device_name"));
        args.putString("user_type", Controller.GetData(this, "userType"));
        messagesTab.setArguments(args);

        // Add fragments to the adapter
        myAdapter.addFragment(messagesTab);
        myAdapter.addFragment(new profile_tab());

        // Configure ViewPager2
        viewPager.setOrientation(ViewPager2.ORIENTATION_HORIZONTAL);
        viewPager.setAdapter(myAdapter);
        viewPager.setUserInputEnabled(false);
        viewPager.setOffscreenPageLimit(tabLayout.getTabCount() - 1);

        // Add listener for tab selection
        tabLayout.addOnTabSelectedListener(this);

        // Set the default selected tab
        viewPager.setCurrentItem(0, false);
        tabLayout.selectTab(tabLayout.getTabAt(0));
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("selectedTab", tabLayout.getSelectedTabPosition());
    }

    @Override
    public void onTabSelected(TabLayout.Tab tab) {
        viewPager.setCurrentItem(tab.getPosition());
        SharedPreferences sharedPreferences = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt("lastSelectedTab", tab.getPosition());
        editor.apply();
    }

    @Override
    public void onTabUnselected(TabLayout.Tab tab) {}

    @Override
    public void onTabReselected(TabLayout.Tab tab) {}

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }
}

package com.test.demibluetoothchatting;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Lifecycle;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import java.util.ArrayList;

public class FragmentsAdapter extends FragmentStateAdapter {

    // List to hold all the fragments to be displayed in the ViewPager2
    private ArrayList<Fragment> fragmentList = new ArrayList<>();

    // Constructor for FragmentsAdapter
    // fragmentManager: manages the fragments for the adapter
    // lifecycle: manages the lifecycle of the fragments in sync with the ViewPager2
    public FragmentsAdapter(@NonNull FragmentManager fragmentManager, @NonNull Lifecycle lifecycle) {
        super(fragmentManager, lifecycle); // Calls the FragmentStateAdapter constructor with fragmentManager and lifecycle
    }

    // This method returns the fragment at the specified position in the fragmentList
    @NonNull
    @Override
    public Fragment createFragment(int position) {
        return fragmentList.get(position); // Get fragment based on position
    }

    // Method to add a fragment to the fragmentList
    // fragment: the Fragment to add to the list
    public void addFragment(Fragment fragment) {
        fragmentList.add(fragment); // Adds the fragment to the ArrayList
    }

    // Returns the number of fragments currently in the fragmentList
    @Override
    public int getItemCount() {
        return fragmentList.size(); // Returns the size of the fragment list
    }
}

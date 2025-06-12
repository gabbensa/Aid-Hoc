package com.test.demibluetoothchatting;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.firebase.ui.database.FirebaseRecyclerAdapter;
import com.firebase.ui.database.FirebaseRecyclerOptions;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.test.demibluetoothchatting.Adapters.AllMessagesAdapter;
import com.test.demibluetoothchatting.Database.DatabaseHelper;
import com.test.demibluetoothchatting.Tabs.profile_tab;

import java.util.ArrayList;
import java.util.List;

public class RoomManagementActivity extends AppCompatActivity {

    private TabLayout tabLayout;
    private ViewPager2 viewPager;
    private ViewPagerAdapter viewPagerAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard); // Réutiliser le layout avec TabLayout et ViewPager2
        Log.d("RoomManagementActivity", "onCreate called");

        // Initialiser les onglets et le ViewPager2
        tabLayout = findViewById(R.id.tabLayout);
        viewPager = findViewById(R.id.pager);

        // Configurer l'adaptateur pour le ViewPager2
        viewPagerAdapter = new ViewPagerAdapter(this);
        viewPager.setAdapter(viewPagerAdapter);

        // Configurer les onglets avec TabLayoutMediator
        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            switch (position) {
                case 0:
                    tab.setIcon(R.drawable.ic_baseline_message_24);
                    break;
                case 1:
                    tab.setIcon(R.drawable.ic_baseline_supervised_user_circle_24);
                    break;
            }
        }).attach();
        
        // Personnaliser les onglets pour agrandir les icônes
        for (int i = 0; i < tabLayout.getTabCount(); i++) {
            if (tabLayout.getTabAt(i) != null) {
                tabLayout.getTabAt(i).setCustomView(R.layout.view_home_tab);
            }
        }
    }

    // Adaptateur pour le ViewPager2
    class ViewPagerAdapter extends FragmentStateAdapter {
        public ViewPagerAdapter(@NonNull AppCompatActivity fragmentActivity) {
            super(fragmentActivity);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            switch (position) {
                case 0:
                    return new RoomManagementFragment();
                case 1:
                    return new profile_tab();
                default:
                    return new RoomManagementFragment();
            }
        }

        @Override
        public int getItemCount() {
            return 2; // Nombre d'onglets
        }
    }

    // Fragment pour la gestion des salles
    public static class RoomManagementFragment extends Fragment {
        private RecyclerView recyclerViewMessages;
        private AllMessagesAdapter adapter;
        private DatabaseHelper dbHelper;
        private SearchView searchViewMessages;
        private List<ChatMessage> allMessages = new ArrayList<>();
        private ProgressBar progressBar;

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View view = inflater.inflate(R.layout.activity_room_management, container, false);
            
            // Initialize RecyclerView
            recyclerViewMessages = view.findViewById(R.id.recyclerViewMessages);
            recyclerViewMessages.setLayoutManager(new LinearLayoutManager(getActivity()));

            // Initialize SearchView
            searchViewMessages = view.findViewById(R.id.searchViewMessages);

            // Initialize ProgressBar
            progressBar = view.findViewById(R.id.progressBar);
            progressBar.setVisibility(View.VISIBLE);

            // Initialize DatabaseHelper
            dbHelper = new DatabaseHelper(requireActivity());

            // Load all messages from Firebase
            loadAllMessages();

            // Add listener for search input
            searchViewMessages.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                @Override
                public boolean onQueryTextSubmit(String query) {
                    filterMessages(query);
                    return false;
                }

                @Override
                public boolean onQueryTextChange(String newText) {
                    filterMessages(newText);
                    return false;
                }
            });
            
            return view;
        }

        private void loadAllMessages() {
            progressBar.setVisibility(View.VISIBLE);
            dbHelper.getAllMessagesFromFirebase(new DatabaseHelper.OnMessagesLoadedListener() {
                @Override
                public void onMessagesLoaded(ArrayList<ChatMessage> messages) {
                    requireActivity().runOnUiThread(() -> {
                        allMessages = messages;
                        if (adapter == null) {
                            adapter = new AllMessagesAdapter(allMessages);
                            recyclerViewMessages.setAdapter(adapter);
                        } else {
                            adapter.updateMessages(allMessages);
                        }
                        progressBar.setVisibility(View.GONE);
                    });
                }
            });
        }

        private void filterMessages(String query) {
            if (query.isEmpty()) {
                adapter.updateMessages(allMessages);
            } else {
                List<ChatMessage> filteredMessages = new ArrayList<>();
                String lowerQuery = query.toLowerCase();
                for (ChatMessage message : allMessages) {
                    if (message.getMessage().toLowerCase().contains(lowerQuery) ||
                        message.getSender().toLowerCase().contains(lowerQuery) ||
                        message.getReceiver().toLowerCase().contains(lowerQuery)) {
                        filteredMessages.add(message);
                    }
                }
                adapter.updateMessages(filteredMessages);
            }
        }

        @Override
        public void onResume() {
            super.onResume();
            loadAllMessages(); // Reload messages when fragment resumes
        }
    }

    // ViewHolder pour afficher les clés de conversation
    public static class ChatGroupViewHolder extends RecyclerView.ViewHolder {
        private final TextView textViewConversationName;

        public ChatGroupViewHolder(@NonNull View itemView) {
            super(itemView);
            textViewConversationName = itemView.findViewById(R.id.textViewConversation);
        }

        public void bind(String conversationKey) {
            textViewConversationName.setText(conversationKey);
        }
    }
}
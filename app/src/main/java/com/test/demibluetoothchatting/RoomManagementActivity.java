package com.test.demibluetoothchatting;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
        private RecyclerView recyclerViewConversations;
        private FirebaseRecyclerAdapter<ChatGroup, ChatGroupViewHolder> adapter;
        private DatabaseReference conversationsRef;
        private SearchView searchViewConversations;
        private List<String> allConversationKeys = new ArrayList<>();

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View view = inflater.inflate(R.layout.activity_room_management, container, false);
            
            // Initialize RecyclerView
            recyclerViewConversations = view.findViewById(R.id.recyclerViewConversations);
            recyclerViewConversations.setLayoutManager(new LinearLayoutManager(getActivity()));

            // Initialize SearchView
            searchViewConversations = view.findViewById(R.id.searchViewConversations);

            // Reference to the root of "chatting_data" in Firebase
            conversationsRef = FirebaseDatabase.getInstance()
                    .getReference()
                    .child("chatting_data");

            // Load all conversation keys for partial matching
            loadAllConversationKeys();

            // Add listener for search input
            searchViewConversations.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                @Override
                public boolean onQueryTextSubmit(String query) {
                    filterConversations(query);
                    return false;
                }

                @Override
                public boolean onQueryTextChange(String newText) {
                    filterConversations(newText);
                    return false;
                }
            });
            
            return view;
        }

        private void loadAllConversationKeys() {
            conversationsRef.addListenerForSingleValueEvent(new com.google.firebase.database.ValueEventListener() {
                @Override
                public void onDataChange(@NonNull com.google.firebase.database.DataSnapshot snapshot) {
                    allConversationKeys.clear();
                    for (com.google.firebase.database.DataSnapshot child : snapshot.getChildren()) {
                        String conversationKey = child.getKey();
                        if (conversationKey != null) {
                            Log.d("RoomManagementActivity", "Loaded conversation key: " + conversationKey);
                            allConversationKeys.add(conversationKey);
                        }
                    }
                    // Display all conversations initially
                    filterConversations("");
                }

                @Override
                public void onCancelled(@NonNull com.google.firebase.database.DatabaseError error) {
                    Log.e("RoomManagementActivity", "Error loading conversation keys: " + error.getMessage());
                }
            });
        }

        private void filterConversations(String query) {
            List<String> filteredKeys = new ArrayList<>();
            for (String key : allConversationKeys) {
                if (key.toLowerCase().contains(query.toLowerCase())) {
                    filteredKeys.add(key);
                }
            }
            Log.d("RoomManagementActivity", "Filtered keys: " + filteredKeys);
            updateAdapter(filteredKeys);
        }

        private void updateAdapter(List<String> keys) {
            Query query = conversationsRef.orderByKey();
            FirebaseRecyclerOptions<ChatGroup> options = new FirebaseRecyclerOptions.Builder<ChatGroup>()
                    .setQuery(query, ChatGroup.class)
                    .build();

            adapter = new FirebaseRecyclerAdapter<ChatGroup, ChatGroupViewHolder>(options) {
                @NonNull
                @Override
                public ChatGroupViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                    View view = LayoutInflater.from(parent.getContext())
                            .inflate(R.layout.item_conversation, parent, false);
                    return new ChatGroupViewHolder(view);
                }

                @Override
                protected void onBindViewHolder(@NonNull ChatGroupViewHolder holder, int position, @NonNull ChatGroup model) {
                    String conversationKey = getRef(position).getKey();
                    if (conversationKey != null && keys.contains(conversationKey)) {
                        holder.bind(conversationKey);
                        holder.itemView.setVisibility(View.VISIBLE);

                        holder.itemView.setOnClickListener(v -> {
                            Intent intent = new Intent(v.getContext(), ChatActivity.class);
                            intent.putExtra("conversationKey", conversationKey);
                            v.getContext().startActivity(intent);
                        });
                    } else {
                        holder.itemView.setVisibility(View.GONE);
                    }
                }
            };

            recyclerViewConversations.setAdapter(adapter);
            adapter.startListening();
        }

        @Override
        public void onStart() {
            super.onStart();
            if (adapter != null) {
                adapter.startListening(); // Ensure the adapter is properly synchronized
            }
        }

        @Override
        public void onStop() {
            super.onStop();
            if (adapter != null) {
                adapter.stopListening(); // Stop listening to prevent inconsistencies
            }
        }

        @Override
        public void onPause() {
            super.onPause();
            if (recyclerViewConversations != null) {
                recyclerViewConversations.setAdapter(null); // Detach adapter to avoid crashes
            }
        }

        @Override
        public void onResume() {
            super.onResume();
            if (recyclerViewConversations != null && adapter != null) {
                recyclerViewConversations.setAdapter(adapter); // Reattach adapter on resume
            }
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
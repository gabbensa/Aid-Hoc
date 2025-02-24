
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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.firebase.ui.database.FirebaseRecyclerAdapter;
import com.firebase.ui.database.FirebaseRecyclerOptions;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;

import java.util.ArrayList;
import java.util.List;

public class RoomManagementActivity extends AppCompatActivity {

    private RecyclerView recyclerViewConversations;
    private FirebaseRecyclerAdapter<ChatGroup, ChatGroupViewHolder> adapter;
    private DatabaseReference conversationsRef;
    private SearchView searchViewConversations;
    private List<String> allConversationKeys = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_room_management);
        Log.d("RoomManagementActivity", "onCreate called");

        // Initialize RecyclerView
        recyclerViewConversations = findViewById(R.id.recyclerViewConversations);
        recyclerViewConversations.setLayoutManager(new LinearLayoutManager(this));

        // Initialize SearchView
        searchViewConversations = findViewById(R.id.searchViewConversations);

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
    protected void onStart() {
        super.onStart();
        if (adapter != null) {
            adapter.startListening(); // Ensure the adapter is properly synchronized
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (adapter != null) {
            adapter.stopListening(); // Stop listening to prevent inconsistencies
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        recyclerViewConversations.setAdapter(null); // Detach adapter to avoid crashes
    }

    @Override
    protected void onResume() {
        super.onResume();
        recyclerViewConversations.setAdapter(adapter); // Reattach adapter on resume
    }

    // ViewHolder for displaying conversation keys
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
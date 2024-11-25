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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.firebase.ui.database.FirebaseRecyclerAdapter;
import com.firebase.ui.database.FirebaseRecyclerOptions;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class RoomManagementActivity extends AppCompatActivity {

    private RecyclerView recyclerViewConversations;
    private FirebaseRecyclerAdapter<ChatGroup, ChatGroupViewHolder> adapter;
    private DatabaseReference conversationsRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_room_management);
        Log.d("RoomManagementActivity", "onCreate called");
        // Initialize RecyclerView
        recyclerViewConversations = findViewById(R.id.recyclerViewConversations);
        recyclerViewConversations.setLayoutManager(new LinearLayoutManager(this));

        // Reference to the root of "chatting_data" in Firebase
        conversationsRef = FirebaseDatabase.getInstance()
                .getReference()
                .child("chatting_data");

        // Configure FirebaseRecyclerOptions
        FirebaseRecyclerOptions<ChatGroup> options =
                new FirebaseRecyclerOptions.Builder<ChatGroup>()
                        .setQuery(conversationsRef, ChatGroup.class)
                        .build();

        // Initialize FirebaseRecyclerAdapter
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
                if (position >= getItemCount()) {
                    Log.e("RoomManagementActivity", "Invalid position: " + position);
                    return; // Prevent crashes due to invalid positions
                }

                // Get the conversation key and bind it to the ViewHolder
                String conversationKey = getRef(position).getKey();
                holder.bind(conversationKey);

                // Handle click events to open ChatActivity
                holder.itemView.setOnClickListener(v -> {
                    Intent intent = new Intent(v.getContext(), ChatActivity.class);
                    intent.putExtra("conversationKey", conversationKey);
                    v.getContext().startActivity(intent);
                });
            }

        };

        recyclerViewConversations.setAdapter(adapter);
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

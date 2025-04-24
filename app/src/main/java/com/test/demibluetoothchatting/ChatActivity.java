package com.test.demibluetoothchatting;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.firebase.ui.database.FirebaseRecyclerAdapter;
import com.firebase.ui.database.FirebaseRecyclerOptions;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class ChatActivity extends AppCompatActivity {

    private RecyclerView recyclerViewMessages;
    private FirebaseRecyclerAdapter<Conversation, ConversationViewHolder> adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        // Set up the AppCompat Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar); // Correct AppCompat Toolbar

        // Enable the back button
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true); // Show back arrow
            getSupportActionBar().setTitle("Chat Details"); // Set screen title
        }

        // Set up the RecyclerView
        recyclerViewMessages = findViewById(R.id.recyclerViewMessages);
        recyclerViewMessages.setLayoutManager(new LinearLayoutManager(this));

        // Retrieve the conversation key
        String conversationKey = getIntent().getStringExtra("conversationKey");
        if (conversationKey == null) {
            Toast.makeText(this, "Error: Missing conversation key!", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Reference the specific conversation in Firebase
        DatabaseReference specificConversationRef = FirebaseDatabase.getInstance()
                .getReference()
                .child("chatting_data")
                .child(conversationKey);

        // Configure FirebaseRecyclerOptions
        FirebaseRecyclerOptions<Conversation> options =
                new FirebaseRecyclerOptions.Builder<Conversation>()
                        .setQuery(specificConversationRef, Conversation.class)
                        .build();

        // Initialize FirebaseRecyclerAdapter
        adapter = new FirebaseRecyclerAdapter<Conversation, ConversationViewHolder>(options) {
            @NonNull
            @Override
            public ConversationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_message, parent, false);
                return new ConversationViewHolder(view);
            }

            @Override
            protected void onBindViewHolder(@NonNull ConversationViewHolder holder, int position, @NonNull Conversation model) {
                holder.bind(model);
            }
        };

        recyclerViewMessages.setAdapter(adapter);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish(); // Close ChatActivity and return to RoomManagementActivity
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (adapter != null) {
            adapter.startListening();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (adapter != null) {
            adapter.stopListening();
        }
    }

    // ViewHolder for displaying individual messages
    public static class ConversationViewHolder extends RecyclerView.ViewHolder {
        private final TextView textViewMessage;
        private final TextView textViewSender;
        private final TextView textViewTimestamp;

        public ConversationViewHolder(@NonNull View itemView) {
            super(itemView);
            textViewMessage = itemView.findViewById(R.id.textViewMessage);
            textViewSender = itemView.findViewById(R.id.textViewSender);
            textViewTimestamp = itemView.findViewById(R.id.textViewTimestamp);
        }

        public void bind(Conversation conversation) {
            textViewMessage.setText(conversation.getMessage());
            textViewSender.setText("From: " + conversation.getSender());
            textViewTimestamp.setText(conversation.getTimestamp());
        }
    }
}

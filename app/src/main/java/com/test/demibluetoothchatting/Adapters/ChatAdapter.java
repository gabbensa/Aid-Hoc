package com.test.demibluetoothchatting.Adapters;

// Import necessary Android and Java components
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.test.demibluetoothchatting.ChatMessage;
import com.test.demibluetoothchatting.R;

import java.util.ArrayList;
import java.util.List;

// ChatAdapter class extends RecyclerView.Adapter, responsible for displaying chat messages in a RecyclerView
public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    // Define view types for sender and receiver
    private static final int VIEW_TYPE_SENDER = 1;
    private static final int VIEW_TYPE_RECEIVER = 2;

    // List to hold the chat messages and a variable to store the current user
    private List<ChatMessage> chatMessages;
    private String currentUser;

    // Constructor for ChatAdapter to initialize the chatMessages and currentUser
    public ChatAdapter(List<ChatMessage> chatMessages, String currentUser) {
        this.chatMessages = chatMessages;
        this.currentUser = currentUser;
    }

    // Determines which type of view (sender or receiver) to use for the current message based on the sender
    @Override
    public int getItemViewType(int position) {
        ChatMessage message = chatMessages.get(position);
        // If the message is sent by the current user, return VIEW_TYPE_SENDER
        if (message.getSender().equals(currentUser)) {
            return VIEW_TYPE_SENDER;
        } else {
            // Otherwise, return VIEW_TYPE_RECEIVER
            return VIEW_TYPE_RECEIVER;
        }
    }

    // Creates the appropriate ViewHolder (SenderViewHolder or ReceiverViewHolder) based on the viewType
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_SENDER) {
            // If viewType is VIEW_TYPE_SENDER, inflate the sender layout
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_message_sender, parent, false);
            return new SenderViewHolder(view);
        } else {
            // If viewType is VIEW_TYPE_RECEIVER, inflate the receiver layout
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_message_receiver, parent, false);
            return new ReceiverViewHolder(view);
        }
    }

    // Binds data to the appropriate ViewHolder (sender or receiver) based on the position in the chatMessages list
    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        // Get the message at the current position
        ChatMessage message = chatMessages.get(position);
        if (holder instanceof SenderViewHolder) {
            // If holder is of type SenderViewHolder, bind the sender message
            ((SenderViewHolder) holder).bind(message);
        } else {
            // If holder is of type ReceiverViewHolder, bind the receiver message
            ((ReceiverViewHolder) holder).bind(message);
        }
    }

    // Returns the total number of items in the chatMessages list
    @Override
    public int getItemCount() {
        return chatMessages.size();
    }

    // ViewHolder for the sender's messages
    class SenderViewHolder extends RecyclerView.ViewHolder {
        TextView messageBody; // TextView to display the message body

        // Constructor to initialize the messageBody TextView from the sender layout
        SenderViewHolder(View itemView) {
            super(itemView);
            messageBody = itemView.findViewById(R.id.message_body);
        }

        // Binds the message data (message text) to the TextView
        void bind(ChatMessage message) {
            messageBody.setText(message.getMessage());
        }
    }

    // ViewHolder for the receiver's messages
    class ReceiverViewHolder extends RecyclerView.ViewHolder {
        TextView messageBody; // TextView to display the message body

        // Constructor to initialize the messageBody TextView from the receiver layout
        ReceiverViewHolder(View itemView) {
            super(itemView);
            messageBody = itemView.findViewById(R.id.message_body);
        }

        // Binds the message data (message text) to the TextView
        void bind(ChatMessage message) {
            messageBody.setText(message.getMessage());
        }
    }
}

package com.test.demibluetoothchatting.Adapters;

// Import necessary Android and Java components
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
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
        return message.getSender().equals(currentUser) ? VIEW_TYPE_SENDER : VIEW_TYPE_RECEIVER;
    }

    // Creates the appropriate ViewHolder (SenderViewHolder or ReceiverViewHolder) based on the viewType
    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_message, parent, false);
        return new MessageViewHolder(view);
    }

    // Binds data to the appropriate ViewHolder (sender or receiver) based on the position in the chatMessages list
    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatMessage message = chatMessages.get(position);
        if (getItemViewType(position) == VIEW_TYPE_SENDER) {
            MessageViewHolder viewHolder = (MessageViewHolder) holder;
            viewHolder.messageSentLayout.setVisibility(View.VISIBLE);
            viewHolder.messageReceivedLayout.setVisibility(View.GONE);
            viewHolder.textViewMessage.setText(message.getMessage());
            viewHolder.textViewTimestamp.setText(message.getTimestamp());
        } else {
            MessageViewHolder viewHolder = (MessageViewHolder) holder;
            viewHolder.messageSentLayout.setVisibility(View.GONE);
            viewHolder.messageReceivedLayout.setVisibility(View.VISIBLE);
            viewHolder.textViewReceivedMessage.setText(message.getMessage());
            viewHolder.textViewReceivedTimestamp.setText(message.getTimestamp());
        }
    }

    // Returns the total number of items in the chatMessages list
    @Override
    public int getItemCount() {
        return chatMessages.size();
    }

    // ViewHolder for the sender's messages
    public static class MessageViewHolder extends RecyclerView.ViewHolder {
        LinearLayout messageSentLayout, messageReceivedLayout;
        TextView textViewMessage, textViewTimestamp, textViewReceivedMessage, textViewReceivedTimestamp;
        public MessageViewHolder(@NonNull View itemView) {
            super(itemView);
            messageSentLayout = itemView.findViewById(R.id.message_sent_layout);
            messageReceivedLayout = itemView.findViewById(R.id.message_received_layout);
            textViewMessage = itemView.findViewById(R.id.textViewMessage);
            textViewTimestamp = itemView.findViewById(R.id.textViewTimestamp);
            textViewReceivedMessage = itemView.findViewById(R.id.textViewReceivedMessage);
            textViewReceivedTimestamp = itemView.findViewById(R.id.textViewReceivedTimestamp);
        }
    }
}

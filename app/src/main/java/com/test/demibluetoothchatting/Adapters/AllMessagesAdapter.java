package com.test.demibluetoothchatting.Adapters;

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

public class AllMessagesAdapter extends RecyclerView.Adapter<AllMessagesAdapter.MessageViewHolder> {
    private List<ChatMessage> messages;
    private String searchQuery = "";

    public AllMessagesAdapter(List<ChatMessage> messages) {
        this.messages = messages;
    }

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_all_messages, parent, false);
        return new MessageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        ChatMessage message = messages.get(position);
        holder.bind(message);
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    public void updateMessages(List<ChatMessage> newMessages) {
        this.messages = newMessages;
        notifyDataSetChanged();
    }

    public void setSearchQuery(String query) {
        this.searchQuery = query.toLowerCase();
        notifyDataSetChanged();
    }

    public static class MessageViewHolder extends RecyclerView.ViewHolder {
        private final TextView textViewUsername;
        private final TextView textViewMessage;
        private final TextView textViewTimestamp;

        public MessageViewHolder(@NonNull View itemView) {
            super(itemView);
            textViewUsername = itemView.findViewById(R.id.textViewUsername);
            textViewMessage = itemView.findViewById(R.id.textViewMessage);
            textViewTimestamp = itemView.findViewById(R.id.textViewTimestamp);
        }

        public void bind(ChatMessage message) {
            textViewUsername.setText(message.getSender());
            textViewMessage.setText(message.getMessage());
            textViewTimestamp.setText(message.getTimestamp());
        }
    }
} 
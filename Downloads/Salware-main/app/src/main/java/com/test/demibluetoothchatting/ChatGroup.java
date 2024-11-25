package com.test.demibluetoothchatting;

public class ChatGroup {
    private String conversationName;

    public ChatGroup() {} // Default constructor for Firebase

    public ChatGroup(String conversationName) {
        this.conversationName = conversationName;
    }

    public String getConversationName() {
        return conversationName;
    }

    public void setConversationName(String conversationName) {
        this.conversationName = conversationName;
    }
}


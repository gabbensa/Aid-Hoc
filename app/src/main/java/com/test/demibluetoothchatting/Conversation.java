package com.test.demibluetoothchatting;

public class Conversation {
    private int id; // Use `int` for numbers
    private String message;
    private String sender;
    private String receiver;
    private String timestamp;

    public Conversation() {} // Default constructor

    public int getId() { return id; }
    public String getMessage() { return message; }
    public String getSender() { return sender; }
    public String getReceiver() { return receiver; }
    public String getTimestamp() { return timestamp; }
}

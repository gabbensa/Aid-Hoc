package com.test.demibluetoothchatting;

public class User {
    private int id;
    private String fullName;
    private String username;
    private String password;

    private String userType;

    public User(int id, String fullName, String username, String password, String userType) {
        this.id = id;
        this.fullName = fullName;
        this.username = username;
        this.password = password;
        this.userType = userType;
    }

    public int getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getUserType() { return userType; }
}


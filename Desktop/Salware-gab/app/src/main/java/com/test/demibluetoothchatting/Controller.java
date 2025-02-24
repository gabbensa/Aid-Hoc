package com.test.demibluetoothchatting;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class Controller {
    private static final String TAG = "Controller";
    private final Handler handler;
    private AcceptThread acceptThread;
    private ConnectThread connectThread;
    private ReadWriteThread readWriteThread;
    private int state;

    static final int NONE = 0;
    static final int LISTEN = 1;
    static final int CONNECTING = 2;
    static final int CONNECTED = 3;

    public Controller(Handler handler) {
        state = NONE;
        this.handler = handler;
    }

    // Set the current state of the connection
    private synchronized void setState(int state) {
        this.state = state;
        handler.obtainMessage(MainActivity.state_change, state, -1).sendToTarget();
    }

    // Get the current connection state
    public synchronized int getState() {
        return state;
    }

    // Start the controller
    public synchronized void start() {
        // Cancel any ongoing connection threads
        if (connectThread != null) {
            connectThread.cancel();
            connectThread = null;
        }

        if (readWriteThread != null) {
            readWriteThread.cancel();
            readWriteThread = null;
        }

        setState(LISTEN);
        if (acceptThread == null) {
            acceptThread = new AcceptThread();
            acceptThread.start();
        }
    }

    // Connect to a server
    public synchronized void connect(String groupOwnerAddress) {
        // Cancel ongoing threads
        if (state == CONNECTING) {
            if (connectThread != null) {
                connectThread.cancel();
                connectThread = null;
            }
        }

        if (readWriteThread != null) {
            readWriteThread.cancel();
            readWriteThread = null;
        }

        connectThread = new ConnectThread(groupOwnerAddress);
        connectThread.start();
        setState(CONNECTING);
    }

    // Manage an established connection
    public synchronized void connected(Socket socket) {
        // Cancel ongoing threads
        if (connectThread != null) {
            connectThread.cancel();
            connectThread = null;
        }

        if (readWriteThread != null) {
            readWriteThread.cancel();
            readWriteThread = null;
        }

        if (acceptThread != null) {
            acceptThread.cancel();
            acceptThread = null;
        }

        readWriteThread = new ReadWriteThread(socket);
        readWriteThread.start();

        setState(CONNECTED);
    }

    // Stop all threads
    public synchronized void stop() {
        if (connectThread != null) {
            connectThread.cancel();
            connectThread = null;
        }

        if (readWriteThread != null) {
            readWriteThread.cancel();
            readWriteThread = null;
        }

        if (acceptThread != null) {
            acceptThread.cancel();
            acceptThread = null;
        }
        setState(NONE);
    }

    public void write(byte[] out) {
        ReadWriteThread r;
        synchronized (this) {
            if (state != CONNECTED) return;
            r = readWriteThread;
        }
        r.write(out);
    }

    private void connectionFailed() {
        Message msg = handler.obtainMessage(MainActivity.message_toast);
        Bundle bundle = new Bundle();
        bundle.putString("toast", "Unable to connect to the device");
        msg.setData(bundle);
        handler.sendMessage(msg);

        Controller.this.start();
    }

    private void connectionLost() {
        Message msg = handler.obtainMessage(MainActivity.message_toast);
        Bundle bundle = new Bundle();
        bundle.putString("toast", "Device connection was lost");
        msg.setData(bundle);
        handler.sendMessage(msg);

        Controller.this.start();
    }

    // Server thread
    private class AcceptThread extends Thread {
        private ServerSocket serverSocket;

        public AcceptThread() {
            try {
                serverSocket = new ServerSocket(8888);
            } catch (IOException e) {
                Log.e(TAG, "Error creating server socket", e);
            }
        }

        public void run() {
            Log.d(TAG, "Server thread started. Waiting for connections...");
            Socket socket;
            while (state != CONNECTED) {
                try {
                    socket = serverSocket.accept();
                    if (socket != null) {
                        synchronized (Controller.this) {
                            switch (state) {
                                case LISTEN:
                                case CONNECTING:
                                    connected(socket);
                                    break;
                                case NONE:
                                case CONNECTED:
                                    socket.close();
                                    break;
                            }
                        }
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Error accepting connection", e);
                    break;
                }
            }
        }

        public void cancel() {
            try {
                serverSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing server socket", e);
            }
        }
    }

    // Client thread
    private class ConnectThread extends Thread {
        private final String host;
        private Socket socket;

        public ConnectThread(String host) {
            this.host = host;
        }

        public void run() {
            try {
                Log.d(TAG, "Attempting to connect to server at: " + host);
                socket = new Socket(host, 8888);
                connected(socket);
            } catch (IOException e) {
                Log.e(TAG, "Connection failed", e);
                connectionFailed();
                try {
                    if (socket != null) socket.close();
                } catch (IOException ex) {
                    Log.e(TAG, "Error closing socket", ex);
                }
            }
        }

        public void cancel() {
            try {
                if (socket != null) socket.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing client socket", e);
            }
        }
    }

    // Thread for managing connection and data transfer
    private class ReadWriteThread extends Thread {
        private final Socket socket;
        private final InputStream inputStream;
        private final OutputStream outputStream;

        public ReadWriteThread(Socket socket) {
            this.socket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "Error getting socket streams", e);
            }

            inputStream = tmpIn;
            outputStream = tmpOut;
        }

        public void run() {
            byte[] buffer = new byte[1024];
            int bytes;

            while (true) {
                try {
                    bytes = inputStream.read(buffer);
                    handler.obtainMessage(MainActivity.message_read, bytes, -1, buffer.clone()).sendToTarget();
                } catch (IOException e) {
                    Log.e(TAG, "Connection lost", e);
                    connectionLost();
                    break;
                }
            }
        }

        public void write(byte[] buffer) {
            try {
                outputStream.write(buffer);
                handler.obtainMessage(MainActivity.message_write, -1, -1, buffer).sendToTarget();
            } catch (IOException e) {
                Log.e(TAG, "Error writing to output stream", e);
            }
        }

        public void cancel() {
            try {
                socket.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing socket", e);
            }
        }
    }

    public static void PutData(Context context, String key, String value) {
        try {
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
            SharedPreferences.Editor editor = preferences.edit();
            editor.putString(key, value);
            editor.apply();
        } catch (Exception e) {
            Log.e(TAG, "Error saving data", e);
        }
    }

    public static String GetData(Context context, String key) {
        try {
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
            return preferences.getString(key, null);
        } catch (Exception e) {
            Log.e(TAG, "Error retrieving data", e);
            return null;
        }
    }
}


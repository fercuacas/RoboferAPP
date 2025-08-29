package com.robofer.app.bt;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.content.ContextCompat;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

import android.content.Context;

public class BtClient {
    public static final UUID UUID_SPP =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private final Context ctx;
    private final BluetoothAdapter adapter;
    private BluetoothDevice device;
    private BluetoothSocket socket;
    private InputStream in;
    private OutputStream out;

    public BtClient(Context ctx) {
        this.ctx = ctx;
        this.adapter = BluetoothAdapter.getDefaultAdapter();
    }

    public boolean hasPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT)
                    == PackageManager.PERMISSION_GRANTED &&
                   ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_SCAN)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    public BluetoothDevice[] getPairedDevices() {
        Set<BluetoothDevice> bonded = adapter.getBondedDevices();
        if (bonded == null) return new BluetoothDevice[0];
        return bonded.toArray(new BluetoothDevice[0]);
    }

    public void selectDevice(BluetoothDevice d) {
        this.device = d;
    }

    public BluetoothDevice getDevice() {
        return device;
    }

    public void connect() throws Exception {
        if (device == null) throw new IllegalStateException("No device selected");
        adapter.cancelDiscovery();
        try {
            socket = device.createRfcommSocketToServiceRecord(UUID_SPP);
            socket.connect();
        } catch (SecurityException e) {
            // Fall back to an insecure socket to rule out pairing issues.
            socket = device.createInsecureRfcommSocketToServiceRecord(UUID_SPP);
            socket.connect();
        }
        in = socket.getInputStream();
        out = socket.getOutputStream();
    }

    public void close() {
        try { if (in != null) in.close(); } catch (Exception ignored) {}
        try { if (out != null) out.close(); } catch (Exception ignored) {}
        try { if (socket != null) socket.close(); } catch (Exception ignored) {}
    }

    public synchronized void writeLine(String s) throws Exception {
        String msg = s + "\n";
        out.write(msg.getBytes());
        out.flush();
    }

    public synchronized String readLine() throws Exception {
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') break;
            sb.append((char) c);
        }
        if (sb.length() == 0 && c == -1) return null;
        return sb.toString().trim();
    }
}

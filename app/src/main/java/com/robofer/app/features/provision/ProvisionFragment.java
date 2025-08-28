package com.robofer.app.features.provision;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.robofer.app.databinding.FragmentProvisionBinding;
import com.robofer.app.bt.BtClient;

import android.app.AlertDialog;

public class ProvisionFragment extends Fragment {

    private FragmentProvisionBinding b;
    private BtClient bt;
    private static final int REQ_BT = 100;
    private BluetoothAdapter adapter;
    private final java.util.ArrayList<BluetoothDevice> found = new java.util.ArrayList<>();
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice d = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (d != null && !containsDevice(d)) {
                    found.add(d);
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                showDeviceDialog();
            } else if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
                BluetoothDevice d = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (bt.getDevice() != null && d != null &&
                        bt.getDevice().getAddress().equals(d.getAddress())) {
                    int state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR);
                    if (state == BluetoothDevice.BOND_BONDED) {
                        requireActivity().runOnUiThread(() -> b.tvStatus.setText("Emparejado"));
                        connectInBackground();
                    } else if (state == BluetoothDevice.BOND_NONE) {
                        requireActivity().runOnUiThread(() -> b.tvStatus.setText("Emparejamiento cancelado"));
                    }
                }
            }
        }
    };

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        b = FragmentProvisionBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle s) {
        super.onViewCreated(v, s);

        bt = new BtClient(requireContext());
        adapter = BluetoothAdapter.getDefaultAdapter();

        IntentFilter f = new IntentFilter();
        f.addAction(BluetoothDevice.ACTION_FOUND);
        f.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        f.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        requireContext().registerReceiver(receiver, f);

        b.btnSelectDevice.setOnClickListener(v1 -> selectDevice());
        b.btnListNets.setOnClickListener(v12 -> listNetworks());
        b.btnSend.setOnClickListener(v13 -> sendCredentials());

        ensurePermissions();
    }

    private void ensurePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!bt.hasPermissions()) {
                requestPermissions(new String[]{
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.ACCESS_FINE_LOCATION
                }, REQ_BT);
            }
        }
    }

    private void selectDevice() {
        if (!bt.hasPermissions()) {
            b.tvStatus.setText("Faltan permisos Bluetooth");
            return;
        }
        found.clear();
        if (adapter.isDiscovering()) adapter.cancelDiscovery();
        adapter.startDiscovery();
        b.tvStatus.setText("Buscando dispositivos...");
    }

    private void showDeviceDialog() {
        if (found.isEmpty()) {
            requireActivity().runOnUiThread(() -> b.tvStatus.setText("No se encontraron dispositivos"));
            return;
        }
        String[] names = new String[found.size()];
        for (int i = 0; i < found.size(); i++) {
            BluetoothDevice d = found.get(i);
            String n = d.getName();
            if (n == null) n = d.getAddress();
            names[i] = n + " (" + d.getAddress() + ")";
        }
        requireActivity().runOnUiThread(() -> {
            new AlertDialog.Builder(requireContext())
                    .setTitle("Elige el robot")
                    .setItems(names, (dlg, w) -> {
                        BluetoothDevice d = found.get(w);
                        bt.selectDevice(d);
                        b.tvDevice.setText("Dispositivo: " + names[w]);
                        adapter.cancelDiscovery();
                        if (d.getBondState() == BluetoothDevice.BOND_BONDED) {
                            connectInBackground();
                        } else {
                            b.tvStatus.setText("Emparejando...");
                            d.createBond();
                        }
                    }).show();
        });
    }

    private boolean containsDevice(BluetoothDevice d) {
        for (BluetoothDevice x : found) {
            if (x.getAddress().equals(d.getAddress())) return true;
        }
        return false;
    }

    private void connectInBackground() {
        b.tvStatus.setText("Conectando...");
        new Thread(() -> {
            try {
                bt.connect();
                bt.writeLine("HELLO");
                String resp = bt.readLine();
                requireActivity().runOnUiThread(() -> b.tvStatus.setText("Conectado: " + resp));
                if (resp != null && resp.startsWith("SSID:")) {
                    final String ssid = resp.substring(5);
                    requireActivity().runOnUiThread(() -> {
                        b.tvSsid.setText("SSID: " + (ssid.isEmpty() ? "(vacío)" : ssid));
                        b.etSsid.setText(ssid);
                    });
                }
            } catch (Exception e) {
                requireActivity().runOnUiThread(() -> b.tvStatus.setText("Error: " + e.getMessage()));
                bt.close();
            }
        }).start();
    }

    private void listNetworks() {
        b.tvStatus.setText("Pidiendo lista...");
        new Thread(() -> {
            try {
                bt.writeLine("LIST");
                java.util.ArrayList<String> ssids = new java.util.ArrayList<>();
                while (true) {
                    String line = bt.readLine();
                    if (line == null || line.equals("END")) break;
                    if (line.startsWith("NET:")) {
                        int p1 = line.indexOf("ssid=");
                        if (p1 >= 0) {
                            int p2 = line.indexOf(';', p1);
                            String s = line.substring(p1 + 5, p2 > 0 ? p2 : line.length());
                            ssids.add(s);
                        }
                    }
                }
                if (ssids.isEmpty()) {
                    requireActivity().runOnUiThread(() -> b.tvStatus.setText("Sin redes"));
                    return;
                }
                String[] arr = ssids.toArray(new String[0]);
                requireActivity().runOnUiThread(() -> {
                    new AlertDialog.Builder(requireContext())
                            .setTitle("Red del ROBOT")
                            .setItems(arr, (d, w) -> {
                                b.etSsid.setText(arr[w]);
                                b.tvSsid.setText("SSID: " + arr[w]);
                            }).show();
                });
            } catch (Exception e) {
                requireActivity().runOnUiThread(() -> b.tvStatus.setText("Error LIST: " + e.getMessage()));
            }
        }).start();
    }

    private void sendCredentials() {
        final String ssid = b.etSsid.getText().toString().trim();
        final String pass = b.etPassword.getText().toString().trim();
        if (ssid.isEmpty()) { b.tvStatus.setText("Escribe SSID"); return; }
        if (pass.isEmpty()) { b.tvStatus.setText("Escribe contraseña"); return; }

        b.tvStatus.setText("Enviando...");
        new Thread(() -> {
            try {
                bt.writeLine("SET:ssid=" + ssid + ";pass=" + pass);
                String resp = bt.readLine();
                requireActivity().runOnUiThread(() -> b.tvStatus.setText("Respuesta: " + resp));
            } catch (Exception e) {
                requireActivity().runOnUiThread(() -> b.tvStatus.setText("Error: " + e.getMessage()));
            }
        }).start();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        try {
            requireContext().unregisterReceiver(receiver);
        } catch (Exception ignored) {}
        if (adapter != null && adapter.isDiscovering()) adapter.cancelDiscovery();
        bt.close();
        b = null;
    }
}

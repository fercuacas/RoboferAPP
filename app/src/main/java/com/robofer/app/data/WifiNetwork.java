package com.robofer.app.data;

public class WifiNetwork {
    public final String ssid;
    public final int rssi;
    public final String security;

    public WifiNetwork(String ssid, int rssi, String security) {
        this.ssid = ssid;
        this.rssi = rssi;
        this.security = security;
    }

    @Override
    public String toString() {
        return ssid + " (" + rssi + " dBm, " + security + ")";
    }
}

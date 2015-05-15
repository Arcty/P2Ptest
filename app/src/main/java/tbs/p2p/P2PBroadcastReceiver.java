package tbs.p2p;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.NetworkInfo;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.Channel;
import android.util.Log;

/**
 * Created by Michael on 5/12/2015.
 */
public class P2PBroadcastReceiver extends BroadcastReceiver {

    private WifiP2pManager wifiP2pManager;
    private Channel mChannel;
    private MainActivity mainActivity;

    public P2PBroadcastReceiver(WifiP2pManager manager, Channel channel, MainActivity activity) {
        super();
        this.wifiP2pManager = manager;
        this.mChannel = channel;
        this.mainActivity = activity;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();

        if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {
            // Check to see if Wi-Fi is enabled and notify appropriate activity
            int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
            if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                // TODO Wifi P2P is enabled
                Log.e("p2pBroadcast", "p2p enabled");
            } else {
                // TODO Wi-Fi P2P is not enabled
            }
        } else if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {
            if (wifiP2pManager != null) {
                wifiP2pManager.requestPeers(mChannel, MainActivity.wifiP2PPeerListener);
            }
        } else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
            // Respond to new connection or disconnections
            Log.e("p2pBroadCast", "Connection changed");
            NetworkInfo networkState = intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);
            WifiP2pInfo wifiInfo = intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_INFO);
            P2PService.setCurrentlyPairedDevice((WifiP2pDevice) intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE));
            while (wifiInfo == null) {
                wifiInfo = intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_INFO);
                P2PService.wifiInfo = wifiInfo;
            }
            if (networkState.isConnected()) {
                MainActivity.toast("connected");
                MainActivity.requestConnectionInfo();
            }

        } else if (WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.equals(action)) {
            // Respond to this device's wifi state changing
            Log.e("p2pBroadCast", "wifi changed");
        }
    }

}

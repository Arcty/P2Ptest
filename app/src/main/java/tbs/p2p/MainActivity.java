package tbs.p2p;

import android.app.Activity;
import android.content.Context;
import android.content.IntentFilter;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.ActionListener;
import android.net.wifi.p2p.WifiP2pManager.Channel;
import android.net.wifi.p2p.WifiP2pManager.PeerListListener;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;


public class MainActivity extends Activity {
    public static WifiP2pManager manager;
    public static Channel channel;
    public static P2PBroadcastReceiver receiver;
    public static final IntentFilter intentFilter = new IntentFilter();
    public static MainActivity mainActivity;
    public static final ActionListener wifiP2PActionListener = new WifiP2pManager.ActionListener() {
        @Override
        public void onSuccess() {

        }

        @Override
        public void onFailure(int i) {

        }
    };

    public static final PeerListListener wifiP2PPeerListener = new WifiP2pManager.PeerListListener() {
        @Override
        public void onPeersAvailable(WifiP2pDeviceList wifiP2pDeviceList) {
            for (WifiP2pDevice wifiP2pDevice : wifiP2pDeviceList.getDeviceList()) {
                Log.e("peerListenerFound", wifiP2pDevice.deviceName + " (" + wifiP2pDevice.deviceAddress + ")");
            }
        }
    };

    public static final WifiP2pConfig config = new WifiP2pConfig();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        manager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        channel = manager.initialize(this, getMainLooper(), null);
        receiver = new P2PBroadcastReceiver(manager, channel, this);

        //Todo use this
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
        mainActivity = this;
    }

    protected void onResume() {
        super.onResume();
        registerReceiver(receiver, intentFilter);
    }

    @Override
    protected void onDestroy() {
        P2PService.destroy();
        unregisterReceiver(receiver);
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(receiver);
    }

    public void connectToDevice(final WifiP2pDevice device) {
        config.deviceAddress = device.deviceAddress;
        manager.connect(channel, config, new ActionListener() {

            @Override
            public void onSuccess() {
                //success logic
                Log.e("failed to connect to", device.deviceName + " (" + device.deviceAddress + ")");
            }

            @Override
            public void onFailure(int reason) {
                //failure logic
            }
        });
    }

    public WifiP2pDevice getConnectedPeer() {
        WifiP2pDevice peer = null;
        for (WifiP2pDevice d : mPeers) {
            if (d.status == WifiP2pDevice.CONNECTED) {
                peer = d;
            }
        }
        return peer;
    }

    public static void toast(final String msg) {
        if (mainActivity != null)
            mainActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(mainActivity, msg, Toast.LENGTH_LONG).show();
                }
            });
    }
}

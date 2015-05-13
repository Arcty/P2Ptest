package tbs.p2p;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
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
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Collection;


public class MainActivity extends Activity {
    public static WifiP2pManager manager;
    public static Channel channel;
    public static P2PBroadcastReceiver receiver;
    public static final IntentFilter intentFilter = new IntentFilter();
    public static MainActivity mainActivity;
    public static Collection<WifiP2pDevice> peers;
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
        public void onPeersAvailable(final WifiP2pDeviceList wifiP2pDeviceList) {
            peers = wifiP2pDeviceList.getDeviceList();
            for (WifiP2pDevice wifiP2pDevice : peers) {
                Log.e("p2ppeerListenerFound", wifiP2pDevice.deviceName + " (" + wifiP2pDevice.deviceAddress + ")");
            }
            listView.setAdapter(new P2PAdapter());
            listView.setOnItemClickListener(new ListView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                    connectToDevice((WifiP2pDevice) peers.toArray()[i]);
                }
            });
        }
    };

    public static final WifiP2pConfig config = new WifiP2pConfig();
    public static ListView listView;
    public static Button refresh;

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
        manager.discoverPeers(channel, wifiP2PActionListener);
        listView = (ListView) findViewById(R.id.list);
        refresh = (Button) findViewById(R.id.refresh);
        refresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                P2PService.enqueueMessage(new Message("mikeTest: " + System.currentTimeMillis(), Message.MessageType.SEND_MESSAGE));
            }
        });
    }

    protected void onResume() {
        super.onResume();
        registerReceiver(receiver, intentFilter);
    }

    @Override
    protected void onDestroy() {
        P2PService.destroy();
        try {
            unregisterReceiver(receiver);
        } catch (Exception e) {
            e.printStackTrace();
        }
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(receiver);
    }

    public static void connectToDevice(final WifiP2pDevice device) {
        //TODO major need to sort this out >> look at p2pservice.host > getIP...
        //Todo make a listview...
        config.deviceAddress = device.deviceAddress;
        manager.connect(channel, config, new ActionListener() {
            @Override
            public void onSuccess() {
                Log.e("p2pconnecting to", device.deviceName + " (" + device.deviceAddress + ")");
                MainActivity.toast("p2pconnected to" + device.deviceName + " (" + device.deviceAddress + ")");
                P2PService.setCurrentlyPairedDevice(device);
                mainActivity.startService(new Intent(mainActivity, P2PService.class));
            }

            @Override
            public void onFailure(int reason) {
                Log.e("p2pfailed to connect to", device.deviceName + " (" + device.deviceAddress + ")");
            }
        });
    }

    public static WifiP2pDevice getConnectedPeer() {
        if (peers == null)
            return null;
        WifiP2pDevice peer = null;
        for (WifiP2pDevice d : peers) {
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

    public static class P2PAdapter extends BaseAdapter {
        public P2PAdapter() {

        }

        @Override
        public int getCount() {
            return peers == null ? 0 : peers.size();
        }

        @Override
        public Object getItem(int i) {
            return peers == null ? null : peers.toArray()[i];
        }

        @Override
        public long getItemId(int i) {
            return 0;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            view = new TextView(mainActivity);
            view.setLayoutParams(new ListView.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            view.setPadding(20, 20, 20, 20);
            ((TextView) view).setTextSize(22);
            final WifiP2pDevice device = ((WifiP2pDevice) peers.toArray()[i]);
            ((TextView) view).setText(device.deviceName + " (" + device.deviceAddress + ")");
            return view;
        }
    }
}

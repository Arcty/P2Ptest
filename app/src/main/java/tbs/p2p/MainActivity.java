package tbs.p2p;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.p2p.*;
import android.net.wifi.p2p.WifiP2pManager.ActionListener;
import android.net.wifi.p2p.WifiP2pManager.Channel;
import android.net.wifi.p2p.WifiP2pManager.PeerListListener;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

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
            //TODO devices found >> do something about it
        }

        @Override
        public void onFailure(int i) {
            //TODO devices not found >> do something about it (rescan?)
        }
    };

    public static final PeerListListener wifiP2PPeerListener = new WifiP2pManager.PeerListListener() {
        @Override
        public void onPeersAvailable(final WifiP2pDeviceList wifiP2pDeviceList) {
            //TODO make refresh button visible
            peers = wifiP2pDeviceList.getDeviceList();
            listView.setAdapter(new P2PAdapter());
            listView.setOnItemClickListener(new ListView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                    clickedDevice = (WifiP2pDevice) peers.toArray()[i];
                    connectToDevice(clickedDevice);
                }
            });
        }
    };

    public static WifiP2pManager.ConnectionInfoListener connectionInfoListener = new WifiP2pManager.ConnectionInfoListener() {
        @Override
        public void onConnectionInfoAvailable(WifiP2pInfo wifiP2pInfo) {
            if (wifiP2pInfo == null) {
                requestConnectionInfo();
                return;
            }
            Log.e("p2p", "receivedInfo : " + wifiP2pInfo.groupOwnerAddress.toString() + "\nisOwner? : " + wifiP2pInfo.isGroupOwner);
            toast("OwnerAddress :" + wifiP2pInfo.groupOwnerAddress.toString());
            if (wifiP2pInfo.groupFormed && wifiP2pInfo.isGroupOwner) {
                P2PService.getServerSocketThreadVoid();
            } else if (wifiP2pInfo.groupFormed) {
                P2PService.getClientSocketThreadVoid(wifiP2pInfo.groupOwnerAddress.toString());
            } else {
                connectToDevice(clickedDevice);
            }
        }
    };

    public static final WifiP2pConfig config = new WifiP2pConfig();
    public static ListView listView;
    private static WifiP2pDevice clickedDevice;
    private static EditText message;
    public static Button refresh;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        manager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        channel = manager.initialize(this, getMainLooper(), new WifiP2pManager.ChannelListener() {
            @Override
            public void onChannelDisconnected() {
                //TODO disconnected
            }
        });
        receiver = new P2PBroadcastReceiver(manager, channel, this);

        //Todo use this
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
        mainActivity = this;
        manager.discoverPeers(channel, wifiP2PActionListener);
        listView = (ListView) findViewById(R.id.list);
        message = (EditText) findViewById(R.id.message);
        refresh = (Button) findViewById(R.id.refresh);
        refresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                P2PService.enqueueMessage(new Message(message.getText().toString(), Message.MessageType.SEND_MESSAGE));
                message.setText("");
            }
        });
        mainActivity.startService(new Intent(mainActivity, P2PService.class));
    }

    protected void onResume() {
        super.onResume();
        registerReceiver(receiver, intentFilter);
        //TODO if P2pser.isStop...
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
                //TODO connected to device start service here and in broadcast receiver
                P2PService.setCurrentlyPairedDevice(device);
            }

            @Override
            public void onFailure(int reason) {
                //TODO failed to connect
                Log.e("p2p", "failed to connect to " + device.deviceName + " (" + device.deviceAddress + ")");
            }
        });
    }

    public static void requestConnectionInfo() {
        if (!P2PService.isActive())
            manager.requestConnectionInfo(channel, connectionInfoListener);
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

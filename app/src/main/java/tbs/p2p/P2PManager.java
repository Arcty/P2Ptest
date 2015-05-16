package tbs.p2p;

import android.app.Activity;
import android.app.Dialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.IntentFilter;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.*;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import org.apache.http.conn.util.InetAddressUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Created by Michael on 5/16/2015.
 */
public class P2PManager {

    public static Thread mainThread, getClientSocketThread, getServerSocketThread, listenerThread;
    public static final P2PClientRunnable p2pClientRunnable = new P2PClientRunnable();
    public static final P2PServerRunnable p2PServerRunnable = new P2PServerRunnable();
    private static InputStream inputStream;
    private static OutputStream outputStream;
    private static boolean stop = false, currentlySendingSomething = false;
    private static final ArrayList<Message> messages = new ArrayList<Message>();
    public static ServerSocket serverSocket;
    private static Socket socketFromServer, socketFromClient;
    private static String host;
    public static WifiP2pInfo wifiInfo = null;
    private static WifiP2pDevice currentlyPairedDevice;
    public static ContentResolver cr;
    private static Activity activity;
    private static Dialog dialog;
    private static WifiP2pDevice clickedDevice;
    public static final WifiP2pConfig config = new WifiP2pConfig();
    public static WifiP2pManager manager;
    public static WifiManager wifiManager;
    public static Collection<WifiP2pDevice> peers;
    public static WifiP2pManager.Channel channel;
    public static P2PBroadcastReceiver receiver;
    public static final IntentFilter intentFilter = new IntentFilter();
    public static P2PListener p2PListener;
    private boolean dialogShown;

    public P2PManager(Activity activity, P2PListener p2PListener, boolean startScan) {
        this.activity = activity;
        this.p2PListener = p2PListener;
        if (startScan)
            startScan();
    }

    public static final WifiP2pManager.ActionListener wifiP2PActionListener = new WifiP2pManager.ActionListener() {
        @Override
        public void onSuccess() {
            //TODO devices found >> do something about it
        }

        @Override
        public void onFailure(int i) {
            //TODO devices not found >> do something about it (rescan?)
        }
    };

    public final WifiP2pManager.PeerListListener wifiP2PPeerListener = new WifiP2pManager.PeerListListener() {
        @Override
        public void onPeersAvailable(final WifiP2pDeviceList wifiP2pDeviceList) {
            //TODO make refresh button visible
            peers = wifiP2pDeviceList.getDeviceList();
            if (dialog != null && dialog.isShowing()) {
                try {
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {

                            dialog.dismiss();
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }


            if (peers.size() > 0)
                showDeviceDialog();
            else
                toast("No peers found, try again");

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
            if (wifiP2pInfo.groupFormed && wifiP2pInfo.isGroupOwner) {
                getServerSocketThreadVoid();
            } else if (wifiP2pInfo.groupFormed) {
                getClientSocketThreadVoid(wifiP2pInfo.groupOwnerAddress.toString());
            } else {
                connectToDevice(clickedDevice);
            }
        }
    };

    public void registerReceivers() {
        try {
            activity.registerReceiver(receiver, intentFilter);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void unRegisterReceivers() {
        try {
            destroy();
            activity.unregisterReceiver(receiver);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showDeviceDialog() {
        if (dialogShown)
            return;
        if (activity == null)
            return;
        dialog = new Dialog(activity, R.style.CustomDialog);
        dialog.setContentView(R.layout.device_list_dialog);
        final ListView listView = (ListView) dialog.findViewById(R.id.list_view);
        listView.setAdapter(new P2PAdapter());
        listView.setOnItemClickListener(new ListView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                clickedDevice = (WifiP2pDevice) peers.toArray()[i];
                toast("Connecting to " + clickedDevice.deviceName + " (" + clickedDevice.deviceAddress + ")");
                if (dialog != null)
                    dialog.dismiss();
                connectToDevice(clickedDevice);
            }
        });

        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                dialog.show();
            }
        });
    }

    public static void connectToDevice(final WifiP2pDevice device) {
        //TODO major need to sort this out >> look at host > getIP...
        //Todo make a listview...
        config.deviceAddress = device.deviceAddress;
        manager.connect(channel, config, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                //TODO connected to device start service here and in broadcast receiver
                setCurrentlyPairedDevice(device);
            }

            @Override
            public void onFailure(int reason) {
                //TODO failed to connect
                Log.e("p2p", "failed to connect to " + device.deviceName + " (" + device.deviceAddress + ")");
            }
        });
    }

    public static void toast(final String msg) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(activity, msg, Toast.LENGTH_LONG).show();
            }
        });
    }

    public void startScan() {
        dialogShown = false;
        if (wifiManager == null) {
            wifiManager = (WifiManager) activity.getSystemService(Context.WIFI_SERVICE);
            if (!wifiManager.isWifiEnabled()) {
                toast("Wifi not enabled, enabling");
                wifiManager.setWifiEnabled(true);
            }
        }

        if (manager == null)
            manager = (WifiP2pManager) activity.getSystemService(Context.WIFI_P2P_SERVICE);

        if (channel == null)
            channel = manager.initialize(activity, activity.getMainLooper(), new WifiP2pManager.ChannelListener() {
                @Override
                public void onChannelDisconnected() {
                    //TODO disconnected
                }
            });

        if (receiver == null)
            receiver = new P2PBroadcastReceiver(manager, channel, this);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

        if (p2PListener != null)
            p2PListener.onScanStarted();

        manager.discoverPeers(channel, wifiP2PActionListener);
    }

    public static void requestConnectionInfo() {
        if (!isActive())
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


    public static WifiP2pDevice getCurrentlyPairedDevice() {
        return currentlyPairedDevice;
    }

    public static void setCurrentlyPairedDevice(WifiP2pDevice device) {
        currentlyPairedDevice = device;
        if (!(device == null)) {
            getIpAddress(device.deviceAddress);
        }
    }

    public static void getClientSocketThreadVoid(final String hostIP) {
        log("getClientSocket");
        if (isActive())
            return;
        getClientSocketThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    host = hostIP;
                    toast("connecting in 250ms");
                    try {
                        Thread.sleep(250);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    if (host.startsWith("/"))
                        host = host.split("/")[1].trim();
                    while (socketFromClient == null || !socketFromClient.isConnected()) {
                        socketFromClient = new Socket(host, 8888);
                    }
                    startMainThread();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        getClientSocketThread.start();
    }

    public static void getServerSocketThreadVoid() {
        log("getServerSocket");
        if (isActive())
            return;
        getServerSocketThread = new Thread(new Runnable() {
            @Override
            public void run() {
                log("getServerSocket1");
                socketFromServer = new Socket();
                socketFromServer = null;
                try {
                    serverSocket = new ServerSocket(8888);
                    log("getServerSocket2");
                    toast("accepting");

                    while (socketFromServer == null) {
                        socketFromServer = serverSocket.accept();
                    }
                    log("getServerSocket3");
                    startMainThread();
                } catch (Exception e) {
                    e.printStackTrace();
                }

            }
        });
        getServerSocketThread.start();
    }

    public static void startMainThread() {
        log("starting mainthread");
        if (p2PListener != null)
            p2PListener.onSocketsConfigured();
        if (mainThread == null || (!mainThread.isAlive() && !(mainThread.isInterrupted()))) {
            mainThread = new Thread(p2PServerRunnable);
            mainThread.start();
        }
    }

    public static class P2PServerRunnable implements Runnable {
        public P2PServerRunnable() {

        }

        @Override
        public void run() {
            listenerThread = new Thread(p2pClientRunnable);
            listenerThread.start();
            //TODO remove for final release
            enqueueMessage(new Message("mikeCheck 1,2,1,2", Message.MessageType.SEND_MESSAGE));
            log("mainThread step 1");
            while (getSocket() == null) {
                try {
                    if (getSocket() != null) {
                        log("mainThread step 2 start");
                        inputStream = getSocket().getInputStream();
                        outputStream = getSocket().getOutputStream();
                        log("mainThread step 2 end");
                    } else {
                        log("mainThread step 2");
                        Log.e("p2psocket is null", "mainThread");
                    }
                    Thread.sleep(400);
                } catch (Exception e) {
                    Log.e("p2pserver", e.getMessage());
                    return;
                }
            }
            log("mainThread step 3");
            while (!stop) {
                if (!currentlySendingSomething && messages != null && messages.size() > 0) {
                    sendMessage();
                }
                try {
                    //Todo this is to save battery a bit (checks if there's a message to be sent 4 times a second)
                    //Todo if you want things to be instantaneous just delete the whole try catch statement or reduce the sleep
                    Thread.sleep(250);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            log("mainThread stopping");
        }
    }

    public static class P2PClientRunnable implements Runnable {

        public P2PClientRunnable() {

        }

        @Override
        public void run() {
            log("listener1");
            // Todo receive file             byte buf[] = new byte[1024];
//         try {
//                /**
//                 * Create a socketFromServer socketFromServer with the host,
//                 * port, and timeout information.
//                 */
//                /**
//                 * Create a byte stream from a JPEG file and pipe it to the output stream
//                 * of the socketFromServer. This data will be retrieved by the server device.
//                 */
//                final OutputStream outputStream = getSocket().getOutputStream();
//                final InputStream inputStream = cr.openInputStream(Uri.parse(Environment.getExternalStorageDirectory().getPath() + "a014.jpg"));
//                while ((len = inputStream.read(buf)) != -1) {
//                    outputStream.write(buf, 0, len);
//                }
//                outputStream.close();
//                inputStream.close();
//            } catch (FileNotFoundException e) {
//                //catch logic
//            } catch (IOException e) {
//                //catch logic
//            }

            while (!stop) {
                try {
                    //Todo this is to save battery a bit (checks if there's a message to be downloaded 50 times a second)
                    //Todo if you want things to be instantaneous just delete the whole try catch statement or reduce the sleep
                    Thread.sleep(20);
                    try {
                        inputStream = getSocket().getInputStream();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    byte[] msg = new byte[inputStream.available()];
                    if (inputStream.available() > 0) {
                        Log.e("available", String.valueOf(inputStream.available()));
                        inputStream.read(msg, 0, inputStream.available());
                        if (msg.length > 3 && p2PListener != null) {
                            p2PListener.onMessageReceived(new String(msg));
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            Log.e("p2PListener", "stopping");
        }
    }

    public static synchronized void enqueueMessage(Message message) {
        if (messages != null && message != null) {
            if (!messages.contains(message)) {
                messages.add(message);
            }
        }
    }

    private static synchronized void sendMessage() {
        if (messages == null || messages.size() < 1)
            return;
        final Message message = messages.get(0);
        switch (message.messageType) {
            case SEND_COMMAND:
                sendSimpleText(message.message);
                break;
            case SEND_FILE:
                //Todo message structure >> fileName + sep + file
                if (cr == null && (activity != null))
                    cr = activity.getContentResolver();
                try {
                    final File file = new File(message.message);
                    copyFile(file.getName(), cr.openInputStream(Uri.parse(Environment.getExternalStorageDirectory().getPath() + "a014.jpg")));
                } catch (Exception e) {
                    e.printStackTrace();
                }
                break;
            case SEND_MESSAGE:
                //Todo message structure >> timeLong + sep + message
                sendSimpleText(String.valueOf(System.currentTimeMillis()) + Message.MESSAGE_SEPERATOR + message.message);
                break;
        }
        messages.remove(message);
    }

    public static boolean sendSimpleText(String text) {
        try {
            currentlySendingSomething = true;
            if (outputStream == null) {
                outputStream = getSocket().getOutputStream();
            }
            log("writing message : " + text);
            outputStream.write(text.getBytes());
            //Todo check this
            outputStream.flush();
            currentlySendingSomething = false;
        } catch (IOException e) {
            return false;
        }
        return true;
    }

    public synchronized static boolean copyFile(String fileName, InputStream inputStream) {
        //Todo apply file name
        byte buf[] = new byte[1024];
        int len;
        try {
            if (outputStream == null) {
                outputStream = getSocket().getOutputStream();
            }
            currentlySendingSomething = true;
            while ((len = inputStream.read(buf)) != -1) {
                outputStream.write(buf, 0, len);
            }
            outputStream.flush();
            inputStream.close();
            currentlySendingSomething = false;
        } catch (IOException e) {
            Log.e("p2pfailed to copyFile", e.toString());
            return false;
        }
        return true;
    }

    public static void destroy() {
        stop = true;
        log("destroy called");
        try {
            getSocket().close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            outputStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            inputStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public static String getIpAddress() {
        try {
            final List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                if (!intf.getName().contains("p2p"))
                    continue;

                final List<InetAddress> addrs = Collections.list(intf.getInetAddresses());

                for (InetAddress addr : addrs) {
                    if (!addr.isLoopbackAddress()) {
                        String sAddr = addr.getHostAddress().toUpperCase();
                        boolean isIPv4 = InetAddressUtils.isIPv4Address(sAddr);

                        if (isIPv4 && sAddr.contains("192.168.49.")) {
                            return sAddr;
                        }
                    }
                }
            }
        } catch (Exception ex) {
            Log.e("getIPAddress", "error in parsing");
        } // for now eat exceptions

        Log.e("getIPAddress", "returning empty ip address");
        return "";
    }

    public static String getMACAddress(String interfaceName) {
        try {
            final List<NetworkInterface> interfaces = Collections
                    .list(NetworkInterface.getNetworkInterfaces());

            for (final NetworkInterface intf : interfaces) {
                if (interfaceName != null) {
                    if (!intf.getName().equalsIgnoreCase(interfaceName))
                        continue;
                }
                byte[] mac = intf.getHardwareAddress();
                if (mac == null)
                    return "";
                StringBuilder buf = new StringBuilder();
                for (int idx = 0; idx < mac.length; idx++)
                    buf.append(String.format("%02X:", mac[idx]));
                if (buf.length() > 0)
                    buf.deleteCharAt(buf.length() - 1);
                return buf.toString();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return "";

    }

    public static void log(String msg) {
        Log.e("p2p", msg);
    }

    public static Socket getSocket() {
        return (socketFromServer == null || !socketFromServer.isConnected()) ? socketFromClient : socketFromServer;
    }

    public static String getIpAddress(String mac) {
        try {
            List<NetworkInterface> interfaces = Collections
                    .list(NetworkInterface.getNetworkInterfaces());
            /*
             * for (NetworkInterface networkInterface : interfaces) { Log.v(TAG,
             * "interface name " + networkInterface.getName() + "mac = " +
             * getMACAddress(networkInterface.getName())); }
             */

            for (NetworkInterface intf : interfaces) {
                if (!intf.getName().contains("p2p"))
                    continue;

                List<InetAddress> addrs = Collections.list(intf.getInetAddresses());

                for (InetAddress addr : addrs) {
                    // Log.v(TAG, "inside");
                    if (!addr.isLoopbackAddress()) {
                        // Log.v(TAG, "isnt loopback");
                        String sAddr = addr.getHostAddress().toUpperCase();
                        boolean isIPv4 = InetAddressUtils.isIPv4Address(sAddr);

                        if (isIPv4) {
                            if (sAddr.contains("192.168.49.")) {
                                return sAddr;
                            }
                        }
                    }
                }
            }
        } catch (Exception ex) {
        } // for now eat exceptions
        return "";
    }

    public static boolean isActive() {
        return getServerSocketThread != null && getSocket() != null && getSocket().isConnected();
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
            view = View.inflate(activity, R.layout.device_list_item, null);
            final WifiP2pDevice device = ((WifiP2pDevice) peers.toArray()[i]);
            ((TextView) view.findViewById(R.id.device_name)).setText(device.deviceName + " (" + device.deviceAddress + ")");
            try {
                final TextView deviceStatus = ((TextView) view.findViewById(R.id.device_status));
                switch (device.status) {
                    case WifiP2pDevice.AVAILABLE:
                        deviceStatus.setText("Available");
                        deviceStatus.setTextColor(0x259b24);
                        break;
                    case WifiP2pDevice.CONNECTED:
                        deviceStatus.setText("Connected");
                        deviceStatus.setTextColor(0x5677fc);
                        break;
                    case WifiP2pDevice.UNAVAILABLE:
                        deviceStatus.setText("Available");
                        deviceStatus.setTextColor(0x999999);
                        break;
                    case WifiP2pDevice.INVITED:
                        deviceStatus.setText("Invited");
                        deviceStatus.setTextColor(0xfb8c00);
                        break;
                    case WifiP2pDevice.FAILED:
                        deviceStatus.setText("Failed");
                        deviceStatus.setTextColor(0xe51c23);
                        break;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return view;
        }
    }

    public interface P2PListener {
        //Todo handle messages here
        void onScanStarted();

        //Todo handle messages here
        void onMessageReceived(String msg);

        //Todo this is when the devices are connected, but not yet communicating
        void onDevicesConnected();

        //Todo this is when the devices are disconnected, but not yet communicating
        void onDevicesDisconnected();

        //Todo this is when the devices can send messages
        void onSocketsConfigured();
    }

}

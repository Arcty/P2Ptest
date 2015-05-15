package tbs.p2p;

import android.app.Service;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.media.MediaActionSound;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pInfo;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;

import org.apache.http.conn.util.InetAddressUtils;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by Michael on 5/12/2015.
 */
public class P2PService extends Service {
    public static Context context;
    public static Thread mainThread, getClientSocketThread, getServerSocketThread, listenerThread;
    public static final P2PClientRunnable p2pClientRunnable = new P2PClientRunnable();
    public static final P2PServerRunnable p2PServerRunnable = new P2PServerRunnable();
    private static InputStream inputStream;
    private static OutputStream outputStream;
    public static boolean stop = false, currentlySendingSomething = false;
    private static final ArrayList<Message> messages = new ArrayList<>();
    public static ServerSocket serverSocket;
    private static Socket socketFromServer, socketFromClient;
    private static String host;
    public static WifiP2pInfo wifiInfo = null;
    private static WifiP2pDevice currentlyPairedDevice;
    public static ContentResolver cr;

    public static WifiP2pDevice getCurrentlyPairedDevice() {
        return currentlyPairedDevice;
    }

    public static void setCurrentlyPairedDevice(WifiP2pDevice device) {
        P2PService.currentlyPairedDevice = device;

        if (!(device == null)) {
            getIpAddress(device.deviceAddress);
        }
        //TODO  if (device != null)
        //    getClientSocketThreadVoid();
        //Todo
        //MainActivity.toast("paired to:" + (device == null ? "null" : (device.deviceName + " (" + device.deviceAddress + ")")));
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        context = getApplicationContext();
        new Thread(new InitRunnable()).start();
    }

    public static void getClientSocketThreadVoid(final String hostIP) {
        log("getClientSocket");
        getClientSocketThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    host = hostIP;
                    toast("about to try connect");
//                    if (host == null || host.length() < 4) {
//                        toast("failed to get IP :" + (host == null ? "" : host));
//                        return;
//                    } else {
//                        toast("host :" + host);
//                    }
                    try {
                        Thread.sleep(250);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }


                    toast("connecting");

                    while (socketFromClient == null || !socketFromClient.isConnected()) {
                        toast("host :" + host);
                        if (host.startsWith("/"))
                            host = host.split("/")[1];
                        socketFromClient = new Socket(host, 8888);
                    }
                    startMainThread();
//                    try {
//                        getServerSocketThread.interrupt();
//                        getServerSocketThread.stop();
//                        getServerSocketThread.join();
//                        serverSocket = null;
//                    } catch (Exception e) {
//                        e.printStackTrace();
//                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        getClientSocketThread.start();
    }

    public static void getServerSocketThreadVoid() {
        log("getServerSocket");
        getServerSocketThread = new Thread(new Runnable() {
            @Override
            public void run() {
                log("getServerSocket1");
                socketFromServer = new Socket();
                try {
                    serverSocket = new ServerSocket(8888);
                    log("getServerSocket2");
                    toast("accepting");
                    socketFromServer = null;
                    while (socketFromServer == null) {
                        socketFromServer = serverSocket.accept();
                    }
                    log("getServerSocket3");
                    log("'is Server > " + serverSocket.getInetAddress());
//
//                    try {
//                        getClientSocketThread.interrupt();
//                        getClientSocketThread.stop();
//                        getClientSocketThread.join();
//                        socketFromClient = null;
//                    } catch (Exception e) {
//                        e.printStackTrace();
//                    }
                    startMainThread();
                } catch (Exception e) {
                    e.printStackTrace();
                }

            }
        });
        getServerSocketThread.start();
    }

    public static class InitRunnable implements Runnable {
        @Override
        public void run() {
            Log.e("p2pinit thread", "started");
            try {
                //TODO getServerSocketThreadVoid();
                cr = context.getContentResolver();
            } catch (Exception e) {
                Log.e("P2PServiceOnCreate", e.toString());
            }


        }
    }

    public static void startMainThread() {
        log("starting mainthread");
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
                    try {
                        log(String.format("inputAvailable : " + inputStream.available()));
                        try {
                            final String hostName = wifiInfo.groupOwnerAddress.getHostName();
                            log("host name :" + hostName);
                            log(String.format("wifiInfo ip = %s vs %s", hostName, getIpAddress()));
                        } catch (Exception e) {
                            log(e.toString());
                        }

                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                }
                try {
                    Thread.sleep(250);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            log("mainThread stopping");
        }
    }

    public static class P2PClientRunnable implements Runnable {
        private static int len;

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
                        if (msg.length > 3)
                            onMessageReceived(new String(msg));
                    }
                } catch (Exception e) {

                }
            }
            Log.e("messageListener", "stopping");
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
            Log.e("p2pfailed to send simple text : " + text, e.toString());
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
        try {
            socketFromServer.close();
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

    public static void onMessageReceived(String message) {
        Log.e("message received", message);
        toast("message received" + message);
    }

    public static String getIpAddress() {
        try {
            final List<NetworkInterface> interfaces = Collections
                    .list(NetworkInterface.getNetworkInterfaces());
        /*
         * for (NetworkInterface networkInterface : interfaces) { Log.v(TAG,
         * "interface name " + networkInterface.getName() + "mac = " +
         * getMACAddress(networkInterface.getName())); }
         */

            for (NetworkInterface intf : interfaces) {
                if (!intf.getName().contains("p2p"))
                    continue;

                Log.e("getIPAddress", intf.getName() + "   " + getMACAddress(intf.getName()));
                final List<InetAddress> addrs = Collections.list(intf.getInetAddresses());

                for (InetAddress addr : addrs) {
                    // Log.v(TAG, "inside");
                    if (!addr.isLoopbackAddress()) {
                        // Log.v(TAG, "isnt loopback");
                        String sAddr = addr.getHostAddress().toUpperCase();
                        Log.v("getIPAddress", "ip=" + sAddr);

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

    public static void toast(String msg) {
        log("tstd > >" + msg);
        MainActivity.toast(msg);
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


}

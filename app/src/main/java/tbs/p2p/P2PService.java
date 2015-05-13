package tbs.p2p;

import android.app.Service;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.net.wifi.p2p.WifiP2pDevice;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;

import org.apache.http.conn.util.InetAddressUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by Michael on 5/12/2015.
 */
public class P2PService extends Service {
    public static Context context;
    public static Thread thread;
    public static final P2PClientRunnable p2pClientRunable = new P2PClientRunnable();
    public static final P2PServerRunnable p2PServerRunnable = new P2PServerRunnable();
    private static InputStream inputStream;
    private static OutputStream outputStream;
    public static boolean stop = false, currentlySendingSomething = false;
    private static final ArrayList<Message> messages = new ArrayList<>();
    public static ServerSocket serverSocket;
    public static Socket socket;
    private static String host;
    private static WifiP2pDevice currentlyPairedDevice;
    public static ContentResolver cr;

    public static WifiP2pDevice getCurrentlyPairedDevice() {
        return currentlyPairedDevice;
    }

    public static void setCurrentlyPairedDevice(WifiP2pDevice device) {
        P2PService.currentlyPairedDevice = device;
        //Todo
        MainActivity.toast("paired to:" + device == null ? "null" : (device.deviceName + " (" + device.deviceAddress + ")"));
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        context = getApplicationContext();
        try {
            serverSocket = new ServerSocket(8888);
            socket = serverSocket.accept();
            socket.bind(null);
            host = getIpAddress();
            if (host == null || host.length() < 4) {
                MainActivity.toast("failed to get IP :" + (host == null ? "" : host));
                return;
            }
            socket.connect((new InetSocketAddress(host, 8888)), 2000);
            cr = context.getContentResolver();
        } catch (Exception e) {
            Log.e("P2PServiceOnCreate", e.toString());
        }

        thread = new Thread(p2PServerRunnable);
        thread.start();
    }


    public static class P2PServerRunnable implements Runnable {
        public P2PServerRunnable() {

        }

        @Override
        public void run() {
            try {
                inputStream = socket.getInputStream();
                outputStream = socket.getOutputStream();
            } catch (IOException e) {
                Log.e("p2pserver", e.getMessage());
                return;
            }

            while (!stop) {
                if (!currentlySendingSomething && messages != null && messages.size() > 0) {
                    sendMessage();
                }

            }

        }
    }

    public static class P2PClientRunnable implements Runnable {
        private static String host;
        private static int port;
        private static int len;
        public static Socket socket = new Socket();

        public P2PClientRunnable() {

        }

        @Override
        public void run() {
            byte buf[] = new byte[1024];
            try

            {
                /**
                 * Create a socket socket with the host,
                 * port, and timeout information.
                 */


                /**
                 * Create a byte stream from a JPEG file and pipe it to the output stream
                 * of the socket. This data will be retrieved by the server device.
                 */
                final OutputStream outputStream = socket.getOutputStream();
                final InputStream inputStream = cr.openInputStream(Uri.parse(Environment.getExternalStorageDirectory().getPath() + "a014.jpg"));
                while ((len = inputStream.read(buf)) != -1) {
                    outputStream.write(buf, 0, len);
                }
                outputStream.close();
                inputStream.close();
            } catch (FileNotFoundException e) {
                //catch logic
            } catch (IOException e) {
                //catch logic
            }

            while (!stop) {
                try {
                    Thread.sleep(20);
                    if (inputStream.available() > 0) {
                        byte[] msg = new byte[inputStream.available()];
                        inputStream.read(msg, 0, inputStream.available());
                        if (msg.length > 3)
                            onMessageReceived(new String(msg));
                    }
                } catch (Exception e) {

                }
            }
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
            outputStream.write(text.getBytes());
            //Todo check this
            outputStream.flush();
            currentlySendingSomething = false;
        } catch (IOException e) {
            Log.e("failed to send simple text : " + text, e.toString());
            return false;
        }
        return true;
    }

    public synchronized static boolean copyFile(String fileName, InputStream inputStream) {
        //Todo apply file name
        byte buf[] = new byte[1024];
        int len;
        try {
            currentlySendingSomething = true;
            while ((len = inputStream.read(buf)) != -1) {
                outputStream.write(buf, 0, len);
            }
            outputStream.flush();
            inputStream.close();
            currentlySendingSomething = false;
        } catch (IOException e) {
            Log.e("failed to copyFile", e.toString());
            return false;
        }
        return true;
    }

    public static void destroy() {
        stop = true;
        try {
            socket.close();
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
        MainActivity.toast("message received" + message);
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

                Log.e("getIPAddress",
                        intf.getName() + "   " + getMACAddress(intf.getName()));

                final List<InetAddress> addrs = Collections.list(intf
                        .getInetAddresses());

                for (InetAddress addr : addrs) {
                    // Log.v(TAG, "inside");

                    if (!addr.isLoopbackAddress()) {
                        // Log.v(TAG, "isnt loopback");
                        String sAddr = addr.getHostAddress().toUpperCase();
                        Log.v("getIPAddress", "ip=" + sAddr);

                        boolean isIPv4 = InetAddressUtils.isIPv4Address(sAddr);

                        if (isIPv4) {
                            if (sAddr.contains("192.168.49.")) {
                                Log.v("getIPAddress", "ip = " + sAddr);
                                return sAddr;
                            }
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
}

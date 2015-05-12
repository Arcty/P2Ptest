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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

/**
 * Created by Michael on 5/12/2015.
 */
public class P2PService extends Service {
    public static Context context;
    public static final Thread thread = new Thread();
    public static final P2PClientRunnable p2pClientRunable = new P2PClientRunnable();
    public static final P2PServerRunnable p2PServerRunnable = new P2PServerRunnable();
    private static InputStream inputStream;
    private static OutputStream outputStream;
    public static boolean stop = false;
    private static final ArrayList<Message> messages = new ArrayList<>();
    public static ServerSocket serverSocket;
    public static Socket socket;
    public static WifiP2pDevice currentlyPairedDevice;
    public static ContentResolver cr;

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
            cr = context.getContentResolver();
        } catch (Exception e) {
            Log.e("P2PserviceOnCreate", e.toString());
        }
    }


    public static class P2PServerRunnable implements Runnable {
        public P2PServerRunnable() {

        }

        @Override
        public void run() {
            try {

                final File f = new File(Environment.getExternalStorageDirectory() + "/newImage.jpg");

                File dirs = new File(f.getParent());
                if (!dirs.exists())
                    dirs.mkdirs();
                f.createNewFile();
                InputStream inputstream = socket.getInputStream();
                OutputStream outputStream = socket.getOutputStream();
                copyFile(inputstream, new FileOutputStream(f));
                serverSocket.close();
                //Todo return f.getAbsolutePath();
            } catch (IOException e) {
                Log.e("p2pserver", e.getMessage());
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
                socket.bind(null);
                socket.connect((new InetSocketAddress(host, 8888)), 2000);

                /**
                 * Create a byte stream from a JPEG file and pipe it to the output stream
                 * of the socket. This data will be retrieved by the server device.
                 */
                final OutputStream outputStream = socket.getOutputStream();
                final
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

    private synchronized void sendMessage() {
        if (messages == null || messages.size() < 1)
            return;

        final Message message = messages.get(0);
        switch (message.messageType) {
            case SEND_COMMAND:

                break;
            case SEND_FILE:
                //Todo message structure >> fileName + sep + file
                try {
                    final File file = new File(message.message);
                    copyFile(file.getName(), cr.openInputStream(Uri.parse(Environment.getExternalStorageDirectory().getPath() + "a014.jpg")), outputStream)
                } catch (Exception e) {
                    e.printStackTrace();
                }
                break;
            case SEND_MESSAGE:
                //Todo message structure >> timeLong + sep + message
                sendSimpleText(String.valueOf(System.currentTimeMillis()) + Message.MESSAGE_SEPERATOR + message.message);
                break;
        }
    }

    public static boolean sendSimpleText(String text) {
        try {
            outputStream.write(text.getBytes());
        } catch (IOException e) {
            Log.e("failed to send simple text : " + text, e.toString());
            return false;
        }
        return true;
    }

    public synchronized static boolean copyFile(String fileName, InputStream inputStream, OutputStream out) {
        byte buf[] = new byte[1024];
        int len;
        try {
            while ((len = inputStream.read(buf)) != -1) {
                out.write(buf, 0, len);
            }
            out.close();
            inputStream.close();
        } catch (IOException e) {
            Log.e("copyFile", e.toString());
            return false;
        }
        return true;
    }

    public static void destroy() {
        stop = true;
    }

    public static void onMessageReceived(String message) {

    }
}

package tbs.p2p;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.*;


public class MainActivity extends Activity {
    public static MainActivity mainActivity;
    public static ListView listView;
    private static EditText message;
    public static Button refresh;
    private static P2PManager p2PManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mainActivity = this;
        listView = (ListView) findViewById(R.id.list);
        message = (EditText) findViewById(R.id.message);
        refresh = (Button) findViewById(R.id.refresh);
        p2PManager = new P2PManager(this, new P2PManager.P2PListener() {
            @Override
            public void onScanStarted() {
                toast("Scan started");
            }

            @Override
            public void onMessageReceived(String msg) {
                toast(msg);
            }

            @Override
            public void onDevicesConnected() {
                toast("connected, not communicating");
            }

            @Override
            public void onDevicesDisconnected() {
                toast("disconnected");
            }

            @Override
            public void onSocketsConfigured() {
                toast("communicating");
            }
        }, true);

        refresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                p2PManager.enqueueMessage(new Message(message.getText().toString(), Message.MessageType.SEND_MESSAGE));
                message.setText("");
            }
        });

    }

    protected void onResume() {
        super.onResume();
        p2PManager.registerReceivers();
        //TODO if P2pser.isStop...
    }

    @Override
    protected void onDestroy() {
        try {
            stopService(new Intent(this, P2PManager.class));
        } catch (Exception e) {
            e.printStackTrace();
        }
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        super.onPause();
        p2PManager.unRegisterReceivers();
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

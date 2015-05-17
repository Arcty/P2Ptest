package tbs.p2p;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import java.util.ArrayList;


public class MainActivity extends Activity {
    public static MainActivity mainActivity;
    public static ListView listView;
    private static EditText message;
    public static Button refresh;
    private static P2PManager p2PManager;
    private static LogAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mainActivity = this;

        listView = (ListView) findViewById(R.id.list);
        message = (EditText) findViewById(R.id.message);
        refresh = (Button) findViewById(R.id.refresh);

        adapter = new LogAdapter();

        listView.setAdapter(adapter);

        if (p2PManager == null)
            p2PManager = new P2PManager(this, new P2PManager.P2PListener() {
                @Override
                public void onScanStarted() {

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

    @Override
    protected void onDestroy() {
        try {
            stopService(new Intent(this, P2PManager.class));
        } catch (Exception e) {
            e.printStackTrace();
        }
        super.onDestroy();
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

    private static final ArrayList<String> logs = new ArrayList<String>();

    private class LogAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return logs == null ? 0 : logs.size();
        }

        @Override
        public Object getItem(int i) {
            return logs == null ? null : logs.get(i);
        }

        @Override
        public long getItemId(int i) {
            return 0;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            if (view == null)
                view = new TextView(mainActivity);
            ((TextView) view).setTextSize(20);
            view.setPadding(8, 8, 8, 8);
            final String text = logs.get(i);
            ((TextView) view).setTextColor(text.startsWith("crashed") ? 0xffdd1111 : 0xff555555);
            ((TextView) view).setText(text);
            return view;
        }
    }

    public static void addLog(String log) {
        logs.add(log);
        try {
            if (adapter != null)
                adapter.notifyDataSetChanged();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}


package com.cooeeui.brand.zenlauncher.unittest.chat;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.cooeeui.brand.zenlauncher.unittest.R;
import com.cooeeui.brand.zenlauncher.unittest.utils.DeviceUtil;
import com.netease.pomelo.DataCallBack;
import com.netease.pomelo.DataEvent;
import com.netease.pomelo.DataListener;
import com.netease.pomelo.PomeloClient;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

public class ChatActivity extends Activity {
    final String host = "120.24.59.174";
    final int port = 3014;
    final String channel = "zenparty";
    final String target = "*";

    String[] users;
    String name;
    String content;
    PomeloClient client;
    ArrayList<Map<String, String>> dialog = new ArrayList<Map<String, String>>();

    TextView tv;
    Button button;
    ListView lv;

    final int MSG_ERROR = -1;
    final int MSG_READY = 0;
    final int MSG_SEND = 1;
    final int MSG_RECEIVE = 2;
    final int MSG_DISCONNECT = 3;

    class ChatContent {
        public String from;
        public String target;
        public String content;

        public ChatContent() {
        }

        public ChatContent(String from, String target, String content) {
            this.from = from;
            this.target = target;
            this.content = content;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.chat_layout);
        tv = (TextView) findViewById(R.id.entry);
        button = (Button) findViewById(R.id.send);
        button.setEnabled(false);
        button.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                sendMessage();
            }
        });
        lv = (ListView) findViewById(R.id.history);

        // get name from device.
        name = String.valueOf(DeviceUtil.getUniqueId(this).hashCode());

        client = new PomeloClient(host, port);
        client.init();
        queryEntry();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (client != null) {
            client.disconnect();
        }

        dialog.clear();
    }

    private void queryEntry() {
        JSONObject msg = new JSONObject();
        try {
            msg.put("uid", name);
            client.request("gate.gateHandler.queryEntry", msg,
                    new DataCallBack() {
                        @Override
                        public void responseData(JSONObject msg) {
                            client.disconnect();
                            try {
                                String ip = msg.getString("host");
                                enter(ip, msg.getInt("port"));
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        }
                    });
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void enter(String host, int port) {
        JSONObject msg = new JSONObject();
        try {
            msg.put("username", name);
            msg.put("rid", channel);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        client = new PomeloClient(host, port);
        client.init();
        client.request("connector.entryHandler.enter", msg, new DataCallBack() {
            @Override
            public void responseData(JSONObject msg) {
                if (msg.has("error")) {
                    Message message = myHandler.obtainMessage();
                    message.what = MSG_ERROR;
                    myHandler.sendMessage(message);
                    client.disconnect();
                    client = new PomeloClient(ChatActivity.this.host, ChatActivity.this.port);
                    client.init();
                    return;
                }
                try {
                    JSONArray jr = msg.getJSONArray("users");
                    users = new String[jr.length() + 1];
                    // * represent all users
                    users[0] = "*";
                    for (int i = 1; i <= jr.length(); i++) {
                        users[i] = jr.getString(i - 1);
                    }
                    // tell handler
                    Message message = myHandler.obtainMessage();
                    message.what = MSG_READY;
                    myHandler.sendMessage(message);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });
        client.on("disconnect", new DataListener() {
            @Override
            public void receiveData(DataEvent event) {
                Message message = myHandler.obtainMessage();
                message.what = MSG_DISCONNECT;
                myHandler.sendMessage(message);
            }
        });
    }

    private void sendMessage() {
        content = tv.getText().toString();
        if (content.equals(""))
            return;
        tv.setText("");

        JSONObject msg = new JSONObject();
        try {
            msg.put("content", content);
            msg.put("target", target);
            msg.put("rid", channel);
            msg.put("from", name);
            client.request("chat.chatHandler.send", msg, new DataCallBack() {
                @Override
                public void responseData(JSONObject msg) {
                    // wait for receive message if send to all or self.
                    if (!target.equals("*") && !target.equals(name)) {
                        Message message = myHandler.obtainMessage();
                        message.what = MSG_SEND;
                        message.obj = new ChatContent(name, target, content);
                        myHandler.sendMessage(message);
                    }
                }
            });
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private String getDate() {
        Date now = new Date(System.currentTimeMillis());
        SimpleDateFormat format = new SimpleDateFormat("HH:mm:ss");
        String nowStr = format.format(now);
        return nowStr;
    }

    private void addHistory(String from, String target, String content) {
        String result;
        if (!name.equals(target)) {
            result = from + " says: " + content;
        } else {
            result = from + " says to u: " + content;
        }
        Map<String, String> map = new HashMap<String, String>();
        map.put("ChatContent", result);
        map.put("DateContent", getDate());
        dialog.add(map);
        SimpleAdapter mSchedule = new SimpleAdapter(this, dialog,
                R.layout.chat_item,
                new String[] {
                        "ChatContent", "DateContent"
                }, new int[] {
                        R.id.ChatContent, R.id.DateContent
                });
        lv.setAdapter(mSchedule);
    }

    // use a handler to change ui.
    Handler myHandler = new Handler() {
        public void handleMessage(android.os.Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case MSG_ERROR:
                    Toast.makeText(ChatActivity.this, "please change your name to login.",
                            Toast.LENGTH_LONG).show();
                    break;
                case MSG_READY:
                    button.setEnabled(true);
                    dialog.clear();
                    Toast.makeText(ChatActivity.this, "ready.",
                            Toast.LENGTH_LONG).show();

                    // wait from broadcast message
                    client.on("onChat", new DataListener() {
                        @Override
                        public void receiveData(DataEvent event) {
                            JSONObject msg = event.getMessage();
                            ChatContent chatContent = new ChatContent();
                            try {
                                msg = msg.getJSONObject("body");
                                chatContent.from = msg.getString("from");
                                chatContent.target = msg.getString("target");
                                chatContent.content = msg.getString("msg");
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                            Message message = myHandler.obtainMessage();
                            message.what = MSG_RECEIVE;
                            message.obj = chatContent;
                            myHandler.sendMessage(message);
                        }
                    });
                    break;
                case MSG_SEND:
                {
                    ChatContent content = (ChatContent) msg.obj;
                    addHistory(content.from, content.target, content.content);
                    break;
                }
                case MSG_RECEIVE:
                {
                    ChatContent content = (ChatContent) msg.obj;
                    addHistory(content.from, content.target, content.content);
                    break;
                }
                case MSG_DISCONNECT:
                    button.setEnabled(false);
                    break;
            }
        };
    };
}

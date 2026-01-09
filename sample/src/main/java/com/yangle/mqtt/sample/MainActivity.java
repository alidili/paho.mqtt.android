package com.yangle.mqtt.sample;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

/**
 * 主页
 * <p>
 * Created by YangLe on 2026/1/9.
 * Website：http://www.yangle.tech
 */
public class MainActivity extends AppCompatActivity implements MQTTService.MQTTCallback {
    private static final String TAG = "MainActivity";

    private MQTTService mqttService;
    private boolean isBound = false;

    private Button btnConnect, btnDisconnect, btnSubscribe, btnUnsubscribe, btnPublish;
    private TextView tvStatus, tvLog;
    private EditText etTopic, etMessage;

    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MQTTService.LocalBinder binder = (MQTTService.LocalBinder) service;
            mqttService = binder.getService();
            mqttService.setCallback(MainActivity.this);
            isBound = true;
            Log.d(TAG, "MQTT服务绑定成功");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
            mqttService = null;
            Log.d(TAG, "MQTT服务断开绑定");
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        setupClickListeners();
    }

    private void initViews() {
        btnConnect = findViewById(R.id.btn_connect);
        btnDisconnect = findViewById(R.id.btn_disconnect);
        btnSubscribe = findViewById(R.id.btn_subscribe);
        btnUnsubscribe = findViewById(R.id.btn_unsubscribe);
        btnPublish = findViewById(R.id.btn_publish);

        tvStatus = findViewById(R.id.tv_status);
        tvLog = findViewById(R.id.tv_log);

        etTopic = findViewById(R.id.et_topic);
        etMessage = findViewById(R.id.et_message);
    }

    private void setupClickListeners() {
        btnConnect.setOnClickListener(v -> connectMQTT());
        btnDisconnect.setOnClickListener(v -> disconnectMQTT());
        btnSubscribe.setOnClickListener(v -> subscribeTopic());
        btnUnsubscribe.setOnClickListener(v -> unsubscribeTopic());
        btnPublish.setOnClickListener(v -> publishMessage());
    }

    private void connectMQTT() {
        if (isBound && mqttService != null) {
            if (!mqttService.isConnected()) {
                appendLog("正在连接MQTT服务器...");
                mqttService.connect();
            } else {
                appendLog("已经连接到MQTT服务器");
            }
        } else {
            appendLog("MQTT服务未绑定");
        }
    }

    private void disconnectMQTT() {
        if (isBound && mqttService != null) {
            if (mqttService.isConnected()) {
                appendLog("正在断开MQTT连接...");
                mqttService.disconnect();
            } else {
                appendLog("当前未连接到MQTT服务器");
            }
        }
    }

    private void subscribeTopic() {
        if (isBound && mqttService != null && mqttService.isConnected()) {
            String topic = etTopic.getText().toString().trim();
            if (!topic.isEmpty()) {
                appendLog("订阅主题: " + topic);
                mqttService.subscribe(topic);
            } else {
                Toast.makeText(this, "请输入主题名称", Toast.LENGTH_SHORT).show();
            }
        } else {
            appendLog("请先连接MQTT服务器");
        }
    }

    private void unsubscribeTopic() {
        if (isBound && mqttService != null && mqttService.isConnected()) {
            String topic = etTopic.getText().toString().trim();
            if (!topic.isEmpty()) {
                appendLog("取消订阅主题: " + topic);
                mqttService.unsubscribe(topic);
            } else {
                Toast.makeText(this, "请输入主题名称", Toast.LENGTH_SHORT).show();
            }
        } else {
            appendLog("请先连接MQTT服务器");
        }
    }

    private void publishMessage() {
        if (isBound && mqttService != null && mqttService.isConnected()) {
            String topic = etTopic.getText().toString().trim();
            String message = etMessage.getText().toString().trim();

            if (!topic.isEmpty() && !message.isEmpty()) {
                appendLog("发布消息到主题 " + topic + ": " + message);
                mqttService.publish(topic, message);
            } else {
                Toast.makeText(this, "请输入主题名称和消息内容", Toast.LENGTH_SHORT).show();
            }
        } else {
            appendLog("请先连接MQTT服务器");
        }
    }

    private void appendLog(String message) {
        runOnUiThread(() -> {
            String timestamp = java.text.DateFormat.getTimeInstance().format(new java.util.Date());
            tvLog.append("[" + timestamp + "] " + message + "\n");
        });
    }

    private void updateStatus(String status) {
        runOnUiThread(() -> {
            tvStatus.setText("状态: " + status);
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent intent = new Intent(this, MQTTService.class);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (isBound) {
            unbindService(serviceConnection);
            isBound = false;
        }
    }

    @Override
    public void onConnectionSuccess() {
        appendLog("MQTT连接成功!");
        updateStatus("已连接");
        runOnUiThread(() -> {
            btnConnect.setEnabled(false);
            btnDisconnect.setEnabled(true);
            btnSubscribe.setEnabled(true);
            btnUnsubscribe.setEnabled(true);
            btnPublish.setEnabled(true);
        });
    }

    @Override
    public void onConnectionFailure(String error) {
        appendLog("MQTT连接失败: " + error);
        updateStatus("连接失败");
        runOnUiThread(() -> {
            btnConnect.setEnabled(true);
            btnDisconnect.setEnabled(false);
            btnSubscribe.setEnabled(false);
            btnUnsubscribe.setEnabled(false);
            btnPublish.setEnabled(false);
        });
    }

    @Override
    public void onMessageReceived(String topic, String message) {
        appendLog("收到消息 - 主题: " + topic + ", 内容: " + message);
    }

    @Override
    public void onMessageDelivered(String topic) {
        appendLog("消息投递完成 - 主题: " + topic);
    }
}
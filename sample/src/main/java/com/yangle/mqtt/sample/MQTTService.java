package com.yangle.mqtt.sample;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * MQTT服务
 * <p>
 * Created by YangLe on 2026/1/9.
 * Website：http://www.yangle.tech
 */
public class MQTTService extends Service implements MqttCallback {
    private static final String TAG = "MQTTService";

    private MqttAndroidClient mqttClient;
    private MqttConnectOptions connectOptions;
    private final AtomicBoolean isConnected = new AtomicBoolean(false);
    private final IBinder binder = new LocalBinder();

    public interface MQTTCallback {
        void onConnectionSuccess();

        void onConnectionFailure(String error);

        void onMessageReceived(String topic, String message);

        void onMessageDelivered(String topic);
    }

    private MQTTCallback callback;

    public class LocalBinder extends Binder {
        public MQTTService getService() {
            return MQTTService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        initializeMQTT();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    private void initializeMQTT() {
        mqttClient = new MqttAndroidClient(this, MQTTConfig.BROKER_URL,
                MQTTConfig.CLIENT_ID, new MemoryPersistence());
        mqttClient.setCallback(this);

        connectOptions = new MqttConnectOptions();
        connectOptions.setCleanSession(MQTTConfig.CLEAN_SESSION);
        connectOptions.setConnectionTimeout(MQTTConfig.CONNECTION_TIMEOUT);
        connectOptions.setKeepAliveInterval(MQTTConfig.KEEP_ALIVE_INTERVAL);
        connectOptions.setUserName(MQTTConfig.USERNAME);
        connectOptions.setPassword(MQTTConfig.PASSWORD.toCharArray());

        Log.d(TAG, "MQTT客户端初始化完成");
    }

    public void connect() {
        if (mqttClient != null && !isConnected.get()) {
            try {
                mqttClient.connect(connectOptions, null, new IMqttActionListener() {
                    @Override
                    public void onSuccess(IMqttToken asyncActionToken) {
                        isConnected.set(true);
                        Log.d(TAG, "MQTT连接成功");
                        if (callback != null) {
                            callback.onConnectionSuccess();
                        }
                    }

                    @Override
                    public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                        isConnected.set(false);
                        Log.e(TAG, "MQTT连接失败: " + exception.getMessage());
                        if (callback != null) {
                            callback.onConnectionFailure(exception.getMessage());
                        }
                    }
                });
            } catch (MqttException e) {
                Log.e(TAG, "MQTT连接异常: " + e.getMessage());
                if (callback != null) {
                    callback.onConnectionFailure(e.getMessage());
                }
            }
        }
    }

    public void disconnect() {
        if (mqttClient != null && isConnected.get()) {
            try {
                mqttClient.disconnect();
                isConnected.set(false);
                Log.d(TAG, "MQTT断开连接");
            } catch (MqttException e) {
                Log.e(TAG, "MQTT断开连接异常: " + e.getMessage());
            }
        }
    }

    public void subscribe(String topic) {
        if (mqttClient != null && isConnected.get()) {
            try {
                mqttClient.subscribe(topic, MQTTConfig.QOS_LEVEL);
                Log.d(TAG, "订阅主题: " + topic);
            } catch (MqttException e) {
                Log.e(TAG, "订阅主题失败: " + e.getMessage());
            }
        }
    }

    public void unsubscribe(String topic) {
        if (mqttClient != null && isConnected.get()) {
            try {
                mqttClient.unsubscribe(topic);
                Log.d(TAG, "取消订阅主题: " + topic);
            } catch (MqttException e) {
                Log.e(TAG, "取消订阅主题失败: " + e.getMessage());
            }
        }
    }

    public void publish(String topic, String message) {
        if (mqttClient != null && isConnected.get()) {
            try {
                MqttMessage mqttMessage = new MqttMessage(message.getBytes());
                mqttMessage.setQos(MQTTConfig.QOS_LEVEL);
                mqttClient.publish(topic, mqttMessage);
                Log.d(TAG, "发布消息到主题 " + topic + ": " + message);
            } catch (MqttException e) {
                Log.e(TAG, "发布消息失败: " + e.getMessage());
            }
        }
    }

    public boolean isConnected() {
        return isConnected.get();
    }

    public void setCallback(MQTTCallback callback) {
        this.callback = callback;
    }

    @Override
    public void connectionLost(Throwable cause) {
        isConnected.set(false);
        Log.e(TAG, "MQTT连接丢失: " + cause.getMessage());
        if (callback != null) {
            callback.onConnectionFailure("连接丢失: " + cause.getMessage());
        }
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) throws Exception {
        String payload = new String(message.getPayload());
        Log.d(TAG, "收到消息 - 主题: " + topic + ", 内容: " + payload);
        if (callback != null) {
            callback.onMessageReceived(topic, payload);
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        try {
            String topic = token.getTopics()[0];
            Log.d(TAG, "消息投递完成 - 主题: " + topic);
            if (callback != null) {
                callback.onMessageDelivered(topic);
            }
        } catch (Exception e) {
            Log.e(TAG, "消息投递完成回调异常: " + e.getMessage());
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        disconnect();
        if (mqttClient != null) {
            mqttClient.close();
        }
    }
}
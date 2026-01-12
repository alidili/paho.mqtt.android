package com.yangle.mqtt.sample

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import org.eclipse.paho.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.util.concurrent.atomic.AtomicBoolean

/**
 * MQTT服务
 * <p>
 * Created by YangLe on 2026/1/9.
 * Website：http://www.yangle.tech
 */
class MQTTService : Service(), MqttCallback {

    companion object {
        private const val TAG = "MQTTService"
    }

    private var mqttClient: MqttAndroidClient? = null
    private var connectOptions: MqttConnectOptions? = null
    val isConnected = AtomicBoolean(false)
    private val binder = LocalBinder()

    interface MQTTCallback {
        fun onConnectionSuccess()
        fun onConnectionFailure(error: String?)
        fun onMessageReceived(topic: String?, message: String?)
        fun onMessageDelivered(topic: String?)
    }

    private var callback: MQTTCallback? = null

    inner class LocalBinder : Binder() {
        fun getService(): MQTTService = this@MQTTService
    }

    override fun onCreate() {
        super.onCreate()
        initializeMQTT()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private fun initializeMQTT() {
        mqttClient = MqttAndroidClient(
            this, MQTTConfig.BROKER_URL, MQTTConfig.CLIENT_ID, MemoryPersistence()
        )
        mqttClient?.setCallback(this)

        connectOptions = MqttConnectOptions().apply {
            isCleanSession = MQTTConfig.CLEAN_SESSION
            connectionTimeout = MQTTConfig.CONNECTION_TIMEOUT
            keepAliveInterval = MQTTConfig.KEEP_ALIVE_INTERVAL
            userName = MQTTConfig.USERNAME
            password = MQTTConfig.PASSWORD.toCharArray()
        }

        Log.d(TAG, "MQTT客户端初始化完成")
    }

    fun connect() {
        if (mqttClient != null && !isConnected.get()) {
            try {
                mqttClient?.connect(connectOptions, null, object : IMqttActionListener {
                    override fun onSuccess(asyncActionToken: IMqttToken?) {
                        isConnected.set(true)
                        Log.d(TAG, "MQTT连接成功")
                        callback?.onConnectionSuccess()
                    }

                    override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                        isConnected.set(false)
                        Log.e(TAG, "MQTT连接失败: ${exception?.message}")
                        callback?.onConnectionFailure(exception?.message)
                    }
                })
            } catch (e: MqttException) {
                Log.e(TAG, "MQTT连接异常: ${e.message}")
                callback?.onConnectionFailure(e.message)
            }
        }
    }

    fun disconnect() {
        if (mqttClient != null && isConnected.get()) {
            try {
                mqttClient?.disconnect()
                isConnected.set(false)
                Log.d(TAG, "MQTT断开连接")
            } catch (e: MqttException) {
                Log.e(TAG, "MQTT断开连接异常: ${e.message}")
            }
        }
    }

    fun subscribe(topic: String) {
        if (mqttClient != null && isConnected.get()) {
            try {
                mqttClient?.subscribe(topic, MQTTConfig.QOS_LEVEL)
                Log.d(TAG, "订阅主题: $topic")
            } catch (e: MqttException) {
                Log.e(TAG, "订阅主题失败: ${e.message}")
            }
        }
    }

    fun unsubscribe(topic: String) {
        if (mqttClient != null && isConnected.get()) {
            try {
                mqttClient?.unsubscribe(topic)
                Log.d(TAG, "取消订阅主题: $topic")
            } catch (e: MqttException) {
                Log.e(TAG, "取消订阅主题失败: ${e.message}")
            }
        }
    }

    fun publish(topic: String, message: String) {
        if (mqttClient != null && isConnected.get()) {
            try {
                val mqttMessage = MqttMessage(message.toByteArray()).apply {
                    qos = MQTTConfig.QOS_LEVEL
                }
                mqttClient?.publish(topic, mqttMessage)
                Log.d(TAG, "发布消息到主题 $topic: $message")
            } catch (e: MqttException) {
                Log.e(TAG, "发布消息失败: ${e.message}")
            }
        }
    }

    fun isConnected(): Boolean = isConnected.get()

    fun setCallback(callback: MQTTCallback?) {
        this.callback = callback
    }

    override fun connectionLost(cause: Throwable?) {
        isConnected.set(false)
        Log.e(TAG, "MQTT连接丢失: ${cause?.message}")
        callback?.onConnectionFailure("连接丢失: ${cause?.message}")
    }

    override fun messageArrived(topic: String?, message: MqttMessage?) {
        val payload = String(message?.payload ?: ByteArray(0))
        Log.d(TAG, "收到消息 - 主题: $topic, 内容: $payload")
        callback?.onMessageReceived(topic, payload)
    }

    override fun deliveryComplete(token: IMqttDeliveryToken?) {
        try {
            val topics = token?.topics
            if (topics != null && topics.isNotEmpty()) {
                val topic = topics[0]
                Log.d(TAG, "消息投递完成 - 主题: $topic")
                callback?.onMessageDelivered(topic)
            }
        } catch (e: Exception) {
            Log.e(TAG, "消息投递完成回调异常: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        disconnect()
        mqttClient?.close()
    }
}
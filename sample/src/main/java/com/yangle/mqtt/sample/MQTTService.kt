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
class MQTTService : Service() {

    companion object {
        private const val TAG = "MQTTService"
    }

    private var mqttClient: MqttAndroidClient? = null
    private var mConnectOptions: MqttConnectOptions? = null
    private val mIsConnected = AtomicBoolean(false)
    private val mBinder = LocalBinder()
    private var mCallback: MQTTCallback? = null

    inner class LocalBinder : Binder() {
        fun getService(): MQTTService = this@MQTTService
    }

    override fun onCreate() {
        super.onCreate()
        initMQTT()
    }

    override fun onBind(intent: Intent?): IBinder = mBinder

    /**
     * 初始化MQTT
     */
    private fun initMQTT() {
        mqttClient = MqttAndroidClient(
            this, MQTTConfig.BROKER_URL, MQTTConfig.CLIENT_ID, MemoryPersistence()
        )

        mqttClient?.setCallback(object : MqttCallback {
            override fun connectionLost(cause: Throwable?) {
                mIsConnected.set(false)
                Log.e(TAG, "MQTT断开连接: ${cause?.message}")
                mCallback?.onConnectionFailure("断开连接: ${cause?.message}")
            }

            override fun messageArrived(topic: String?, message: MqttMessage?) {
                val payload = String(message?.payload ?: ByteArray(0))
                Log.d(TAG, "收到消息 - 主题: $topic, 内容: $payload")
                mCallback?.onMessageReceived(topic ?: "", payload)
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) {
                val topics = token?.topics
                if (topics != null && topics.isNotEmpty()) {
                    val topic = topics[0]
                    Log.d(TAG, "消息投递完成 - 主题: $topic")
                    mCallback?.onMessageDelivered(topic)
                }
            }
        })

        mConnectOptions = MqttConnectOptions().apply {
            isCleanSession = MQTTConfig.CLEAN_SESSION
            connectionTimeout = MQTTConfig.CONNECTION_TIMEOUT
            keepAliveInterval = MQTTConfig.KEEP_ALIVE_INTERVAL
            userName = MQTTConfig.USERNAME
            password = MQTTConfig.PASSWORD.toCharArray()
        }
    }

    /**
     * MQTT连接
     */
    fun connect() {
        if (mqttClient != null && !mIsConnected.get()) {
            try {
                mqttClient?.connect(mConnectOptions, null, object : IMqttActionListener {
                    override fun onSuccess(asyncActionToken: IMqttToken?) {
                        mIsConnected.set(true)
                        Log.d(TAG, "MQTT连接成功")
                        mCallback?.onConnectionSuccess()
                    }

                    override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                        mIsConnected.set(false)
                        Log.e(TAG, "MQTT连接失败: ${exception?.message}")
                        mCallback?.onConnectionFailure(exception?.message ?: "")
                    }
                })
            } catch (e: MqttException) {
                Log.e(TAG, "MQTT连接异常: ${e.message}")
                mCallback?.onConnectionFailure(e.message ?: "")
            }
        }
    }

    /**
     * MQTT断开连接
     */
    fun disconnect() {
        if (mqttClient != null && mIsConnected.get()) {
            try {
                mqttClient?.disconnect()
                mIsConnected.set(false)
                Log.d(TAG, "MQTT断开连接")
            } catch (e: MqttException) {
                Log.e(TAG, "MQTT断开连接异常: ${e.message}")
            }
        }
    }

    /**
     * 订阅主题
     *
     * @param topic 主题名称
     */
    fun subscribe(topic: String) {
        if (mqttClient != null && mIsConnected.get()) {
            try {
                mqttClient?.subscribe(topic, MQTTConfig.QOS_LEVEL)
                Log.d(TAG, "订阅主题: $topic")
            } catch (e: MqttException) {
                Log.e(TAG, "订阅主题失败: ${e.message}")
            }
        }
    }

    /**
     * 取消订阅主题
     *
     * @param topic 主题名称
     */
    fun unsubscribe(topic: String) {
        if (mqttClient != null && mIsConnected.get()) {
            try {
                mqttClient?.unsubscribe(topic)
                Log.d(TAG, "取消订阅主题: $topic")
            } catch (e: MqttException) {
                Log.e(TAG, "取消订阅主题失败: ${e.message}")
            }
        }
    }

    /**
     * 发布消息
     *
     * @param topic   主题
     * @param message 消息内容
     */
    fun publish(topic: String, message: String) {
        if (mqttClient != null && mIsConnected.get()) {
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

    /**
     * MQTT是否已连接
     *
     * @return true: 已连接 false: 未连接
     */
    fun isConnected(): Boolean = mIsConnected.get()

    /**
     * 设置回调
     *
     * @param callback MQTTCallback
     */
    fun setCallback(callback: MQTTCallback) {
        this.mCallback = callback
    }

    override fun onDestroy() {
        super.onDestroy()
        disconnect()
        mqttClient?.close()
    }

    /**
     * 回调方法
     */
    interface MQTTCallback {
        /**
         * 连接成功
         */
        fun onConnectionSuccess()

        /**
         * 连接失败
         *
         * @param error 失败原因
         */
        fun onConnectionFailure(error: String)

        /**
         * 收到消息
         *
         * @param topic   主题
         * @param message 消息内容
         */
        fun onMessageReceived(topic: String, message: String)

        /**
         * 消息投递完成
         *
         * @param topic 主题
         */
        fun onMessageDelivered(topic: String)
    }
}
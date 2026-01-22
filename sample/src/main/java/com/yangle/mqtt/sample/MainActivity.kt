package com.yangle.mqtt.sample

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.yangle.mqtt.sample.MQTTService.MQTTCallback
import com.yangle.mqtt.sample.databinding.ActivityMainBinding
import java.text.DateFormat
import java.util.Date

/**
 * 主页
 * <p>
 * Created by YangLe on 2026/1/9.
 * Website：http://www.yangle.tech
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var binding: ActivityMainBinding
    private var mqttService: MQTTService? = null
    private var mServiceConnected = false

    /**
     * MQTT服务
     */
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val binder = service as MQTTService.LocalBinder
            mqttService = binder.getService()
            mqttService?.setCallback(object : MQTTCallback {
                override fun onConnectionSuccess() {
                    appendLog("MQTT连接成功!")
                    runOnUiThread {
                        binding.tvStatus.text = "状态: 已连接"
                        binding.btnConnect.isEnabled = false
                        binding.btnDisconnect.isEnabled = true
                        binding.btnSubscribe.isEnabled = true
                        binding.btnUnsubscribe.isEnabled = true
                        binding.btnPublish.isEnabled = true
                    }
                }

                override fun onConnectionFailure(error: String) {
                    appendLog("MQTT连接失败: $error")
                    runOnUiThread {
                        binding.tvStatus.text = "状态: 连接失败"
                        binding.btnConnect.isEnabled = true
                        binding.btnDisconnect.isEnabled = false
                        binding.btnSubscribe.isEnabled = false
                        binding.btnUnsubscribe.isEnabled = false
                        binding.btnPublish.isEnabled = false
                    }
                }

                override fun onMessageReceived(topic: String, message: String) {
                    appendLog("收到消息 - 主题: $topic, 内容: $message")
                }

                override fun onMessageDelivered(topic: String) {
                    appendLog("消息投递完成 - 主题: $topic")
                }
            })
            mServiceConnected = true
            Log.d(TAG, "MQTT服务绑定成功")
        }

        override fun onServiceDisconnected(name: ComponentName) {
            mServiceConnected = false
            mqttService = null
            Log.d(TAG, "MQTT服务断开绑定")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        init()
    }

    private fun init() {
        // 绑定MQTT服务
        val intent = Intent(this, MQTTService::class.java)
        bindService(intent, serviceConnection, BIND_AUTO_CREATE)

        binding.btnConnect.setOnClickListener { connectMQTT() }
        binding.btnDisconnect.setOnClickListener { disconnectMQTT() }
        binding.btnSubscribe.setOnClickListener { subscribeTopic() }
        binding.btnUnsubscribe.setOnClickListener { unsubscribeTopic() }
        binding.btnPublish.setOnClickListener { publishMessage() }
    }

    /**
     * 连接MQTT
     */
    private fun connectMQTT() {
        if (mServiceConnected && mqttService != null) {
            if (!mqttService!!.isConnected()) {
                appendLog("正在连接MQTT服务器...")
                mqttService!!.connect()
            } else {
                appendLog("已经连接到MQTT服务器")
            }
        } else {
            appendLog("MQTT服务未绑定")
        }
    }

    /**
     * 断开连接MQTT
     */
    private fun disconnectMQTT() {
        if (mServiceConnected && mqttService != null) {
            if (mqttService!!.isConnected()) {
                appendLog("正在断开MQTT连接...")
                mqttService!!.disconnect()
            } else {
                appendLog("当前未连接到MQTT服务器")
            }
        }
    }

    /**
     * 订阅主题
     */
    private fun subscribeTopic() {
        if (mServiceConnected && mqttService != null && mqttService!!.isConnected()) {
            val topic = binding.etTopic.text.toString().trim()
            if (topic.isNotEmpty()) {
                appendLog("订阅主题: $topic")
                mqttService!!.subscribe(topic)
            } else {
                Toast.makeText(this, "请输入主题名称", Toast.LENGTH_SHORT).show()
            }
        } else {
            appendLog("请先连接MQTT服务器")
        }
    }

    /**
     * 取消订阅主题
     */
    private fun unsubscribeTopic() {
        if (mServiceConnected && mqttService != null && mqttService!!.isConnected()) {
            val topic = binding.etTopic.text.toString().trim()
            if (topic.isNotEmpty()) {
                appendLog("取消订阅主题: $topic")
                mqttService!!.unsubscribe(topic)
            } else {
                Toast.makeText(this, "请输入主题名称", Toast.LENGTH_SHORT).show()
            }
        } else {
            appendLog("请先连接MQTT服务器")
        }
    }

    /**
     * 发布消息
     */
    private fun publishMessage() {
        if (mServiceConnected && mqttService != null && mqttService!!.isConnected()) {
            val topic = binding.etTopic.text.toString().trim()
            val message = binding.etMessage.text.toString().trim()

            if (topic.isNotEmpty() && message.isNotEmpty()) {
                appendLog("发布消息到主题 $topic: $message")
                mqttService!!.publish(topic, message)
            } else {
                Toast.makeText(this, "请输入主题名称和消息内容", Toast.LENGTH_SHORT).show()
            }
        } else {
            appendLog("请先连接MQTT服务器")
        }
    }

    /**
     * 显示日志
     *
     * @param message 日志内容
     */
    private fun appendLog(message: String) {
        runOnUiThread {
            val timestamp = DateFormat.getTimeInstance().format(Date())
            binding.tvLog.append("[$timestamp] $message\n")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (mServiceConnected) {
            unbindService(serviceConnection)
            mServiceConnected = false
        }
    }
}
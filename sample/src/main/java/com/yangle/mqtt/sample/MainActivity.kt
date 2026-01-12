package com.yangle.mqtt.sample

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.text.DateFormat
import java.util.Date

/**
 * 主页
 * <p>
 * Created by YangLe on 2026/1/9.
 * Website：http://www.yangle.tech
 */
class MainActivity : AppCompatActivity(), MQTTService.MQTTCallback {

    companion object {
        private const val TAG = "MainActivity"
    }

    private var mqttService: MQTTService? = null
    private var isBound = false

    private lateinit var btnConnect: Button
    private lateinit var btnDisconnect: Button
    private lateinit var btnSubscribe: Button
    private lateinit var btnUnsubscribe: Button
    private lateinit var btnPublish: Button
    private lateinit var tvStatus: TextView
    private lateinit var tvLog: TextView
    private lateinit var etTopic: EditText
    private lateinit var etMessage: EditText

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val binder = service as MQTTService.LocalBinder
            mqttService = binder.getService()
            mqttService?.setCallback(this@MainActivity)
            isBound = true
            Log.d(TAG, "MQTT服务绑定成功")
        }

        override fun onServiceDisconnected(name: ComponentName) {
            isBound = false
            mqttService = null
            Log.d(TAG, "MQTT服务断开绑定")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupClickListeners()
    }

    private fun initViews() {
        btnConnect = findViewById(R.id.btn_connect)
        btnDisconnect = findViewById(R.id.btn_disconnect)
        btnSubscribe = findViewById(R.id.btn_subscribe)
        btnUnsubscribe = findViewById(R.id.btn_unsubscribe)
        btnPublish = findViewById(R.id.btn_publish)

        tvStatus = findViewById(R.id.tv_status)
        tvLog = findViewById(R.id.tv_log)

        etTopic = findViewById(R.id.et_topic)
        etMessage = findViewById(R.id.et_message)
    }

    private fun setupClickListeners() {
        btnConnect.setOnClickListener { connectMQTT() }
        btnDisconnect.setOnClickListener { disconnectMQTT() }
        btnSubscribe.setOnClickListener { subscribeTopic() }
        btnUnsubscribe.setOnClickListener { unsubscribeTopic() }
        btnPublish.setOnClickListener { publishMessage() }
    }

    private fun connectMQTT() {
        if (isBound && mqttService != null) {
            if (!mqttService!!.isConnected.get()) {
                appendLog("正在连接MQTT服务器...")
                mqttService!!.connect()
            } else {
                appendLog("已经连接到MQTT服务器")
            }
        } else {
            appendLog("MQTT服务未绑定")
        }
    }

    private fun disconnectMQTT() {
        if (isBound && mqttService != null) {
            if (mqttService!!.isConnected.get()) {
                appendLog("正在断开MQTT连接...")
                mqttService!!.disconnect()
            } else {
                appendLog("当前未连接到MQTT服务器")
            }
        }
    }

    private fun subscribeTopic() {
        if (isBound && mqttService != null && mqttService!!.isConnected.get()) {
            val topic = etTopic.text.toString().trim()
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

    private fun unsubscribeTopic() {
        if (isBound && mqttService != null && mqttService!!.isConnected.get()) {
            val topic = etTopic.text.toString().trim()
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

    private fun publishMessage() {
        if (isBound && mqttService != null && mqttService!!.isConnected.get()) {
            val topic = etTopic.text.toString().trim()
            val message = etMessage.text.toString().trim()

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

    private fun appendLog(message: String) {
        runOnUiThread {
            val timestamp = DateFormat.getTimeInstance().format(Date())
            tvLog.append("[$timestamp] $message\n")
        }
    }

    private fun updateStatus(status: String) {
        runOnUiThread {
            tvStatus.text = "状态: $status"
        }
    }

    override fun onStart() {
        super.onStart()
        val intent = Intent(this, MQTTService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onStop() {
        super.onStop()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }

    override fun onConnectionSuccess() {
        appendLog("MQTT连接成功!")
        updateStatus("已连接")
        runOnUiThread {
            btnConnect.isEnabled = false
            btnDisconnect.isEnabled = true
            btnSubscribe.isEnabled = true
            btnUnsubscribe.isEnabled = true
            btnPublish.isEnabled = true
        }
    }

    override fun onConnectionFailure(error: String?) {
        appendLog("MQTT连接失败: $error")
        updateStatus("连接失败")
        runOnUiThread {
            btnConnect.isEnabled = true
            btnDisconnect.isEnabled = false
            btnSubscribe.isEnabled = false
            btnUnsubscribe.isEnabled = false
            btnPublish.isEnabled = false
        }
    }

    override fun onMessageReceived(topic: String?, message: String?) {
        appendLog("收到消息 - 主题: $topic, 内容: $message")
    }

    override fun onMessageDelivered(topic: String?) {
        appendLog("消息投递完成 - 主题: $topic")
    }
}
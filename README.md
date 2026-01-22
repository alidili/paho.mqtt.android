![封面](https://upload-images.jianshu.io/upload_images/3270074-b8f4c01f999b2714.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)

# 0.写在前面

2026年的第一篇文章，今天来讲讲基于MQTT协议的消息推送方案，MQTT（Message Queuing Telemetry Transport，消息队列遥测传输）是一种基于发布/订阅模式的轻量级消息传输协议，专门为低带宽、高延迟或不稳定的网络环境设计。

还记得在之前的文章中介绍过另一种轻量级的推送方案SSE（Server-Sent Events），这种方案是半双工的，只适用于服务器向客户端单向通信，适用的场景比较有限。如果想要实现全双工通信，没有聊天等复杂系统的需求，只是收发消息，除了WebSocket方案MQTT也是个不错的选择。

# 1.MQTT与WebSocket对比

一提到MQTT大部分介绍都会先说，MQTT是一种轻量级的消息传输协议，那为什么是轻量级的呢，一起看下：

- 协议头部开销小：
  - MQTT固定头部仅2字节，可变头部根据情况决定
  - 相比之下，WebSocket需要6–18字节以上，HTTP协议头部更是需要数百字节

- 二进制协议：
  - MQTT使用二进制格式传输数据，相比文本协议更加紧凑
  - 减少了数据传输量和解析开销

- 发布/订阅模式：
  - 支持一对多的消息分发，减少网络流量
  - 解耦了消息发送者和接收者，提高了系统灵活性

- QoS等级控制：
  - 提供三种消息质量等级：最多一次、至少一次、正好一次
  - 可根据业务需求选择合适的QoS，平衡可靠性和性能

**对比下MQTT与WebSocket：**

| 对比维度       | MQTT                  | WebSocket       |
| ---------- |-----------------------|-----------------|
| 协议类型       | 应用层消息协议               | 应用层传输协议         |
| 设计目标       | 低功耗、弱网、海量设备通信         | 实时双向通信          |
| 协议依赖       | 直接基于 TCP              | 基于 HTTP Upgrade |
| 通信模型       | 发布 / 订阅               | 点对点全双工          |
| 是否需要中间节点   | 需要 Broker             | 不需要             |
| 消息路由       | Topic 路由内建            | 需业务实现           |
| 最小消息开销     | 2 字节                  | 6–18 字节以上       |
| 心跳机制       | 协议内建                  | ping / pong     |
| 可靠性保障      | QoS 0 / 1 / 2         | 需业务实现           |
| 离线消息       | 支持（Session 机制）        | 不支持             |
| 断线重连       | 协议级支持                 | 需业务实现           |
| 弱网适应性      | 强                     | 一般              |
| 单连接资源消耗    | 低                     | 较高              |
| 大规模连接能力    | 百万级                   | 十万级以内           |
| 浏览器支持      | 需 MQTT over WebSocket | 原生支持            |
| Android 支持 | 支持                    | 支持              |
| 典型消息大小     | 小消息、高频                | 中等消息            |
| 典型应用场景     | IoT、设备通信、状态上报         | 在线聊天、网页实时通信     |
| 服务端控制能力    | 偏弱，依赖 Broker          | 强，完全自控          |

# 2.实现

## 2.1 依赖配置

在Android中比较常用的MQTT三方库是 **paho.mqtt.android**，但是因为这个库很多年没有维护了，导致在Android 14及以上版本的系统中运行会Crash，所以我Fork了一份代码，修改兼容了高版本系统，并上传到了JitPack仓库中。

在app根目录的build.gradle文件中加入依赖：

```
dependencies {
    implementation 'org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5'
    implementation 'com.github.alidili:paho.mqtt.android:1.0.0'
}
```

在项目根目录的build.gradle文件中增加如下配置：

```
allprojects {
    repositories {
        maven { url "https://jitpack.io" }
    }
}
```

## 2.2 MQTT服务类

为了方便使用，把MQTT相关的方法都写在了一个Service中，启动后先初始化MqttAndroidClient，然后设置一些回调监听，可以看到回调了3个方法：

- connectionLost: MQTT断开连接，可在此增加一些重连的逻辑

- messageArrived: 收到已订阅主题的消息

- deliveryComplete: 向特定主题发送消息成功

```
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
```

MQTT提供了三种QoS等级，在移动端推送场景中，通常选择QoS 1作为平衡点。：

- **QoS 0（最多一次）**：消息最多传递一次，可能丢失但不重复，适用于实时性要求高但允许丢消息的场景
- **QoS 1（至少一次）**：消息至少传递一次，保证不丢失但可能重复，适用于重要消息传递
- **QoS 2（正好一次）**：消息精确传递一次，保证不丢失不重复，但开销最大

对外暴露一些方法，MQTT连接、MQTT断开连接、订阅主题、取消订阅主题、发布消息。

```
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
```

## 2.3 UI调用类

完整的MainActivity逻辑如下，

```
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
```

# 3.测试

使用Eclipse Mosquitto做为Broker进行测试，安装完成后切换到安装目录，打开命令行窗口，执行下面的指令生成密码文件：

```
// username修改为你的用户名，执行后会提示输入两遍密码
mosquitto_passwd -c passwd_file username
```

然后打开mosquitto.conf配置，再最下面增加下面的配置：

```
listener 1883 0.0.0.0
password_file passwd_file
allow_anonymous false
```

然后执行：

```
mosquitto -v -c mosquitto.conf
```

到这里Broker就运行起来了，先启动一个订阅者，来接收客户端发送的消息：

```
.\mosquitto_sub -h 你的电脑IP -p 1883 -t android/mqtt/demo -u admin -P 123456
```

其中android/mqtt/demo是你要订阅的主题，admin和123456换成你修改的用户名和密码，在客户端发送一条消息，可以看到已经收到了：

![Broker接收消息](https://upload-images.jianshu.io/upload_images/3270074-1308f943e30f23e2.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)

然后再执行发布消息，可以看到客户端也接收到了消息：

![客户端接收消息](https://upload-images.jianshu.io/upload_images/3270074-393c442266526c70.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)

# 4.写在最后

GitHub地址：https://github.com/alidili/paho.mqtt.android

到这里，Android消息推送MQTT方案就介绍完了，如有问题可以给我留言评论或者在GitHub中提交Issues，谢谢！
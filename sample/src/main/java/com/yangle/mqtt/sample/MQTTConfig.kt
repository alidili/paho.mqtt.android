package com.yangle.mqtt.sample

/**
 * MQTT配置
 * <p>
 * Created by YangLe on 2026/1/9.
 * Website：http://www.yangle.tech
 */
object MQTTConfig {

    /**
     * MQTT服务器地址
     */
    const val BROKER_URL = "tcp://10.0.2.19:1883"

    /**
     * 客户端ID
     */
    const val CLIENT_ID = "android_mqtt_demo"

    /**
     * 用户名和密码
     */
    const val USERNAME = "admin"
    const val PASSWORD = "123456"

    /**
     * 连接选项
     */
    const val CONNECTION_TIMEOUT = 30
    const val KEEP_ALIVE_INTERVAL = 60
    const val CLEAN_SESSION = true

    /**
     * QoS级别
     */
    const val QOS_LEVEL = 1
}
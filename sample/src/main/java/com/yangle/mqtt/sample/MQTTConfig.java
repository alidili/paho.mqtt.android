package com.yangle.mqtt.sample;

/**
 * MQTT配置
 * <p>
 * Created by YangLe on 2026/1/9.
 * Website：http://www.yangle.tech
 */
public class MQTTConfig {

    /**
     * MQTT服务器地址
     */
    public static final String BROKER_URL = "mqtt://127.0.0.1:1883";

    /**
     * 客户端ID
     */
    public static final String CLIENT_ID = "android_mqtt_demo";

    /**
     * 用户名和密码
     */
    public static final String USERNAME = "admin";
    public static final String PASSWORD = "123456";

    /**
     * 连接选项
     */
    public static final int CONNECTION_TIMEOUT = 30;
    public static final int KEEP_ALIVE_INTERVAL = 60;
    public static final boolean CLEAN_SESSION = true;

    /**
     * QoS级别
     */
    public static final int QOS_LEVEL = 1;
}
package com.yuantian.cloudcontrol.config;


public class ControlConfig {

    // 云端服务器监听的 TCP 端口号
    public static final int SERVER_PORT = 50008;

    // 电机控制的目标设定值（转速 RPM）
    public static final float TARGET_SPEED = 1500.0f;

    // 硬件物理特性的 PWM 输出限幅
    public static final int MAX_PWM = 250;
    public static final int MIN_PWM = 0;
}
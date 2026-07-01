package com.yuantian.cloudcontrol.model;

/**
 * 工业级规范：被控对象（电机）的数据载体类（高精度网络诊断版）
 */
public class DeviceData {

    private float actualSpeed;
    private int outputPwm;
    private long timestamp;
    private int sequenceNumber;

    // 🌟 核心修改：将时延与抖动指标升级为 double 浮点型，单位统一为毫秒 (ms)，支持高精度小数显示
    private double latency;               // 精确单向延迟 (ms)
    private double rtt;                   // 精确往返时延 (ms)
    private long jitter;                  // 精确控制周期抖动 (ms)
    private double communicationInterval; // 精确通信时间间隔 (ms)

    private float packetLossRate;         // 累计应用层丢包率 (%)

    private double commandDispatchLatency;  // 指令下发延迟 (微秒 μs)

    private float targetFrequency;        // 理论控制频率 (Hz)
    private float actualFrequency;        // 实际采样频率 (Hz)
    private float cyclePacketLossRate;    // 周期丢包率 (%)

    // 🌟 新增：IEEE 1588 / NTP 级高精度硬件/内核纳秒级四时戳
    private long t1;                      // T1: 下位机发送状态包的本地纳秒时戳
    private long t2;                      // T2: 云端接收状态包的服务器本地纳秒时戳
    private long t3;                      // T3: 云端计算完PWM并准备下发指令的服务器本地纳秒时戳
    private long t4;                      // T4: 下位机接收到云端控制指令的本地纳秒时戳
    // 🌟 核心新增：物理层基础带宽与传输层 TCP 理论极限速率字段
    private double baseBandwidthKbps;
    private double tcpMaxThroughputMbps;

    public DeviceData() {
        this.timestamp = System.currentTimeMillis();
    }

    // ==========================================
    // Getter 和 Setter 方法
    // ==========================================
    public float getActualSpeed() {
        return actualSpeed;
    }

    public void setActualSpeed(float actualSpeed) {
        this.actualSpeed = actualSpeed;
    }

    public int getOutputPwm() {
        return outputPwm;
    }

    public void setOutputPwm(int outputPwm) {
        this.outputPwm = outputPwm;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    public void setSequenceNumber(int sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    // 🌟 属性类型变更对应的 Setter/Getter 升级
    public double getLatency() {
        return latency;
    }

    public void setLatency(double latency) {
        this.latency = latency;
    }

    public double getRtt() {
        return rtt;
    }

    public void setRtt(double rtt) {
        this.rtt = rtt;
    }

    public long getJitter() {
        return jitter;
    }

    public void setJitter(long jitter) {
        this.jitter = jitter;
    }

    public double getCommunicationInterval() {
        return communicationInterval;
    }

    public void setCommunicationInterval(double communicationInterval) {
        this.communicationInterval = communicationInterval;
    }

    public float getPacketLossRate() {
        return packetLossRate;
    }

    public void setPacketLossRate(float packetLossRate) {
        this.packetLossRate = packetLossRate;
    }

    public double getCommandDispatchLatency() {
        return commandDispatchLatency;
    }

    public void setCommandDispatchLatency(double commandDispatchLatency) {
        this.commandDispatchLatency = commandDispatchLatency;
    }

    public float getTargetFrequency() {
        return targetFrequency;
    }

    public void setTargetFrequency(float targetFrequency) {
        this.targetFrequency = targetFrequency;
    }

    public float getActualFrequency() {
        return actualFrequency;
    }

    public void setActualFrequency(float actualFrequency) {
        this.actualFrequency = actualFrequency;
    }

    public float getCyclePacketLossRate() {
        return cyclePacketLossRate;
    }

    public void setCyclePacketLossRate(float cyclePacketLossRate) {
        this.cyclePacketLossRate = cyclePacketLossRate;
    }

    // 🌟 新增时戳的 Setter/Getter
    public long getT1() {
        return t1;
    }

    public void setT1(long t1) {
        this.t1 = t1;
    }

    public long getT2() {
        return t2;
    }

    public void setT2(long t2) {
        this.t2 = t2;
    }

    public long getT3() {
        return t3;
    }

    public void setT3(long t3) {
        this.t3 = t3;
    }

    public long getT4() {
        return t4;
    }

    public void setT4(long t4) {
        this.t4 = t4;
    }

    public double getBaseBandwidthKbps() {
        return baseBandwidthKbps;
    }

    public void setBaseBandwidthKbps(double baseBandwidthKbps) {
        this.baseBandwidthKbps = baseBandwidthKbps;
    }

    public double getTcpMaxThroughputMbps() {
        return tcpMaxThroughputMbps;
    }

    public void setTcpMaxThroughputMbps(double tcpMaxThroughputMbps) {
        this.tcpMaxThroughputMbps = tcpMaxThroughputMbps;
    }
}


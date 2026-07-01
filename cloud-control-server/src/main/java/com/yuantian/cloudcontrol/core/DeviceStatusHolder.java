package com.yuantian.cloudcontrol.core;

import com.yuantian.cloudcontrol.model.DeviceData;
import com.yuantian.cloudcontrol.model.DeviceDataLog;
import com.yuantian.cloudcontrol.repository.DeviceDataLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 工业级规范：云端全局状态共享中心（支持跨帧 RTT 闭环计算与高并发落盘）
 */
@Component
public class DeviceStatusHolder {

    private final AtomicReference<DeviceData> latestData = new AtomicReference<>(new DeviceData());

    // 动态通信速率（Hz）
    private double targetFrequency = 20.0;

    // 网络遥测滑动窗口变量
    private int lastReceivedSequence = -1;
    private int totalLostPackets = 0;
    private int cyclePacketCount = 0;
    private int cycleLostPackets = 0;
    private static final int CYCLE_WINDOW_SIZE = 100;

    // 高并发安全缓冲队列，用于暂存轨迹数据
    private final ConcurrentLinkedQueue<DeviceDataLog> dataBufferQueue = new ConcurrentLinkedQueue<>();
    private final DeviceDataLogRepository logRepository;
    private volatile boolean receivePaused = false;
    ////////////////////////////////////////////////////////////////////////////////////////////
    // --- 新增：带宽与极限速率统计指标 ---
    private volatile double baseBandwidthKbps = 0.0;
    private volatile double tcpMaxThroughputMbps = 0.0;

    private long lastWindowTimestampNano = System.nanoTime();
    private long accumulatedBytes = 0;
    private static final long WINDOW_DURATION_NANO = 1_000_000_000L; // 1秒统计窗口
    private static final double TCP_WINDOW_SIZE_BITS = 64.0 * 1024.0 * 8.0; // 默认64KB缓冲区


    @Autowired
    public DeviceStatusHolder(DeviceDataLogRepository logRepository, @Lazy ControlEngine controlEngine) {
        this.logRepository = logRepository;
    }

    public void setReceivePaused(boolean paused) {
        this.receivePaused = paused;
        System.out.println("[状态中心] 网闸状态变更：是否暂停接收 = " + paused);
    }

    public boolean isReceivePaused() {
        return this.receivePaused;
    }

    public synchronized void setTargetFrequency(double hz) {
        if (hz > 0) {
            this.targetFrequency = hz;
            System.out.println("[状态中心] 网页端修改通信速率为: " + hz + " Hz");
        }
    }

    public double getTargetFrequency() {
        return this.targetFrequency;
    }

    /**
     * 更新当前最新数据，执行跨帧 RTT 解析
     */
    public void updateLatestData(DeviceData newData) {
        DeviceData oldData = this.latestData.get();

        newData.setTargetFrequency((float) this.targetFrequency);

        // 1. 基于系统时钟精确解算通信时间间隔与 Jitter
        if (oldData != null && oldData.getTimestamp() > 0) {
            long intervalNano = newData.getT2() - oldData.getT2();
            if (intervalNano > 0) {
                double intervalMs = intervalNano / 1_000_000.0;
                newData.setCommunicationInterval(intervalMs);
                newData.setActualFrequency((float) (1000.0 / intervalMs));

                double currentJitter = Math.abs(intervalMs - oldData.getCommunicationInterval());
                long smoothedJitter = (long) (0.8 * oldData.getJitter() + 0.2 * currentJitter);
                newData.setJitter(smoothedJitter);
            }
        }

        // 🌟 2. 核心突破：跨帧解算上一帧的高精度 RTT 与 绝对单向时延
        // newData.getT4() 存放的是上位机传上来的 LastT4
        if (oldData != null && oldData.getT1() > 0 && oldData.getT3() > 0 && newData.getT4() > 0) {
            // RTT = (LastT4 - 上一帧T1) - (上一帧T3 - 上一帧T2)
            long pureNetworkNano = (newData.getT4() - oldData.getT1()) - (oldData.getT3() - oldData.getT2());
            if (pureNetworkNano < 0) pureNetworkNano = 0; // 边界物理保护

            double preciseRtt = pureNetworkNano / 1_000_000.0; // 转换为毫秒小数
            newData.setRtt(preciseRtt);
            newData.setLatency(preciseRtt / 2.0);
        } else {
            // 兜底机制：如果是第一帧或者数据断流无法闭环，沿用上一帧历史数据
            newData.setRtt(oldData != null ? oldData.getRtt() : 0.0);
            newData.setLatency(oldData != null ? oldData.getLatency() : 0.0);
        }

        // 3. 计算丢包率
        if (lastReceivedSequence != -1) {
            int currentSeq = newData.getSequenceNumber();
            if (currentSeq > (lastReceivedSequence + 1)) {
                int lost = currentSeq - lastReceivedSequence - 1;
                totalLostPackets += lost;
                cycleLostPackets += lost;
            }
            float totalLossRate = currentSeq > 0 ? ((float) totalLostPackets / currentSeq) * 100 : 0;
            newData.setPacketLossRate(totalLossRate);

            cyclePacketCount++;
            if (cyclePacketCount >= CYCLE_WINDOW_SIZE) {
                float cycleLossRate = ((float) cycleLostPackets / (cyclePacketCount + cycleLostPackets)) * 100;
                newData.setCyclePacketLossRate(cycleLossRate);
                cyclePacketCount = 0;
                cycleLostPackets = 0;
            } else {
                newData.setCyclePacketLossRate(oldData != null ? oldData.getCyclePacketLossRate() : 0);
            }
        }
        this.lastReceivedSequence = newData.getSequenceNumber();
        ////////////////////////////////////////////////////////////////////////////
        // 2. 🌟 核心重构：自适应基础带宽速率解算
        // 从借道的 timestamp 中提取当前帧的物理字节数
        long currentFrameBytes = newData.getTimestamp();
        // 恢复正确的系统毫秒时间戳给大屏
        newData.setTimestamp(System.currentTimeMillis());
        this.accumulatedBytes += currentFrameBytes;

        long currentNano = System.nanoTime();
        if (currentNano - lastWindowTimestampNano >= WINDOW_DURATION_NANO) {
            double durationSeconds = (currentNano - lastWindowTimestampNano) / 1_000_000_000.0;
            // 基础带宽速率公式：Bytes * 8 / (Seconds * 1000) -> Kbps
            this.baseBandwidthKbps = (this.accumulatedBytes * 8.0) / (durationSeconds * 1000.0);

            this.accumulatedBytes = 0;
            this.lastWindowTimestampNano = currentNano;
        }

        // 3. 🌟 核心重构：传输层 TCP 理论极限速率解算 (Mathis Formula 变体)
        double rttSeconds = newData.getRtt() / 1000.0; // 转化为秒
        if (rttSeconds > 0.0001) {
            // TCP极限速率公式：WindowSize(bits) / RTT(seconds) -> Mbps
            this.tcpMaxThroughputMbps = (TCP_WINDOW_SIZE_BITS / rttSeconds) / 1_000_000.0;
        } else {
            this.tcpMaxThroughputMbps = 100.0; // 局域网/回环极低延迟下的物理链路层上限保护
        }

        // 装配入最新的实体快照中
        newData.setBaseBandwidthKbps(this.baseBandwidthKbps);
        newData.setTcpMaxThroughputMbps(this.tcpMaxThroughputMbps);
        ///////////////////////////////////////////////////////////////////

        // 4. 更新内存快照 (当前 newData 成为下一次的 oldData)
        this.latestData.set(newData);

        // 5. 异步推入缓冲队列用于批量落盘
        DeviceDataLog log = new DeviceDataLog();
        log.setSaveTime(LocalDateTime.now());
        log.setActualSpeed(newData.getActualSpeed());
        log.setOutputPwm(newData.getOutputPwm());
        dataBufferQueue.offer(log);


        ////////////////////////////////////////////////////////////////////////////////////

    }

    public DeviceData getLatestData() {
        return this.latestData.get();
    }

    @Scheduled(fixedDelay = 500)
    public void batchSaveToDatabase() {
        if (dataBufferQueue.isEmpty()) {
            return;
        }

        List<DeviceDataLog> batchList = new ArrayList<>();
        DeviceDataLog logNode;

        while ((logNode = dataBufferQueue.poll()) != null) {
            batchList.add(logNode);
        }

        if (!batchList.isEmpty()) {
            logRepository.saveAll(batchList);
        }
    }
}
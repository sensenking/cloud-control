package com.yuantian.cloudcontrol.network;

import com.yuantian.cloudcontrol.core.ControlEngine;
import com.yuantian.cloudcontrol.model.DeviceData;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@ChannelHandler.Sharable
public class NettyServerHandler extends SimpleChannelInboundHandler<String> {

    private final ControlEngine controlEngine;

    @Autowired
    public NettyServerHandler(ControlEngine controlEngine) {
        this.controlEngine = controlEngine;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String msg) throws Exception {
        // 🌟 网闸前置判定：若暂停接收则直接丢弃
        if (controlEngine.getDeviceStatusHolder().isReceivePaused()) {
            System.out.print("pause");
            return;
        }

        // 🌟 T2: 当前帧到达服务器网络栈的瞬间戳
        long t2ServerReceived = System.nanoTime();
        long readTimestamp = System.currentTimeMillis();

        String inputLine = msg.trim();
        if (inputLine.isEmpty() || "ok".equalsIgnoreCase(inputLine)) return;

        try {
            String[] parts = inputLine.split(",");

            // 🌟 严格对齐最新的 4 字段协议: Seq, T1, Speed, LastT4
            if (parts.length < 4) {
                System.err.println("[Netty网络中心] 丢弃非法报文，期待4字段: " + inputLine);
                return;
            }
            //T1：上位机发送上行帧瞬间（上位机本地）→ 打包上传云端
            //T2：云端收到上行帧瞬间（云端本地）→ 云端保存，下行原路带回
            //T3：云端下发控制帧写入缓冲区瞬间（云端本地）→ 云端保存，下行原路带回
            //T4：上位机收到云端下行帧瞬间（上位机本地）

            DeviceData deviceData = new DeviceData();

            // 1. 解析上行工业控制字段
            deviceData.setSequenceNumber(Integer.parseInt(parts[0]));

            // T1: 当前帧下位机/上位机发出的纳秒戳
            long t1ClientDispatched = Long.parseLong(parts[1]);
            deviceData.setT1(t1ClientDispatched);
            deviceData.setT2(t2ServerReceived); // 存入当前帧 T2
            deviceData.setActualSpeed(Float.parseFloat(parts[2]));
            deviceData.setTimestamp(readTimestamp);

            // 🌟 2. 提取上一帧的 T4 (LastT4)，暂存在 deviceData 供 StatusHolder 跨帧解算
            long lastT4 = Long.parseLong(parts[3]);
            deviceData.setT4(lastT4);

            // 3. 驱动闭环控制算法引擎计算 PWM
            int targetPwm = controlEngine.processControlLoop(deviceData);
            deviceData.setOutputPwm(targetPwm);

            // 🌟 T3: 当前帧算法决策完毕，下发前的纳秒戳
            long t3ServerDispatched = System.nanoTime();
            deviceData.setT3(t3ServerDispatched);

            // 获取期望的 Hz 速率
            double currentHz = controlEngine.getDeviceStatusHolder().getTargetFrequency();

            // 🌟 4. 极简下发报文拼装: TargetPwm, CurrentHz\n
            String responseFrame = String.format("%d,%.1f\n", targetPwm, currentHz);

            // 5. 异步下发并交由共享状态中心更新内存
            ctx.writeAndFlush(responseFrame).addListener((ChannelFutureListener) future -> {
                long afterWriteTime = System.nanoTime();
                double dispatchLatencyMicros = (long) Math.ceil((afterWriteTime - t3ServerDispatched) / 1000.0);
                deviceData.setCommandDispatchLatency(dispatchLatencyMicros);

                if (future.isSuccess()) {
                    // 灌入大屏共享内存，触发跨帧 RTT 解算
                   // controlEngine.getDeviceStatusHolder().updateLatestData(deviceData);
                    //////////////////////////////////////////////////////////////////
                    int frameBytes = inputLine.length() + 54;
                    // 将其临时借道 timestamp 或字段传递（此处利用 timestamp 存储物理帧大小，或者直接由状态中心提取）
                    deviceData.setTimestamp(frameBytes);

                    // 灌入大屏共享内存，触发跨帧 RTT 与速率解算
                    controlEngine.getDeviceStatusHolder().updateLatestData(deviceData);
                } else {
                    System.err.println("[Netty网络中心] 数据下发失败: " + future.cause().getMessage());
                }
            });

        } catch (NumberFormatException e) {
            System.err.println("[Netty网络中心] 实时数据解析异常: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("[Netty网络中心] 运行时控制回路未知异常: " + e.getMessage());
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        System.err.println("[Netty网络中心] 远端物理通道异常关闭: " + cause.getMessage());
        ctx.close();
    }
}
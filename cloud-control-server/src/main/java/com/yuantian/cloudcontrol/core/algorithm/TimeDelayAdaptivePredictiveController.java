package com.yuantian.cloudcontrol.core.algorithm;

import com.yuantian.cloudcontrol.core.ControlEngine;
import com.yuantian.cloudcontrol.model.DeviceData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工业级规范：智能时延自适应预测控制器（Time-Delay Adaptive Predictive Controller）
 * 融合了一阶直流电机物理特征模型(K, T)、网络动态QoS指标回溯以及残差自适应修正机制
 */
@Component("timeDelayAdaptivePredictiveController")
public class TimeDelayAdaptivePredictiveController implements BaseController {

    // 1. 🌟 前端参数绑定矩阵 (全 volatile 并发保护，严格对应您的滑块及表单变量名)
    private volatile float kp = 0.3f;
    private volatile float predictHorizon = 3.0f; // 预测时域超前增益系数
    private volatile float adaptiveGain = 0.002f; // 物理模型残差自适应修正步长
    private volatile float delaySteps = 4.0f;     // 前端传入的基准时延步数 (在无遥测数据时作为安全降级兜底)

    // 2. 🌟 新增：直流电机物理对象特征参数 (支持大屏矩阵动态调谐，提供更精准的惯性响应预测)
    private volatile float modelK = 1.0f;          // 电机系统增益 (1单位PWM对应的稳定转速RPM变化量)
    private volatile float modelT = 0.12f;         // 电机机械时间常数 (秒)

    // 3. 内部预测器状态变量 (由 Netty 线程高频独占更新，无需 volatile)
    private float predictedSpeed = 0.0f;          // 内部一阶物理模型超前预测速度
    private float modelErrorIntegral = 0.0f;       // 模型预测残差的累积积分项
    private float lastPwmOutput = 0.0f;            // 上一时刻控制量，用于物理特征离散化递推

    // 4. 🌟 历史控制输出时间队列 (代替原ArrayBlockingQueue锁结构，完全杜绝时变公网抖动导致的预测脱节)
    private final LinkedList<PwmHistoryNode> pwmHistoryQueue = new LinkedList<>();

    // 5. 跨模块引用，用以安全提取 Netty 回路中的高精度遥测快照
    private final ControlEngine controlEngine;

    @Autowired
    public TimeDelayAdaptivePredictiveController(@Lazy ControlEngine controlEngine) {
        this.controlEngine = controlEngine;
    }

    /**
     * 内部高并发数据节点：将历史控制量与产生时的纳秒时间戳强绑定
     */
    private static class PwmHistoryNode {
        long timestampNano; // 灌入网络栈的时刻戳
        float pwmValue;     // 对应的控制输出

        PwmHistoryNode(long timestampNano, float pwmValue) {
            this.timestampNano = timestampNano;
            this.pwmValue = pwmValue;
        }
    }

    /**
     * 核心计算闭环：由 Netty 网络层高频触发
     */
    @Override
    public float calculate(float targetSpeed, float actualSpeed) {
        // --- 🌟 步骤一：提取网络遥测回路中的动态指标 ---
        DeviceData latestLog = controlEngine.getDeviceStatusHolder().getLatestData();

        // 1. 控制步长 dt (秒)：由当前运行速率 Hz 动态倒数计算
        double targetHz = controlEngine.getDeviceStatusHolder().getTargetFrequency();
        float dt = (float) (1.0 / (targetHz > 0 ? targetHz : 20.0));

        // 2. 动态单向纯时延 tau (秒)：提取当前帧网络解算出的 latency
        double networkLatencyMs = latestLog != null ? latestLog.getLatency() : 0.0;

        float tau;
        if (networkLatencyMs > 0) {
            tau = (float) (networkLatencyMs / 1000.0); // 正常遥测状态：使用实时单向时延
        } else {
            tau = delaySteps * dt; // 降级策略：若遥测数据未就绪，使用前端滑块指定的(步数*步长)作为兜底
        }

        long currentNano = System.nanoTime();

        // --- 🌟 步骤二：时延自适应回溯 (从缓冲序列中检索纯迟延对应的历史控制量) ---
        float delayedPwm = lastPwmOutput; // 默认降级值
        long targetHistoryNano = currentNano - (long)(tau * 1_000_000_000L); // 计算理论上由于时延导致的控制量发送锚点

        // 限制队列容量防止内存泄漏
        while (pwmHistoryQueue.size() > 500) {
            pwmHistoryQueue.removeFirst();
        }

        // 精准匹配距离历史延迟时间点最近的控制量输出
        for (PwmHistoryNode node : pwmHistoryQueue) {
            if (node.timestampNano >= targetHistoryNano) {
                delayedPwm = node.pwmValue;
                break;
            }
        }

        // --- 🌟 步骤三：带物理特征模型 (K, T) 的超前速度预测与残差修正 ---
        // 1. 估算系统真实残差 (下位机实际反馈转速 - 内部历史预测速度)
        float currentModelError = actualSpeed - predictedSpeed;

        // 2. 物理预测自适应残差修正项积分
        modelErrorIntegral += currentModelError * dt;
        modelErrorIntegral = Math.max(-100.0f, Math.min(100.0f, modelErrorIntegral)); // 积分限幅防止饱和

        // 3. 结合一阶物理对象特征差分推演下一个周期的超前预测速度
        // 差分公式：y(k) = alpha * y(k-1) + (1-alpha) * modelK * u_delayed + 自适应修正项
        float alpha = (float) Math.exp(-dt / (modelT > 0 ? modelT : 0.01f));
        predictedSpeed = (alpha * predictedSpeed) + ((1.0f - alpha) * modelK * delayedPwm)
                + (adaptiveGain * modelErrorIntegral);

        // --- 🌟 步骤四：预测时域闭环纠偏与主控制量解算 ---
        // 利用超前预测速度 predictedSpeed 代替具有严重时延滞后的 actualSpeed 参与反馈
        float predictiveError = targetSpeed - predictedSpeed;

        // 结合预测时域超前增益 predictHorizon 增强对趋势的捕捉
        float rawPwm = kp * predictiveError * predictHorizon;

        // --- 🌟 步骤五：物理边界安全限幅与状态滑移 ---
        float finalPwm = Math.max(0.0f, Math.min(250.0f, rawPwm));
        lastPwmOutput = finalPwm;

        // 将带纳秒时间戳的当前最新控制输出压入历史记录队列
        pwmHistoryQueue.addLast(new PwmHistoryNode(currentNano, finalPwm));

        return finalPwm;
    }

    /**
     * 实现 BaseController 参数接收接口：支持前端参数矩阵及滑块的动态解析注入
     */
    @Override
    public void updateParams(Map<String, Object> params) {
        try {
            // 原有前端绑定的三大核心滑块参数
            if (params.containsKey("kp")) this.kp = Float.parseFloat(params.get("kp").toString());
            if (params.containsKey("predictHorizon")) this.predictHorizon = Float.parseFloat(params.get("predictHorizon").toString());
            if (params.containsKey("adaptiveGain")) this.adaptiveGain = Float.parseFloat(params.get("adaptiveGain").toString());
            if (params.containsKey("delaySteps")) this.delaySteps = Float.parseFloat(params.get("delaySteps").toString());

            // 🌟 扩展支持：允许在大屏上实时优化电机的物理特征参数 (K, T)
            if (params.containsKey("modelK")) this.modelK = Float.parseFloat(params.get("modelK").toString());
            if (params.containsKey("modelT")) this.modelT = Float.parseFloat(params.get("modelT").toString());
            if (params.containsKey("Model_K")) this.modelK = Float.parseFloat(params.get("Model_K").toString());
            if (params.containsKey("Model_T")) this.modelT = Float.parseFloat(params.get("Model_T").toString());

            System.out.println("[自适应预测控制器] 前端参数矩阵成功调谐同步。");
        } catch (Exception e) {
            System.err.println("[自适应预测控制器] 解析参数矩阵异常: " + e.getMessage());
        }
    }

    /**
     * 实现 BaseController 参数回读接口：将数据导出给 CloudControlController 供大屏同步显示
     */
    @Override
    public Map<String, Object> getCurrentParams() {
        Map<String, Object> paramsMap = new ConcurrentHashMap<>();
        paramsMap.put("kp", kp);
        paramsMap.put("predictHorizon", predictHorizon);
        paramsMap.put("adaptiveGain", adaptiveGain);
        paramsMap.put("delaySteps", delaySteps);
        paramsMap.put("modelK", modelK);
        paramsMap.put("modelT", modelT);
        return paramsMap;
    }

    /**
     * 实现 BaseController 接口的抽象方法：安全切入/复位逻辑
     */
    @Override
    public synchronized void reset() {
        try {
            pwmHistoryQueue.clear();
            predictedSpeed = 0.0f;
            modelErrorIntegral = 0.0f;
            lastPwmOutput = 0.0f;
            System.out.println("[自适应预测控制器] 控制拓扑状态成功复位，历史轨迹完全清洗。");
        } catch (Exception e) {
            System.err.println("[自适应预测控制器] 复位异常: " + e.getMessage());
        }
    }
}
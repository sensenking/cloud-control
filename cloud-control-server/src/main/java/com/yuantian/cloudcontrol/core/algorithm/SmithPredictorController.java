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
 * 工业级规范：自适应动态网络时延史密斯预估控制器 (Adaptive Network Smith Predictor)
 * 完美适配前端参数矩阵调谐表单，动态绑定 Netty 遥测回路控制指标
 */
@Component("smithPredictorController")
public class SmithPredictorController implements BaseController {

    // 1. 前级主 PID 控制器参数 (增加 volatile 并发保护，名称与前端表单键名大小写严格对齐)
    private volatile float kp = 0.25f;
    private volatile float ki = 0.08f;
    private volatile float kd = 0.02f;

    // 2. 被控对象(电机驱动系统)数学模型参数: G(s) = K / (T*s + 1)
    private volatile float modelK = 1.0f;  // 模型增益 (对应前端 modelK / Model_K)
    private volatile float modelT = 0.12f; // 模型时间常数 (秒，对应前端 modelT / Model_T)

    // 3. 史密斯内部模型计算状态变量 (受高频 Netty 线程独占调用，无需 volatile)
    private float y_m_noDelay = 0.0f;   // 无滞后内部模型输出速度快照 (RPM)
    private float pidIntegral = 0.0f;   // 前级 PID 积分累加器
    private float lastPidError = 0.0f;  // 上一次前级 PID 的控制误差
    private float lastPwmOutput = 0.0f; // 历史控制量，用于模型输入迭代

    // 4. 动态纯迟延时间步长队列 (历史状态时间缓冲器)
    // 存储无延迟模型输出的预测历史序列，用以在时变网络中抽取对应时延下的预估输出
    private final LinkedList<ModelHistoryNode> modelHistoryQueue = new LinkedList<>();

    // 5. 跨模块引擎引用，用于实时抽取网络遥测回路中的动态指标
    private final ControlEngine controlEngine;

    @Autowired
    public SmithPredictorController(@Lazy ControlEngine controlEngine) {
        this.controlEngine = controlEngine;
    }

    /**
     * 内部数据节点：关联高精度时间戳记录模型的理论输出值
     */
    private static class ModelHistoryNode {
        long timestampNano; // 记录时刻的纳秒时间戳
        float value;        // 对应的无延迟输出速度

        ModelHistoryNode(long timestampNano, float value) {
            this.timestampNano = timestampNano;
            this.value = value;
        }
    }

    /**
     * 核心计算闭环：由高频 Netty 异步网络接收回路触发 (NettyServerHandler -> ControlEngine 调用)
     */
    @Override
    public float calculate(float targetSpeed, float actualSpeed) {
        // --- 🌟 步骤一：提取网络遥测回路中的动态指标 ---
        DeviceData latestLog = controlEngine.getDeviceStatusHolder().getLatestData();

        // 1. 控制步长 dt (秒)：由当前运行速率 Hz 动态倒数计算，实时随前端变频而调整
        double targetHz = controlEngine.getDeviceStatusHolder().getTargetFrequency();
        float dt = (float) (1.0 / (targetHz > 0 ? targetHz : 20.0));

        // 2. 单向纯时延 tau (秒)：提取当前帧网络单向时延量（单位为 ms，转换为秒）
        // 完美利用您 Netty 回路解算出的动态 latency 指标
        double networkLatencyMs = latestLog != null ? latestLog.getLatency() : 0.0;

        // 防御性安全边界策略：若遥测数据未就绪或出现公网断连异常，提供 25ms 基础传输时延保护
        float tau = networkLatencyMs > 0 ? (float) (networkLatencyMs / 1000.0) : 0.025f;

        long currentNano = System.nanoTime();

        // --- 🌟 步骤二：无延迟内部对象模型离散化推演 ---
        // 被控对象一阶惯性环节离散化差分公式：y_m(k) = alpha * y_m(k-1) + (1 - alpha) * modelK * u(k-1)
        // 扩展系数 alpha = exp(-dt / modelT)
        float alpha = (float) Math.exp(-dt / (modelT > 0 ? modelT : 0.01f));
        y_m_noDelay = (alpha * y_m_noDelay) + ((1.0f - alpha) * modelK * lastPwmOutput);

        // 将当前的无延迟预测结果压入纯迟延缓冲队列
        modelHistoryQueue.addLast(new ModelHistoryNode(currentNano, y_m_noDelay));

        // --- 🌟 步骤三：时延步长自适应匹配 (从缓冲序列中检索纯迟延对应的预估值) ---
        float y_m_withDelay = y_m_noDelay; // 缺省降级值
        long targetHistoryNano = currentNano - (long)(tau * 1_000_000_000L); // 理论上延迟前的纳秒时间锚点

        // 清除队列中过于陈旧(超过两倍最大时延窗口)的数据，防止高频下内存泄漏
        while (modelHistoryQueue.size() > 500) {
            modelHistoryQueue.removeFirst();
        }

        // 自适应寻找距离历史延迟时间点最近的预估模型数据作为“带迟延模型输出值”
        for (ModelHistoryNode node : modelHistoryQueue) {
            if (node.timestampNano >= targetHistoryNano) {
                y_m_withDelay = node.value;
                break;
            }
        }

        // --- 🌟 步骤四：史密斯误差解算与前级主 PID 闭环 ---
        // 史密斯预估反馈核心公式：e_smith = 目标值 - [实际反馈值 - 带迟延预估值 + 无迟延预估值]
        float smithFeedbackError = targetSpeed - (actualSpeed - y_m_withDelay + y_m_noDelay);

        // 1. 比例项
        float pTerm = kp * smithFeedbackError;

        // 2. 积分项 (工业级改进：加入积分分离策略，起动或大幅切速时挂起积分，防止大范围超调)
        if (Math.abs(smithFeedbackError) < 400.0f) {
            pidIntegral += smithFeedbackError * dt;
        } else {
            pidIntegral = 0.0f; // 大误差下清除历史累积，防止超调
        }

        // 积分抗饱和抗溜幅限制
        pidIntegral = Math.max(-50.0f, Math.min(50.0f, pidIntegral));
        float iTerm = ki * pidIntegral;

        // 3. 微分项 (微分时间步长与动态 dt 强绑定)
        float dTerm = dt > 0 ? kd * ((smithFeedbackError - lastPidError) / dt) : 0.0f;
        lastPidError = smithFeedbackError;

        // 4. 总控制输出合成
        float rawPwm = pTerm + iTerm + dTerm;

        // --- 🌟 步骤五：下位机物理边界安全限幅与控制量迭代 ---
        // 严格遵循您的下位机驱动器 0 ~ 250 PWM 输入边界
        float finalPwmOutput = Math.max(0.0f, Math.min(250.0f, rawPwm));
        lastPwmOutput = finalPwmOutput; // 更新下一次迭代输入量

        return finalPwmOutput;
    }

    /**
     * 实现 BaseController 参数接收接口：解析并注入来自网页端下发的动态参数
     * 完美兼容大小写，防止前端传参格式（如 modelK 或 Model_K）不一致导致的注入失败
     */
    @Override
    public void updateParams(Map<String, Object> params) {
        try {
            // 前级 PID 系数实时修改
            if (params.containsKey("kp")) this.kp = Float.parseFloat(params.get("kp").toString());
            if (params.containsKey("ki")) this.ki = Float.parseFloat(params.get("ki").toString());
            if (params.containsKey("kd")) this.kd = Float.parseFloat(params.get("kd").toString());

            // 被控模型参数 K, T 动态修改 (兼顾大小写与下划线，全面兼容前端命名)
            if (params.containsKey("modelK")) this.modelK = Float.parseFloat(params.get("modelK").toString());
            if (params.containsKey("modelT")) this.modelT = Float.parseFloat(params.get("modelT").toString());
            if (params.containsKey("Model_K")) this.modelK = Float.parseFloat(params.get("Model_K").toString());
            if (params.containsKey("Model_T")) this.modelT = Float.parseFloat(params.get("Model_T").toString());

            System.out.println("[史密斯预估器] 网页端控制台参数调谐成功。当前内部参数 -> " + getCurrentParams());
        } catch (Exception e) {
            System.err.println("[史密斯预估器] 动态解析 Web 调谐参数异常: " + e.getMessage());
        }
    }

    /**
     * 实现 BaseController 参数回读接口：将当前参数集转化为 Map 提供给 CloudControlController
     * 便于大屏控制台实时同步、刷新当前系统的参数数值，防止滑块跳变
     */
    @Override
    public Map<String, Object> getCurrentParams() {
        Map<String, Object> paramsMap = new ConcurrentHashMap<>();
        paramsMap.put("kp", kp);
        paramsMap.put("ki", ki);
        paramsMap.put("kd", kd);
        paramsMap.put("modelK", modelK);
        paramsMap.put("modelT", modelT);
        return paramsMap;
    }
    /**
     * 实现 BaseController 接口的抽象方法：环路状态重置引擎
     * 🌟 核心价值：在网页端进行算法热切换的瞬间，彻底清洗历史脏数据，防止切换冲击震荡
     */
    @Override
    public synchronized void reset() {
        try {
            // 1. 清空历史时延纳秒匹配缓冲队列，防止上一算法残留的动态指标干扰史密斯推演
            modelHistoryQueue.clear();

            // 2. 将无滞后内部模型状态、历史控制输入、前级 PID 积分项和误差项全部归零
            y_m_noDelay = 0.0f;
            pidIntegral = 0.0f;
            lastPidError = 0.0f;
            lastPwmOutput = 0.0f;

            System.out.println("[史密斯预估器] 核心算法拓扑状态重置成功，历史缓冲队列已完全阻断清洗。");
        } catch (Exception e) {
            System.err.println("[史密斯预估器] 执行重置复位逻辑时发生异常: " + e.getMessage());
        }
    }
}
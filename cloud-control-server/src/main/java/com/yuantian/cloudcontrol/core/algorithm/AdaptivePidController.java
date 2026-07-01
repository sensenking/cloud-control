package com.yuantian.cloudcontrol.core.algorithm;

import org.springframework.stereotype.Component;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工业级规范：基于最速下降法（梯度下降）与移动窗口平滑滤波的自适应 PID 控制器
 * 利用瞬时误差求偏导，通过前后帧差分动态辨识 Jacobian 灵敏度，实现 Kp、Ki 独立闭环优化
 */
@Component("adaptivePidController")
public class AdaptivePidController implements BaseController {

    // 1. 🌟 前端参数矩阵解耦绑定 (全 volatile 保护，严格对应拆分后的各自学习率/步长)
    private volatile float kp = 0.2f;
    private volatile float ki = 0.05f;
    private volatile float kd = 0.01f; // Kd 保持传统固定调谐模式

    private volatile float kpLearningRate = 0.005f; // Kp 最速下降学习率 (对应原 adaptiveRate 升级)
    private volatile float kiLearningRate = 0.001f; // Ki 最速下降学习率
    private volatile int windowSize = 10;           // 移动窗口滤波深度 (默认 10 帧)

    // 2. 核心数学计算历史快照状态变量 (由高频 Netty 接收线程独占，无需 volatile)
    private float integral = 0.0f;
    private float previousError = 0.0f;

    private float lastActualSpeed = 0.0f;  // y(k-1) 用于计算转速差分
    private float lastPwmOutput = 0.0f;    // u(k-1) 用于计算控制量差分
    private float prevPwmOutput = 0.0f;    // u(k-2)

    // 3. 🌟 移动窗口平滑滤波器 (Moving Window)
    // 用于对梯度计算出的瞬时原始 Kp, Ki 进行滑动平均，完全滤除网络噪声导致的尖峰
    private final LinkedList<Float> kpWindowQueue = new LinkedList<>();
    private final LinkedList<Float> kiWindowQueue = new LinkedList<>();

    @Override
    public float calculate(float targetSpeed, float actualSpeed) {
        // --- 🌟 步骤一：基础控制误差解算 ---
        float error = targetSpeed - actualSpeed;

        // --- 🌟 步骤二：通过前后帧差分动态估算系统 Jacobian 灵敏度 (Jacobian Matrix Identification) ---
        // 理论公式：Jacobian = \partial y / \partial u \approx \Delta actualSpeed / \Delta Pwm
        float deltaY = actualSpeed - lastActualSpeed;
        float deltaU = lastPwmOutput - prevPwmOutput;

        float jacobian = 1.0f; // 缺省正向物理增益兜底值
        if (Math.abs(deltaU) > 0.01f) {
            jacobian = deltaY / deltaU;
            // 工业防爆策略：加入正向死区拦截与幅值截断，防止系统突变或噪声带来负反馈或无穷大梯度
            if (jacobian <= 0.0f) jacobian = 0.1f;
            if (jacobian > 5.0f) jacobian = 5.0f;
        }

        // 状态滑移，以便下一帧迭代
        lastActualSpeed = actualSpeed;

        // --- 🌟 步骤三：应用最速下降算法（瞬时误差求偏导）计算原始 Kp, Ki 增量 ---
        // 性能指标：J = 0.5 * e^2.
        // 梯度偏导推导：
        // \Delta Kp = - learningRate * (\partial J / \partial Kp) = learningRate * e * jacobian * (\partial u / \partial Kp)
        // 在常规控制律中，\partial u / \partial Kp \approx e
        // \Delta Ki = - learningRate * (\partial J / \partial Ki) = learningRate * e * jacobian * (\partial u / \partial Ki)
        // 在常规控制律中，\partial u / \partial Ki \approx \int e \approx integral

        float rawKpIncrement = kpLearningRate * error * jacobian * error;
        float rawKiIncrement = kiLearningRate * error * jacobian * integral;

        // 计算最速下降瞬时调谐值
        float instantKp = this.kp + rawKpIncrement;
        float instantKi = this.ki + rawKiIncrement;

        // 安全硬限幅限制：防止最速下降初期发散，保障电机底层物理安全
        instantKp = Math.max(0.01f, Math.min(2.0f, instantKp));
        instantKi = Math.max(0.001f, Math.min(0.5f, instantKi));

        // --- 🌟 步骤四：移动窗口滑动平均滤波（Smoothing） ---
        // 1. 将瞬时更新值灌入窗口队列
        kpWindowQueue.addLast(instantKp);
        kiWindowQueue.addLast(instantKi);

        // 2. 裁剪超出前端指定 windowSize 深度的旧状态
        int currentLimit = Math.max(1, windowSize);
        while (kpWindowQueue.size() > currentLimit) kpWindowQueue.removeFirst();
        while (kiWindowQueue.size() > currentLimit) kiWindowQueue.removeFirst();

        // 3. 计算窗口内参数均值实现平滑滤波
        float smoothedKp = 0.0f;
        for (float val : kpWindowQueue) smoothedKp += val;
        smoothedKp /= kpWindowQueue.size();

        float smoothedKi = 0.0f;
        for (float val : kiWindowQueue) smoothedKi += val;
        smoothedKi /= kiWindowQueue.size();

        // 将平滑后的自适应参数更新回内核成员变量，供下一帧基准使用，并同步给前端大屏
        this.kp = smoothedKp;
        this.ki = smoothedKi;

        // --- 🌟 步骤五：传统标准控制量合成 (常规 PID 回路) ---
        // 1. 积分项累加及抗饱和限幅
        integral += error;
        integral = Math.max(-400.0f, Math.min(400.0f, integral));

        // 2. 传统固定调谐微分项
        float derivative = error - previousError;
        previousError = error;

        // 3. 合成最终控制量 (使用滑动平滑后的最速下降参数)
        float out = (smoothedKp * error) + (smoothedKi * integral) + (this.kd * derivative);

        // --- 🌟 步骤六：物理边界安全限幅与下位机状态对齐 ---
        float finalPwm = Math.max(0.0f, Math.min(250.0f, out));

        // 滚动控制量历史快照用于下一帧的 Jacobian 推演
        prevPwmOutput = lastPwmOutput;
        lastPwmOutput = finalPwm;

        return finalPwm;
    }

    /**
     * 实现 BaseController 参数接收接口：解析拆分后的独立参数矩阵
     */
    @Override
    public void updateParams(Map<String, Object> p) {
        try {
            // 基础三项
            if (p.containsKey("kp")) this.kp = Float.parseFloat(p.get("kp").toString());
            if (p.containsKey("ki")) this.ki = Float.parseFloat(p.get("ki").toString());
            if (p.containsKey("kd")) this.kd = Float.parseFloat(p.get("kd").toString());

            // 🌟 方案 A 对接：接收解耦拆分后的各自学习率及窗口深度
            if (p.containsKey("kpLearningRate")) this.kpLearningRate = Float.parseFloat(p.get("kpLearningRate").toString());
            if (p.containsKey("kiLearningRate")) this.kiLearningRate = Float.parseFloat(p.get("kiLearningRate").toString());
            if (p.containsKey("windowSize")) {
                this.windowSize = Math.round(Float.parseFloat(p.get("windowSize").toString()));
            }

            System.out.println("[最速下降PID] 前端参数矩阵在线注入成功。");
        } catch (Exception e) {
            System.err.println("[最速下降PID] 解析大屏参数矩阵异常: " + e.getMessage());
        }
    }

    /**
     * 实现 BaseController 参数回读接口：将数据同步传回 CloudControlController 刷新前端大屏
     */
    @Override
    public Map<String, Object> getCurrentParams() {
        Map<String, Object> params = new ConcurrentHashMap<>();
        params.put("kp", kp);
        params.put("ki", ki);
        params.put("kd", kd);
        params.put("kpLearningRate", kpLearningRate);
        params.put("kiLearningRate", kiLearningRate);
        params.put("windowSize", (float) windowSize);
        return params;
    }

    /**
     * 环路热切换复位清洗接口
     */
    @Override
    public synchronized void reset() {
        try {
            kpWindowQueue.clear();
            kiWindowQueue.clear();
            integral = 0.0f;
            previousError = 0.0f;
            lastActualSpeed = 0.0f;
            lastPwmOutput = 0.0f;
            prevPwmOutput = 0.0f;
            System.out.println("[最速下降PID] 梯度优化器及移动窗口滤波器成功初始化。");
        } catch (Exception e) {
            System.err.println("[最速下降PID] 复位异常: " + e.getMessage());
        }
    }
}
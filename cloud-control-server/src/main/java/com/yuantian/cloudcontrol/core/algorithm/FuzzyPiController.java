package com.yuantian.cloudcontrol.core.algorithm;

import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工业级规范：紧凑型解析式自适应模糊 PI 控制器 (Analytic Fuzzy PI Controller)
 * 采用连续解析函数(双曲正切 tanh)拟合模糊隶属度与推理规则，深度融合大屏 ecGain 二级调谐矩阵
 * 有效清洗稳态高频颤振，并实现 Kp 和 Ki 的非线性自适应双向联动
 */
@Component("fuzzyPiController")
public class FuzzyPiController implements BaseController {

    // 1. 🌟 前端参数矩阵解耦绑定 (全 volatile 保护，完美对接网页大屏滑动条)
    private volatile float kp = 0.15f;      // 基准比例增益
    private volatile float ki = 0.02f;      // 基准积分增益
    private volatile float fuzzyGain = 0.05f; // 模糊控制量总增益
    private volatile float ecGain = 1.0f;    // 🌟 学生指定追加：误差变化率权重（大屏二级调谐滑块）

    // 2. 内部控制回路状态变量 (由高频 Netty 网络接收线程独占，无需 volatile)
    private float integral = 0.0f;
    private float lastError = 0.0f;
    private boolean isFirstRun = true;       // 用于规避系统起动时初帧 ec 突变的防御边界

    @Override
    public float calculate(float target, float actual) {
        // --- 🌟 步骤一：基础控制误差与二级增益解算 ---
        float error = target - actual;

        // 计算误差变化率 ec，并安全注入大屏 ecGain 权重因子进行横向物理变论域缩放
        float rawEc = isFirstRun ? 0.0f : (error - lastError);
        float ec = rawEc * this.ecGain;

        lastError = error;
        isFirstRun = false;

        // --- 🌟 步骤二：工业级紧凑型解析式模糊规则引擎 (Analytic Fuzzy Engine) ---
        // 核心数学机理：利用 tanh(x) 的渐进饱和特性模拟模糊集合的“隶属度函数”。
        // tanh(x) 在原点附近接近线性，在远处平滑饱和到 [-1, 1]，天生自带低通平滑与软截断特性。

        // 1. 提取当前误差状态的模糊合成量 E_norm 和 EC_norm
        // 引入适当的缩放因子（例如除以 100.0/50.0），使滑块处于常规区间时 tanh 能安全工作在非饱和高灵敏区
        double eNorm = Math.tanh(error / 100.0);
        double ecNorm = Math.tanh(ec / 50.0);

        // 2. 模拟模糊控制规则矩阵的解析解算：
        // 规则 A：当误差与趋势同向扩大时(eNorm * ecNorm > 0)，应增大 Kp 加快响应，减小 Ki 抑制超调。
        // 规则 B：当误差接近稳态或处于恢复期时(eNorm * ecNorm < 0)，应减小 Kp 防止颤振，增大 Ki 锁死静差。
        // 利用平滑乘积运算（eNorm * ecNorm）直接推导出连续的模糊调谐因子
        float fuzzyFactor = (float) (eNorm * ecNorm);

        // 3. 动态计算 Kp 和 Ki 的模糊自适应调节量
        float finalKp = kp + (fuzzyGain * fuzzyFactor);
        float finalKi = ki - (fuzzyGain * 0.2f * fuzzyFactor); // 积分自适应步长通常为比例的 1/5

        // 防御性工程死区拦截，确保在任何极端的模糊输出下，参数不会变成负数而导致正反馈发散
        finalKp = Math.max(0.01f, Math.min(2.0f, finalKp));
        finalKi = Math.max(0.001f, Math.min(0.2f, finalKi));

        // --- 🌟 步骤三：带抗饱和的常规 PI 控制律解算 ---
        // 1. 积分累加
        integral += error;
        // 严格执行积分抗饱和限幅，防止大范围切速时引起电机长时间过冲
        integral = Math.max(-300.0f, Math.min(300.0f, integral));

        // 2. 合成最终控制量
        float out = (finalKp * error) + (finalKi * integral);

        // --- 🌟 步骤四：物理边界安全限幅拦截 ---
        // 严格对齐下位机驱动器 0 ~ 250 PWM 的输入上限
        return Math.max(0.0f, Math.min(250.0f, out));
    }

    /**
     * 实现 BaseController 参数接收接口：解析来自网页端大屏的动态参数矩阵
     */
    @Override
    public void updateParams(Map<String, Object> p) {
        try {
            if (p.containsKey("kp")) this.kp = Float.parseFloat(p.get("kp").toString());
            if (p.containsKey("ki")) this.ki = Float.parseFloat(p.get("ki").toString());
            if (p.containsKey("fuzzyGain")) this.fuzzyGain = Float.parseFloat(p.get("fuzzyGain").toString());

            // 🌟 完美接收大屏下发的二级调谐滑块权重 ecGain
            if (p.containsKey("ecGain")) this.ecGain = Float.parseFloat(p.get("ecGain").toString());

            System.out.println("[解析式模糊PI] 大屏控制台参数矩阵在线调谐成功。当前 ecGain = " + this.ecGain);
        } catch (Exception e) {
            System.err.println("[解析式模糊PI] 动态解析 Web 传参异常: " + e.getMessage());
        }
    }

    /**
     * 实现 BaseController 参数回读接口：将当前最新参数集转化为 Map 提供给大屏进行防跳变同步
     */
    @Override
    public Map<String, Object> getCurrentParams() {
        Map<String, Object> paramsMap = new ConcurrentHashMap<>();
        paramsMap.put("kp", kp);
        paramsMap.put("ki", ki);
        paramsMap.put("fuzzyGain", fuzzyGain);
        paramsMap.put("ecGain", ecGain); // 同步导出，确保方案 A 元数据不丢失
        return paramsMap;
    }

    /**
     * 实现 BaseController 接口的抽象方法：算法热切换状态重置洗刷引擎
     * 🌟 核心价值：在网页端从其他算法切入模糊控制的瞬间，彻底清空历史积分与上一时刻旧误差，消除切换电流冲击
     */
    @Override
    public synchronized void reset() {
        try {
            this.integral = 0.0f;
            this.lastError = 0.0f;
            this.isFirstRun = true;
            System.out.println("[解析式模糊PI] 核心算法拓扑成功复位，历史状态已完全清洗对齐。");
        } catch (Exception e) {
            System.err.println("[解析式模糊PI] 执行复位重置逻辑时发生异常: " + e.getMessage());
        }
    }
}
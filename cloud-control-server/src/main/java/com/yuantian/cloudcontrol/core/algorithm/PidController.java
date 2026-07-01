package com.yuantian.cloudcontrol.core.algorithm;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.Map;

/**
 * 工业级规范：标准位置式 PID 控制器实现类
 */
@Component("pidController")
public class PidController implements BaseController {

    // 🌟 核心调谐参数全部采用 volatile，保障多线程并发可见性
    @Value("${industrial.control.pid.kp:0.25}")
    private volatile float kp;

    @Value("${industrial.control.pid.ki:0.08}")
    private volatile float ki;

    @Value("${industrial.control.pid.kd:0.02}")
    private volatile float kd;

    @Value("${industrial.control.max-pwm:250}")
    private volatile int maxPwm;

    @Value("${industrial.control.min-pwm:0}")
    private volatile int minPwm;

    // 内部控制状态变量
    private float integral = 0.0f;
    private float lastError = 0.0f;

    @Override
    public float calculate(float targetValue, float actualValue) {
        float error = targetValue - actualValue;
        integral += error;

        // 积分抗饱和抗发散保护（采用硬编码或物理边界防护）
        if (integral > 500.0f) integral = 500.0f;
        if (integral < -500.0f) integral = -500.0f;

        float derivative = error - lastError;
        float out = (kp * error) + (ki * integral) + (kd * derivative);
        lastError = error;

        // 输出限幅防护
        return Math.max(minPwm, Math.min(maxPwm, out));
    }

    @Override
    public void updateParams(Map<String, Object> params) {
        // 🌟 方案A 对接：动态解析并更新本地参数
        if (params.containsKey("kp")) this.kp = Float.parseFloat(params.get("kp").toString());
        if (params.containsKey("ki")) this.ki = Float.parseFloat(params.get("ki").toString());
        if (params.containsKey("kd")) this.kd = Float.parseFloat(params.get("kd").toString());
    }

    @Override
    public Map<String, Object> getCurrentParams() {
        // 🌟 统一向外导出当前参数快照
        return Map.of(
                "kp", this.kp,
                "ki", this.ki,
                "kd", this.kd
        );
    }

    @Override
    public void reset() {
        // 🌟 算法切换时，内部历史状态立即从零计算
        this.integral = 0.0f;
        this.lastError = 0.0f;
        System.out.println("[PidController] 内部历史状态已安全清零");
    }
}
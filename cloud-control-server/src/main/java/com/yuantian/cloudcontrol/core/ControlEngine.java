package com.yuantian.cloudcontrol.core;

import com.yuantian.cloudcontrol.core.algorithm.BaseController;
import com.yuantian.cloudcontrol.model.DeviceData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;

import java.util.HashMap;
import java.util.Map;

/**
 * 工业级规范：云端高并发控制核心引擎
 * 负责调度、切换算法，提供线程安全的回路计算以及方案 A 的参数元数据导出
 */
@Component
public class ControlEngine {

    // 自动装配系统中所有实现了 BaseController 接口的控制器（Key 为 Bean 的名称）
    private final Map<String, BaseController> algorithmMap;
    private final DeviceStatusHolder deviceStatusHolder;

    // 🌟 当前激活算法的名称，使用 volatile 确保 Netty 回路能实时感知 Web 端的切换
    private volatile String currentAlgoName = "pidController";

    @Value("${industrial.control.target-speed:1500.0}")
    private volatile float targetSpeed;

    @Autowired
    public ControlEngine(Map<String, BaseController> algorithmMap, DeviceStatusHolder deviceStatusHolder) {
        this.algorithmMap = algorithmMap;
        this.deviceStatusHolder = deviceStatusHolder;
    }

    @PostConstruct
    public void init() {
        System.out.println("[控制引擎] 工业级算法库全量装配就绪！当前默认激活: " + currentAlgoName);
        System.out.println("当前可用算法清单: " + algorithmMap.keySet());
    }

    /**
     * 🌟 动态切换算法（联动状态重置与线程安全防护）
     * @param algoName 目标算法组件的 Bean 名称
     */
    public synchronized void switchAlgorithm(String algoName) {
        if (algorithmMap.containsKey(algoName)) {
            this.currentAlgoName = algoName;

            // 🌟 满足要求：新算法切换时，联动触发 reset 方法，使其模型与历史状态从零开始计算
            algorithmMap.get(algoName).reset();

            System.out.println("[控制引擎] 算法成功切换至: " + algoName + "，且内部状态已安全清零");
        } else {
            throw new IllegalArgumentException("未找到指定的控制算法组件: " + algoName);
        }
    }

    /**
     * 🌟 获取当前正在工作的控制器（具备轻量级线程安全性）
     */
    public BaseController getActiveController() {
        return algorithmMap.get(this.currentAlgoName);
    }

    /**
     * 🌟 方案 A 核心支撑：全量导出当前系统中所有算法的参数元数据矩阵快照
     */
    public Map<String, Object> getAlgorithmsMetadata() {
        Map<String, Object> meta = new HashMap<>();
        Map<String, Map<String, Object>> list = new HashMap<>();

        // 遍历提取每个算法当前的特定参数结构
        for (Map.Entry<String, BaseController> entry : algorithmMap.entrySet()) {
            list.put(entry.getKey(), entry.getValue().getCurrentParams());
        }

        meta.put("currentAlgorithm", this.currentAlgoName);
        meta.put("supportedAlgorithms", list);
        return meta;
    }

    // --- 线程安全的属性访问器 ---
    public String getCurrentAlgoName() {
        return this.currentAlgoName;
    }

    public float getTargetSpeed() {
        return this.targetSpeed;
    }

    public synchronized void setTargetSpeed(float targetSpeed) {
        this.targetSpeed = targetSpeed;
    }

    public DeviceStatusHolder getDeviceStatusHolder() {
        return this.deviceStatusHolder;
    }

    /**
     * 由高频 Netty 异步网络接收回路触发的核心控制计算闭环
     */
    public int processControlLoop(DeviceData deviceData) {
        float actualSpeed = deviceData.getActualSpeed();

        // 🌟 线程安全地提取当前算法并执行控制推演
        BaseController controller = getActiveController();
        float outputPwm = controller.calculate(this.targetSpeed, actualSpeed);

        // 将控制结果反向注入遥测实体，下发回工业现场的下位机执行机构
        deviceData.setOutputPwm((int) outputPwm);
        return (int) outputPwm;
    }
}
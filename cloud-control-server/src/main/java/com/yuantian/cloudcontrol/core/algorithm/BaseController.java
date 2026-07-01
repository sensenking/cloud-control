package com.yuantian.cloudcontrol.core.algorithm;

import java.util.Map;

/**
 * 工业级规范：所有控制算法的基类接口
 */
public interface BaseController {
    /**
     * 核心控制回路计算接口
     */
    float calculate(float targetSpeed, float actualSpeed);

    /**
     * 🌟 统一参数更新入口（方案A 动态参数矩阵对接）
     */
    void updateParams(Map<String, Object> params);

    /**
     * 🌟 统一参数导出入口
     */
    Map<String, Object> getCurrentParams();

    /**
     * 🌟 新增：算法切换或重置时，状态与内部模型清零接口
     */
    void reset();
}
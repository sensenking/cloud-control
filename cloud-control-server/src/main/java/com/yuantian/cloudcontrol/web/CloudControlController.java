package com.yuantian.cloudcontrol.web;

import com.yuantian.cloudcontrol.core.ControlEngine;
import com.yuantian.cloudcontrol.core.DeviceStatusHolder;
import com.yuantian.cloudcontrol.core.algorithm.BaseController;
import com.yuantian.cloudcontrol.model.DeviceData;
import com.yuantian.cloudcontrol.model.DeviceDataLog;
import com.yuantian.cloudcontrol.repository.DeviceDataLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 工业级规范：云端核心控制与遥测路由中心（完全补全版）
 * 完美保留原有全量网络QoS指标，并全动态支持方案 A 参数矩阵
 */
@RestController
@RequestMapping("/api/control")
@CrossOrigin
public class CloudControlController {

    private final DeviceStatusHolder deviceStatusHolder;
    private final ControlEngine controlEngine;
    private final DeviceDataLogRepository logRepository;

    @Autowired
    public CloudControlController(DeviceStatusHolder deviceStatusHolder,
                                  ControlEngine controlEngine,
                                  DeviceDataLogRepository logRepository) {
        this.deviceStatusHolder = deviceStatusHolder;
        this.controlEngine = controlEngine;
        this.logRepository = logRepository;
    }

    /**
     * 🌟 接口 1：获取全量算法元数据矩阵（方案 A 核心支撑）
     * 请求路径：GET /api/control/meta
     */
    @GetMapping("/meta")
    public Map<String, Object> getAlgorithmsMeta() {
        Map<String, Object> response = new HashMap<>();
        try {
            response.put("status", "success");
            response.put("meta", controlEngine.getAlgorithmsMetadata());
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "拉取算法元数据失败: " + e.getMessage());
        }
        return response;
    }

    /**
     * 🌟 接口 2：动态切换算法（统一入参映射名为 name，对齐原始项目习惯）
     * 请求路径：POST /api/control/switch-algorithm
     */
    @PostMapping("/switch-algorithm")
    public Map<String, Object> switchAlgorithm(@RequestParam String name) {
        try {
            controlEngine.switchAlgorithm(name);
            return Map.of("status", "success", "message", "已切换至: " + name + "，模型状态已重置从零计算");
        } catch (Exception e) {
            return Map.of("status", "error", "message", "切换失败: " + e.getMessage());
        }
    }

    /**
     * 🌟 接口 3：动态参数调谐接口（方案 A 兼容全局 targetSpeed 更新）
     * 请求路径：POST /api/control/update-params
     */
    @PostMapping("/update-params")
    public Map<String, Object> updateParams(@RequestBody Map<String, Object> params) {
        System.out.println("【后端探针】路由层收到前端参数矩阵: " + params);
        try {
            // 1. 优先捕获并更新全局目标速比
            if (params.containsKey("targetSpeed")) {
                controlEngine.setTargetSpeed(Float.parseFloat(params.get("targetSpeed").toString()));
            }

            // 2. 将动态参数矩阵透传给当前激活的算法实例（各取所需）
            BaseController activeController = controlEngine.getActiveController();
            if (activeController != null) {
                activeController.updateParams(params);
                return Map.of("status", "success", "message", "参数注入成功");
            } else {
                return Map.of("status", "error", "message", "引擎内部未检测到激活的控制器");
            }
        } catch (Exception e) {
            return Map.of("status", "error", "message", "参数解析失败: " + e.getMessage());
        }
    }

    /**
     * 🌟 接口 4：网页端修改通信速率接口（完全恢复）
     * 请求路径：POST /api/control/frequency?hz=50.0
     */
    @PostMapping("/frequency")
    public String updateFrequency(@RequestParam double hz) {
        if (hz <= 0 || hz > 200) {
            return "错误：工业控制频率推荐设置在 1.0 ~ 200.0 Hz 之间！";
        }
        deviceStatusHolder.setTargetFrequency(hz);
        return "成功：云端通信速率已调整为 " + hz + " Hz，将在下一帧立刻下发给下位机！";
    }

    /**
     * 🌟 接口 5：高频遥测快照数据同步接口（完全补全版）
     * 请求路径：GET /api/control/telemetry
     * 特性：完整恢复了原始项目的所有底层指标，同时动态追加了当前算法参数快照，阻断前端数据覆盖
     */
    @GetMapping("/telemetry")
    public Map<String, Object> getRealtimeTelemetry() {
        DeviceData latestLog = deviceStatusHolder.getLatestData();
        Map<String, Object> responseData = new HashMap<>();

        // 1. 核心控制指标
        responseData.put("targetSpeed", controlEngine.getTargetSpeed());
        responseData.put("actualSpeed", latestLog.getActualSpeed());
        responseData.put("outputPwm", latestLog.getOutputPwm());
        responseData.put("serverStatus", "RUNNING");

        // 2. 通信周期与速率
        responseData.put("targetFrequency", latestLog.getTargetFrequency());
        responseData.put("actualFrequency", latestLog.getActualFrequency());
        responseData.put("communicationInterval", latestLog.getCommunicationInterval());
        responseData.put("jitter", latestLog.getJitter());

        // 3. 时延与网络链路指标
        responseData.put("sequenceNumber", latestLog.getSequenceNumber());
        responseData.put("rtt", latestLog.getRtt());
        responseData.put("latency", latestLog.getLatency());
        responseData.put("commandDispatchLatency", latestLog.getCommandDispatchLatency());

        // 4.
        responseData.put("baseBandwidthKbps", latestLog.getBaseBandwidthKbps());
        responseData.put("tcpMaxThroughputMbps", latestLog.getTcpMaxThroughputMbps());

        // 5. 🌟 核心扩展：注入当前算法运行时上下文（供前端进行方案 A 表单判定和防覆写保护）
        responseData.put("currentAlgorithm", controlEngine.getCurrentAlgoName());
        BaseController activeController = controlEngine.getActiveController();
        if (activeController != null) {
            Map<String, Object> currentP = activeController.getCurrentParams();
            //System.out.println("【遥测探针】当前准备发往前端的参数快照: " + currentP);
            responseData.put("currentParams", activeController.getCurrentParams());
        }


        return responseData;
    }

    /**
     * 接口 6：获取离线全量数据日志
     */
    @GetMapping("/history")
    public Map<String, Object> getFullRunHistory() {
        List<DeviceDataLog> allHistory = logRepository.findAll();
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("totalRecords", allHistory.size());
        response.put("data", allHistory);
        return response;
    }

    /**
     * 接口 7：暂停/恢复云端网络栈接收下位机控制报文的网闸接口
     */
    @PostMapping("/receive-gate")
    public Map<String, Object> toggleReceiveGate(@RequestParam boolean pause) {
        Map<String, Object> result = new HashMap<>();
        // 适配您的原始变量名 setReceivePaused
        deviceStatusHolder.setReceivePaused(pause);

        result.put("status", "success");
        result.put("isPaused", pause);
        result.put("message", pause ? "云端网络栈已切断，接收回路进入挂起暂停状态。" : "云端网络栈已重新激活，控制回路全速恢复运行。");
        return result;
    }
}
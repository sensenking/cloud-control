package com.yuantian.cloudcontrol.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "device_data_log")
public class DeviceDataLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 必须保留时间轴，否则全量数据无法按时间顺序绘制图表
    private LocalDateTime saveTime;

    // 仅保留你要求的两个核心业务字段
    private float actualSpeed;
    private int outputPwm;

    public DeviceDataLog() {}

    // --- Getters & Setters ---
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public LocalDateTime getSaveTime() { return saveTime; }
    public void setSaveTime(LocalDateTime saveTime) { this.saveTime = saveTime; }
    public float getActualSpeed() { return actualSpeed; }
    public void setActualSpeed(float actualSpeed) { this.actualSpeed = actualSpeed; }
    public int getOutputPwm() { return outputPwm; }
    public void setOutputPwm(int outputPwm) { this.outputPwm = outputPwm; }
}
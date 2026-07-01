package com.yuantian.cloudcontrol.repository;

import com.yuantian.cloudcontrol.model.DeviceDataLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 工业级规范：内存数据库访问层
 * 继承 JpaRepository 后，自动获得 save(), findAll() 等标准方法
 */
@Repository
public interface DeviceDataLogRepository extends JpaRepository<DeviceDataLog, Long> {
}
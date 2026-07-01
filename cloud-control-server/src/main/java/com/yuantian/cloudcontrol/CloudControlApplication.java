package com.yuantian.cloudcontrol;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;


@SpringBootApplication // 这是一个奇妙的注解，它代表这是一个 Spring Boot 项目，会自动扫描当前包及其子包下的所有组件
@EnableScheduling // 🌟新增：显式开启 Spring 的定时任务调度功能，用于支持异步批量落盘
public class CloudControlApplication {

    public static void main(String[] args) {
        // 启动 Spring 容器
        SpringApplication.run(CloudControlApplication.class, args);
        System.out.println("\n[系统通知] Spring Boot 基础业务容器启动成功！监控网页准备就绪。");
    }
}



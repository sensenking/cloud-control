package com.yuantian.cloudcontrol.network;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * 工业级规范：Netty 异步非阻塞 TCP 服务器启动引导程序
 * 作为一个随 Spring 容器启动而异步运行的组件
 */
@Component
public class NettyServerBootstrapper {

    // 从 application.yml 中动态获取 Netty 监听的 TCP 端口，若不存在则默认为 50008
    @Value("${industrial.control.server-port:50008}")
    private int serverPort;

    private final NettyChannelInitializer nettyChannelInitializer;

    // 定义 Netty 的两大核心线程组
    private EventLoopGroup bossGroup;   // 负责接收客户端连接的“老板线程组”
    private EventLoopGroup workerGroup; // 负责处理网络读写和业务计算的“员工线程组”

    /**
     * 构造方法注入：组装通道责任链初始化器
     */
    @Autowired
    public NettyServerBootstrapper(NettyChannelInitializer nettyChannelInitializer) {
        this.nettyChannelInitializer = nettyChannelInitializer;
    }

    /**
     * 生命周期回调方法：在 Spring 完成 Bean 初始化后自动触发
     * 必须在新开启的线程中异步启动 Netty，绝对不能阻塞 Spring Boot 主线程（Tomcat 启动流）
     */
    @PostConstruct
    public void start() {
        new Thread(() -> {
            // 1. 实例化多线程 Reactor 模型的线程组
            bossGroup = new NioEventLoopGroup(1); // 工业规范：监听连接通常 1 个线程足矣
            workerGroup = new NioEventLoopGroup();   // 默认线程数为 CPU 核心数 * 2，完美支持高并发

            try {
                // 2. 创建服务器端启动辅助引导类
                ServerBootstrap bootstrap = new ServerBootstrap();
                bootstrap.group(bossGroup, workerGroup)
                        .channel(NioServerSocketChannel.class) // 指定使用 NIO 异步非阻塞通道
                        .childHandler(nettyChannelInitializer)  // 挂载我们自定义的管道责任链
                        // 工业级参数调优
                        .option(ChannelOption.SO_BACKLOG, 128)        // 设置临时存放已完成三次握手请求的队列大小
                        .childOption(ChannelOption.SO_KEEPALIVE, true); // 开启 TCP 底层心跳保活机制

                System.out.println("[Netty网络中心] 正在启动异步非阻塞工业高并发 TCP 服务器...");

                // 3. 异步绑定端口并启动服务器，开始智能监听下位机连接
                ChannelFuture future = bootstrap.bind(serverPort).sync();
                System.out.println("[Netty网络中心] 服务器启动成功！正在智能监听端口: " + serverPort);

                // 4. 阻塞等待服务器通道关闭（维持子线程运行，直到服务器生命周期结束）
                future.channel().closeFuture().sync();

            } catch (InterruptedException e) {
                System.err.println("[Netty网络中心] 服务器在运行期间遭遇中断异常: " + e.getMessage());
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                System.err.println("[Netty网络中心] 服务器发生致命错误，启动失败: " + e.getMessage());
            } finally {
                // 优雅关闭释放所有网络和线程资源
                shutdown();
            }
        }, "netty-server-thread").start(); // 为该守护后台线程命名，方便工业日志排查
    }

    /**
     * lifecycle 注销回调：当 Spring 容器关闭（应用停止）时，自动触发优雅停机
     */
    @PreDestroy
    public void stop() {
        System.out.println("[Netty网络中心] 监测到云端容器即将关闭，正在执行优雅停机释放资源...");
        shutdown();
    }

    /**
     * 优雅释放 Netty 线程组资源的私有方法
     */
    private void shutdown() {
        if (bossGroup != null && !bossGroup.isShuttingDown()) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null && !workerGroup.isShuttingDown()) {
            workerGroup.shutdownGracefully();
        }
        System.out.println("[Netty网络中心] 所有 Netty 网络线程组已彻底注销，资源释放完毕。");
    }
}
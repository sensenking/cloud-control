package com.yuantian.cloudcontrol.network;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.util.CharsetUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 工业级规范：Netty 通道初始化器
 * 用于配置新接入客户端的责任链（Pipeline）
 */
@Component
public class NettyChannelInitializer extends ChannelInitializer<SocketChannel> {

    private final NettyServerHandler nettyServerHandler;

    /**
     * 构造方法注入：将线程安全的业务处理器组装进来
     */
    @Autowired
    public NettyChannelInitializer(NettyServerHandler nettyServerHandler) {
        this.nettyServerHandler = nettyServerHandler;
    }

    /**
     * 初始化通道责任链，为流经的数据流挂载编解码器与业务处理器
     */
    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();

        pipeline.addLast(new LineBasedFrameDecoder(1024));

        // 1. 入站解码器：将接收到的字节流按照 UTF-8 编码自动解码为 Java String 字符串
        pipeline.addLast("decoder", new StringDecoder(CharsetUtil.UTF_8));

        // 2. 出站编码器：将发送的 Java String 字符串自动编码为底层传输的字节流
        pipeline.addLast("encoder", new StringEncoder(CharsetUtil.UTF_8));

        // 3. 核心业务处理器：将解码后的字符串送入业务计算逻辑
        pipeline.addLast("handler", nettyServerHandler);
    }
}
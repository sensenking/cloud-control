
## 各模块功能说明
### 1. speed_temp（51单片机）
- 采集电机实时转速，通过串口向上位机传输转速报文
- 接收上位机转发的云端PID控制量，输出PWM驱动电机调速
- 硬件底层时序、中断逻辑控制

### 2. cloud-control-client（C#上位机）
- 串口收发单片机转速数据，自定义波特率（默认57600）
- TCP客户端连接云端，实时上传转速、接收云端下发控制指令
- 实时转速曲线可视化、鼠标高亮、运行/暂停/复位硬件控制
- 实验数据一键导出CSV文件，用于离线数据分析

### 3. cloud-control-server（SpringBoot云端）
- TCP服务端维持上位机长连接，双向数据流解析
- 基于T1/T2/T3/T4四时戳高精度解算RTT往返时延、单向延迟、通信抖动
- 滑动窗口统计实时带宽、TCP理论极限吞吐量、累计/周期丢包率
- Smith预估控制器补偿网络传输滞后，优化长时延下PID控制稳定性
- 定时批量将电机运行轨迹数据存入MySQL，减轻数据库压力
- 动态限制通信交互频率，规避速率过高/过低引发的系统震荡

## 运行环境依赖
### 云端服务 cloud-control-server
- JDK 1.8 / JDK11
- SpringBoot 2.7.x
- Maven
- MySQL 8.0

### Windows上位机 cloud-control-client
- Visual Studio 2019/2022
- .NET Framework 4.8
- System.Windows.Forms.DataVisualization 图表控件

### 51单片机 speed_temp
- Keil C51
- STC89C52/AT89C51单片机

## 开源说明
本项目为课程设计/毕设学习工程，可自由下载、学习、二次开发。

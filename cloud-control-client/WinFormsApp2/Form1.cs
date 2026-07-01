using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.IO.Ports;
using System.Net.Sockets;
using System.Text;
using System.Threading;
using System.Windows.Forms;
using OxyPlot;
using OxyPlot.Axes;
using OxyPlot.Series;

namespace WinFormsApp2
{
    public partial class Form1 : Form
    {
        // ==================== 1. 工业级多线程共享邮箱架构 ====================
        private class SharedMailbox
        {
            public readonly object LockUplink = new object();
            public readonly object LockDownlink = new object();

            // [上行数据区]：串口物理层 -> TCP网络层 & UI渲染
            public uint ActualSpeed = 0;            // 🌟 修改为 uint 适配单片机的无符号整数
            public long T1Nano = 0;
            public long UartRttNs = 0;
            public double ClientHz = 0.0;
            public int Sequence = 0;

            // [下行数据区]：TCP网络层/UI -> 串口物理层
            public byte TargetPwm = 0;              // 🌟 修改为 byte (0~255) 适配单片机 8 位 PWM
            public byte CommandCode = 0x10;         // 控制码: 0x10 正常, 0xFA 急停, 0xEB 闭环配置
            public double GlobalTargetHz = 20.0;    // 云端目标频率 (currentHz)

            // [QoS 服务质量监控统计]
            public int TotalExpectedFrames = 0;
            public int TotalReceivedFrames = 0;
            public int CrcErrorCount = 0;
            public int LostFrameCount = 0;
            public long TotalValidBytes = 0;
            public double ArrivalJitterMs = 0.0;
        }

        private readonly SharedMailbox _mailbox = new SharedMailbox();

        // 异步工作线程句柄
        private Thread _serialCoreThread;
        private Thread _tcpCoreThread;
        private bool _isSerialThreadRunning = false;
        private bool _isTcpThreadRunning = false;

        // 核心状态机开关
        private bool _isActivelySending = false;
        private DateTime _experimentStartTime;

        // OxyPlot 引擎变量
        private PlotModel _plotModel;
        private LineSeries _speedSeries;
        private readonly List<double> _cacheTime = new List<double>();
        private readonly List<double> _cacheSpeed = new List<double>();
        private readonly List<int> _cachePwm = new List<int>();
        private readonly List<double> _cacheRtt = new List<double>();

        // 仪表盘刷新定时器
        private System.Windows.Forms.Timer _dashboardTimer;
        private DateTime _lastThroughputCalcTime;
        private long _lastValidBytesCount = 0;

        public Form1()
        {
            InitializeComponent();
            InitializeOxyPlotEngine();
            InitializeDashboardTimer();
            _experimentStartTime = DateTime.Now;
            _lastThroughputCalcTime = DateTime.Now;
        }

        private void Form1_Load(object sender, EventArgs e)
        {
            LogToBox("[系统启动] 极简定长二进制协议框架已加载。");

            // 🌟 初始状态 UI 锁
            btnPause.Enabled = false;
            btnApplyExperiment.Enabled = false; // 只有连上串口才能发配置

            if (cmbBaudRate.Items.Count > 0) cmbBaudRate.SelectedIndex = 0;
            if (cmbSerialPort.Items.Count > 0) cmbSerialPort.SelectedIndex = 0;
            if (cmbExperimentType.Items.Count > 0) cmbExperimentType.SelectedIndex = 0;
        }

        // ==================== 2. Modbus CRC16 ====================
        private static ushort CalculateModbusCrc16(byte[] buffer, int length)
        {
            ushort crc = 0xFFFF;
            for (int i = 0; i < length; i++)
            {
                crc ^= buffer[i];
                for (int j = 0; j < 8; j++)
                {
                    if ((crc & 1) != 0) { crc >>= 1; crc ^= 0xA001; }
                    else { crc >>= 1; }
                }
            }
            return crc;
        }

        // ==================== 3. 串口物理层线程 (定长极简协议 & 一收一发) ====================
        private void SerialProcessorLoop(object param)
        {
            var settings = (Tuple<string, int>)param;
            SerialPort serialPortHandle = null;
            byte[] rawBuffer = new byte[1024];
            int currentBufferLength = 0;

            try
            {
                serialPortHandle = new SerialPort(settings.Item1, settings.Item2, Parity.None, 8, StopBits.One);
                serialPortHandle.ReadTimeout = 50;
                serialPortHandle.Open();

                // 🌟 跨线程回调，安全解锁 UI
                Invoke(new Action(() =>
                {
                    LogToBox($"[物理层] UART {settings.Item1} 开启，协议：8字节上行/7字节下行");
                    btnToggleSerial.Text = "关闭物理串口";
                    cmbSerialPort.Enabled = false;
                    btnApplyExperiment.Enabled = true;
                    btnToggleSerial.Enabled = true;
                }));
            }
            catch (Exception ex)
            {
                Invoke(new Action(() =>
                {
                    LogToBox($"[致命错误] 串口挂载失败: {ex.Message}");
                    btnToggleSerial.Enabled = true; // 恢复按钮可点击
                    cmbSerialPort.Enabled = true; // 恢复串口选择框可编辑
                }));
                _isSerialThreadRunning = false;
                return;
            }

            long previousFrameTimeTicks = Stopwatch.GetTimestamp();
            long stopwatchFrequency = Stopwatch.Frequency;
            int lastSequenceNumber = -1;

            while (_isSerialThreadRunning)
            {
                long loopCycleStartTimeTicks = Stopwatch.GetTimestamp();
                double dynamicIntervalSeconds;

                lock (_mailbox.LockDownlink)
                {
                    dynamicIntervalSeconds = 1.0 / _mailbox.GlobalTargetHz;
                }

                try
                {
                    if (!serialPortHandle.IsOpen) break;

                    // ----------------- A. 主动发送下行控制帧 (定长 7 字节) -----------------
                    if (_isActivelySending)
                    {
                        byte currentPwm; byte currentCmd;
                        lock (_mailbox.LockDownlink)
                        {
                            currentPwm = _mailbox.TargetPwm;
                            currentCmd = _mailbox.CommandCode;
                        }

                        // [0xA5] [Cmd] [PWM] [0x00] [CRC_L] [CRC_H] [0x0A]
                        byte[] txFrame = new byte[7];
                        txFrame[0] = 0xA5;
                        txFrame[1] = currentCmd;
                        txFrame[2] = currentPwm;
                        txFrame[3] = 0x00;

                        ushort localCrc = CalculateModbusCrc16(txFrame, 4);
                        txFrame[4] = (byte)(localCrc & 0xFF);
                        txFrame[5] = (byte)((localCrc >> 8) & 0xFF);
                        txFrame[6] = 0x0A;

                        long tUartStart = Stopwatch.GetTimestamp();
                        serialPortHandle.Write(txFrame, 0, 7);

                        // ----------------- B. 阻塞接收上行反馈帧 (定长 8 字节) -----------------
                        long calculatedUartRttNs = 0;
                        bool frameReceivedSuccess = false;
                        int retryCount = 0;

                        while (retryCount < 50 && _isSerialThreadRunning)
                        {
                            int bytesAvailable = serialPortHandle.BytesToRead;
                            if (bytesAvailable > 0)
                            {
                                int readBytes = serialPortHandle.Read(rawBuffer, currentBufferLength,
                                    Math.Min(bytesAvailable, rawBuffer.Length - currentBufferLength));
                                currentBufferLength += readBytes;
                            }

                            // 期待的定长 8 字节上行帧：[0x5A] [Seq] [Speed_H] [Speed_L] [0x00] [CRC_L] [CRC_H] [0x0D]
                            if (currentBufferLength >= 8)
                            {
                                if (rawBuffer[0] == 0x5A && rawBuffer[7] == 0x0D)
                                {
                                    ushort receivedCrc = (ushort)(rawBuffer[5] | (rawBuffer[6] << 8));
                                    if (CalculateModbusCrc16(rawBuffer, 5) == receivedCrc)
                                    {
                                        //long t1NanoTimestamp = DateTime.UtcNow.Ticks * 100;
                                        long currentFrameTimeTicks = Stopwatch.GetTimestamp();

                                        byte currentSeq = rawBuffer[1]; // 🌟 1 字节序列号
                                        uint decodedSpeed = (uint)((rawBuffer[2] << 8) | rawBuffer[3]); // 🌟 无符号整型拼接

                                        calculatedUartRttNs = (currentFrameTimeTicks - tUartStart) * 1_000_000_000 / stopwatchFrequency;

                                        long elapsedTicksSinceLastFrame = currentFrameTimeTicks - previousFrameTimeTicks;
                                        double localHz = (double)stopwatchFrequency / elapsedTicksSinceLastFrame;
                                        previousFrameTimeTicks = currentFrameTimeTicks;

                                        int lostFramesDetected = 0;
                                        if (lastSequenceNumber != -1)
                                        {
                                            int expectedSeq = (lastSequenceNumber + 1) & 0xFF; // 8位环回
                                            if (currentSeq != expectedSeq)
                                            {
                                                lostFramesDetected = (currentSeq >= expectedSeq) ?
                                                    (currentSeq - expectedSeq) : (256 - expectedSeq + currentSeq);
                                            }
                                        }
                                        lastSequenceNumber = currentSeq;

                                        lock (_mailbox.LockUplink)
                                        {
                                            _mailbox.ActualSpeed = decodedSpeed;
                                            //_mailbox.T1Nano = t1NanoTimestamp;
                                            _mailbox.UartRttNs = Math.Abs(calculatedUartRttNs);
                                            _mailbox.ClientHz = localHz;
                                            _mailbox.Sequence = currentSeq;

                                            _mailbox.TotalReceivedFrames++;
                                            _mailbox.TotalExpectedFrames += (1 + lostFramesDetected);
                                            _mailbox.LostFrameCount += lostFramesDetected;
                                            _mailbox.TotalValidBytes += 8;
                                        }

                                        frameReceivedSuccess = true;
                                        Array.Copy(rawBuffer, 8, rawBuffer, 0, currentBufferLength - 8);
                                        currentBufferLength -= 8;
                                        break;
                                    }
                                    else
                                    {
                                        lock (_mailbox.LockUplink) _mailbox.CrcErrorCount++;
                                        Array.Copy(rawBuffer, 8, rawBuffer, 0, currentBufferLength - 8);
                                        currentBufferLength -= 8;
                                    }
                                }
                                else
                                {
                                    Array.Copy(rawBuffer, 1, rawBuffer, 0, currentBufferLength - 1);
                                    currentBufferLength--;
                                }
                            }
                            else
                            {
                                Thread.Sleep(1);
                                retryCount++;
                            }
                        }

                        if (!frameReceivedSuccess && _isSerialThreadRunning)
                        {
                            lock (_mailbox.LockUplink)
                            {
                                _mailbox.LostFrameCount++;
                                _mailbox.TotalExpectedFrames++;
                            }
                        }
                    }
                }
                catch (Exception) { break; }

                // ----------------- C. 动态时间补偿 -----------------
                long loopCycleEndTimeTicks = Stopwatch.GetTimestamp();
                double timeElapsedSeconds = (double)(loopCycleEndTimeTicks - loopCycleStartTimeTicks) / stopwatchFrequency;
                int remainingSleepTimeMilliseconds = (int)Math.Max(0, (dynamicIntervalSeconds - timeElapsedSeconds) * 1000);
                Thread.Sleep(remainingSleepTimeMilliseconds);
            }

            if (serialPortHandle != null && serialPortHandle.IsOpen) serialPortHandle.Close();
            Invoke(new Action(() => LogToBox("[物理层] 串口通信句柄已彻底销毁。")));
        }

        // ==================== 4. TCP 网络层线程 ====================

        private void TcpNetworkCoreLoop(object param)
        {
            var networkSettings = (Tuple<string, int>)param;
            TcpClient networkClient = null;
            NetworkStream networkStream = null;

            // 缓存系统高精度时钟频率，避免在循环中重复调用
            long stopwatchFrequency = Stopwatch.Frequency;
            long lastTcpReceiveTicks = Stopwatch.GetTimestamp();

            // 🌟 核心状态驻留：用于缓存上一帧有效下发指令到达时的物理纳秒戳 (LastT4)
            long lastT4Nano = 0;

            try
            {
                networkClient = new TcpClient();
                var connectAsyncResult = networkClient.BeginConnect(networkSettings.Item1, networkSettings.Item2, null, null);
                bool connectSuccess = connectAsyncResult.AsyncWaitHandle.WaitOne(1500);
                if (!connectSuccess) throw new TimeoutException();

                networkClient.EndConnect(connectAsyncResult);
                networkStream = networkClient.GetStream();
                networkStream.ReadTimeout = 1500;

                Invoke(new Action(() =>
                {
                    LogToBox($"[网络层] Netty 云主机 [{networkSettings.Item1}:{networkSettings.Item2}] 连接成功。");
                    btnToggleNetwork.Text = "断开云端网络连接";
                    btnToggleNetwork.Enabled = true;
                    txtCloudIp.Enabled = false;
                    txtCloudPort.Enabled = false;
                }));
            }
            catch (Exception)
            {
                Invoke(new Action(() =>
                {
                    LogToBox("[致命错误] 连云失败。");
                    btnToggleNetwork.Enabled = true;
                    txtCloudIp.Enabled = true;
                    txtCloudPort.Enabled = true;
                }));
                _isTcpThreadRunning = false;
                return;
            }

            byte[] networkReadBuffer = new byte[1024];
            StringBuilder stringStreamCache = new StringBuilder();

            while (_isTcpThreadRunning)
            {
                long loopCycleStartTimeTicks = Stopwatch.GetTimestamp();
                double dynamicIntervalSeconds;

                lock (_mailbox.LockDownlink)
                {
                    dynamicIntervalSeconds = 1.0 / _mailbox.GlobalTargetHz;
                }

                try
                {
                    int sequence; float currentSpeed;
                    lock (_mailbox.LockUplink)
                    {
                        sequence = _mailbox.Sequence;
                        currentSpeed = _mailbox.ActualSpeed;
                        // 注意：UartRttNs 不再上报云端，仅留作本地大屏显示
                    }

                    // 🌟 T1: 拦截当前帧即将推入 TCP 发送缓冲区瞬间的高精度绝对物理纳秒戳
                    long t1Ticks = Stopwatch.GetTimestamp();
                    long t1Nano = (t1Ticks * 1_000_000_000) / stopwatchFrequency;

                    // 🌟 上行协议重构：严格的 4 字段 "Seq,T1,Speed,LastT4\n"
                    string uplinkMessageFrame = $"{sequence},{t1Nano},{currentSpeed:F2},{lastT4Nano}\n";
                    byte[] rawTxBytes = Encoding.UTF8.GetBytes(uplinkMessageFrame);
                    networkStream.Write(rawTxBytes, 0, rawTxBytes.Length);

                    // ----------------- 阻塞接收下行反馈 -----------------
                    int receivedChunkLength = networkStream.Read(networkReadBuffer, 0, networkReadBuffer.Length);

                    // 🌟 T4: 一旦 Read 阻塞被唤醒，说明网卡收到了数据包，立刻捕获到达时间戳
                    long t4ArrivalTicks = Stopwatch.GetTimestamp();
                    long currentT4Nano = (t4ArrivalTicks * 1_000_000_000) / stopwatchFrequency;

                    if (receivedChunkLength > 0)
                    {
                        // Jitter (网络抖动) 滑动测算
                        long currentArrivalTicks = t4ArrivalTicks;
                        double actualInterval = (currentArrivalTicks - lastTcpReceiveTicks) / (double)stopwatchFrequency;
                        lastTcpReceiveTicks = currentArrivalTicks;
                        double jitterMs = Math.Abs(actualInterval - dynamicIntervalSeconds) * 1000.0;
                        lock (_mailbox.LockUplink) { _mailbox.ArrivalJitterMs = jitterMs; }

                        stringStreamCache.Append(Encoding.UTF8.GetString(networkReadBuffer, 0, receivedChunkLength));
                        string totalCacheString = stringStreamCache.ToString();

                        // 寻找帧尾定界符 \n 以防 TCP 粘包裂包
                        if (totalCacheString.Contains("\n"))
                        {
                            int delimiterIndex = totalCacheString.LastIndexOf('\n');
                            string completedPackets = totalCacheString.Substring(0, delimiterIndex);
                            stringStreamCache.Clear();
                            stringStreamCache.Append(totalCacheString.Substring(delimiterIndex + 1));

                            string[] processLines = completedPackets.Split(new[] { '\n' }, StringSplitOptions.RemoveEmptyEntries);
                            if (processLines.Length > 0)
                            {
                                string finalValidLine = processLines[processLines.Length - 1].Trim();
                                string[] payloadTokens = finalValidLine.Split(',');

                                // 🌟 下行协议重构：期待云端极简 2 字段 "TargetPwm,CurrentHz"
                                if (payloadTokens.Length >= 2)
                                {
                                    int cloudPwm = int.Parse(payloadTokens[0]);
                                    double cloudHz = double.Parse(payloadTokens[1]);

                                    // 🌟 确认帧格式合法后，才将本次捕获的 T4 存档，作为下一帧上报的 LastT4
                                    lastT4Nano = currentT4Nano;

                                    // 写入下行邮箱，驱动物理层串口线程
                                    lock (_mailbox.LockDownlink)
                                    {
                                        _mailbox.TargetPwm = (byte)Math.Min(255, Math.Max(0, cloudPwm));
                                        if (cloudHz >= 0.5 && cloudHz <= 500.0)
                                        {
                                            _mailbox.GlobalTargetHz = cloudHz;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                catch (Exception ex)
                {
                    Invoke(new Action(() => LogToBox($"[脱机熔断] 云网络异常或超时: {ex.Message}")));
                    break;
                }

                // ----------------- 自适应动态时间补偿算法 -----------------
                long loopCycleEndTimeTicks = Stopwatch.GetTimestamp();
                double timeElapsedSeconds = (double)(loopCycleEndTimeTicks - loopCycleStartTimeTicks) / stopwatchFrequency;
                int remainingSleepTimeMilliseconds = (int)Math.Max(0, (dynamicIntervalSeconds - timeElapsedSeconds) * 1000);
                Thread.Sleep(remainingSleepTimeMilliseconds);
            }

            networkStream?.Close();
            networkClient?.Close();
            Invoke(new Action(() => LogToBox("[网络层] 网络套接字彻底销毁。")));
        }

        // ==================== 5. 🌟 严格 UI 状态机事件群 ====================

        private void btnToggleNetwork_Click(object sender, EventArgs e)
        {
            if (_isTcpThreadRunning)
            {
                // 关闭动作
                _isTcpThreadRunning = false;
                _tcpCoreThread?.Join(1000);
                btnToggleNetwork.Text = "建立云端网络连接";
                txtCloudIp.Enabled = true; txtCloudPort.Enabled = true;
            }
            else
            {
                // 连接动作 - 先置灰自身防止连击
                btnToggleNetwork.Enabled = false;
                string ipAddress = txtCloudIp.Text.Trim();
                if (string.IsNullOrEmpty(ipAddress)) ipAddress = "127.0.0.1";
                int targetPort = int.Parse(txtCloudPort.Text.Trim());

                _isTcpThreadRunning = true;
                _tcpCoreThread = new Thread(TcpNetworkCoreLoop) { IsBackground = true, Name = "TCP-Core" };
                _tcpCoreThread.Start(new Tuple<string, int>(ipAddress, targetPort));
            }
        }

        private void btnToggleSerial_Click(object sender, EventArgs e)
        {
            if (_isSerialThreadRunning)
            {
                // 关闭动作
                _isSerialThreadRunning = false;
                _serialCoreThread?.Join(1000);
                btnToggleSerial.Text = "打开物理串口";
                cmbSerialPort.Enabled = true;
                btnApplyExperiment.Enabled = false; // 断开后禁止发配置
            }
            else
            {
                // 打开动作 - 状态互斥锁
                btnToggleSerial.Enabled = false;
                string portName = cmbSerialPort.Text.Trim();
                if (string.IsNullOrEmpty(portName)) portName = "COM3";
                int baudRate = 57600;

                _isSerialThreadRunning = true;
                _serialCoreThread = new Thread(SerialProcessorLoop) { IsBackground = true, Name = "UART-Core" };
                _serialCoreThread.Start(new Tuple<string, int>(portName, baudRate));
            }
        }

        private void btnApplyExperiment_Click(object sender, EventArgs e)
        {
            // 🌟 修复: 点击直接强行向串口插队发送一帧配置单字节 0xEB / 0xEA
            if (!_isSerialThreadRunning) return;

            //byte code = (cmbExperimentType.SelectedIndex == 0) ? (byte)0xEB : (byte)0xEA;
            byte code = (byte)0xEB;
            // 构造一条急停/切频单字节帧，或者改变共享内存中的 CommandCode 让底层循环发出
            lock (_mailbox.LockDownlink)
            {
                _mailbox.CommandCode = code;
            }
            LogToBox($"[协议强插] 下位机实验状态码已锁定并开始下发: 0x{code:X2}");
        }

        private void btnRun_Click(object sender, EventArgs e)
        {
            _isActivelySending = true;
            _experimentStartTime = DateTime.Now;
            // 构造一条开始单字节帧，或者改变共享内存中的 CommandCode 让底层循环发出
            byte code = (byte)0x10;

            lock (_mailbox.LockDownlink)
            {
                _mailbox.CommandCode = code;
            }

            // UI 互斥逻辑
            btnRun.Enabled = false;
            btnPause.Enabled = true;

            LogToBox("[系统使能] 运动闭环数据流激活！");
        }

        private void btnPause_Click(object sender, EventArgs e)
        {
            _isActivelySending = false;

            // 🌟 强发一帧急停协议 [0xA5] [0xFA] [0x00] [0x00] [CRC] [0x0A]
            lock (_mailbox.LockDownlink) { _mailbox.CommandCode = 0xFA; _mailbox.TargetPwm = 0; }

            // UI 互斥逻辑
            btnRun.Enabled = true;
            btnPause.Enabled = false;

            LogToBox("[安全熔断] 物理层急停锁死。单片机由于收不到新帧将安全怠速。");
        }

        private void btnReset_Click(object sender, EventArgs e)
        {
            lock (_mailbox.LockUplink)
            {
                _mailbox.TotalReceivedFrames = 0;
                _mailbox.TotalExpectedFrames = 0;
                _mailbox.CrcErrorCount = 0;
                _mailbox.LostFrameCount = 0;
                _mailbox.TotalValidBytes = 0;
                _mailbox.ArrivalJitterMs = 0;
            }
            lock (_mailbox.LockDownlink) { _mailbox.CommandCode = 0xEC; _mailbox.TargetPwm = 0; }
            _cacheTime.Clear(); _cacheSpeed.Clear(); _cachePwm.Clear(); _cacheRtt.Clear();
            _speedSeries.Points.Clear();
            _plotModel.InvalidatePlot(true);
            LogToBox("[洗刷完毕] 环形缓存区与数据表全量归零。");
        }

        private void btnSave_Click(object sender, EventArgs e)
        {
            if (_cacheTime.Count == 0)
            {
                MessageBox.Show("数据矩阵流为空，拒绝持久化！");
                return;
            }

            using (SaveFileDialog saveFileDialog = new SaveFileDialog())
            {
                saveFileDialog.Filter = "CSV控制矩阵 (*.csv)|*.csv";
                saveFileDialog.Title = "持久化保存";
                if (saveFileDialog.ShowDialog() == DialogResult.OK)
                {
                    try
                    {
                        using (StreamWriter sw = new StreamWriter(saveFileDialog.FileName, false, Encoding.UTF8))
                        {
                            sw.WriteLine("绝对时间轴(s),电机伺服反馈转速(rad/s),控制输出量(PWM),总线局部硬Rtt(ms)");
                            for (int i = 0; i < _cacheTime.Count; i++)
                            {
                                sw.WriteLine($"{_cacheTime[i]:F3},{_cacheSpeed[i]:F2},{_cachePwm[i]},{_cacheRtt[i]:F3}");
                            }
                        }
                        LogToBox($"[写盘成功] 数据落盘至: {saveFileDialog.FileName}");
                    }
                    catch (Exception ex) { MessageBox.Show($"写入崩溃: {ex.Message}"); }
                }
            }
        }

        // ==================== 6. OxyPlot 渲染与指标刷新 ====================
        private void DashboardTimer_Tick(object sender, EventArgs e)
        {
            float speed; long rttNs; double clientHz; int crcCount; int lostCount;
            int totalExpected; long totalValidBytes; double arrivalJitter;

            lock (_mailbox.LockUplink)
            {
                speed = _mailbox.ActualSpeed; rttNs = _mailbox.UartRttNs; clientHz = _mailbox.ClientHz;
                crcCount = _mailbox.CrcErrorCount; lostCount = _mailbox.LostFrameCount;
                totalExpected = _mailbox.TotalExpectedFrames; totalValidBytes = _mailbox.TotalValidBytes;
                arrivalJitter = _mailbox.ArrivalJitterMs;
            }

            int pwm; double cloudHz;
            lock (_mailbox.LockDownlink)
            {
                pwm = _mailbox.TargetPwm;
                cloudHz = _mailbox.GlobalTargetHz;
            }

            double elapsedSeconds = (DateTime.Now - _experimentStartTime).TotalSeconds;
            double uartRttMs = rttNs / 1_000_000.0;

            if (_isActivelySending)
            {
                if (_cacheTime.Count >= 1000)
                {
                    _cacheTime.RemoveAt(0); _cacheSpeed.RemoveAt(0); _cachePwm.RemoveAt(0); _cacheRtt.RemoveAt(0);
                }
                // 🌟 绘图与接收状态强绑定，只有收到新帧才产生波形
                _cacheTime.Add(elapsedSeconds); _cacheSpeed.Add(speed); _cachePwm.Add(pwm); _cacheRtt.Add(uartRttMs);

                _speedSeries.Points.Clear();
                for (int i = 0; i < _cacheTime.Count; i++)
                {
                    _speedSeries.Points.Add(new DataPoint(_cacheTime[i], _cacheSpeed[i]));
                }
                _plotModel.InvalidatePlot(true);
            }

            double crcErrorRate = totalExpected > 0 ? (double)crcCount / totalExpected * 100.0 : 0.0;
            double lostFrameRate = totalExpected > 0 ? (double)lostCount / totalExpected * 100.0 : 0.0;

            DateTime now = DateTime.Now;
            double timeDelta = (now - _lastThroughputCalcTime).TotalSeconds;
            if (timeDelta >= 1.0)
            {
                double effectiveThroughputBps = (totalValidBytes - _lastValidBytesCount) / timeDelta;
                _lastValidBytesCount = totalValidBytes;
                _lastThroughputCalcTime = now;
                txtThroughput.Text = $"{effectiveThroughputBps:F1} B/s";
            }

            txtTheoreticalHz.Text = $"{cloudHz:F1} Hz";
            txtUartRtt.Text = $"{uartRttMs:F3} ms";
            txtLossRate.Text = $"{lostFrameRate:F2} %";
            txtCrcErrorRate.Text = $"{crcErrorRate:F2} %";
            txtActualRxHz.Text = $"{clientHz:F1} Hz";
            txtArrivalJitter.Text = $"{arrivalJitter:F2} ms";
        }

        // ==================== 7. OxyPlot 初始化 ====================
        private void InitializeOxyPlotEngine()
        {
            _plotModel = new PlotModel { Title = "定长二进制协议高频测速曲线", TitleFontSize = 11 };
            var xAxis = new LinearAxis { Position = AxisPosition.Bottom, Title = "基准时间戳 (s)" };
            var yAxis = new LinearAxis { Position = AxisPosition.Left, Title = "测量转速" };
            _plotModel.Axes.Add(xAxis); _plotModel.Axes.Add(yAxis);

            _speedSeries = new LineSeries { Title = "测速值", Color = OxyColors.Navy, StrokeThickness = 2 };
            _plotModel.Series.Add(_speedSeries);
            plotViewSpeed.Model = _plotModel;
        }

        private void InitializeDashboardTimer()
        {
            _dashboardTimer = new System.Windows.Forms.Timer { Interval = 33 };
            _dashboardTimer.Tick += DashboardTimer_Tick;
            _dashboardTimer.Start();
        }

        private void LogToBox(string logText)
        {
            if (rtxtSystemLog.InvokeRequired)
            {
                rtxtSystemLog.Invoke(new Action(() => LogToBox(logText)));
            }
            else
            {
                rtxtSystemLog.AppendText($"[{DateTime.Now:HH:mm:ss.fff}] {logText}\r\n");
                rtxtSystemLog.ScrollToCaret();
            }
        }
    }
}
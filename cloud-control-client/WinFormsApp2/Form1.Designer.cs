namespace WinFormsApp2
{
    partial class Form1
    {
        /// <summary>
        ///  Required designer variable.
        /// </summary>
        private System.ComponentModel.IContainer components = null;

        /// <summary>
        ///  Clean up any resources being used.
        /// </summary>
        /// <param name="disposing">true if managed resources should be disposed; otherwise, false.</param>
        protected override void Dispose(bool disposing)
        {
            if (disposing && (components != null))
            {
                components.Dispose();
            }
            base.Dispose(disposing);
        }

        #region Windows Form Designer generated code

        /// <summary>
        ///  Required method for Designer support - do not modify
        ///  the contents of this method with the code editor.
        /// </summary>
        private void InitializeComponent()
        {
            groupBox1 = new GroupBox();
            txtCloudPort = new TextBox();
            btnToggleNetwork = new Button();
            txtCloudIp = new TextBox();
            label2 = new Label();
            label1 = new Label();
            groupBox2 = new GroupBox();
            btnToggleSerial = new Button();
            cmbSerialPort = new ComboBox();
            cmbBaudRate = new ComboBox();
            label4 = new Label();
            label3 = new Label();
            groupBox3 = new GroupBox();
            btnApplyExperiment = new Button();
            cmbExperimentType = new ComboBox();
            label5 = new Label();
            groupBox4 = new GroupBox();
            btnSave = new Button();
            btnReset = new Button();
            btnPause = new Button();
            btnRun = new Button();
            groupBox5 = new GroupBox();
            rtxtSystemLog = new RichTextBox();
            plotView1 = new OxyPlot.WindowsForms.PlotView();
            plotViewSpeed = new OxyPlot.WindowsForms.PlotView();
            groupBox6 = new GroupBox();
            groupBox7 = new GroupBox();
            txtTheoreticalHz = new TextBox();
            label6 = new Label();
            txtArrivalJitter = new TextBox();
            label12 = new Label();
            txtActualRxHz = new TextBox();
            txtThroughput = new TextBox();
            txtCrcErrorRate = new TextBox();
            txtLossRate = new TextBox();
            txtUartRtt = new TextBox();
            label11 = new Label();
            label10 = new Label();
            label9 = new Label();
            label8 = new Label();
            label7 = new Label();
            groupBox1.SuspendLayout();
            groupBox2.SuspendLayout();
            groupBox3.SuspendLayout();
            groupBox4.SuspendLayout();
            groupBox5.SuspendLayout();
            groupBox6.SuspendLayout();
            groupBox7.SuspendLayout();
            SuspendLayout();
            // 
            // groupBox1
            // 
            groupBox1.Controls.Add(txtCloudPort);
            groupBox1.Controls.Add(btnToggleNetwork);
            groupBox1.Controls.Add(txtCloudIp);
            groupBox1.Controls.Add(label2);
            groupBox1.Controls.Add(label1);
            groupBox1.Location = new Point(641, 94);
            groupBox1.Name = "groupBox1";
            groupBox1.Size = new Size(324, 222);
            groupBox1.TabIndex = 0;
            groupBox1.TabStop = false;
            groupBox1.Text = "2. 云主机通信网络配置";
            groupBox1.UseWaitCursor = true;
            // 
            // txtCloudPort
            // 
            txtCloudPort.Location = new Point(95, 119);
            txtCloudPort.Name = "txtCloudPort";
            txtCloudPort.Size = new Size(196, 30);
            txtCloudPort.TabIndex = 5;
            txtCloudPort.Text = "50008";
            txtCloudPort.UseWaitCursor = true;
            // 
            // btnToggleNetwork
            // 
            btnToggleNetwork.Location = new Point(6, 182);
            btnToggleNetwork.Name = "btnToggleNetwork";
            btnToggleNetwork.Size = new Size(306, 34);
            btnToggleNetwork.TabIndex = 4;
            btnToggleNetwork.Text = "建立云端网络连接";
            btnToggleNetwork.UseVisualStyleBackColor = true;
            btnToggleNetwork.UseWaitCursor = true;
            btnToggleNetwork.Click += btnToggleNetwork_Click;
            // 
            // txtCloudIp
            // 
            txtCloudIp.Location = new Point(95, 62);
            txtCloudIp.Name = "txtCloudIp";
            txtCloudIp.Size = new Size(196, 30);
            txtCloudIp.TabIndex = 2;
            txtCloudIp.Text = "127.0.0.1";
            txtCloudIp.UseWaitCursor = true;
            // 
            // label2
            // 
            label2.AutoSize = true;
            label2.Location = new Point(3, 119);
            label2.Name = "label2";
            label2.Size = new Size(86, 24);
            label2.TabIndex = 1;
            label2.Text = "网关端口:";
            label2.UseWaitCursor = true;
            // 
            // label1
            // 
            label1.AutoSize = true;
            label1.Location = new Point(0, 62);
            label1.Name = "label1";
            label1.Size = new Size(89, 24);
            label1.TabIndex = 0;
            label1.Text = "云主机 IP:";
            label1.UseWaitCursor = true;
            // 
            // groupBox2
            // 
            groupBox2.Controls.Add(btnToggleSerial);
            groupBox2.Controls.Add(cmbSerialPort);
            groupBox2.Controls.Add(cmbBaudRate);
            groupBox2.Controls.Add(label4);
            groupBox2.Controls.Add(label3);
            groupBox2.Location = new Point(44, 84);
            groupBox2.Name = "groupBox2";
            groupBox2.Size = new Size(384, 232);
            groupBox2.TabIndex = 1;
            groupBox2.TabStop = false;
            groupBox2.Text = "1. 被控对象物理串口连接";
            // 
            // btnToggleSerial
            // 
            btnToggleSerial.Location = new Point(17, 198);
            btnToggleSerial.Name = "btnToggleSerial";
            btnToggleSerial.Size = new Size(315, 34);
            btnToggleSerial.TabIndex = 4;
            btnToggleSerial.Text = "打开物理串口";
            btnToggleSerial.UseVisualStyleBackColor = true;
            btnToggleSerial.Click += btnToggleSerial_Click;
            // 
            // cmbSerialPort
            // 
            cmbSerialPort.FormattingEnabled = true;
            cmbSerialPort.Items.AddRange(new object[] { "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8" });
            cmbSerialPort.Location = new Point(139, 70);
            cmbSerialPort.Name = "cmbSerialPort";
            cmbSerialPort.Size = new Size(182, 32);
            cmbSerialPort.TabIndex = 3;
           // cmbSerialPort.SelectedIndexChanged += cmbSerialPort_SelectedIndexChanged;
            // 
            // cmbBaudRate
            // 
            cmbBaudRate.FormattingEnabled = true;
            cmbBaudRate.Items.AddRange(new object[] { "9600", "57600", "115200" });
            cmbBaudRate.Location = new Point(139, 129);
            cmbBaudRate.Name = "cmbBaudRate";
            cmbBaudRate.Size = new Size(182, 32);
            cmbBaudRate.TabIndex = 2;
            // 
            // label4
            // 
            label4.AutoSize = true;
            label4.Location = new Point(17, 135);
            label4.Name = "label4";
            label4.Size = new Size(100, 24);
            label4.TabIndex = 1;
            label4.Text = "波特率选择";
            // 
            // label3
            // 
            label3.AutoSize = true;
            label3.Location = new Point(17, 75);
            label3.Name = "label3";
            label3.Size = new Size(82, 24);
            label3.TabIndex = 0;
            label3.Text = "端口选择";
            // 
            // groupBox3
            // 
            groupBox3.Controls.Add(btnApplyExperiment);
            groupBox3.Controls.Add(cmbExperimentType);
            groupBox3.Controls.Add(label5);
            groupBox3.Location = new Point(1258, 94);
            groupBox3.Name = "groupBox3";
            groupBox3.Size = new Size(373, 222);
            groupBox3.TabIndex = 2;
            groupBox3.TabStop = false;
            groupBox3.Text = "3. 实验配置选择";
            // 
            // btnApplyExperiment
            // 
            btnApplyExperiment.Location = new Point(6, 182);
            btnApplyExperiment.Name = "btnApplyExperiment";
            btnApplyExperiment.Size = new Size(348, 34);
            btnApplyExperiment.TabIndex = 4;
            btnApplyExperiment.Text = "确定实验配置";
            btnApplyExperiment.UseVisualStyleBackColor = true;
            btnApplyExperiment.Click += btnApplyExperiment_Click;
            // 
            // cmbExperimentType
            // 
            cmbExperimentType.FormattingEnabled = true;
            cmbExperimentType.Items.AddRange(new object[] { "速度闭环实验", "温度闭环实验" });
            cmbExperimentType.Location = new Point(151, 50);
            cmbExperimentType.Name = "cmbExperimentType";
            cmbExperimentType.Size = new Size(182, 32);
            cmbExperimentType.TabIndex = 1;
            // 
            // label5
            // 
            label5.AutoSize = true;
            label5.Location = new Point(6, 53);
            label5.Name = "label5";
            label5.Size = new Size(82, 24);
            label5.TabIndex = 0;
            label5.Text = "实验选择";
            // 
            // groupBox4
            // 
            groupBox4.Controls.Add(btnSave);
            groupBox4.Controls.Add(btnReset);
            groupBox4.Controls.Add(btnPause);
            groupBox4.Controls.Add(btnRun);
            groupBox4.Location = new Point(44, 440);
            groupBox4.Name = "groupBox4";
            groupBox4.Size = new Size(579, 137);
            groupBox4.TabIndex = 3;
            groupBox4.TabStop = false;
            groupBox4.Text = "功能框";
            // 
            // btnSave
            // 
            btnSave.Location = new Point(419, 54);
            btnSave.Name = "btnSave";
            btnSave.Size = new Size(112, 57);
            btnSave.TabIndex = 3;
            btnSave.Text = "保存";
            btnSave.UseVisualStyleBackColor = true;
            btnSave.Click += btnSave_Click;
            // 
            // btnReset
            // 
            btnReset.Location = new Point(272, 54);
            btnReset.Name = "btnReset";
            btnReset.Size = new Size(112, 57);
            btnReset.TabIndex = 2;
            btnReset.Text = "复位";
            btnReset.UseVisualStyleBackColor = true;
            btnReset.Click += btnReset_Click;
            // 
            // btnPause
            // 
            btnPause.Enabled = false;
            btnPause.Location = new Point(139, 54);
            btnPause.Name = "btnPause";
            btnPause.Size = new Size(112, 57);
            btnPause.TabIndex = 1;
            btnPause.Text = "暂停";
            btnPause.UseVisualStyleBackColor = true;
            btnPause.Click += btnPause_Click;
            // 
            // btnRun
            // 
            btnRun.Location = new Point(6, 54);
            btnRun.Name = "btnRun";
            btnRun.Size = new Size(112, 57);
            btnRun.TabIndex = 0;
            btnRun.Text = "运行";
            btnRun.UseVisualStyleBackColor = true;
            btnRun.Click += btnRun_Click;
            // 
            // groupBox5
            // 
            groupBox5.Controls.Add(rtxtSystemLog);
            groupBox5.Controls.Add(plotView1);
            groupBox5.Location = new Point(50, 647);
            groupBox5.Name = "groupBox5";
            groupBox5.Size = new Size(300, 238);
            groupBox5.TabIndex = 4;
            groupBox5.TabStop = false;
            groupBox5.Text = "日志框";
            // 
            // rtxtSystemLog
            // 
            rtxtSystemLog.Location = new Point(21, 40);
            rtxtSystemLog.Name = "rtxtSystemLog";
            rtxtSystemLog.Size = new Size(234, 192);
            rtxtSystemLog.TabIndex = 8;
            rtxtSystemLog.Text = "";
            // 
            // plotView1
            // 
            plotView1.Location = new Point(3, 26);
            plotView1.Name = "plotView1";
            plotView1.PanCursor = Cursors.Hand;
            plotView1.Size = new Size(112, 34);
            plotView1.TabIndex = 1;
            plotView1.Text = "plotView1";
            plotView1.ZoomHorizontalCursor = Cursors.SizeWE;
            plotView1.ZoomRectangleCursor = Cursors.SizeNWSE;
            plotView1.ZoomVerticalCursor = Cursors.SizeNS;
            // 
            // plotViewSpeed
            // 
            plotViewSpeed.Location = new Point(71, 54);
            plotViewSpeed.Name = "plotViewSpeed";
            plotViewSpeed.PanCursor = Cursors.Hand;
            plotViewSpeed.Size = new Size(415, 345);
            plotViewSpeed.TabIndex = 5;
            plotViewSpeed.ZoomHorizontalCursor = Cursors.SizeWE;
            plotViewSpeed.ZoomRectangleCursor = Cursors.SizeNWSE;
            plotViewSpeed.ZoomVerticalCursor = Cursors.SizeNS;
            // 
            // groupBox6
            // 
            groupBox6.Controls.Add(plotViewSpeed);
            groupBox6.Location = new Point(721, 440);
            groupBox6.Name = "groupBox6";
            groupBox6.Size = new Size(562, 439);
            groupBox6.TabIndex = 6;
            groupBox6.TabStop = false;
            groupBox6.Text = "绘图区";
            // 
            // groupBox7
            // 
            groupBox7.Controls.Add(txtTheoreticalHz);
            groupBox7.Controls.Add(label6);
            groupBox7.Controls.Add(txtArrivalJitter);
            groupBox7.Controls.Add(label12);
            groupBox7.Controls.Add(txtActualRxHz);
            groupBox7.Controls.Add(txtThroughput);
            groupBox7.Controls.Add(txtCrcErrorRate);
            groupBox7.Controls.Add(txtLossRate);
            groupBox7.Controls.Add(txtUartRtt);
            groupBox7.Controls.Add(label11);
            groupBox7.Controls.Add(label10);
            groupBox7.Controls.Add(label9);
            groupBox7.Controls.Add(label8);
            groupBox7.Controls.Add(label7);
            groupBox7.ImeMode = ImeMode.Disable;
            groupBox7.Location = new Point(1375, 440);
            groupBox7.Name = "groupBox7";
            groupBox7.Size = new Size(432, 454);
            groupBox7.TabIndex = 7;
            groupBox7.TabStop = false;
            groupBox7.Text = "通信指标监控区";
            // 
            // txtTheoreticalHz
            // 
            txtTheoreticalHz.Location = new Point(183, 44);
            txtTheoreticalHz.Name = "txtTheoreticalHz";
            txtTheoreticalHz.Size = new Size(150, 30);
            txtTheoreticalHz.TabIndex = 12;
            // 
            // label6
            // 
            label6.AutoSize = true;
            label6.Location = new Point(6, 47);
            label6.Name = "label6";
            label6.Size = new Size(118, 24);
            label6.TabIndex = 11;
            label6.Text = "理论通信速率";
            // 
            // txtArrivalJitter
            // 
            txtArrivalJitter.Location = new Point(183, 398);
            txtArrivalJitter.Name = "txtArrivalJitter";
            txtArrivalJitter.Size = new Size(150, 30);
            txtArrivalJitter.TabIndex = 10;
            // 
            // label12
            // 
            label12.AutoSize = true;
            label12.Location = new Point(6, 388);
            label12.Name = "label12";
            label12.Size = new Size(118, 24);
            label12.TabIndex = 9;
            label12.Text = "下发间隔抖动";
            // 
            // txtActualRxHz
            // 
            txtActualRxHz.Location = new Point(183, 336);
            txtActualRxHz.Name = "txtActualRxHz";
            txtActualRxHz.Size = new Size(150, 30);
            txtActualRxHz.TabIndex = 8;
            // 
            // txtThroughput
            // 
            txtThroughput.Location = new Point(183, 270);
            txtThroughput.Name = "txtThroughput";
            txtThroughput.Size = new Size(150, 30);
            txtThroughput.TabIndex = 7;
            // 
            // txtCrcErrorRate
            // 
            txtCrcErrorRate.Location = new Point(183, 210);
            txtCrcErrorRate.Name = "txtCrcErrorRate";
            txtCrcErrorRate.Size = new Size(150, 30);
            txtCrcErrorRate.TabIndex = 6;
            // 
            // txtLossRate
            // 
            txtLossRate.Location = new Point(183, 148);
            txtLossRate.Name = "txtLossRate";
            txtLossRate.Size = new Size(150, 30);
            txtLossRate.TabIndex = 5;
            // 
            // txtUartRtt
            // 
            txtUartRtt.Location = new Point(183, 96);
            txtUartRtt.Name = "txtUartRtt";
            txtUartRtt.Size = new Size(150, 30);
            txtUartRtt.TabIndex = 0;
            // 
            // label11
            // 
            label11.AutoSize = true;
            label11.Location = new Point(6, 339);
            label11.Name = "label11";
            label11.Size = new Size(118, 24);
            label11.TabIndex = 4;
            label11.Text = "实际接收速率";
            // 
            // label10
            // 
            label10.AutoSize = true;
            label10.Location = new Point(6, 276);
            label10.Name = "label10";
            label10.Size = new Size(118, 24);
            label10.TabIndex = 3;
            label10.Text = "串口有效吞吐";
            // 
            // label9
            // 
            label9.AutoSize = true;
            label9.Location = new Point(6, 216);
            label9.Name = "label9";
            label9.Size = new Size(100, 24);
            label9.TabIndex = 2;
            label9.Text = "校验错误率";
            // 
            // label8
            // 
            label8.AutoSize = true;
            label8.Location = new Point(6, 151);
            label8.Name = "label8";
            label8.Size = new Size(64, 24);
            label8.TabIndex = 1;
            label8.Text = "丢帧率";
            // 
            // label7
            // 
            label7.AutoSize = true;
            label7.Location = new Point(0, 99);
            label7.Name = "label7";
            label7.Size = new Size(160, 24);
            label7.TabIndex = 0;
            label7.Text = "串口往返 RTT 时延";
            // 
            // Form1
            // 
            AutoScaleDimensions = new SizeF(11F, 24F);
            AutoScaleMode = AutoScaleMode.Font;
            AutoScroll = true;
            AutoSizeMode = AutoSizeMode.GrowAndShrink;
            ClientSize = new Size(1889, 897);
            Controls.Add(groupBox7);
            Controls.Add(groupBox6);
            Controls.Add(groupBox5);
            Controls.Add(groupBox4);
            Controls.Add(groupBox3);
            Controls.Add(groupBox2);
            Controls.Add(groupBox1);
            Name = "Form1";
            Text = "电机伺服边缘测控大屏系统";
            groupBox1.ResumeLayout(false);
            groupBox1.PerformLayout();
            groupBox2.ResumeLayout(false);
            groupBox2.PerformLayout();
            groupBox3.ResumeLayout(false);
            groupBox3.PerformLayout();
            groupBox4.ResumeLayout(false);
            groupBox5.ResumeLayout(false);
            groupBox6.ResumeLayout(false);
            groupBox7.ResumeLayout(false);
            groupBox7.PerformLayout();
            ResumeLayout(false);
        }

        #endregion

        private GroupBox groupBox1;
        private Label label1;
        private Label label2;
        private TextBox txtCloudIp;
        private Button btnToggleNetwork;
        private TextBox txtCloudPort;
        private GroupBox groupBox2;
        private Label label3;
        private Label label4;
        private ComboBox cmbBaudRate;
        private ComboBox cmbSerialPort;
        private Button btnToggleSerial;
        private GroupBox groupBox3;
        private Label label5;
        private ComboBox cmbExperimentType;
        private Button btnApplyExperiment;
        private GroupBox groupBox4;
        private Button btnRun;
        private Button btnSave;
        private Button btnReset;
        private Button btnPause;
        private GroupBox groupBox5;
        private TextBox textBox2;
        private OxyPlot.WindowsForms.PlotView plotView1;
        private OxyPlot.WindowsForms.PlotView plotViewSpeed;
        private GroupBox groupBox6;
        private GroupBox groupBox7;
        private Label label11;
        private Label label10;
        private Label label9;
        private Label label8;
        private Label label7;
        private TextBox txtActualRxHz;
        private TextBox txtThroughput;
        private TextBox txtCrcErrorRate;
        private TextBox txtLossRate;
        private TextBox txtUartRtt;
        private TextBox txtArrivalJitter;
        private Label label12;
        private RichTextBox rtxtSystemLog;
        private Label label6; // 🌟 修复处的变量声明
        private TextBox txtTheoreticalHz;
    }
}
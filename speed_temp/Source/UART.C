#include <reg51.h>
#include <UART.h>

// ==================== 全局标志位与变量声明 ====================
bit RI_Flag = 0;
bit Start_Flag = 0;
bit Reset_Flag = 0;
bit open_loop = 0;
bit close_loop = 0;
bit speed_flag = 1; // 默认直接切入速度实验
bit temp_flag = 0;
unsigned int uSpeed = 0; // 存放从上位机解算出的 PWM 占空比
bit U_Flag = 0;

// 引入系统全局的实际测速变量（此变量在 Display.c 或 main.c 中维护）
extern unsigned int speed;
extern bit flag_send; 
 bit flag_start = 0;
// ======== bit flag_start = 0;============ 协议状态机与缓存定义 ====================
unsigned char rx_buf[7];    // 下行控制帧接收缓存 (定长 7 字节)
unsigned char rx_cnt = 0;   // 接收帧长计数器
unsigned char tx_seq = 0;   // 上行状态帧循环流水号 (0~255)

// ==================== 波特率配置函数 ====================
void UART_init(unsigned int baud)
{
    SCON = 0x50;                              // 方式1，8位UART，可变波特率
    PCON = 0X80;                            
    AUXR = 0x11;                              // 独立波特率发生器，12T模式
    BTR  = 256-(11059200/6/32)/baud;
    ES   = 1;
    PS   = 1;                                 // 串口中断设为最高优先级
}

// ==================== 基础字节发送函数 ====================
void SendByte(unsigned char dat)
{
    SBUF = dat;
    while(TI == 0);
    TI = 0;
}

// ==================== 工业级 Modbus CRC16 算法 ====================
unsigned int Calculate_CRC16(unsigned char *puchMsg, unsigned int usDataLen)
{
    unsigned int crc = 0xFFFF;
    unsigned int i, j;
    for(i = 0; i < usDataLen; i++)
    {
        crc ^= puchMsg[i];
        for(j = 0; j < 8; j++)
        {
            if(crc & 1)
            {
                crc >>= 1;
                crc ^= 0xA001;
            }
            else
            {
                crc >>= 1;
            }
        }
    }
    return crc;
}

// ==================== [上行] 发送 8 字节定长物理状态帧 ====================
// 协议格式: [0x5A] [Seq] [Speed_H] [Speed_L] [0x00] [CRC_L] [CRC_H] [0x0D]
void Send_Reply_Frame(void)
{
    unsigned char tx_buf[8];
    unsigned int crc;
    unsigned char i;

    tx_buf[0] = 0x5A;                        // 帧头
    tx_buf[1] = tx_seq++;                    // 循环流水号，自增
    tx_buf[2] = (unsigned char)(speed >> 8); // 转速高8位
    tx_buf[3] = (unsigned char)(speed & 0xFF); // 转速低8位
    tx_buf[4] = 0x00;                        // 预留对齐位

    // 计算 0~4 字节的 CRC16
    crc = Calculate_CRC16(tx_buf, 5);
    tx_buf[5] = (unsigned char)(crc & 0xFF);        // 校验和低字节
    tx_buf[6] = (unsigned char)((crc >> 8) & 0xFF); // 校验和高字节
    tx_buf[7] = 0x0D;                        // 帧尾 \r

    // 阻塞式全量发送
    for(i = 0; i < 8; i++)
    {
        SendByte(tx_buf[i]);
    }
}

// ==================== 核心协议解析路由 ====================
void Process_Command(unsigned char cmd, unsigned char pwm)
{
    if(cmd == 0xFA) 
    {
        // 暂停/急停指令
        RI_Flag = 1;
        uSpeed = 0;
    } 
    else if(cmd == 0xEB) 
    {
        // 闭环配置下发
        close_loop = 1;
        Start_Flag = 1;
			
    } 
    else if(cmd == 0xEA) 
    {
        // 开环配置下发
        open_loop = 1;
        Start_Flag = 1;
    } 
    else if(cmd == 0x10) 
    {
        // 正常 PWM 控制流
				flag_start = 1;
        U_Flag = 1;
        uSpeed = pwm; // 直接赋值 8位 PWM 占空比
    } 
    else if (cmd == 0xEC) 
    {
        // 软件复位
        Reset_Flag = 1;
    }
		if(flag_start == 1){
			    // ?? 核心逻辑：以收驱发。只要收到并校验通过了合法帧，必须立刻回馈转速
    Send_Reply_Frame();
		
		}


}

// ==================== 串口中断服务函数 (接收状态机) ====================
// 期待的下行帧: [0xA5] [Cmd] [PWM] [0x00] [CRC_L] [CRC_H] [0x0A]
void uart() interrupt 4
{
    if(RI)
    {
        unsigned char ch;
        RI = 0;
        ch = SBUF;

        // 状态机：寻找帧头
        if (rx_cnt == 0)
        {
            if (ch == 0xA5) 
            {
                rx_buf[rx_cnt++] = ch;
            }
        }
        else
        {
            rx_buf[rx_cnt++] = ch;
            
            // 收到完整的 7 字节
            if (rx_cnt >= 7)
            {
                rx_cnt = 0; // 游标复位，准备迎接下一帧
                
                // 验证帧尾
                if (rx_buf[6] == 0x0A)
                {
                    // 验证 CRC16
                    unsigned int crc_calc = Calculate_CRC16(rx_buf, 4);
                    unsigned int crc_recv = rx_buf[4] | (((unsigned int)rx_buf[5]) << 8);
                    
                    if (crc_calc == crc_recv)
										{
												                        // 校验通过，提取 Cmd 与 PWM，执行系统路由
                       // while(!flag_send){}; // 等待定时器 T0 触发，确保系统状态更新完毕
                        Process_Command(rx_buf[1], rx_buf[2]);
												U_Flag = 1;
                        flag_send = 0; // 清除定时器标志，准备下一次触发
										}


                        
                   
                }
            }
        }
    }
}
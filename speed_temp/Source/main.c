#include <reg51.h>
#include <intrins.h>
#include <UART.h>
#include <PWM.h>
#include <DS18B20.h>
#include <STC12C5A60S2.h>
#include <HEATERDRIVE.h>
#include <TM1638.h>
#include <Display.h>
#include <MOTORDRIVE.h>
#include <stdio.h>
#include <string.h>
#include <math.h>

sfr ISP_CONTR = 0xC7;
bit RI_Flag_Control = 0;
// bit open_loop_setpwm_flag = 1;
unsigned int TempShow = 0;
//unsigned int Temp12filterNum = 0;
// float TempShow1 = 0.0;
bit flag_send = 0;

void main()
{  	 
      TM1638_Init();  // 数码管初始化,通用
	  Port_init();	   	  
//	  ENMotor();
//	  PWM_init();  
      UART_init(57600);	  // 串口中断初始化,通用   57600  19200
	  EA=1;	 
//    Time0_Config();	// 速度实验定时器配置
//	  ConfigINT0();
	  while(1)	  //等待接收串口数据，选择温度实验还是速度实验
	  {
	  	speed_flag = 1;
		 if(speed_flag == 1)	// 速度实验
		 {
		 	Time0_Config();
			ConfigINT0();
			break;		
		 }
		 if(temp_flag == 1)	   // 温度实验
		 {
		   	Heater_Init();	 // 加热片初始化
			ConfigT1M1();	// 定时器初始化
			ENHeater();	   //启动加热片
		 	break;
		 }
	  } 
	  while(1)	  //等待接收串口数据，跳出条件close_loop=1或open_loop=1
	  {
		 if(close_loop == 1 || open_loop == 1)
		 {
		 	break;		
		 }
	  } 
	  if(speed_flag == 1 && close_loop == 1)	  // 速度闭环实验
	  {           	  
		  while(1)
		  {		
		  		// 产生定时器中断	
				if(timer0flag == 1 && RI_Flag_Control == 1)
				{			
					timer0flag = 0;
					flag_send = 1;
//					PWM0_Setting(uSpeed);
					// 发送当前速度
					//SendData(speed);
					// 通过设定一个大致的占空比，来保持当前速度不变
					PWM0_Setting(keepSpeed(speed));
					// 显示速度	
					displaySpeed(speed);								     				
				}
				// 接收到控制量，产生串口中断处理
				if(U_Flag == 1 && RI_Flag_Control == 1)
				{
					U_Flag = 0;//在串口协议接收到信息后     置1  可以改变控制量了
					// 设定占空比,开启速度改变
					PWM0_Setting(uSpeed);
					// 开启定时器定时5ms
					TR0 = 1;
					// 开启脉冲计数定时
					EX0=1;
					//SendData(speed);		
				}		
				if(Start_Flag == 1)
				{
					Start_Flag = 0;
					InitMotor();
					ENMotor();
					PWM_init();
					speed = 0;
					//SendData(speed);  //第一次发送初始速度0
					RI_Flag_Control = 1;
				}
				if(RI_Flag)	   // 停止运行,关闭pwm,初始化电机,串口发送停止
				{
					RI_Flag = 0;
					ClosePWM0();
					InitMotor();
					RI_Flag_Control = 0;	// 关闭数码管显示,关闭PWM设置和数据发送			 
				}
				if(Reset_Flag)	  //软件复位,清除当前实验中的数据,从main函数开始执行，需要重新设置开环和闭环实验 
				{
					ISP_CONTR = 0x20;	//产生软件复位
				}				   
	     }
	  }
	  if(speed_flag == 1 && open_loop == 1)	  // 速度开环实验
	  {	  	           	  
		  while(1)
		  {			
				if(timer0flag == 1 && RI_Flag_Control == 1)
				{			
					timer0flag = 0;
					TR0 = 1;	// 开启定时器0定时
					EX0 = 1;	// 开启脉冲计数定时器 
					SendData(speed);	
					displaySpeed(speed);								     				
				}		
				if(Start_Flag == 1)
				{
					Start_Flag = 0;
					InitMotor();	// 速度实验特有，电机初始化
					ENMotor();
					PWM_init();
					TR0 = 1;	// 开启定时器0定时
					EX0 = 1;	// 开启脉冲计数定时器 
					speed = 0;
					SendData(speed);  //第一次发送初始速度0
					RI_Flag_Control = 1;
					PWM0_Setting(uSpeed);	//设定串口接收到的占空比
				}
				if(RI_Flag)	   // 停止运行,关闭pwm,初始化电机,串口发送停止
				{
					RI_Flag = 0;
					ClosePWM0();
					InitMotor();
					RI_Flag_Control = 0;	// 关闭数码管显示,关闭PWM设置和数据发送			 
				}
				if(Reset_Flag)	  //软件复位,清除当前实验中的数据,从main函数开始执行，需要重新设置开环和闭环实验 
				{
					ISP_CONTR = 0x20;	//产生软件复位
				}				   
	     }
	  }
	  if(temp_flag == 1 && close_loop == 1)	// 温度闭环实验
	  {
	  		while(1)
			{
				if(timer1flag == 1 && RI_Flag_Control == 1)
				{
					timer1flag=0;
					displayTemp(TempShow);
					PreReadTemp();		
					TempShow = ReadTemp();
					if(TempShow < 8000){SendData(TempShow);}		//防止ds18b20初始读数85			
				}
				if(U_Flag == 1 && RI_Flag_Control == 1)
				{
					U_Flag = 0;
					SetPWM1(uSpeed);	
				}
				if(Start_Flag == 1)
				{
					Start_Flag = 0;
					PWM_init();
					displayTemp(0000);
					RI_Flag_Control = 1;	
				}
				if(RI_Flag)
				{
					RI_Flag = 0;
					ClosePWM1();
//					Heater_Init();
					RI_Flag_Control = 0;
				}
				if(Reset_Flag)
				{
					ISP_CONTR=0X20;  
		  		}
			}
	  }
	  if(temp_flag == 1 && open_loop == 1)	  // 温度开环实验
	  {
			while(1)
			{
			 	if(timer1flag == 1 && RI_Flag_Control == 1)
				{
					timer1flag=0;
					PreReadTemp();
					TempShow = ReadTemp();
					displayTemp(TempShow);
					if(TempShow < 8000){SendData(TempShow);}	//防止ds18b20初始读数85				
				}
				if(Start_Flag == 1)
				{
					Start_Flag = 0;
					PWM_init();
					SetPWM1(uSpeed);
					displayTemp(0000);
					RI_Flag_Control = 1;	
				}
				if(RI_Flag == 1)
				{
				    RI_Flag = 0;
					ClosePWM1();
//					Heater_Init();
					RI_Flag_Control = 0;
				}
				if(Reset_Flag == 1)
				{
					ISP_CONTR=0X20;  
				}
			}				
	  } 	  
}



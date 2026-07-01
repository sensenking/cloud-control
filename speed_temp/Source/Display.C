#include <reg51.h>
#include <intrins.h>
#include <TM1638.h>
#include <Display.h>
#include <UART.h>

// TIME用于速度实验定时设置
// 5ms:4608, 10ms:9216, 15ms:13824, 20ms:18432, 25ms:23040, 30ms:27648, 40ms:36864, 50ms:46080, 60ms:55296, 70ms:64512
#define TIME      4608
// 5ms:125, 10ms:63, 15ms:42, 20ms:31, 25ms:25, 30ms:21, 40ms:16, 50ms:13, 60ms:10, 70ms:9
#define pulse_time 125
// TIME1用于温度实验定时设置
#define TIME1 46080

const unsigned char  CathodeCode[11] = {0X3F, 0X06, 0X5B, 0X4F, 0X66, 0X6D, 0X7D, 0X07, 0X7F, 0X6F, 0X80};
const unsigned char  CathodeCode1[11] = {0xBF, 0X86, 0XDB, 0xCF, 0XE6, 0XED, 0XFD, 0X87, 0XFF, 0XEF};
//unsigned int num1,num2,num3,num4;
// unsigned char i; 

unsigned int pulse = 0;
unsigned int speed = 0;
bit timer0flag = 0;
bit timer1flag = 0;
// unsigned int timer0flag_i = 0;


void delay(unsigned int x)
{
	
	while(--x);
}
// 用于速度定时															 
void Time0_Config()
{
	TMOD = 0X01;
  /*******Setting the time *********************/
	TH0=(65536-TIME)>>8;      
	TL0=(65536-TIME)&0x00ff;
	/*******unenable the timer**********************/
	TR0 = 0;
	/*******Enable the Overflow interrupt*********/
	ET0 = 1;
}
// 用于温度定时
void ConfigT1M1()
{
	TMOD&=0X0f;
	TMOD|=0X10;
	TH1=(65536-TIME1)/256;  
	TL1=(65536-TIME1)%256;
	ET1=1;
	TR1=1;
	PT1=1;
}
//void Time1_Config()
//{
//	TMOD |= 0X10;
//  /*******Setting the time *********************/
//	TH1=(65536-TIME1)>>8;      
//	TL1=(65536-TIME1)&0x00ff;
//	/*******Enable the timer**********************/
//	TR1=1;
//	/*******Enable the Overflow interrupt*********/
//	ET1=1;
//}

void ConfigINT0()
{
	/**********unenable the external interrupt*****/
	EX0=0;		   // 关闭外部中断
	/***************Edge_triggered***************/
	IT0=1;		   // 低电平后产生外部中断
}
// 速度实验外部中断
void Int0() interrupt 0 
{
		pulse++;
}
// 速度实验定时器中断
void Timer0() interrupt 1  
{
	if(speed_flag == 1)
	{
	 	//	static unsigned char i=0;
		TH0=(65536-TIME)/256;    
		TL0=(65536-TIME)%256;
		speed = pulse*pulse_time;	  	//speed=(pulse*60)/(96*time); time = TIME*12*0.000001/11.0592
		// 关闭脉冲计数
		EX0 = 0;	 
		pulse = 0;
		// 关闭定时
		TR0 = 0;
		timer0flag=1;
	}
	else if(temp_flag == 1)
	{
		ET0 = 0;
		TR0 = 0;
	}

}

// 温度实验定时器中断, 定时1秒
void Timer1() interrupt 3  
{
	static unsigned char i=0;
	TH1=(65536-TIME1)/256;      
	TL1=(65536-TIME1)%256;
	i++;
	if(i==20)
	{ 
		 i=0;
		 timer1flag=1;
	}
}

//void Timer0() interrupt 1  
//{
////	static unsigned char i=0;
//	int i=0;
//	TH0=(65536-TIME)/256;    
//	TL0=(65536-TIME)%256;
//	i++;
//	if(i == 1)
//	{ 
//		 i = 0;
//		 speed = pulse*pulse_time;	  	//speed=(pulse*60)/(96*time); time = TIME*12*0.000001/11.0592
//		 pulse = 0;
//		 timer0flag = 1;
//	}
//}

//void Timer1() interrupt 3 
//{
//	unsigned char j = 0;
//	TH1 = (65536-TIME1)/256;    
//	TL1 = (65536-TIME1)%256;
//    j++;
//    if(j == 1)
//	{
//		j = 0;
//		timer1flag = 1;
//    }
//}

void displaySpeed(unsigned int num)
{
	   unsigned int num1,num2,num3,num4;
	   unsigned char i;
       num1 = num/1000;
	   num2 = num/100%10;  
	   num3 = num/10%10;
	   num4 = num%10;
	   for(i=0;i<5;i++)
	   { 
         Write_DATA(TM1638_DIG0,CathodeCode[num1]);
		 delay(2);
	     Write_DATA(TM1638_DIG1,CathodeCode[num2]);
		 delay(2);
		 Write_DATA(TM1638_DIG2,CathodeCode[num3]);
		 delay(2);
		 Write_DATA(TM1638_DIG3,CathodeCode[num4]);
      }
}

bit delay1(int x)
{
	int i,j;
	bit a=0;
	for(i=0;i<x;i++)
	{
		for(j=0;j<110;j++)
		{;}
	}
	a=1;
	return(a);
}

void displayTemp(unsigned int num)
{
	unsigned int num1,num2,num3,num4;
	unsigned char i;   
	num1 = num/1000;
	num2 = num/100%10; 
	num3 = num/10%10;
	num4 = num%10;
	for(i=0;i<10;i++)
	{		 
		Write_DATA(TM1638_DIG0,CathodeCode[num1]);
		delay1(10);
		Write_DATA(TM1638_DIG1,CathodeCode1[num2]);
		delay1(10);
		Write_DATA(TM1638_DIG2,CathodeCode[num3]);
		delay1(10);
		Write_DATA(TM1638_DIG3,CathodeCode[num4]);
     }
}

int keepSpeed(unsigned int spd)
{
	// 根据当前的speed计算出大概的控制量,四舍五入取整数
   	unsigned int u = 0;
	u = (spd + 850)/27;
	if(u > 170)
	{
		u = 170;	
	}
	return u;	
}




#include <reg51.h>
#include <intrins.h>
#include <DS18B20.h>

#define TIME100us  92  //100us
#define TIME10us  9  //10us
#define TIME1us   1
sbit DQ = P2^6;  
bit Reset;


//bit delay(int x)
//{
//	int i,j;
//	bit a=0;
//	for(i=0;i<x;i++)
//	{
//		for(j=0;j<110;j++)
//		{;}
//	}
//	a=1;
//	return(a);
//}

/****************************
 定时部分
*****************************/
void ConfigTimer0M2(unsigned char time)  //0~250us
{
	TMOD&=0XF0;
	TMOD|=0X02;
	TH0=256-time+6;
	TL0=TH0;
	ET0=1;
	TR0=1;
	EA=1;
}
//void Timer0Int() interrupt 1
//{
//
//	ET0=0;
//	TR0=0;
//}
void delay100us()
{
	ConfigTimer0M2(TIME100us);
	while(TR0);
}
void TimeDelay500us()
{
	delay100us();delay100us();
	delay100us();delay100us();
	delay100us();
}

void delay10us()
{
	ConfigTimer0M2(TIME10us);
	while(TR0);
}
void TimeDelay60us()
{
	delay10us();delay10us();delay10us();
	delay10us();delay10us();delay10us();
}
void  TimeDelay2us()
{
	_nop_();
	_nop_();
	_nop_();
	_nop_();
	_nop_();
	_nop_();
}
/***************************DS18B20  Operation***************************/
bit DS18B20_Reset(void)          // Return value:0--Fail  1--Success
{
	DQ=0;
	TimeDelay500us();
	DQ=1;
	TimeDelay60us();
	if(DQ)
	{
		return 0;
	}
	else
	{
		while(!DQ);
		return 1;
	}
}

/****************************Write one bit******************************/
void WriteBit(bit w)
{
	DQ=0;
	TimeDelay2us();
	if(w)
	{
		DQ=1;
	}
	TimeDelay60us();
	if(!w)
	{
		DQ=1;
		TimeDelay2us();
	}
}
/******************************Read one bit****************************/
bit ReadBit(void)
{
	DQ=0;
	TimeDelay2us();
	DQ=1;
	TimeDelay2us();
	if(DQ) 
	{
		TimeDelay60us();
		return(1);
	}
	else
	{
		//TimeDelay60us();//不能去掉，虽然数据手册上没要求
		TimeDelay60us();//不能去掉，虽然数据手册上没要求
		TimeDelay2us();
		return(0);
	}
}
/***********写1字节**************/
void WriteByte(unsigned char byt)
{
	unsigned char i;
	for(i=0;i<8;i++)
	{
		WriteBit(byt&0x01);
		byt=byt>>1;
	}
}
/***********读1字节**************/
unsigned char ReadByte(void)
{
	bit r;
	unsigned char i,dat;
	for(i=0;i<8;i++)
	{
		r=ReadBit();
		dat>>=1;
		if(r)
		dat|=0x80;
	}
	return dat;
}
/*******描述：DS18B20接收了这个函数的命令，才能开始转换****/
void PreReadTemp(void)  
{
		do
		{
			Reset=DS18B20_Reset();
		}while(!Reset);
		WriteByte(0xcc);
		WriteByte(0x44);
}
/**********读取温度值***************/
/*描述：该函数的返回值是温度的100倍
如：返回值是2650，表示测量温度值是26.5℃
*/
unsigned int ReadTemp(void)
{
	unsigned int Temp;
	float tt;
	unsigned char LSbyte;
	unsigned char MSbyte;
	do
	{
		Reset=DS18B20_Reset();
	}while(!Reset);
	WriteByte(0xcc);
	WriteByte(0xbe);
	LSbyte=ReadByte();
	MSbyte=ReadByte();
	DS18B20_Reset();
	Temp=MSbyte;
	Temp<<=8;
	Temp|=LSbyte;
//	Temp = Temp*6.25;
	tt = Temp *0.0625;
	Temp = tt*100;
	return Temp;
}

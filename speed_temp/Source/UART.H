#ifndef _UART_H_
#define _UART_H_


sfr AUXR=0X8E;
sfr BTR=0X9C;

extern bit RI_Flag;
extern bit Start_Flag;
extern bit Reset_Flag;
extern bit close_loop;
extern bit open_loop;
extern unsigned int uSpeed;
extern bit U_Flag;
extern bit speed_flag;
extern bit temp_flag;

void UART_init(unsigned int baud);
//unsigned char GetByte(void);
void  SendData(unsigned int dat);
void  SendByte(unsigned char dat);
//void SendString(unsigned char *p);

#endif
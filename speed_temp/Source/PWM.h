#ifndef _PWM_H_
#define _PWM_H_



sfr CCON = 0xD8; //PCA control register
sfr CMOD = 0xD9; //PCA mode register
sfr CCAPM0 = 0xDA; //PCA module-0 mode register
sfr CCAPM1 = 0xDB; //PCA module-1 mode register
sfr CL = 0xE9; //PCA base timer LOW
sfr CH = 0xF9; //PCA base timer HIGH
sfr CCAP0L = 0xEA; //PCA module-0 capture register LOW
sfr CCAP0H = 0xFA; //PCA module-0 capture register HIGH
sfr CCAP1L = 0xEB; //PCA module-1 capture register LOW
sfr CCAP1H = 0xFB; //PCA module-1 capture register HIGH
sfr PCAPWM0 = 0xf2;
sfr PCAPWM1 = 0xf3;
sbit CR = CCON^6; //PCA timer run control bit
sbit CF = CCON^7; //PCA timer overflow flag
sbit CCF0 = CCON^0; //PCA module-0 interrupt flag
sbit CCF1 = CCON^1; //PCA module-1 interrupt flag

sfr P1M1 = 0X91;            //配置I/O模式见手册87页
sfr P1M0 = 0X92;

 
void SetPWM1(unsigned char pwm);
void ClosePWM1();
void Port_init();
void PWM_init();
void ClosePWM0();
void InitPWM(void);
void PWM0_Setting(unsigned char pwm);

#endif
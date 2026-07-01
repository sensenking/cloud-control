#ifndef _DISPLAY_H_
#define _DISPLAY_H_

extern unsigned int pulse;
extern unsigned int speed;
extern bit timer0flag;
extern bit timer1flag;



void Time0_Config();
//void Time1_Config();
void ConfigINT0();
void ConfigT1M1();
void displaySpeed(unsigned int num);
void displayTemp(unsigned int num);
//void time0_interrupt();
int keepSpeed(unsigned int spd);



#endif


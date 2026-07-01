#ifndef _PID_H
#define _PID_H

extern unsigned int Adjust_PWM;
void  PID_Config();
float PID_Control(float refspeed,float realspeed);

#endif

#include <reg51.h>
#include <PID.h>
#include <PWM.h>

/***************************************
作者：zyq
日期：2015、10、22
内容：利用增量式PID控制电机转速
****************************************/


static float SetSpeed;                //设定转速
static float ActualSpeed;             //实际转速
float err;                     //偏差值
float err_next;                //上一个偏差值
float err_last;                //再上一个偏差值
float Kp,Ki,Kd;                //比例、积分、微分常数

void  PID_Config()
{
		SetSpeed = 0.0;
		ActualSpeed = 0.0;
		err = 0.0;
		err_last = 0.0;
		err_next = 0.0;
		Kp = 0.22;		     
		Ki = 0.05;	  
		Kd = 0.01;	
//		Kp = 0.53;		     
//		Ki = 0;			  
//		Kd = 0;			  
}

/*******refspeed: this is setspeed
        realspeed：this is sampling speed*******/

float PID_Control(float refspeed,float realspeed)   //位置式PID
{
	 static float ierr = 0;
	 static float ires = 0;
	 int duty_err;
	
   	 SetSpeed = refspeed;
	 ActualSpeed = realspeed;
     err = SetSpeed-ActualSpeed;

	 ierr += err;
	 ires = Ki*ierr;
	 if(ires > 125)
	   ires = 125;
	 if(ires < -120)
	   ires = -120;

	 duty_err = Kp*err + ires + Kd*(err-err_next);

	 err_last = err_next;
     err_next = err;

	 if(duty_err > 250)
	 {
		duty_err = 255;
     }
	 if(duty_err < 0)
	 {
	 	duty_err = 0;
	 }
	 
   return duty_err;
	 
 }


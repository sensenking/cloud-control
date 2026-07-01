#include <REG51.H>
#include <MOTORDRIVE.H>

sbit ENMOTOR=P1^1;        //ENABLEA
sbit MOTOR1=P1^2;  	      //IN2
sbit MOTOR2=P1^3;         //pwm pin

void InitMotor()
{
	ENMOTOR=0;              //stop motor
	MOTOR1=1;
	MOTOR2=1;
}
void ENMotor()
{
	MOTOR1=0;
	MOTOR2=0;
	ENMOTOR=1;
}
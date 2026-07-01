#include <reg51.h>
#include <heaterdrive.h>

sbit ENHEATER=P1^6;
sbit HEATER1=P1^4;     //PWM1
sbit HEATER2=P1^5;

void Heater_Init(void)
{
	ENHEATER=0;
	HEATER1=1;
	HEATER2=1;
}
void ENHeater(void)
{
	ENHEATER=1;
	HEATER1=0;
	HEATER2=0;
}
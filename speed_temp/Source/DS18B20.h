#ifndef _DS18B20_H_
#define _DS18B20_H_

//bit delay(int x);
void PreReadTemp();
unsigned int ReadTemp();
void display(unsigned int value);
void displayfast(unsigned int value);

#endif
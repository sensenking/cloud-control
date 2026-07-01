#include <reg51.h>
#include <TM1638.h>


//const unsigned char  CathodeCode[11] = {0X3F, 0X06, 0X5B, 0X4F, 0X66, 0X6D, 0X7D, 0X07, 0X7F, 0X6F, 0X80};

void TM1638_WriteData(unsigned char DATA)             //Write Data
{
		unsigned char i;  
	  for( i = 0; i < 8 ; i++ )
			{
				CLK = 0;                                                                       
        if(DATA&0x01)
           DIO = 1;                                                                   
        else
					 DIO = 0;
				DATA = DATA>>1;                              //Must Write or error
        CLK = 1;                                                                       
        
			}		
}

void Write_Command(unsigned char cmd)                    //Write Command   
{
      STB=0;
      TM1638_WriteData(cmd);
      STB=1;
}

void Write_DATA(unsigned char add,unsigned char DATA1)                //Write data to permanent address
{
      Write_Command(0x44);                                            //the model of permanent address
      STB=0;
      TM1638_WriteData(0xc0|add);                                     //keep the bit of B7 and B6 is high level
      TM1638_WriteData(DATA1);
      STB=1;
}

void TM1638_Init()
{ 
      unsigned char i;
			Write_Command(0x8A);                         //the command of Display control 
			Write_Command(0x40);                         //Write data to the display register
	    Write_Command(0xC0);                         
	    STB = 0;
	    for(i=0;i<16;i++)
	    {
			TM1638_WriteData(0X00);	
      }
			STB = 1;
}
package spider65.ebike.tsdz2_esp32.data;

import static spider65.ebike.tsdz2_esp32.utils.Utils.unsignedByteToInt;

public class TSDZ_Debug {

    public byte[] data;

    public int dutyCycle; // D
    public int motorERPS; // D
    public int focAngle; // D
    public int torqueSensorValue; // D ADC torque sensor
    public int adcThrottle; // value from ADC Throttle/Temperature
    public int throttle; // Throttled mapped to 0-255
    public float pTorque; // Torque in Nm
    public float cadencePulseHighPercentage; // temperature mapped to 0-255

    /*
    #pragma pack(1)
    typedef struct _tsdz_debug
    {
      volatile uint8_t ui8_adc_throttle;
      volatile uint8_t ui8_throttle;
      volatile uint16_t ui16_adc_pedal_torque_sensor;
      volatile uint8_t ui8_duty_cycle;
      volatile uint16_t ui16_motor_speed_erps;
      volatile uint8_t ui8_foc_angle;
      volatile uint16_t ui16_pedal_torque_x100;
      volatile uint16_t ui16_cadence_sensor_pulse_high_percentage_x10;
    } struct_tsdz_debug;
     */

    public void setData(byte[] data) {
        int val;

        this.data = data;

        adcThrottle = unsignedByteToInt(data[0]);
        throttle = unsignedByteToInt(data[1]);
        val = unsignedByteToInt(data[2]);
        val += unsignedByteToInt(data[3]) << 8;
        torqueSensorValue = val;
        dutyCycle = unsignedByteToInt(data[4]);
        val = unsignedByteToInt(data[5]);
        val += unsignedByteToInt(data[6]) << 8;
        motorERPS = val;
        focAngle = unsignedByteToInt(data[7]);
        val = unsignedByteToInt(data[8]);
        val += unsignedByteToInt(data[9]) << 8;
        pTorque = (float)val/100;
        val = unsignedByteToInt(data[10]);
        val += unsignedByteToInt(data[11]) << 8;
        cadencePulseHighPercentage = (float)val/10;
    }
}

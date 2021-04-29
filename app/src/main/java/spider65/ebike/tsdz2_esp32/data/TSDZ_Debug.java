package spider65.ebike.tsdz2_esp32.data;

import android.util.Log;

import static spider65.ebike.tsdz2_esp32.TSDZConst.DEBUG_ADV_SIZE;
// Debug Characteristic Notification
// According to the BLE specification the notification length can be max ATT_MTU - 3.
// The 3 bytes subtracted is the 3-byte header(OP-code (operation, 1 byte) and the attribute handle (2 bytes))
// In BLE 4.1 the ATT_MTU is 23 bytes, but in BLE 4.2 the ATT_MTU can be negotiated up to 247 bytes
// -> Max payload for BT 4.1 is 20 bytes
public class TSDZ_Debug {

    private static final String TAG = "TSDZ_Debug";

    public short dutyCycle;
    public int motorERPS;
    public short focAngle;
    public int torqueADCValue; // Torque sensor ADC value (16 bits)
    public short adcThrottle; // value from ADC Throttle/Temperature
    public short throttle;    // Throttled mapped to 0-255
    public float pTorque;     // Torque in Nm
    public short fwOffset;
    public float pcbTemperature;
    public short debugFlags;
    public short debug1;
    public short debug2;
    public short debug3;
    public short debug4;
    public short debug5;
    public short debug6;

    /*
    #pragma pack(1)
    typedef struct _tsdz_debug {
    volatile uint8_t ui8_adc_throttle;
    volatile uint8_t ui8_throttle;
    volatile uint16_t ui16_adc_pedal_torque_sensor;
    volatile uint8_t ui8_duty_cycle;
    volatile uint16_t ui16_motor_speed_erps;
    volatile uint8_t ui8_foc_angle;
    volatile uint16_t ui16_pedal_torque_x100;
    volatile uint8_t ui8_fw_hall_cnt_offset;
    volatile int16_t i16_pcb_temperaturex10;
    volatile uint8_t ui8_debugFlags;
    volatile uint8_t ui8_debug1;
    volatile uint8_t ui8_debug2;
    volatile uint8_t ui8_debug3;
    volatile uint8_t ui8_debug4;
    volatile uint8_t ui8_debug5;
    volatile uint8_t ui8_debug6;
    } struct_tsdz_debug;
    */

    public boolean setData(byte[] data) {
        if (data.length != DEBUG_ADV_SIZE) {
            Log.e(TAG, "Wrong Debug BT message size!");
            return false;
        }
        adcThrottle = (short)(data[0] & 255);
        throttle = (short)(data[1] & 255);
        torqueADCValue = ((data[3] & 255) << 8) + (data[2] & 255);
        dutyCycle = (short)(data[4] & 255);
        motorERPS = ((data[6] & 255) << 8) + (data[5] & 255);
        focAngle = (short)(data[7] & 255);
        pTorque = (float)(((data[9] & 255) << 8) + (data[8] & 255)) / 100;
        fwOffset = (short)(data[10] & 255);
        // temperature is a signed short
        short t = (short)(((data[12] & 0xff) << 8) | (data[11] & 0xff));
        pcbTemperature = (float)(t) / 10;
        debugFlags = (short)(data[13] & 255);
        debug1 = (short)(data[14] & 255);
        debug2 = (short)(data[15] & 255);
        debug3 = (short)(data[16] & 255);
        debug4 = (short)(data[17] & 255);
        debug5 = (short)(data[18] & 255);
        debug6 = (short)(data[19] & 255);
        return true;
    }
}

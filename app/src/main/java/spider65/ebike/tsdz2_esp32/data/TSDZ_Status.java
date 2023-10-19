package spider65.ebike.tsdz2_esp32.data;

import android.util.Log;

import static spider65.ebike.tsdz2_esp32.TSDZConst.STATUS_ADV_SIZE;

// Status Characteristic Notification
// According to the BLE specification the notification length can be max ATT_MTU - 3.
// The 3 bytes subtracted is the 3-byte header(OP-code (operation, 1 byte) and the attribute handle (2 bytes))
// In BLE 4.1 the ATT_MTU is 23 bytes, but in BLE 4.2 the ATT_MTU can be negotiated up to 247 bytes
// -> Max payload for BT 4.1 is 20 bytes
public class TSDZ_Status {

    private static final String TAG = "TSDZ_Status";

    public RidingMode ridingMode;
    public boolean streetMode;
    public short assistLevel;
    public float speed;
    public short cadence;
    public float motorTemperature;
    public int pPower;
    public float volts;
    public float amperes;
    public short status;
    public boolean brake;
    public boolean controllerFromESP32ReceiveError;
    public boolean esp32FromControllerReceiveError;
    public boolean esp32FromLDCReceiveError;
    public int wattHour;
    public short rxcErrors;
    public short rxlErrors;

    public short torqueSmoothPct;
    public short torqueSmoothAvg;
    public short torqueSmoothMin;
    public short torqueSmoothMax;

    public short dutyCycle;
    public int motorERPS;
    public short focAngle;
    public int torqueADCValue; // Torque sensor ADC value (16 bits)
    public short adcThrottle; // value from ADC Throttle/Temperature
    public short throttle;    // Throttled mapped to 0-255
    public float pTorque;     // Torque in Nm
    public short fwOffset;
    public float pcbTemperature;
    public boolean timeDebug;
    public boolean hallDebug;
    public short debug1;
    public short debug2;
    public short debug3;
    public short debug4;
    public short debug5;
    public short debug6;


    public enum RidingMode {
        OFF_MODE(0),
        POWER_ASSIST_MODE(1),
        TORQUE_ASSIST_MODE(2),
        CADENCE_ASSIST_MODE(3),
        eMTB_ASSIST_MODE(4),
        WALK_ASSIST_MODE(5),
        CRUISE_MODE(6);

        public final int value;

        RidingMode(int value) {
            this.value = value;
        }

        public static RidingMode valueOf(int val) {
            for (RidingMode e : values())
                if (e.value == val) return e;
            return null;
        }
    }

    /*
    #pragma pack(1)
    typedef struct _tsdz_data {
        volatile uint8_t ui8_riding_mode;               // 0 - bit 7 Street Mode enabled flag
        volatile uint8_t ui8_assist_level;              // 1
        volatile uint8_t ui8_system_state;              // 2
        volatile uint16_t ui16_wheel_speed_x10;         // 3
        volatile uint8_t ui8_pedal_cadence_RPM;         // 5
        volatile uint16_t ui16_pedal_torque_x100;       // 6
        volatile int16_t i16_motor_temperaturex10;      // 8
        volatile int16_t i16_pcb_temperaturex10;        // 10
        volatile uint16_t ui16_battery_voltage_x1000;   // 12
        volatile uint8_t ui8_battery_current_x10;       // 14
        volatile uint16_t ui16_battery_wh;              // 15
        volatile uint8_t ui8_adc_throttle;              // 17
        volatile uint8_t ui8_throttle;                  // 18
        volatile uint16_t ui16_adc_pedal_torque_sensor; // 19
        volatile uint8_t ui8_duty_cycle;                // 21
        volatile uint16_t ui16_motor_speed_erps;        // 22
        volatile uint8_t ui8_foc_angle;                 // 24
        volatile uint8_t ui8_fw_hall_cnt_offset;        // 25
        volatile uint8_t ui8_TorqueSmoothPct;           // 26
        volatile uint8_t ui8_TorqueAVG;                 // 27
        volatile uint8_t ui8_TorqueMin;                 // 28
        volatile uint8_t ui8_TorqueMax;                 // 29
        volatile uint8_t ui8_rxc_errors;                // 30
        volatile uint8_t ui8_rxl_errors;                // 31
        volatile uint8_t ui8_debugFlags;                // 32
        volatile uint8_t ui8_debug1;                    // 33
        volatile uint8_t ui8_debug2;                    // 34
        volatile uint8_t ui8_debug3;                    // 35
        volatile uint8_t ui8_debug4;                    // 36
        volatile uint8_t ui8_debug5;                    // 37
        volatile uint8_t ui8_debug6;                    // 38
    } struct_tsdz_data;
    */

    public boolean setData(byte[] data) {
        if (data.length != STATUS_ADV_SIZE) {
            Log.e(TAG, "Wrong Status BT message size!");
            return false;
        }
        ridingMode = RidingMode.valueOf(data[0] & 0x7f);
        streetMode = (data[0] & 0x80) != 0;
        assistLevel = (short)(data[1] & 255);
        status = (short)(data[2] & 0x0f);
        brake = (data[2] & 0x20) != 0;
        controllerFromESP32ReceiveError = (data[2] & 0x10) != 0;
        esp32FromControllerReceiveError = (data[2] & 0x80) != 0;
        esp32FromLDCReceiveError = (data[2] & 0x40) != 0;
        speed = (float)(((data[4] & 255) << 8) + (data[3] & 255)) / 10;
        cadence = (short)(data[5] & 255);
        long l = ((data[7] & 255) << 8) + (data[6] & 255);
        pTorque = (float)l / 100;
        pPower = (int)((l * cadence / 96) + 5) / 10;
        // temperature is a signed short
        short t = (short)(((data[9] & 0xff) << 8) | (data[8] & 0xff));
        motorTemperature = (float)(t) / 10;
        t = (short)(((data[11] & 0xff) << 8) | (data[10] & 0xff));
        pcbTemperature = (float)(t) / 10;
        volts = (float)(((data[13] & 255) << 8) + (data[12] & 255)) / 1000;
        amperes = (float)(data[14] & 255) / 10;
        wattHour = ((data[16] & 255) << 8) + ((data[15] & 255));
        adcThrottle = (short)(data[17] & 255);
        throttle = (short)(data[18] & 255);
        torqueADCValue = ((data[20] & 255) << 8) + (data[19] & 255);
        dutyCycle = (short)(data[21] & 255);
        motorERPS = ((data[23] & 255) << 8) + (data[22] & 255);
        focAngle = (short)(data[24] & 255);
        fwOffset = (short)(data[25] & 255);
        torqueSmoothPct = (short)(data[26] & 255);
        torqueSmoothAvg = (short)(data[27] & 255);
        torqueSmoothMin = (short)(data[28] & 255);
        torqueSmoothMax = (short)(data[29] & 255);
        rxcErrors = (short)(data[30] & 255);
        rxlErrors = (short)(data[31] & 255);
        timeDebug = (data[32] & 255) == 0x20;
        hallDebug = (data[32] & 255) == 0x40;
        debug1 = (short)(data[33] & 255);
        debug2 = (short)(data[34] & 255);
        debug3 = (short)(data[35] & 255);
        debug4 = (short)(data[36] & 255);
        debug5 = (short)(data[37] & 255);
        debug6 = (short)(data[38] & 255);
        return true;
    }
}

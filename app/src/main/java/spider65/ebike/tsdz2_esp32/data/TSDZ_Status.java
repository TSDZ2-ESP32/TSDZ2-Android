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
    public boolean controllerCommError;
    public boolean lcdCommError;
    public int wattHour;
    public short rxcErrors;
    public short rxlErrors;
    // Values currently not used
    // public short ui8_status3;
    // public short ui8_status4;
    // public short ui8_status5;

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
    typedef struct _tsdz_status
    {
      volatile uint8_t ui8_riding_mode;
      volatile uint8_t ui8_assist_level;
      volatile uint16_t ui16_wheel_speed_x10;
      volatile uint8_t ui8_pedal_cadence_RPM;
      volatile uint16_t ui16_motor_temperaturex10;
      volatile uint16_t ui16_pedal_power_x10;
      volatile uint16_t ui16_battery_voltage_x1000;
      volatile uint8_t ui8_battery_current_x10;
      volatile uint8_t ui8_controller_system_state;
      volatile uint8_t ui8_braking;
      volatile uint16_t ui16_battery_wh;
    } struct_tsdz_status;
    */

    public boolean setData(byte[] data) {
        if (data.length != STATUS_ADV_SIZE) {
            Log.e(TAG, "Wrong Status BT message size!");
            return false;
        }
        ridingMode = RidingMode.valueOf(data[0] & 0x7f);
        streetMode = (data[0] & 0x80) != 0;
        assistLevel = (short)(data[1] & 255);
        speed = (float)(((data[3] & 255) << 8) + (data[2] & 255)) / 10;
        cadence = (short)(data[4] & 255);
        // temperature is a signed short
        short t = (short)((data[5] & 0xff) | (data[6] << 8));
        motorTemperature = (float)(t) / 10;
        pPower = ((data[8] & 255) << 8) + ((data[7] & 255));
        pPower = (this.pPower+5)/10;
        volts = (float)(((data[10] & 255) << 8) + (data[9] & 255)) / 1000;
        amperes = (float)(data[11] & 255) / 10;
        status = (short)(data[12] & 0x1f);
        brake = (data[12] & 0x20) != 0;
        controllerCommError = (data[12] & 0x80) != 0;
        lcdCommError = (data[12] & 0x40) != 0;
        wattHour = ((data[14] & 255) << 8) + ((data[13] & 255));
        rxcErrors = (short)(data[15] & 255);
        rxlErrors = (short)(data[16] & 255);
        return true;
    }
}

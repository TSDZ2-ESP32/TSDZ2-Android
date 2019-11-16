package spider65.ebike.tsdz2_esp32.data;

import static spider65.ebike.tsdz2_esp32.utils.Utils.unsignedByteToInt;

public class TSDZ_Status {
    public RidingMode ridingMode;
    public int assistLevel;
    public float speed;
    public int cadence;
    public float temperature;
    public int pPower;
    public float volts;
    public float amperes;
    public int status;
    public boolean brake;
    public int wattHour;

    public byte[] data;

    public enum RidingMode {
        OFF_MODE(0),
        POWER_ASSIST_MODE(1),
        TORQUE_ASSIST_MODE(2),
        CADENCE_ASSIST_MODE(3),
        eMTB_ASSIST_MODE(4),
        WALK_ASSIST_MODE(5),
        CRUISE_MODE(6),
        CADENCE_SENSOR_CALIBRATION_MODE(7);

        RidingMode(int value) {
            this.value = value;
        }

        public static RidingMode valueOf(int val) {
            for (RidingMode e : values()) {
                if (e.value ==val) {
                    return e;
                }
            }
            return null;
        }

        public final int value;
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


    public void setData(byte[] data) {
        this.data = data;
        this.ridingMode = RidingMode.valueOf(unsignedByteToInt(data[0]));
        this.assistLevel = unsignedByteToInt(data[1]);
        int val;
        val = unsignedByteToInt(data[2]);
        val += unsignedByteToInt(data[3]) << 8;
        this.speed = ((float)val) / 10;
        this.cadence = unsignedByteToInt(data[4]);
        val = unsignedByteToInt(data[5]);
        val += unsignedByteToInt(data[6]) << 8;
        this.temperature = ((float)val) / 10;
        val = unsignedByteToInt(data[7]);
        val += unsignedByteToInt(data[8]) << 8;
        this.pPower = val/10 + (val % 10 < 5 ? 0 : 1);
        val = unsignedByteToInt(data[9]);
        val += unsignedByteToInt(data[10]) << 8;
        this.volts = ((float)val) / 1000;
        this.amperes = ((float)unsignedByteToInt(data[11])) / 10;
        this.status = unsignedByteToInt(data[12]);
        this.brake = unsignedByteToInt(data[13]) != 0;
        if (data.length > 15) {
            val = unsignedByteToInt(data[14]);
            val += unsignedByteToInt(data[15]) << 8;
            this.wattHour = val;
        }
    }
}

package spider65.ebike.tsdz2_esp32;

public interface TSDZConst {

    int NO_ERROR                                  = 0;
    int ERROR_MOTOR_BLOCKED                       = 1;
    int ERROR_TORQUE_SENSOR                       = 2;
    /*
    int ERROR_BRAKE_APPLIED_DURING_POWER_ON       = 3;  // currently not used
    int ERROR_THROTTLE_APPLIED_DURING_POWER_ON    = 4;  // currently not used
    int ERROR_NO_SPEED_SENSOR_DETECTED            = 5;  // currently not used
    int ERROR_LOW_CONTROLLER_VOLTAGE              = 6;  // controller works with no less than 15 V so give error code if voltage is too low
    */
    int ERROR_BATTERY_OVERCURRENT                 = 7;
    int ERROR_OVERVOLTAGE                         = 8;
    int ERROR_TEMPERATURE_LIMIT                   = 9;
    int ERROR_TEMPERATURE_MAX                     = 10;


    // BT Command request/notification codes
    byte CMD_ESP_OTA_START  = 0x01;
    byte CMD_GET_APP_VERSION = 0x02;
    byte CMD_STM8S_OTA_START = 0x03;
    byte CMD_ESP_OTA_STATUS = 0x05;
    byte CMD_STM8_OTA_STATUS = 0x06;
    byte CMD_HALL_DATA = 0x07; // Notification sent during Motor Test
    byte CMD_ESP32_CONFIG = 0x08;
    byte CMD_STREET_MODE = 0x09;
    byte CMD_ASSIST_MODE = 0x0A;
    byte CMD_MOTOR_TEST = 0x0B; // following 3 bytes: START/STOP, Duty Cycle, Phase Angle adj
    byte TEST_STOP = 0; // second byte of CMD_MOTOR_TEST command
    byte TEST_START = 1; // second byte of CMD_MOTOR_TEST command


    byte STREET_MODE_LCD_MASTER = 0;
    byte STREET_MODE_FORCE_OFF = 1;
    byte STREET_MODE_FORCE_ON = 2;

    byte ASSIST_MODE_LCD_MASTER = 0;
    byte ASSIST_MODE_FORCE_POWER = 1;
    byte ASSIST_MODE_FORCE_EMTB = 2;
    byte ASSIST_MODE_FORCE_TORQUE = 3;
    byte ASSIST_MODE_FORCE_CADENCE = 4;

    // sub commands of CMD_ESP32_CONFIG
    byte CONFIG_GET = 0;
    byte CONFIG_SET = 1;

    // Value ranges for the DS18B20 temperature sensor pin
    byte MIN_DS18B20_PIN = 3;
    byte MAX_DS18B20_PIN = 31;

    // size in bytes of the Status/Debug BT notifications
    int STATUS_ADV_SIZE = 39;

    // Default Hall Counter Offset values
    int[] DEFAULT_HALL_OFFSET = {44,23,44,23,44,23};
    double DEFAULT_AVG_OFFSET = (double)Math.round(((44*3) + (23*3))/6D * 2) / 2.0; // average rounded to 0.5

    // Default motor Phase
    int DEFAULT_ROTOR_OFFSET = 4; // Rotor angle adjust regarding the Hall angle reference
    int DEFAULT_PHASE_ANGLE = 64; // Phase has 90 deg difference from rotor position
}

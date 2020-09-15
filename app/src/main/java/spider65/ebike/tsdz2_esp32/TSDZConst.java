package spider65.ebike.tsdz2_esp32;

public interface TSDZConst {

    int NO_ERROR                                  = 0;
    int ERROR_MOTOR_BLOCKED                       = 1;
    int ERROR_TORQUE_SENSOR                       = 2;
    /*
    int ERROR_BRAKE_APPLIED_DURING_POWER_ON       = 3;  // currently not used
    int ERROR_THROTTLE_APPLIED_DURING_POWER_ON    = 4;  // currently not used
    int ERROR_NO_SPEED_SENSOR_DETECTED            = 5;  // currently not used
    */
    int ERROR_LOW_CONTROLLER_VOLTAGE              = 6;  // controller works with no less than 15 V so give error code if voltage is too low
    int ERROR_OVERVOLTAGE                         = 8;
    int ERROR_TEMPERATURE_LIMIT                   = 9;
    int ERROR_TEMPERATURE_MAX                     = 10;


    // BT Command request/notification codes
    byte CMD_ESP_OTA_START  = 0x01;
    byte CMD_GET_APP_VERSION = 0x02;
    byte CMD_STM8S_OTA_START = 0x03;
    byte CMD_ESP_OTA_STATUS = 0x05;
    byte CMD_STM8_OTA_STATUS = 0x06;
    byte CMD_ESP32_CONFIG = 0x08;
    byte CMD_STREET_MODE = 0x09;

    byte STREET_MODE_LCD_MASTER = 0;
    byte STREET_MODE_FORCE_OFF = 1;
    byte STREET_MODE_FORCE_ON = 2;

    // sub commands of CMD_ESP32_CONFIG
    byte CONFIG_GET = 0;
    byte CONFIG_SET = 1;

    // Value ranges for the DS18B20 temperature sensor pin
    byte MIN_DS18B20_PIN = 3;
    byte MAX_DS18B20_PIN = 31;

    // size in bytes of the Status/Debug BT notifications
    int DEBUG_ADV_SIZE = 16;
    int STATUS_ADV_SIZE = 17;

    // limit values used in the LevelSetupActivity
    int PWM_DUTY_CYCLE_MAX = 254;
    int WALK_ASSIST_DUTY_CYCLE_MAX = 80;
}

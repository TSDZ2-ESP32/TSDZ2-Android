package spider65.ebike.tsdz2_esp32;

public interface TSDZConst {
    // BT Command request/notification codes
    byte CMD_ESP_OTA_START  = 0x01;
    byte CMD_GET_APP_VERSION = 0x02;
    byte CMD_STM8S_OTA_START = 0x03;
    byte CMD_LOADER_OTA_START = 0x04;
    byte CMD_ESP_OTA_STATUS = 0x05;
    byte CMD_STM_OTA_STATUS = 0x06;
    byte CMD_CADENCE_CALIBRATION = 0x07;
    byte CMD_ESP32_CONFIG = 0x08;

    // sub commands of CMD_CADENCE_CALIBRATION
    byte CALIBRATION_START = 0;
    byte CALIBRATION_STOP = 1;
    byte CALIBRATION_SAVE = 2;

    // sub commands of CMD_ESP32_CONFIG
    byte CONFIG_GET = 0;
    byte CONFIG_SET = 1;

    // Value ranges for the DS18B20 temperature sensor pin
    byte MIN_DS18B20_PIN = 3;
    byte MAX_DS18B20_PIN = 31;

    // size in bytes of the Status/Debug BT notifications
    int DEBUG_ADV_SIZE = 14;
    int STATUS_ADV_SIZE = 17;

    // limit values used in the LevelSetupActivity
    int PWM_DUTY_CYCLE_MAX = 254;
    int WALK_ASSIST_DUTY_CYCLE_MAX = 80;
}

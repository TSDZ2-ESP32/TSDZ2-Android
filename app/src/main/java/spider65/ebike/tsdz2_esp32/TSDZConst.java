package spider65.ebike.tsdz2_esp32;

public interface TSDZConst {
    byte CMD_ESP_OTA_START  = 0x01;
    byte CMD_GET_APP_VERSION = 0x02;
    byte CMD_STM8S_OTA_START = 0x03;
    byte CMD_LOADER_OTA_START = 0x04;
    byte CMD_ESP_OTA_STATUS = 0x05;
    byte CMD_STM_OTA_STATUS = 0x06;
    byte CMD_CADENCE_CALIBRATION = 0x07;
    byte CMD_ESP32_CONFIG = 0x08;

    byte CALIBRATION_START = 0;
    byte CALIBRATION_STOP = 1;
    byte CALIBRATION_SAVE = 2;

    byte CONFIG_GET = 0;
    byte CONFIG_SET = 1;

    byte MIN_DS18B20_PIN = 3;
    byte MAX_DS18B20_PIN = 31;

    int DEBUG_ADV_SIZE = 14;
    int STATUS_ADV_SIZE = 17;

    int PWM_DUTY_CYCLE_MAX = 254;
    int WALK_ASSIST_DUTY_CYCLE_MAX = 80;
}

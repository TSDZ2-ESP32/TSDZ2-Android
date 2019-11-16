package spider65.ebike.tsdz2_esp32.data;

import android.util.Log;

import static spider65.ebike.tsdz2_esp32.utils.Utils.unsignedByteToInt;

public class TSDZ_Config {

    public enum TempControl {
        none (0),
        tempADC (1),
        tempESP (2);

        private final int value;
        TempControl(int value) {
            this.value = value;
        }

        public static TempControl fromValue(int value) {
            switch (value) {
                case 0:
                    return none;
                case 1:
                    return tempADC;
                case 2:
                    return tempESP;
            }
            return null;
        }

        public int getValue() {
            return value;
        }
    }

    public int ui8_motor_type;
    public int ui8_motor_temperature_min_value_to_limit;
    public int ui8_motor_temperature_max_value_to_limit;
    public int ui8_motor_acceleration;
    public boolean advanced_cadence_sensor_mode;
    public int ui16_cadence_sensor_pulse_high_percentage_x10;
    public int ui8_pedal_torque_per_10_bit_ADC_step_x100;
    public TempControl temperature_control;
    public boolean throttleEnabled;
    public boolean assist_without_pedal_rotation;
    public int ui8_assist_without_pedal_rotation_threshold;
    public int ui8_lights_configuration;
    public int ui16_wheel_perimeter;
    public int ui8_oem_wheel_divisor;
    public int ui16_battery_voltage_reset_wh_counter_x10;
    public int ui8_battery_max_current;
    public int ui8_target_max_battery_power_div25;
    public int ui8_battery_cells_number;
    public int ui16_battery_pack_resistance_x1000;
    public int ui16_battery_low_voltage_cut_off_x10;
    public int ui8_li_io_cell_overvolt_x100;
    public int ui8_li_io_cell_full_bars_x100;
    public int ui8_li_io_cell_one_bar_x100;
    public int ui8_li_io_cell_empty_x100;
    public boolean ui8_street_mode_enabled;
    public boolean ui8_street_mode_power_limit_enabled;
    public boolean ui8_street_mode_throttle_enabled;
    public int ui8_street_mode_power_limit_div25;
    public int ui8_street_mode_speed_limit;
    public int ui8_eMTB_assist_sensitivity;
    public int[] ui8_power_assist_level = new int[4];
    public int[] ui8_torque_assist_level = new int[4];
    public int[] ui8_walk_assist_level = new int[4];

    /*
    #pragma pack(1)
    typedef struct _tsdz_cfg
    {
      volatile uint8_t ui8_motor_type;
      volatile uint8_t ui8_motor_temperature_min_value_to_limit;
      volatile uint8_t ui8_motor_temperature_max_value_to_limit;
      volatile uint8_t ui8_motor_acceleration;
      volatile uint8_t ui8_cadence_sensor_mode;
      volatile uint16_t ui16_cadence_sensor_pulse_high_percentage_x10;
      volatile uint8_t ui8_pedal_torque_per_10_bit_ADC_step_x100;
      volatile uint8_t ui8_optional_ADC_function;
      volatile uint8_t ui8_assist_without_pedal_rotation_threshold;
      volatile uint8_t ui8_lights_configuration;
      volatile uint16_t ui16_wheel_perimeter;
      volatile uint8_t ui8_oem_wheel_divisor;
      volatile uint16_t ui16_battery_voltage_reset_wh_counter_x10;
      volatile uint8_t ui8_battery_max_current;
      volatile uint8_t ui8_target_max_battery_power_div25;
      volatile uint8_t ui8_battery_cells_number;
      volatile uint16_t ui16_battery_pack_resistance_x1000;
      volatile uint16_t ui16_battery_low_voltage_cut_off_x10;
      volatile uint8_t ui8_li_io_cell_overvolt_x100;
      volatile uint8_t ui8_li_io_cell_full_bars_x100;
      volatile uint8_t ui8_li_io_cell_one_bar_x100;
      volatile uint8_t ui8_li_io_cell_empty_x100;
      volatile uint8_t ui8_street_mode_enabled;
      volatile uint8_t ui8_street_mode_power_limit_enabled;
      volatile uint8_t ui8_street_mode_throttle_enabled;
      volatile uint8_t ui8_street_mode_power_limit_div25;
      volatile uint8_t ui8_street_mode_speed_limit;
      volatile uint8_t ui8_eMTB_assist_sensitivity;
      volatile uint8_t ui8_power_assist_level[4];
      volatile uint8_t ui8_torque_assist_level[4];
      volatile uint8_t ui8_walk_assist_level[4];
      volatile uint8_t ui8_esp32_temp_control;
    } struct_tsdz_cfg;
    */

    public void setData(byte[] data) {
        int val;
        ui8_motor_type = unsignedByteToInt(data[0]);
        ui8_motor_temperature_min_value_to_limit = unsignedByteToInt(data[1]);
        ui8_motor_temperature_max_value_to_limit = unsignedByteToInt(data[2]);
        ui8_motor_acceleration = unsignedByteToInt(data[3]);
        advanced_cadence_sensor_mode = (unsignedByteToInt(data[4])!= 0); // ui8_cadence_sensor_mode
        val = unsignedByteToInt(data[5]);
        val += unsignedByteToInt(data[6]) << 8;
        ui16_cadence_sensor_pulse_high_percentage_x10 = val;
        ui8_pedal_torque_per_10_bit_ADC_step_x100 = unsignedByteToInt(data[7]);
        throttleEnabled = unsignedByteToInt(data[8]) == 2;
        ui8_assist_without_pedal_rotation_threshold = unsignedByteToInt(data[9]);
        assist_without_pedal_rotation = (ui8_assist_without_pedal_rotation_threshold != 0);
        ui8_lights_configuration = unsignedByteToInt(data[10]);
        val = unsignedByteToInt(data[11]);
        val += unsignedByteToInt(data[12]) << 8;
        ui16_wheel_perimeter = val;
        ui8_oem_wheel_divisor = unsignedByteToInt(data[13]);
        val = unsignedByteToInt(data[14]);
        val += unsignedByteToInt(data[15]) << 8;
        ui16_battery_voltage_reset_wh_counter_x10 = val;
        ui8_battery_max_current = unsignedByteToInt(data[16]);
        ui8_target_max_battery_power_div25 = unsignedByteToInt(data[17]) * 25;
        ui8_battery_cells_number = unsignedByteToInt(data[18]);
        val = unsignedByteToInt(data[19]);
        val += unsignedByteToInt(data[20]) << 8;
        ui16_battery_pack_resistance_x1000 = val;
        val = unsignedByteToInt(data[21]);
        val += unsignedByteToInt(data[22]) << 8;
        ui16_battery_low_voltage_cut_off_x10 = val;
        ui8_li_io_cell_overvolt_x100 = unsignedByteToInt(data[23]) + 200;
        ui8_li_io_cell_full_bars_x100 = unsignedByteToInt(data[24]) + 200;
        ui8_li_io_cell_one_bar_x100 = unsignedByteToInt(data[25]) + 200;
        ui8_li_io_cell_empty_x100 = unsignedByteToInt(data[26]) + 200;
        ui8_street_mode_enabled = unsignedByteToInt(data[27]) != 0;
        ui8_street_mode_power_limit_enabled = unsignedByteToInt(data[28]) != 0;
        ui8_street_mode_throttle_enabled = unsignedByteToInt(data[29]) != 0;
        ui8_street_mode_power_limit_div25 = unsignedByteToInt(data[30]) * 25;
        ui8_street_mode_speed_limit = unsignedByteToInt(data[31]);
        ui8_eMTB_assist_sensitivity = unsignedByteToInt(data[32]);
        for (int i=0;i<4;i++)
            ui8_power_assist_level[i] = unsignedByteToInt(data[33+i]);
        for (int i=0;i<4;i++)
            ui8_torque_assist_level[i] = unsignedByteToInt(data[37+i]);
        for (int i=0;i<4;i++)
            ui8_walk_assist_level[i] = unsignedByteToInt(data[41+i]);
        if (unsignedByteToInt(data[45]) != 0) {
            temperature_control = TempControl.tempESP; // Temperature controlled by ESP32 DS18B20 sensor
        } else if (unsignedByteToInt(data[8]) == 1) {
            temperature_control = TempControl.tempADC; // Temperature controlled by Controller sensor
        } else
            temperature_control = TempControl.none; // No temperature control
    }

    public byte[] toByteArray() {
        byte[] data = new byte[46];
        data[0] = (byte)(ui8_motor_type & 0xff);
        data[1] = (byte)ui8_motor_temperature_min_value_to_limit;
        data[2] = (byte)ui8_motor_temperature_max_value_to_limit;
        data[3] = (byte)ui8_motor_acceleration;
        data[4] = (byte)(advanced_cadence_sensor_mode? 1:0); // ui8_cadence_sensor_mode
        data[5] = (byte)ui16_cadence_sensor_pulse_high_percentage_x10;
        data[6] = (byte)(ui16_cadence_sensor_pulse_high_percentage_x10 >>> 8);
        data[7] = (byte)ui8_pedal_torque_per_10_bit_ADC_step_x100;
        data[8] = (byte)(throttleEnabled ? 2:0); // ui8_optional_ADC_function
        data[8] = temperature_control == TempControl.tempADC ? (byte)1:data[8]; // ui8_optional_ADC_function
        data[9] = (assist_without_pedal_rotation ? (byte)ui8_assist_without_pedal_rotation_threshold : (byte)0);
        data[10] = (byte)ui8_lights_configuration;
        data[11] = (byte)ui16_wheel_perimeter;
        data[12] = (byte)(ui16_wheel_perimeter >>> 8);
        data[13] = (byte)ui8_oem_wheel_divisor;
        data[14] = (byte)ui16_battery_voltage_reset_wh_counter_x10;
        data[15] = (byte)(ui16_battery_voltage_reset_wh_counter_x10 >>> 8);
        data[16] = (byte)ui8_battery_max_current;
        data[17] = (byte)(ui8_target_max_battery_power_div25 / 25);
        data[18] = (byte)ui8_battery_cells_number;
        data[19] = (byte)ui16_battery_pack_resistance_x1000;
        data[20] = (byte)(ui16_battery_pack_resistance_x1000 >>> 8);
        data[21] = (byte)ui16_battery_low_voltage_cut_off_x10;
        data[22] = (byte)(ui16_battery_low_voltage_cut_off_x10 >>> 8);
        data[23] = (byte)(ui8_li_io_cell_overvolt_x100 - 200);
        data[24] = (byte)(ui8_li_io_cell_full_bars_x100 - 200);
        data[25] = (byte)(ui8_li_io_cell_one_bar_x100 - 200);
        data[26] = (byte)(ui8_li_io_cell_empty_x100 - 200);
        data[27] = (byte)(ui8_street_mode_enabled? 0x01:0x00);
        data[28] = (byte)(ui8_street_mode_power_limit_enabled? 0x01:0x00);
        data[29] = (byte)(ui8_street_mode_throttle_enabled? 0x01:0x00);
        data[30] = (byte)(ui8_street_mode_power_limit_div25/25);
        data[31] = (byte)ui8_street_mode_speed_limit;
        data[32] = (byte)ui8_eMTB_assist_sensitivity;
        for (int i=0;i<4;i++)
            data[33+i] = (byte)ui8_power_assist_level[i];
        for (int i=0;i<4;i++)
            data[37+i] = (byte)ui8_torque_assist_level[i];
        for (int i=0;i<4;i++)
            data[41+i] = (byte)ui8_walk_assist_level[i];
        data[45] = temperature_control == TempControl.tempESP ? (byte)1:(byte)0;
        return data;
    }
}

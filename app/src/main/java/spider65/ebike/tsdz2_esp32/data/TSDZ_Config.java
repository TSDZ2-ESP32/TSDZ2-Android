package spider65.ebike.tsdz2_esp32.data;


import android.util.Log;

public class TSDZ_Config {

    private static final String TAG = "TSDZ_Config";
    private static final int CFG_SIZE = 70;

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

    public int ui8_foc_angle_multiplicator;
    public int ui8_motor_temperature_min_value_to_limit;
    public int ui8_motor_temperature_max_value_to_limit;
    public int ui8_motor_acceleration;
    public int ui8_hall_offset_adj;
    public int ui8_max_speed;
    public int ui8_street_max_speed;
    public int ui8_pedal_torque_per_10_bit_ADC_step_x100;
    public TempControl temperature_control;
    public boolean throttleEnabled;
    public boolean assist_without_pedal_rotation;
    public int ui8_assist_without_pedal_rotation_threshold;
    public int ui8_lights_configuration;
    public int ui16_wheel_perimeter;
    public boolean ui8_cruise_enabled;
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
    public int ui8_phase_angle_adj;
    public boolean ui8_street_mode_power_limit_enabled;
    public boolean ui8_street_mode_throttle_enabled;
    public int ui8_street_mode_power_limit_div25;
    public int ui8_street_mode_speed_limit;
    public int[] ui8_cadence_assist_level = new int[4];
    public int[] ui8_power_assist_level = new int[4];
    public int[] ui8_torque_assist_level = new int[4];
    public int[] ui8_eMTB_assist_level = new int[4];
    public int[] ui8_walk_assist_level = new int[4];
    public boolean torque_offset_fix;
    public int ui16_torque_offset_ADC;
    public int[] ui8_hall_ref_angles = new int[6];
    public int[] ui8_hall_counter_offset = new int[6];
    public boolean fieldWeakeningEnabled;
    public boolean torqueSmoothEnable;
    public boolean streetModeStartupEnable;
    public int ui8_torque_smooth_min;
    public int ui8_torque_smooth_max;


    /*
    #pragma pack(1)
    typedef struct _tsdz_cfg {
        volatile uint8_t ui8_motor_inductance_x1048576;
        volatile uint8_t ui8_motor_temperature_min_value_to_limit;
        volatile uint8_t ui8_motor_temperature_max_value_to_limit;
        volatile uint8_t ui8_motor_acceleration;
        volatile uint8_t ui8_hall_offset_adj;
        volatile uint8_t ui8_max_speed;
        volatile uint8_t ui8_street_max_speed;
        volatile uint8_t ui8_pedal_torque_per_10_bit_ADC_step_x100;
        volatile uint8_t ui8_optional_ADC_function;
        volatile uint8_t ui8_assist_without_pedal_rotation_threshold;
        volatile uint8_t ui8_lights_configuration;
        volatile uint16_t ui16_wheel_perimeter;
        volatile uint8_t ui8_cruise_mode_enabled;
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
        volatile uint8_t ui8_phase_angle_adj;
        volatile uint8_t ui8_street_mode_power_limit_enabled;
        volatile uint8_t ui8_street_mode_throttle_enabled;
        volatile uint8_t ui8_street_mode_power_limit_div25;
        volatile uint8_t ui8_street_mode_speed_limit;
        volatile uint8_t ui8_esp32_temp_control;
        volatile uint8_t ui8_cadence_assist_level[4];
        volatile uint8_t ui8_power_assist_level[4];
        volatile uint8_t ui8_torque_assist_level[4];
        volatile uint8_t ui8_eMTB_assist_sensitivity[4];
        volatile uint8_t ui8_walk_assist_level[4];
        volatile uint8_t ui8_flags;
        volatile uint16_t ui16_torque_offset_value;
        volatile uint8_t ui8_hall_ref_angles[6];
        volatile uint8_t ui8_hall_offsets[6];
        volatile uint8_t ui8_torque_smooth_min;
        volatile uint8_t ui8_torque_smooth_max;
    } struct_tsdz_cfg;
    */

    public boolean setData(byte[] data) {
        if (data.length != CFG_SIZE) {
            Log.e(TAG, "setData: wrong data size" + data.length);
            return false;
        }
        ui8_foc_angle_multiplicator = (data[0] & 255);
        ui8_motor_temperature_min_value_to_limit = (data[1] & 255);
        ui8_motor_temperature_max_value_to_limit = (data[2] & 255);
        ui8_motor_acceleration = (data[3] & 255);
        ui8_hall_offset_adj = (data[4] & 255 ); // ui8_cadence_sensor_mode
        ui8_max_speed = (data[5] & 255);
        ui8_street_max_speed = (data[6] & 255);
        ui8_pedal_torque_per_10_bit_ADC_step_x100 = (data[7] & 255);
        throttleEnabled = (data[8] & 255) == 2;
        ui8_assist_without_pedal_rotation_threshold = (data[9] & 255);
        assist_without_pedal_rotation = (ui8_assist_without_pedal_rotation_threshold != 0);
        ui8_lights_configuration = (data[10] & 255);
        ui16_wheel_perimeter = (data[11] & 255) + ((data[12] & 255) << 8);
        ui8_cruise_enabled = (data[13] & 255) != 0;
        ui16_battery_voltage_reset_wh_counter_x10 = (data[14] & 255) + ((data[15] & 255) << 8);
        ui8_battery_max_current = (data[16] & 255);
        ui8_target_max_battery_power_div25 = (data[17] & 255) * 25;
        ui8_battery_cells_number = (data[18] & 255);
        ui16_battery_pack_resistance_x1000 = (data[19] & 255) + ((data[20] & 255) << 8);
        ui16_battery_low_voltage_cut_off_x10 = (data[21] & 255) + ((data[22] & 255) << 8);
        ui8_li_io_cell_overvolt_x100 = (data[23] & 255) + 200;
        ui8_li_io_cell_full_bars_x100 = (data[24] & 255) + 200;
        ui8_li_io_cell_one_bar_x100 = (data[25] & 255) + 200;
        ui8_li_io_cell_empty_x100 = (data[26] & 255) + 200;
        ui8_phase_angle_adj = (data[27] & 255);
        ui8_street_mode_power_limit_enabled = (data[28] & 255) != 0;
        ui8_street_mode_throttle_enabled = (data[29] & 255) != 0;
        ui8_street_mode_power_limit_div25 = (data[30] & 255) * 25;
        ui8_street_mode_speed_limit = (data[31] & 255);
        if ((data[32] & 255) != 0) {
            temperature_control = TempControl.tempESP; // Temperature controlled by ESP32 DS18B20 sensor
        } else if ((data[8] & 255) == 1) {
            temperature_control = TempControl.tempADC; // Temperature controlled by Controller sensor
        } else
            temperature_control = TempControl.none; // No motorTemperature control
        for (int i=0;i<4;i++)
            ui8_cadence_assist_level[i] = (data[33+i] & 255);
        for (int i=0;i<4;i++)
            ui8_power_assist_level[i] = (data[37+i] & 255);
        for (int i=0;i<4;i++)
            ui8_torque_assist_level[i] = (data[41+i] & 255);
        for (int i=0;i<4;i++)
            ui8_eMTB_assist_level[i] = (data[45+i] & 255);
        for (int i=0;i<4;i++)
            ui8_walk_assist_level[i] = (data[49+i] & 255);
        torque_offset_fix = (data[53] & 0x01 ) != 0;
        fieldWeakeningEnabled = (data[53] & 0x02) != 0;
        torqueSmoothEnable = (data[53] & 0x04) != 0;
        streetModeStartupEnable = (data[53] & 0x08) != 0;
        ui16_torque_offset_ADC = (data[54] & 255) + ((data[55] & 255) << 8);
        for (int i=0;i<6;i++)
            ui8_hall_ref_angles[i] = (data[56+i] & 255);
        for (int i=0;i<6;i++)
            ui8_hall_counter_offset[i] = (data[62+i] & 255);
        ui8_torque_smooth_min = (data[68] & 255);
        ui8_torque_smooth_max = (data[69] & 255);
        return true;
    }

    public byte[] toByteArray() {
        byte[] data = new byte[CFG_SIZE];
        data[0] = (byte)(ui8_foc_angle_multiplicator & 0xff);
        data[1] = (byte)ui8_motor_temperature_min_value_to_limit;
        data[2] = (byte)ui8_motor_temperature_max_value_to_limit;
        data[3] = (byte)ui8_motor_acceleration;
        data[4] = (byte)ui8_hall_offset_adj;
        data[5] = (byte)ui8_max_speed;
        data[6] = (byte)(ui8_street_max_speed);
        data[7] = (byte)ui8_pedal_torque_per_10_bit_ADC_step_x100;
        data[8] = (byte)(throttleEnabled ? 2:0); // ui8_optional_ADC_function
        data[8] = temperature_control == TempControl.tempADC ? (byte)1:data[8]; // ui8_optional_ADC_function
        data[9] = (assist_without_pedal_rotation ? (byte)ui8_assist_without_pedal_rotation_threshold : (byte)0);
        data[10] = (byte)ui8_lights_configuration;
        data[11] = (byte)ui16_wheel_perimeter;
        data[12] = (byte)(ui16_wheel_perimeter >>> 8);
        data[13] = (byte)(ui8_cruise_enabled? 1:0);
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
        data[27] = (byte)ui8_phase_angle_adj;
        data[28] = (byte)(ui8_street_mode_power_limit_enabled? 1:0);
        data[29] = (byte)(ui8_street_mode_throttle_enabled? 1:0);
        data[30] = (byte)(ui8_street_mode_power_limit_div25/25);
        data[31] = (byte)ui8_street_mode_speed_limit;
        data[32] = temperature_control == TempControl.tempESP ? (byte)1:(byte)0;
        for (int i=0;i<4;i++)
            data[33+i] = (byte)ui8_cadence_assist_level[i];
        for (int i=0;i<4;i++)
            data[37+i] = (byte)ui8_power_assist_level[i];
        for (int i=0;i<4;i++)
            data[41+i] = (byte)ui8_torque_assist_level[i];
        for (int i=0;i<4;i++)
            data[45+i] = (byte)ui8_eMTB_assist_level[i];
        for (int i=0;i<4;i++)
            data[49+i] = (byte)ui8_walk_assist_level[i];
        data[53] = 0;
        data[53] |= (byte)(torque_offset_fix? 1:0);
        data[53] |= (byte)(fieldWeakeningEnabled? 2:0);
        data[53] |= (byte)(torqueSmoothEnable? 4:0);
        data[53] |= (byte)(streetModeStartupEnable? 8:0);
        data[54] = (byte)ui16_torque_offset_ADC;
        data[55] = (byte)(ui16_torque_offset_ADC >>> 8);
        for (int i=0;i<6;i++)
            data[56+i] = (byte)(ui8_hall_ref_angles[i] & 0xff);
        for (int i=0;i<6;i++)
            data[62+i] = (byte)(ui8_hall_counter_offset[i] & 0xff);
        data[68] = (byte)ui8_torque_smooth_min;
        data[69] = (byte)ui8_torque_smooth_max;
        return data;
    }
}
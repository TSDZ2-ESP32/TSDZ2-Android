package spider65.ebike.tsdz2_esp32.ota;

import android.util.Log;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

class Esp32AppImageTool {

    private static final String TAG = "Esp32AppImageTool";

    /*
    Image format is:
    ---------------------------
    esp_image_header_t
    esp_image_segment_header_t 1
    segment data 1
    .
    .
    .
    esp_image_segment_header_t n
    segment data n
    crc (1 byte, padded with zeros until its size is one byte less than a multiple of 16 bytes.
         xor of segment data of all segments and the 0xEF byte)
    SHA256 hash (optional 32 bytes)
    ECDSA signature (optional 68 bytes)
    ---------------------------

    First segment data format:
    ---------------------------
    esp_app_desc_t
    CUSTOM FIXED DATA (data in this position must declared as: const __attribute__((section(".rodata_custom_desc"))) <type> <name> = <value>
                       E.g. const __attribute__((section(".rodata_custom_desc"))) uint32_t bt_passkey = 123456;
    ---------------------------
     */
    private static final int ESP_IMAGE_HEADER_SIZE = 24;
    private static final int ESP_IMAGE_SEGMENT_HEADER_SIZE = 8; // uint32_t load addr + uint32_t data length
    private static final int ESP_APP_DESC_SIZE = 256;

    private static final int ESP_MAGIC_BYTE = 0xE9; // first byte of ESP32 bin app image
    private static final int SEG_COUNT_POS = 1; // position of the segments count info (1 byte)
    private static final int SHA_FLAG_POS = 23; // position of SHA256 flag (1 byte, if value=1 SHA256 is appended at end of file)
    private static final int APP_VERSION_POS = 48; // position of app version string and app name string (32 bytes each, 0 padded)

    static class EspImageInfo {
        boolean signed;
        boolean shaPresent;
        String appVersion;
        String appName;
        byte[] sha256;
        byte crc;
        long dataLength;
        long crcPos;
        long btPin;
    }

    private static long readUnsignedIntLittleEndian(RandomAccessFile raf) throws IOException {
        byte[] data = new byte[4];
        raf.readFully(data);
        return ((long)data[0]&0xff) |
                (((long)data[1] & 0xff) << 8) |
                (((long)data[2] & 0xff) << 16) |
                (((long)data[3] & 0xff) << 24);
    }

    private static byte calcCRC(File f) throws IOException{
        byte crc = (byte)0xef;
        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(f, "r");
            raf.seek(SEG_COUNT_POS);
            int segmentCount = raf.readUnsignedByte();
            long pos = ESP_IMAGE_HEADER_SIZE;
            for (int i = 0; i < segmentCount; i++) {
                raf.seek(pos + 4); // esp_image_segment_header_t.data_len
                long segSize = readUnsignedIntLittleEndian(raf); // read current segment size
                byte[] tmp = new byte[(int)segSize];
                raf.readFully(tmp);
                for (int j = 0; j < segSize; j++)
                    crc ^= tmp[j];
                pos += ESP_IMAGE_SEGMENT_HEADER_SIZE + segSize; // add sizeof(esp_image_segment_header_t) + segSize
            }
        } finally {
            try {
                if (raf != null)
                    raf.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return crc;
    }

    private static byte[] calcSHA256(File f, long lastPos) {
        BufferedInputStream bis = null;
        MessageDigest digest = null;
        try {
            bis = new BufferedInputStream(new FileInputStream(f));
            digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[32768];
            long totRead = 0;
            int currRead;
            while (totRead <= lastPos) {
                currRead = bis.read(buffer);
                if ((totRead + currRead) > lastPos) {
                    digest.update(buffer, 0, (int) (lastPos + 1 - totRead));
                } else {
                    digest.update(buffer, 0, currRead);
                }
                totRead += currRead;
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (bis != null)
                    bis.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (digest != null)
            return digest.digest();
        else
            return null;
    }

    static boolean updateFile(File f, EspImageInfo imageInfo, int btPin) {
        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(f, "rws");
            byte[] pinData = new byte[4];
            for (int i=0;i<4;i++) {
                pinData[i] = (byte)((btPin >>> (i*8)) & 0xff);
            }
            raf.seek(ESP_IMAGE_HEADER_SIZE + ESP_IMAGE_SEGMENT_HEADER_SIZE + ESP_APP_DESC_SIZE);
            raf.write(pinData);
            byte crc = calcCRC(f);
            raf.seek(imageInfo.crcPos);
            raf.write(crc);
            byte[] hash = calcSHA256(f, imageInfo.crcPos);
            raf.seek(imageInfo.crcPos+1);
            raf.write(hash);
            raf.close();
        } catch (Exception e) {
            Log.e(TAG,e.toString());
            return false;
        } finally {
            try {
                if (raf != null)
                    raf.close();
            } catch (Exception e) {
                Log.e(TAG,e.toString());
            }
        }

        return true;
    }

    static EspImageInfo checkFile(File f) {
        EspImageInfo info = new EspImageInfo();

        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(f,"r");
            if (raf.read() != ESP_MAGIC_BYTE) {
                Log.d(TAG,"Wrong Magic byte");
                return null;
            }
            raf.seek(SEG_COUNT_POS);
            int segmentCount = raf.readUnsignedByte();
            raf.seek(SHA_FLAG_POS);
            // check if SHA256 hash is present after checksum
            info.shaPresent = (raf.readUnsignedByte() == 1);
            // read app version string
            byte[] data = new byte[32];
            raf.seek(APP_VERSION_POS);
            raf.readFully(data);
            for (int i=0; i<data.length; i++) {
                if (data[i] == 0) {
                    info.appVersion = new String(Arrays.copyOfRange(data, 0, i), StandardCharsets.UTF_8);
                    break;
                }
            }
            // read app name string
            raf.readFully(data);
            for (int i=0; i<data.length; i++) {
                if (data[i] == 0) {
                    info.appName = new String(Arrays.copyOfRange(data, 0, i), StandardCharsets.UTF_8);
                    break;
                }
            }
            // Read custom data (BT Pairing PIN)
            raf.seek(ESP_IMAGE_HEADER_SIZE + ESP_IMAGE_SEGMENT_HEADER_SIZE + ESP_APP_DESC_SIZE);
            info.btPin = readUnsignedIntLittleEndian(raf);
            // Calculate CRC of all segments data
            info.dataLength = ESP_IMAGE_HEADER_SIZE;  // first esp_image_segment_header_t position
            for (int i=0; i<segmentCount; i++) {
                raf.seek(info.dataLength+4); // esp_image_segment_header_t.data_len
                long segSize = readUnsignedIntLittleEndian(raf); // read current segment size
                info.dataLength += ESP_IMAGE_SEGMENT_HEADER_SIZE + segSize; // add sizeof(esp_image_segment_header_t) + segSize
            }

            // CRC position (file is padded with zeros until is one byte less than a multiple of 16 bytes)
            info.crcPos = info.dataLength + (15 - info.dataLength%16);
            raf.seek(info.crcPos);
            info.crc = (byte)raf.readUnsignedByte();

            // SHA256 starts after CRC and is 32 bytes long
            byte [] hash = null;
            if (info.shaPresent) {
                info.sha256 = new byte[32];
                raf.readFully(info.sha256);
                hash = calcSHA256(f,info.crcPos);
            }

            raf.close();
            raf = null;

            // CRC check
            if (info.crc != calcCRC(f)) {
                Log.d(TAG,"CRC WRONG!");
                return null;
            }

            // SHA256 check
            if (info.shaPresent && !Arrays.equals(info.sha256, hash)) {
                Log.d(TAG,"SHA256 WRONG!");
                return null;
            }

            // If the options CONFIG_SECURE_SIGNED_APPS_NO_SECURE_BOOT or CONFIG_SECURE_BOOT_ENABLED
            // are enabled then the application image will have additional 68 bytes for an ECDSA
            // signature
            if (f.length() > (info.crcPos+1+32))
                info.signed = true;

            return info;
        } catch (Exception e) {
            Log.e(TAG,e.toString());
            return null;
        } finally {
            try {
                if (raf != null)
                    raf.close();
            } catch (Exception e) {
                Log.e(TAG,e.toString());
            }
        }
    }
}

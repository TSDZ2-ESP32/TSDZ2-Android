package spider65.ebike.tsdz2_esp32.ota;

import android.util.Log;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

class Esp32AppImageTool {

    private static final String TAG = "Esp32AppImageTool";

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
        ByteBuffer bb = ByteBuffer.wrap(data);
        bb.order(ByteOrder.LITTLE_ENDIAN);
        return bb.getInt() & 0xffffffffL;
    }

    private static byte calcCRC(File f) throws IOException{
        byte crc = (byte)0xef;
        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(f, "r");
            raf.seek(1);
            int segmentCount = raf.readUnsignedByte();
            long pos = 24;
            for (int i = 0; i < segmentCount; i++) {
                raf.seek(pos + 4); // esp_image_segment_header_t.data_len
                long segSize = readUnsignedIntLittleEndian(raf); // read current segment size
                byte[] tmp = new byte[(int)segSize];
                raf.readFully(tmp);
                for (int j = 0; j < segSize; j++)
                    crc ^= tmp[j];
                pos += 8 + segSize; // add sizeof(esp_image_segment_header_t) + segSize
            }
        } catch (IOException e) {
            throw e;
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
            raf.seek(0x120);
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
        byte crcCalc = (byte)0xef;
        try {
            raf = new RandomAccessFile(f,"r");
            if (raf.read() != 0xE9) {
                Log.d(TAG,"Wrong Magic byte");
                return null;
            }
            raf.seek(1);
            int segmentCount = raf.readUnsignedByte();
            raf.seek(23);
            // check if SHA256 hash is present after checksum
            info.shaPresent = (raf.readUnsignedByte() == 1);
            // read app version string
            byte[] data = new byte[32];
            raf.seek(48);
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
            raf.seek(0x120);
            info.btPin = readUnsignedIntLittleEndian(raf);
            // Calculate CRC of all segments data
            raf.seek(24 + 4);
            info.dataLength = 24;  // first esp_image_segment_header_t position
            for (int i=0; i<segmentCount; i++) {
                raf.seek(info.dataLength+4); // esp_image_segment_header_t.data_len
                long segSize = readUnsignedIntLittleEndian(raf); // read current segment size
                info.dataLength += 8 + segSize; // add sizeof(esp_image_segment_header_t) + segSize
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

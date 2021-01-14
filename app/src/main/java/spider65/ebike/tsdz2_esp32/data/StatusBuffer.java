package spider65.ebike.tsdz2_esp32.data;

import androidx.annotation.NonNull;

import static spider65.ebike.tsdz2_esp32.TSDZConst.STATUS_ADV_SIZE;

/*
Circular buffer containing TSDZ_STATUS_BROADCAST records. One buffer instance contains up to 300 records (5 minutes)
The record structure is the timestamp (8 bytes System.currentTimeMillis()) and the TSDZ_STATUS_BROADCAST byte[] data
 */
public class StatusBuffer {

    private static final int NUM_RECORDS = 300;

    private final static Object mLock = new Object();

    private static StatusBuffer mPool;
    private StatusBuffer next;

    // First and last timestamps of the records contained in the StatusBuffer
    long startTime,endTime;

    public int position = 0;
    public byte[] data = new byte[(8+STATUS_ADV_SIZE)*NUM_RECORDS];

    // Return the next free StatusBuffer from the Pool or a new one if all are used
    // The pool is growing with the use
    static StatusBuffer obtain() {
        synchronized (mLock) {
            if (mPool != null) {
                StatusBuffer res = mPool;
                mPool = res.next;
                return res;
            }
            return new StatusBuffer();
        }
    }

    static void recycle(@NonNull StatusBuffer buffer) {
        synchronized (mLock) {
            buffer.position = 0;
            buffer.startTime = 0;
            buffer.endTime = 0;
            buffer.next = mPool;
            mPool = buffer;
        }
    }

    boolean addRecord(byte[] rec, long time) {
        if (position >= data.length)
            return true;

        data[position++] = (byte) ((time >>> 56) & 0xFF);
        data[position++] = (byte) ((time >>> 48) & 0xFF);
        data[position++] = (byte) ((time >>> 40) & 0xFF);
        data[position++] = (byte) ((time >>> 32) & 0xFF);
        data[position++] = (byte) ((time >>> 24) & 0xFF);
        data[position++] = (byte) ((time >>> 16) & 0xFF);
        data[position++] = (byte) ((time >>> 8) & 0xFF);
        data[position++] = (byte) (time & 0xFF);
        System.arraycopy(rec, 0, data, position, STATUS_ADV_SIZE);
        position += STATUS_ADV_SIZE;
        if (startTime == 0)
            startTime = time;
        endTime = time;

        return (position >= data.length);
    }
}

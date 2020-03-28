package spider65.ebike.tsdz2_esp32.data;

import androidx.annotation.NonNull;

import static spider65.ebike.tsdz2_esp32.TSDZConst.DEBUG_ADV_SIZE;

public class DebugBuffer {
    private static final int NUM_RECORDS = 300;

    private final static Object mLock = new Object();
    private static DebugBuffer mPool;

    static DebugBuffer obtain() {
        synchronized (mLock) {
            if (mPool != null) {
                DebugBuffer res = mPool;
                mPool = res.next;
                return res;
            }
            return new DebugBuffer();
        }
    }

    static void recycle(@NonNull DebugBuffer buffer) {
        synchronized (mLock) {
            buffer.position = 0;
            buffer.startTime = 0;
            buffer.endTime = 0;
            buffer.next = mPool;
            mPool = buffer;
        }
    }

    private DebugBuffer next;
    long startTime,endTime;
    public int position = 0;
    public byte[] data = new byte[(8+DEBUG_ADV_SIZE)*NUM_RECORDS];

    boolean addRecord(byte[] rec, long time) {
        if (position >= data.length)
            return true;
        data[position++] = (byte)((time >>> 56) & 0xFF);
        data[position++] = (byte)((time >>> 48) & 0xFF);
        data[position++] = (byte)((time >>> 40) & 0xFF);
        data[position++] = (byte)((time >>> 32) & 0xFF);
        data[position++] = (byte)((time >>> 24) & 0xFF);
        data[position++] = (byte)((time >>> 16) & 0xFF);
        data[position++] = (byte)((time >>> 8) & 0xFF);
        data[position++] = (byte)(time & 0xFF);
        System.arraycopy(rec,0, data, position, DEBUG_ADV_SIZE);
        position+=DEBUG_ADV_SIZE;
        if (startTime == 0)
            startTime = time;
        endTime = time;

        return (position >= data.length);
    }
}

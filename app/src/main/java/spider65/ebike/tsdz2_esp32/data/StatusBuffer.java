package spider65.ebike.tsdz2_esp32.data;

import androidx.annotation.NonNull;

import static spider65.ebike.tsdz2_esp32.TSDZConst.STATUS_ADV_SIZE;

public class StatusBuffer {

    private StatusBuffer next;
    public byte[] data = new byte[STATUS_ADV_SIZE];

    private final static Object mLock = new Object();
    private static StatusBuffer mPool;

    public static StatusBuffer obtain() {
        synchronized (mLock) {
            if (mPool != null) {
                StatusBuffer res = mPool;
                mPool = res.next;
                return res;
            }
            return new StatusBuffer();
        }
    }

    public static void recycle(@NonNull StatusBuffer buffer) {
        synchronized (mLock) {
            buffer.next = mPool;
            mPool = buffer;
        }
    }
}

package spider65.ebike.tsdz2_esp32.data;

public class RingBuffer {
    private int size;
    private float emptyVal;

    private int first;
    private int num;
    private float[] buffer;
    private long lastTimeRef;

    public RingBuffer(int size, float emptyVal) {
        this.size = size;
        this.emptyVal = emptyVal;
        buffer = new float[size];
        first = 0;
        num = 0;
        lastTimeRef = 0;
    }

    public void add(float value, long timeRef) {
        synchronized (this) {
            if (num == 0)
                lastTimeRef = timeRef - 1;

            // fill eventually missing values
            while (lastTimeRef++ < timeRef) {
                add(emptyVal);
            }
            add(value);
        }
    }

    private void add(float value) {
        if (num == size)
            buffer[(++first+num)%size] = value;
        else
            buffer[(first+num++)%size] = value;
    }

    public float[] getValues() {
        synchronized (this) {
            float[] copy = new float[num];
            for (int i = 0; i < num; i++)
                copy[i] = buffer[(first + i) % size];
            return copy;
        }
    }

    public long lastRef() {
        synchronized (this) {
            return lastTimeRef;
        }
    }
}

package spider65.ebike.tsdz2_esp32.ota;

import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import fi.iki.elonen.NanoHTTPD;


public class HttpdServer extends NanoHTTPD {
    private static final String TAG = "HttpdServer";

    private final File updateFile;
    private final ProgressInputStreamListener listener;

    public HttpdServer(File file, ProgressInputStreamListener listener) {
        super(8089);
        updateFile = file;
        this.listener = listener;
    }

    @Override
    public Response serve(IHTTPSession session) {
        ProcessInputStream pis = null;
        long size = 0;
        try {
            size = updateFile.length();
            //size = context.getContentResolver().openFileDescriptor(fileUri, "r").getStatSize();
            InputStream inStream = new FileInputStream(updateFile);
            //InputStream inStream = context.getContentResolver().openInputStream(fileUri);
            Log.i(TAG, "File length is : " + size);
            pis = new ProcessInputStream(inStream, size);
            pis.setListener(listener);
        } catch (Exception e) {
            Log.e(TAG,e.toString());
        }
        return newFixedLengthResponse(Response.Status.OK, "application/octet-stream", pis, size);
    }

    public static class ProcessInputStream extends InputStream{

        private final InputStream in;
        private final long length;
        private long sumRead;
        private ProgressInputStreamListener listener = null;
        private int percent;

        public ProcessInputStream(InputStream inputStream, long length) {
            this.in=inputStream;
            sumRead=0;
            this.length=length;
        }

        @Override
        public int read(byte[] b) throws IOException {
            int readCount = in.read(b);
            evaluatePercent(readCount);
            return readCount;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int readCount = in.read(b, off, len);
            evaluatePercent(readCount);
            return readCount;
        }

        @Override
        public long skip(long n) throws IOException {
            long skip = in.skip(n);
            evaluatePercent(skip);
            return skip;
        }

        @Override
        public int read() throws IOException {
            int read = in.read();
            if(read!=-1){
                evaluatePercent(1);
            }
            return read;
        }

        public void setListener(ProgressInputStreamListener listener){
            this.listener = listener;
        }

        private void evaluatePercent(long readCount){
            if(readCount!=-1){
                sumRead+=readCount;
                percent=(int)(sumRead*100/length);
            }
            notifyListener();
        }

        private void notifyListener(){
            if (listener != null)
                listener.progress(percent);
        }
    }
}
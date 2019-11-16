package spider65.ebike.tsdz2_esp32.ota;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;


public class HttpdServer extends NanoHTTPD {
    private static final String TAG = "HttpdServer";

    private File updateFile;
    private Context context;
    private ProgressInputStreamListener listener = null;

    public HttpdServer(File file, Context context, ProgressInputStreamListener listener) {
        super(8089);
        updateFile = file;
        this.context = context;
        this.listener = listener;
    }

    private HttpdServer() {
        super(8089);
    }


    @Override
    public Response serve(String uri, Method method,
                          Map<String, String> header, Map<String, String> parameters,
                          Map<String, String> files) {

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
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return newFixedLengthResponse(Response.Status.OK, "application/octet-stream", pis, size);
        //return newChunkedResponse(Response.Status.OK, "application/octet-stream", inStream);
    }

    public static class ProcessInputStream extends InputStream{

        private InputStream in;
        private long length,sumRead;
        private ProgressInputStreamListener listener = null;
        private int percent;

        public ProcessInputStream(InputStream inputStream, long length) throws IOException {
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

        public ProcessInputStream setListener(ProgressInputStreamListener listener){
            this.listener = listener;
            return this;
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
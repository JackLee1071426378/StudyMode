package jackli.updatehttp;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import okhttp3.Call;
import okhttp3.Response;

import static jackli.updatehttp.DownUtils.DownLoadCallback.*;

/***********************************************
 *                                             *
 *             Written by JackLi               *
 *                  8/4/2018                   *
 *                                             *
 ***********************************************/

public class DownUtils {

    public static void download(final String url, final String path, final int bufSize, final DownLoadCallback callback)
    {
        HtmlUtils.request(url, new HtmlUtils.HtmlProcess() {
            @Override
            public void process(Call call, Response response, String name) throws NullPointerException, IOException {
                try {
                    InputStream inputStream = response.body().byteStream();
                    final long length = response.body().contentLength();
                    File space = new File(path);
                    File file = new File(space, name);
                    System.out.println("Free Space:" + space.getFreeSpace() + "\nRequire Space:" + length);
                    long startedTime = System.currentTimeMillis();
                    if (space.getFreeSpace() < length) {
                        callback.onFail(LACK_OF_SPACE, null);
                    } else {
                        if (file.exists()) {
                            file.delete();
                        }
                        FileOutputStream fileOutputStream = new FileOutputStream(file);
                        byte[] fileGet = new byte[bufSize];
                        long sizeOfDownloaded = 0;
                        int gotLength = inputStream.read(fileGet);
                        while (gotLength != -1) {
                            fileOutputStream.write(fileGet, 0, gotLength);
                            sizeOfDownloaded += gotLength;
                            int per = (int) ((sizeOfDownloaded * 1f) / (length * 1f) * 100f);
                            callback.onProcess(sizeOfDownloaded, length, per);
                            gotLength = inputStream.read(fileGet);
                        }
                        fileOutputStream.close();
                        callback.onDone(file);
                        System.out.println("Done " + (System.currentTimeMillis() - startedTime) + "ms");
                    }
                    inputStream.close();
                }catch (FileNotFoundException e)
                {
                    callback.onFail(PERMISSION_DENIED,e);
                }
            }
        }, callback);
    }

    public interface DownLoadCallback extends HtmlUtils.HtmlErrorCallback
    {
        int LACK_OF_SPACE = 2;
        int PERMISSION_DENIED = 3;

        void onProcess(long current, long total, int percents);

        void onDone(File file);
    }
}
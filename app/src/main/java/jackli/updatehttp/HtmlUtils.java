package jackli.updatehttp;

import java.io.IOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import static jackli.updatehttp.HtmlUtils.HtmlErrorCallback.*;


/***********************************************
 *                                             *
 *             Written by JackLi               *
 *                  8/6/2018                   *
 *                                             *
 ***********************************************/

public class HtmlUtils {

    public static void request(final String url, final HtmlProcess htmlProcess, final HtmlErrorCallback callback)
    {
        new Thread()
        {
            @Override
            public void run() {
                super.run();
                if (url == null)
                {
                    callback.onFail(FILE_NOT_FOUND,new NullPointerException("Update url was null!"));
                    return;
                }
                String _name = getFileName(url);
                if (_name.equals(""))
                {
                    _name = getFileName(url.substring(0,url.length() - 1));
                }
                final String name = _name;
                new OkHttpClient().newCall(new Request.Builder().url(url).build()).enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        callback.onFail(CONNECT_TIMES_OUT,e);
                    }

                    @Override
                    public void onResponse(Call call, Response response){
                        try {
                            htmlProcess.process(call, response, name);
                        }catch (NullPointerException e)
                        {
                            callback.onFail(FILE_NOT_FOUND,e);
                        }catch (SocketException e)
                        {
                            callback.onFail(NETWORK_CHANGED,e);
                        }catch (SocketTimeoutException e)
                        {
                            callback.onFail(CONNECT_TIMES_OUT,e);
                        }catch (IOException e)
                        {
                            e.printStackTrace();
                        }
                    }
                });
            }
        }.start();
    }

    public interface HtmlErrorCallback
    {
        int FILE_NOT_FOUND = -1;
        int CONNECT_TIMES_OUT = 0;
        int NETWORK_CHANGED = 1;

        void onFail(int type, Exception e);
    }

    public interface HtmlProcess
    {
        void process(Call call, Response response, String name) throws NullPointerException,IOException;
    }

    private static String getFileName(String url)
    {
        return url.substring(url.lastIndexOf("/") + 1);
    }
}

package jackli.updatehttp;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ProgressBar;

import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Response;

/***********************************************
 *                                             *
 *             Written by JackLi               *
 *                  8/6/2018                   *
 *                                             *
 ***********************************************/

public class HttpUpdate {

    AlertDialog load = null;

    Context context;

    public HttpUpdate(Context context)
    {
        this.context = context;
    }

    public HttpUpdate openLoadingView(View loadView)
    {
        AlertDialog.Builder loadBuilder = new AlertDialog.Builder(context);
        loadBuilder.setView(loadView);
        load = loadBuilder.create();
        load.show();
        return this;
    }

    private void dismissLoad()
    {
        if (load != null)
        {
            load.dismiss();
        }
    }

    public void getInformation(final String checkUrl, final String appName, final String localPath, final UpdateCall updateCall)
    {
        final Object[] objs = new Object[3];
        objs[0] = updateCall;
        /*
            0 - updateCall
            1 - information/type
            2 - e
        */
        HtmlUtils.request(checkUrl, new HtmlUtils.HtmlProcess() {
            @Override
            public void process(Call call, Response response, String name) throws NullPointerException, IOException {
                String urlJson = response.body().string();
                try
                {
                    Map<String,Object> appInformationUrls = JSONObject.parseObject(urlJson);
                    String informationUrl = (String) appInformationUrls.get(appName);
                    Log.i("I","Get URL:" + informationUrl);
                    HtmlUtils.request(informationUrl, new HtmlUtils.HtmlProcess() {
                        @Override
                        public void process(Call call, Response response, String name) throws NullPointerException, IOException {
                            String informationJson = response.body().string();
                            try {
                                Map<String,Object> appInformations = JSONObject.parseObject(informationJson);
                                Information information = new Information();
                                information.localPath = localPath;
                                information.updateCall = updateCall;
                                information.version = (String) appInformations.get("version");
                                information.versionCode = (int) appInformations.get("versionCode");
                                information.message = (String) appInformations.get("message");
                                information.downloadUrl = (String) appInformations.get("url");
                                Message msg = new Message();
                                msg.what = 0;
                                objs[1] = information;
                                objs[2] = HttpUpdate.this;
                                msg.obj = objs;
                                handler.sendMessage(msg);
                            } catch (JSONException e)
                            {
                                dismissLoad();
                                Message msg = new Message();
                                msg.what = 2;
                                objs[1] = UpdateCall.JSON_ERROR;
                                objs[2] = e;
                                msg.obj = objs;
                                handler.sendMessage(msg);
                            }
                        }
                    }, new HtmlUtils.HtmlErrorCallback() {
                        @Override
                        public void onFail(int type, Exception e) {
                            dismissLoad();
                            Message msg = new Message();
                            msg.what = 2;
                            objs[1] = type;
                            objs[2] = e;
                            msg.obj = objs;
                            handler.sendMessage(msg);
                        }
                    });
                }catch (JSONException e)
                {
                    dismissLoad();
                    Message msg = new Message();
                    msg.what = 2;
                    objs[1] = UpdateCall.JSON_ERROR;
                    objs[2] = e;
                    msg.obj = objs;
                    handler.sendMessage(msg);
                }
            }
        }, new HtmlUtils.HtmlErrorCallback() {
            @Override
            public void onFail(int type, Exception e) {
                dismissLoad();
                Message msg = new Message();
                msg.what = 2;
                objs[1] = type;
                objs[2] = e;
                msg.obj = objs;
                handler.sendMessage(msg);
            }
        });
    }

    private static void install (Context context, Uri uri)
    {
        Intent intent = new Intent();
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setAction(android.content.Intent.ACTION_VIEW);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setDataAndType(uri, "application/vnd.android.package-archive");
        context.startActivity(intent);
    }

    @Deprecated
    public static void installApk(Context context, File file)
    {
        install(context, Uri.fromFile(file));
    }

    public static void installApk(Context context, File file, String fileProvider)
    {
        install(context, FileProvider.getUriForFile(context, fileProvider, file));
    }

    @SuppressLint("HandlerLeak")
    public Handler handler = new Handler()
    {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            Object[] objs = (Object[]) msg.obj;
            UpdateCall updateCall = (UpdateCall) objs[0];
            switch (msg.what)
            {
                case 0:
                    dismissLoad();
                    Information information = (Information) objs[1];
                    HttpUpdate httpUpdate = (HttpUpdate) objs[2];
                    updateCall.onInFormationDone(httpUpdate, information);
                    break;
                case 1:
                    ((ProgressDialog) objs[1]).dismiss();
                    File file = (File) objs[2];
                    updateCall.onDownloadDone(file);
                    break;
                case 2:
                    int type = (int) objs[1];
                    Exception e = (Exception) objs[2];
                    updateCall.onFail(type, e);
                    break;
            }

        }
    };

    public Dialog createDialog(Information information)
    {
        return new Dialog(information);
    }

    public class Dialog
    {
        private String UPDATE = "Update";
        private String CANCEL = "Cancel";
        private String TITLE_UPDATE = "Found new version";
        private String TITLE_DOWNLOAD = "Downloading...";

        private AlertDialog mAlertDialog;
        private AlertDialog.Builder mBuilder;

        private Information mInformation;

        private DialogInterface.OnClickListener onClickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                final ProgressDialog progressDialog = new ProgressDialog(context);
                progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                progressDialog.setMax(100);
                progressDialog.setTitle(TITLE_DOWNLOAD);
                progressDialog.setCancelable(false);
                progressDialog.show();
                final Object[] objs = new Object[3];
                objs[0] = mInformation.updateCall;
                                /*
                                    0 - updateCall
                                    1 - type
                                    2 - e
                                */
                @SuppressLint("HandlerLeak") final Handler processHandler = new Handler()
                {
                    @Override
                    public void handleMessage(Message msg) {
                        super.handleMessage(msg);
                        if (msg.what == -1)
                        {
                            progressDialog.dismiss();
                        }
                        if (msg.what == 1)
                        {
                            long[] obj = (long[]) msg.obj;
                            if (progressDialog.getMax() == 100)
                            {
                                progressDialog.setMax((int) obj[1]);
                            }
                            progressDialog.setProgress((int) obj[0]);
                        }
                    }
                };
                DownUtils.download(mInformation.downloadUrl, mInformation.localPath, 1000, new DownUtils.DownLoadCallback() {
                    @Override
                    public void onProcess(long current, long total, int percents) {
                        Message msg = new Message();
                        msg.what = 1;
                        long[] obj = new long[2];
                        obj[0] = current;
                        obj[1] = total;
                        msg.obj = obj;
                        processHandler.sendMessage(msg);
                    }

                    @Override
                    public void onDone(File file) {
                        Message msg = new Message();
                        msg.what = 1;
                        objs[1] = progressDialog;
                        objs[2] = file;
                        msg.obj = objs;
                        handler.sendMessage(msg);
                    }

                    @Override
                    public void onFail(int type, Exception e) {
                        Message message = new Message();
                        message.what = -1;
                        processHandler.sendMessage(message);
                        Message msg = new Message();
                        msg.what = 2;
                        objs[1] = type;
                        objs[2] = e;
                        msg.obj = objs;
                        handler.sendMessage(msg);
                    }
                });
            }
        };

        public Dialog(Information information)
        {
            mBuilder = new AlertDialog.Builder(context);
            this.mInformation = information;
            mBuilder.setTitle(TITLE_UPDATE)
                    .setMessage(mInformation.message)
                    .setNegativeButton(CANCEL,null)
                    .setPositiveButton(UPDATE, onClickListener);
        }

        public AlertDialog.Builder getBuilder() {
            return mBuilder;
        }

        public Dialog setTitleOfUpdate(String titleOfUpdate)
        {
            TITLE_UPDATE = titleOfUpdate;
            mBuilder.setTitle(TITLE_UPDATE);
            return this;
        }

        public Dialog setTitleOfDownload(String titleOfDownload)
        {
            TITLE_DOWNLOAD = titleOfDownload;
            return this;
        }

        public Dialog setUpdateButton(String update, @Nullable DialogInterface.OnClickListener onClickListener)
        {
            this.UPDATE = update;
            if (onClickListener == null) mBuilder.setPositiveButton(UPDATE, this.onClickListener);
            else mBuilder.setPositiveButton(UPDATE, onClickListener);
            return this;
        }

        public Dialog setCancelButton(String cancel, @Nullable DialogInterface.OnClickListener onClickListener)
        {
            this.CANCEL = cancel;
            mBuilder.setNegativeButton(CANCEL, onClickListener);
            return this;
        }

        public Dialog setMessage(String message)
        {
            mBuilder.setMessage(message);
            return this;
        }

        public AlertDialog create()
        {
            return mBuilder.create();
        }

        public void show()
        {
            mBuilder.create().show();
        }
    }

    public class Information
    {
        // Get from setting
        public String localPath;
        private UpdateCall updateCall;

        // Get from JSON
        public String version;
        public int versionCode;
        public String message;
        public String downloadUrl;

        private boolean checkData()
        {
            if (version == null || message == null || downloadUrl == null)
            {
                updateCall.onFail(UpdateCall.JSON_ERROR,null);
                return false;
            }
            return true;
        }

        public boolean checkIsLatest()
        {
            int currentVersionCode = 0;
            String currentVersion = "";
            try {
                currentVersionCode = context.getPackageManager().getPackageInfo(context.getPackageName(),0).versionCode;
                currentVersion = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
            } catch (PackageManager.NameNotFoundException e)
            {
                e.printStackTrace();
            }
            return !(versionCode > currentVersionCode || ((versionCode == currentVersionCode) && (!version.equals(currentVersion))));
        }

        @Override
        public String toString() {
            return    "localPath : " + localPath + "\n"
                    + "version : " + version + "\n"
                    + "versionCode : " + versionCode + "\n"
                    + "message : " + message + "\n"
                    + "downloadUrl : " + downloadUrl;
        }
    }

    public interface UpdateCall
    {
        int JSON_ERROR = -2;

        void onInFormationDone(HttpUpdate httpUpdate, Information information);//0

        void onDownloadDone(File file);//1

        void onFail(int type, Exception e);//2
    }

}

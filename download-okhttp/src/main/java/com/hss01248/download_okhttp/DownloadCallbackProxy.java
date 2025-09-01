package com.hss01248.download_okhttp;

/**
 * @Despciption todo
 * @Author hss
 * @Date 9/1/25 4:51 PM
 * @Version 1.0
 */
public class DownloadCallbackProxy implements IDownloadCallback{

    public DownloadCallbackProxy setCallback(IDownloadCallback callback) {
        this.callback = callback;
        return this;
    }

    IDownloadCallback callback;

    public IDownloadCallback getCallback() {
        return callback;
    }

    @Override
    public void onSuccess(String url, String path) {
        if(callback != null){
            callback.onSuccess(url, path);
        }
    }

    @Override
    public void onFailed(String url, String path, String code, String msg, Throwable e) {
        if(callback != null){
            callback.onFailed(url, path, code, msg, e);
        }

    }

    @Override
    public void onProgress(String url, String path, long total, long alreadyReceived, long speed) {
        IDownloadCallback.super.onProgress(url, path, total, alreadyReceived, speed);
        if(callback != null){
            callback.onProgress(url, path, total, alreadyReceived, speed);
        }
    }

    @Override
    public void onCancel(String url, String path) {
        IDownloadCallback.super.onCancel(url, path);
        if(callback != null){
            callback.onCancel(url, path);
        }
    }

    @Override
    public void onStartReal(String url, String path) {
        IDownloadCallback.super.onStartReal(url, path);
        if(callback != null){
            callback.onStartReal(url, path);
        }
    }

    @Override
    public void onCodeStart(String url, String path) {
        IDownloadCallback.super.onCodeStart(url, path);
        if(callback != null){
            callback.onCodeStart(url, path);
        }
    }
}

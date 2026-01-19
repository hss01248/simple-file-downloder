package com.hss01248.download_okhttp;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * @Despciption todo
 * @Author hss
 * @Date 8/27/24 3:01 PM
 * @Version 1.0
 */
public class OkhttpDownloadUtil {

    public static void setClient(OkHttpClient client) {
        OkhttpDownloadUtil.client = client;
    }

    volatile static OkHttpClient client;

    volatile static Set<String> runningTask = new CopyOnWriteArraySet<>();
    static HashMap<String, IDownloadCallback> callbackHashMap = new HashMap<>();

    public static void pauseOrStop(String url) {
        runningTask.remove(url);
    }

    volatile static ExecutorService service;

    public static void setThreadCount(int threadCount) {
        OkhttpDownloadUtil.threadCount = threadCount;
    }

    static int threadCount = 30;

    public static void setLogEnable(boolean logEnable) {
        OkhttpDownloadUtil.logEnable = logEnable;
    }

    static boolean logEnable = false;

    public static void setGlobalSaveDir(String globalSaveDir) {
        OkhttpDownloadUtil.globalSaveDir = globalSaveDir;
    }

    static String globalSaveDir;

    public static void downloadAsync(DownloadConfig config) {
        if (service == null) {
            service = Executors.newFixedThreadPool(threadCount);
        }
        try {
            service.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        downloadSync(config);
                    } catch (Throwable throwable) {
                        w(throwable, config.getUrl());
                        config.getCallback().onFailed(config.getUrl(), config.getFilePath(),
                                throwable.getClass().getSimpleName(), throwable.getMessage(), throwable);
                    }
                }
            });
        } catch (Throwable throwable) {
            // 等待队列超出长度,栈溢出等
            w(throwable);
            config.getCallback().onFailed(config.getUrl(), config.getFilePath(),
                    throwable.getClass().getSimpleName(), throwable.getMessage(), throwable);
        }

    }

    public static void downloadSync(DownloadConfig config) {

        String url = config.getUrl();
        String filePath = config.getFilePath();
        boolean forceRedownload = config.isForceRedownload();
        boolean notAcceptRanges = config.isNotAcceptRanges();

        Map<String, String> headers = config.getHeaders();
        Long fileSizeAlreadyKnown = config.getFileSizeAlreadyKnown();
        IDownloadCallback callback = config.getCallback();
        callback.onStartReal(url, filePath);

        initClient();
        if (runningTask.contains(url)) {
            w("该url已经在下载中,切换callback", url);
            // callback.onFailed(url,filePath,"","该url已经在下载中",null);
            IDownloadCallback iDownloadCallback = callbackHashMap.get(url);
            if (iDownloadCallback instanceof DownloadCallbackProxy) {
                DownloadCallbackProxy callbackProxy = (DownloadCallbackProxy) iDownloadCallback;
                callbackProxy.setCallback(config.getCallback());
            }
            return;
        }
        try {
            filePath = FileAndDirUtil.dealFilePath(config);
            config.setFilePath(filePath);
        } catch (Throwable e) {
            callback.onFailed(url, filePath, "", e.getMessage(), null);
            return;
        }

        File file = new File(filePath);

        Request.Builder builder = new Request.Builder()
                .url(url)
                .get();
        if (headers != null) {
            for (String s : headers.keySet()) {
                builder.header(s, headers.get(s) + "");
            }
        }
        if (file.exists() && file.isDirectory()) {
            callback.onFailed(url, filePath, "", "file path is dir, please rename it or your file path", null);
            return;
        }
        runningTask.add(url);
        DownloadCallbackProxy proxy = new DownloadCallbackProxy().setCallback(callback);
        callbackHashMap.put(url, proxy);
        callback = proxy;
        config.setCallback(proxy);

        boolean isRangeRequest = false;
        File tempFile = null;

        // 首先发送 HEAD 请求获取 Content-Length（如果未知）
        if (fileSizeAlreadyKnown == null || fileSizeAlreadyKnown == 0) {
            Request.Builder headBuilder = new Request.Builder()
                    .url(url)
                    .head();
            if (headers != null) {
                for (String s : headers.keySet()) {
                    headBuilder.header(s, headers.get(s) + "");
                }
            }
            Request headRequest = headBuilder.build();
            try {
                Response headResponse = client.newCall(headRequest).execute();
                if (headResponse.isSuccessful()) {
                    String lenStr = headResponse.header("Content-Length");
                    if (lenStr != null && !"".equals(lenStr)) {
                        try {
                            fileSizeAlreadyKnown = Long.parseLong(lenStr);
                        } catch (Throwable throwable) {
                        }
                    }
                } else {
                    w("head() request failed", url, headResponse.code(), headResponse.message());
                }
            } catch (Throwable throwable) {
                w("head() request failed", url, throwable);
            }
        }

        // 根据 Content-Length 生成临时文件路径
        if (fileSizeAlreadyKnown != null && fileSizeAlreadyKnown > 0) {
            tempFile = new File(FileAndDirUtil.getTempFilePath(filePath, fileSizeAlreadyKnown));
        } else {
            // 如果没有 Content-Length，使用旧格式
            tempFile = new File(filePath + ".0.tmp");
        }

        d("tempFile path: " + tempFile.getAbsolutePath(), "content-length: " + fileSizeAlreadyKnown);

        // 优先检查最终文件是否已存在
        if (file.exists() && file.isFile() && file.length() > 0) {
            if (forceRedownload) {
                file.delete();
                // 清理所有相关临时文件
                FileAndDirUtil.cleanupOtherTempFiles(file, null);
            } else {
                if (fileSizeAlreadyKnown != null && fileSizeAlreadyKnown > 0) {
                    if (file.length() == fileSizeAlreadyKnown) {
                        // 文件大小一致，已经下载完成
                        d("file already exist and same bytes as header", filePath, url);
                        runningTask.remove(url);
                        callbackHashMap.remove(url);
                        // 清理可能存在的临时文件
                        FileAndDirUtil.cleanupOtherTempFiles(file, null);
                        callback.onSuccess(url, filePath);
                        return;
                    } else {
                        // 服务端文件大小与本地不一致，说明服务端文件已变更
                        d("server file changed, local size: " + file.length() + ", remote size: "
                                + fileSizeAlreadyKnown);
                        if (config.isKeepHistoryVersions()) {
                            // 保留历史版本
                            if (!FileAndDirUtil.renameToHistoryVersion(file, config.getMaxHistoryVersions())) {
                                w("failed to rename old file to history version", file.getAbsolutePath());
                            }
                        } else {
                            // 直接删除旧文件
                            file.delete();
                        }
                        // 清理旧的临时文件（大小不匹配的）
                        FileAndDirUtil.cleanupOtherTempFiles(file, tempFile);
                    }
                } else {
                    // 没有 Content-Length，无法判断是否需要重新下载，跳过
                    d("file exists but no content-length to compare, assuming complete", filePath);
                    runningTask.remove(url);
                    callbackHashMap.remove(url);
                    callback.onSuccess(url, filePath);
                    return;
                }
            }
        }

        // 检查是否存在其他临时文件（可能是之前下载不同大小版本的遗留）
        File existingTempFile = FileAndDirUtil.findMatchingTempFile(file, fileSizeAlreadyKnown);
        if (existingTempFile != null && !existingTempFile.equals(tempFile)) {
            // 存在大小不匹配的临时文件，说明服务端文件已变更，删除旧临时文件
            d("found old temp file with different size, deleting: " + existingTempFile.getName());
            existingTempFile.delete();
        }

        // 检查当前临时文件是否存在，用于断点续传
        if (tempFile.exists() && tempFile.isFile() && tempFile.length() > 0) {
            if (forceRedownload) {
                tempFile.delete();
            } else {
                if (fileSizeAlreadyKnown != null && fileSizeAlreadyKnown > 0) {
                    if (tempFile.length() == fileSizeAlreadyKnown) {
                        // 临时文件已下载完成，直接重命名
                        d("temp file already complete, renaming", filePath, url);
                        if (tempFile.renameTo(file)) {
                            runningTask.remove(url);
                            callbackHashMap.remove(url);
                            callback.onSuccess(url, filePath);
                            return;
                        } else {
                            w("rename temp file to final file failed", tempFile.getAbsolutePath(), filePath);
                        }
                    } else if (tempFile.length() < fileSizeAlreadyKnown) {
                        // 临时文件未完成，尝试断点续传
                        if (!notAcceptRanges) {
                            builder.header("Range", "bytes=" + tempFile.length() + "-");
                            isRangeRequest = true;
                            d("resuming download from byte " + tempFile.length());
                        } else {
                            // 客户端不允许断点续传，删除临时文件重新下载
                            tempFile.delete();
                        }
                    } else {
                        // 临时文件比预期大，删除重新下载
                        tempFile.delete();
                    }
                }
            }
        }
        /*
         * if(!isRangeRequest){
         * file.delete();
         * }
         */
        Request request = builder
                .build();
        try {
            Response response = client.newCall(request).execute();
            if (!response.isSuccessful()) {
                w("download failed0", url, response.code(), response.message());
                runningTask.remove(url);
                callbackHashMap.remove(url);
                callback.onFailed(url, filePath, response.code() + "", "download failed: " + response.message(), null);
                return;
            }
            if (response.body() == null) {
                w("download failed: request success but response body is empty!", url, response.code(),
                        response.message());
                runningTask.remove(url);
                callbackHashMap.remove(url);
                callback.onFailed(url, filePath, "", "request success but response body is empty! ", null);
                return;
            }

            if (!isRangeRequest) {
                String lenStr = response.header("Content-Length");
                if (lenStr != null && !"".equals(lenStr)) {
                    try {
                        Long newContentLength = Long.parseLong(lenStr);
                        if (fileSizeAlreadyKnown == null || fileSizeAlreadyKnown == 0) {
                            fileSizeAlreadyKnown = newContentLength;
                            // 更新临时文件路径
                            File newTempFile = new File(FileAndDirUtil.getTempFilePath(filePath, fileSizeAlreadyKnown));
                            if (!tempFile.equals(newTempFile)) {
                                if (tempFile.exists()) {
                                    tempFile.delete();
                                }
                                tempFile = newTempFile;
                            }
                        }
                    } catch (Throwable throwable) {
                    }
                }
            }
            if (file.exists() && file.length() > 0) {
                if (!isRangeRequest) {
                    if (fileSizeAlreadyKnown != null && fileSizeAlreadyKnown > 0) {
                        if (file.length() == fileSizeAlreadyKnown) {
                            callback.onSuccess(url, file.getAbsolutePath());
                            d("文件大小与远程一致2," + url);
                            runningTask.remove(url);
                            callbackHashMap.remove(url);
                            FileAndDirUtil.cleanupOtherTempFiles(file, null);
                            return;
                        } else {
                            // 服务端文件已变更
                            if (config.isKeepHistoryVersions()) {
                                FileAndDirUtil.renameToHistoryVersion(file, config.getMaxHistoryVersions());
                            } else {
                                file.delete();
                            }
                        }
                    } else {
                        file.delete();
                    }
                }
            }

            // httpcode!=206 且响应头没有Content-Range的话,就说明还是全部文件,而不是部分文件:

            InputStream inputStream = response.body().byteStream();
            // 使用临时文件进行下载，append模式基于临时文件判断
            boolean append = tempFile.exists() && tempFile.length() > 0 && isRangeRequest;
            // inputStream.available():1049256 返回的和content-length不一致, 因为服务端用的buffered
            d("download as append: " + append, url, "inputStream.available():" +
                    inputStream.available(), "content-length:" + fileSizeAlreadyKnown,
                    "tempFile:" + tempFile.getAbsolutePath());
            if (fileSizeAlreadyKnown == null) {
                if (!append) {
                    // fileSizeAlreadyKnown = (long) inputStream.available();
                    w("没有返回cotent-length,尝试获取inputStream.available:" + inputStream.available());
                }
            }
            Long finalFileSizeAlreadyKnown1 = fileSizeAlreadyKnown;
            long len = tempFile.exists() ? tempFile.length() : 0;
            try {
                IDownloadCallback finalCallback = callback;
                // 下载到临时文件
                boolean success = writeFileFromIS(url, tempFile, inputStream, append, config, new IDownloadCallback() {
                    @Override
                    public void onSuccess(String url, String path) {

                    }

                    @Override
                    public void onFailed(String url, String path, String code, String msg, Throwable e) {

                    }

                    long lastReceived = 0;
                    long lastProgressTime = 0;

                    @Override
                    public void onProgress(String url, String path, long total, long alreadyReceived, long s) {

                        if (finalFileSizeAlreadyKnown1 == null) {
                            total = 0;
                        } else {
                            total = finalFileSizeAlreadyKnown1;
                        }
                        alreadyReceived = alreadyReceived + len;

                        if (lastReceived == 0) {
                            lastReceived = alreadyReceived;
                            lastProgressTime = System.currentTimeMillis();
                            finalCallback.onProgress(url, path, total, alreadyReceived, 0L);
                        } else {
                            long changed = alreadyReceived - lastReceived;
                            long speed = 0;
                            if (System.currentTimeMillis() != lastProgressTime) {
                                speed = changed * 1000 / (System.currentTimeMillis() - lastProgressTime);
                            }
                            lastProgressTime = System.currentTimeMillis();
                            lastReceived = alreadyReceived;
                            finalCallback.onProgress(url, path, total, alreadyReceived, speed);
                            if (finalFileSizeAlreadyKnown1 != null) {
                                d("download-progress",
                                        ((alreadyReceived + len) * 100.0 / finalFileSizeAlreadyKnown1) + "%, "
                                                + url,
                                        (alreadyReceived + len) / 1024 + "KB, speed: " + speed / 1024 + "KB/s");
                            }
                        }
                    }
                });
                runningTask.remove(url);
                callbackHashMap.remove(url);
                if (success) {
                    // 对比一下文件大小,相等才是成功:
                    if (fileSizeAlreadyKnown != null) {
                        if (tempFile.length() != fileSizeAlreadyKnown) {
                            w("download failed6", url, "size not same",
                                    "file size not same as the content-length: " + tempFile.length() + ", "
                                            + fileSizeAlreadyKnown);
                            callback.onFailed(url, filePath, "size not same",
                                    "file size not same as the content-length: " + tempFile.length() + ", "
                                            + fileSizeAlreadyKnown,
                                    null);
                            return;
                        }
                    }
                    // 下载成功，将临时文件重命名为最终文件名
                    if (file.exists()) {
                        file.delete();
                    }
                    if (tempFile.renameTo(file)) {
                        callback.onSuccess(url, filePath);
                        d("download success", url, response.code(), response.message(), filePath,
                                "file.length:" + file.length(), "content-length:" + finalFileSizeAlreadyKnown1);
                    } else {
                        // 重命名失败，可能跨文件系统，尝试复制
                        w("rename temp file failed, temp file path: " + tempFile.getAbsolutePath());
                        callback.onFailed(url, filePath, "rename_failed",
                                "download success but rename temp file to final file failed", null);
                    }
                } else {
                    w("download failed4", url, response.code(), response.message());
                    callback.onFailed(url, filePath, "", "download file write failed: ", null);
                }
            } catch (InterruptedException e) {
                w(url, e, "请求被取消/暂停");
                callback.onCancel(url, filePath);
            }

        } catch (Throwable throwable) {
            w("download failed-reqeust failed", url, filePath, throwable);
            runningTask.remove(url);
            callbackHashMap.remove(url);
            callback.onFailed(url, filePath, "", "download file  failed: " + throwable.getMessage(), throwable);
        }
    }

    private static void initClient() {
        if (client == null) {
            client = new OkHttpClient.Builder()
                    .followRedirects(true)
                    .followSslRedirects(true)
                    .retryOnConnectionFailure(true)
                    .connectTimeout(20, TimeUnit.SECONDS)
                    .readTimeout(20, TimeUnit.SECONDS)
                    .writeTimeout(20, TimeUnit.SECONDS)
                    .build();
        }
    }

    private static int sBufferSize = 524288;

    public static boolean writeFileFromIS(String url, final File file,
            final InputStream is,
            final boolean append,
            DownloadConfig config,
            final IDownloadCallback listener) throws InterruptedException, IOException {
        if (is == null || file.isDirectory()) {
            w("FileIOUtils, create file <" + file + "> failed.");
            return false;
        }
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            file.createNewFile();
        }
        OutputStream os = null;
        try {
            os = new BufferedOutputStream(new FileOutputStream(file, append), sBufferSize);
            if (listener == null) {
                byte[] data = new byte[sBufferSize];
                for (int len; (len = is.read(data)) != -1;) {
                    os.write(data, 0, len);
                }
            } else {
                // 对于网络流量,这个api不准确
                int curSize = 0;
                listener.onProgress(url, file.getAbsolutePath(), 0, 0, 0);
                byte[] data = new byte[sBufferSize];
                long lastProgress = 0;

                for (int len; (len = is.read(data)) != -1;) {
                    os.write(data, 0, len);
                    curSize += len;
                    if (System.currentTimeMillis() - lastProgress > config.getProgressCallbackIntervalMills()) {
                        lastProgress = System.currentTimeMillis();
                        listener.onProgress(url, file.getAbsolutePath(), 0, curSize, 0);
                    }
                    if (!runningTask.contains(url)) {
                        // 中断读取
                        throw new InterruptedException("paused or stop");
                    }
                }
                listener.onProgress(url, file.getAbsolutePath(), 0, curSize, 0);
            }
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        } finally {
            try {
                is.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                if (os != null) {
                    os.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    static void w(Object... args) {
        if (!logEnable) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("warn:  ");
        for (Object arg : args) {
            sb.append(arg)
                    .append("\n");
            if (arg instanceof Throwable) {
                ((Throwable) arg).printStackTrace();
            }
        }
        System.out.println(sb.toString());
    }

    static void d(Object... args) {
        if (!logEnable) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("debug:  ");
        for (Object arg : args) {
            sb.append(arg)
                    .append("\n");
            if (arg instanceof Throwable) {
                ((Throwable) arg).printStackTrace();
            }
        }
        System.out.println(sb.toString());
    }

    public static void main(String[] args) {
        OkhttpDownloadUtil.logEnable = true;
        String url = "https://oss-kodo.hss01248.tech/test_video/navi-crud.mov";
        DownloadConfig.newBuilder()
                .url(url)
                .saveDir("/Users/hss/Downloads2")
                .start(new IDownloadCallback() {
                    @Override
                    public void onSuccess(String url, String path) {
                        System.out.println("onSuccess: " + url + " " + path);

                    }

                    @Override
                    public void onFailed(String url, String path, String code, String msg, Throwable e) {
                        System.out.println("onFailed: " + url + " " + path + " " + code + " " + msg);

                    }
                });
    }
}

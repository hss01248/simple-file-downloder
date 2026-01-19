# simple-file-downloder

* simplely base on okhttp and http range, and  java thread

* no more depend on the fucking changeable android service.

> **[CHANGELOG2](./CHANGELOG2.md)** | **[更新日志](./CHANGELOG2-ZH.md)**

download-okhttp  is a java module ,can use in java server code;

download-okhttp-android is a simple callback wrapper and enter code wrapper for android.



# usage

```groovy
//java use
implementation "com.github.hss01248.simple-file-downloder:download-okhttp:1.0.2"
//android use:
implementation "com.github.hss01248.simple-file-downloder:download-okhttp-android:1.0.2"
```



## download-okhttp

```java
//global init:  must init in java project;
OkhttpDownloadUtil.setGlobalSaveDir(String dir)
  
OkhttpDownloadUtil.setThreadCount(int threadCount) //default 30
OkhttpDownloadUtil.setLogEnable(AppUtils.isAppDebug());




//start
DownloadConfig.newBuilder()
           .url(url)
           .requestSync(false)
  				....
  				.start(IDownloadCallback callback);
//with start() called

```

the download can config as this:

![image-20240829173100173](https://cdn.jsdelivr.net/gh/shuiniuhss/myimages@main/imagemac3/image-20240829173100173.png)

### New Features (v1.1.0)

#### Smart Temp File & Resume
- Temp file format: `filename.ext.{ContentLength}.tmp`
- Auto-detect server file changes and clean up stale temp files

#### History Version Management
```java
DownloadConfig.newBuilder()
    .url(url)
    .saveDir("/downloads")
    .keepHistoryVersions(true)   // Keep old versions when server file changes
    .maxHistoryVersions(3)       // Max versions to keep (default: 5)
    .start(callback);
```
- Old files renamed to `filename(-1).ext`, `filename(-2).ext`...
- Auto cleanup when exceeding max versions





If the filePath not set ,then will use saveDir and parse name from url, to generate the final filePath to save. 

If the save dir has files more then 1000, it will create a dir next to the save dir , named as {save dir name}-n, n is the number of count that named before. 

If file name >150 characters, will split to 150



//todo:

add interceptors  , custom okhttpclient

### callback:

```java
public interface IDownloadCallback{

    void onSuccess(String url, String path);

    void onFailed(String url, String path, String code, String msg, Throwable e);

    default  void onProgress(String url, String path, long total, long alreadyReceived,long speed){}
  
}
```

### pause or stop the downloding:

```java
OkhttpDownloadUtil.pauseOrStop(String url)
```



## download-okhttp-android

```java
public static DownloadConfig.Builder prepareDownload(String url){
        return DownloadConfig.newBuilder()
                .url(url)
                .requestSync(false);
    }

//use the callback wrapper:
  .start(new DownloadCallbackOnMainThreadWrapper(new IDownloadCallback() {}));


//自动全局配置:
      File dir = context.getExternalFilesDir("downloader");
        if(dir == null){
            dir = new File(context.getFilesDir(),"downloader");
        }
        OkhttpDownloadUtil.setGlobalSaveDir(dir.getAbsolutePath());
        OkhttpDownloadUtil.setLogEnable(AppUtils.isAppDebug());
```

The globalSaveDir is set to context.getExternalFilesDir("downloader") by default;



//todo:

Notification support 

download list ui and manager support

# the old FileDownloader lib:

Fork from https://github.com/lingochamp/FileDownloader 1.7.7 

and has adapte android 14 service type.



```groovy
api 'com.github.hss01248.simple-file-downloder:filedownloder-old:1.0.2'
```




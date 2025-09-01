/*
 * Copyright (c) 2015 LingoChamp Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hss01248.download_okhttp;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * @Despciption todo
 * @Author hss
 * @Date 8/28/24 11:01 AM
 * @Version 1.0
 */
public class DownloadConfig {

    private String url;
    private String filePath;

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    private String fileName;
    private boolean forceRedownload;
    private boolean notAcceptRanges;

    private Map<String,String> headers;

    private Long fileSizeAlreadyKnown;

    private IDownloadCallback callback;

    private int progressCallbackIntervalMills;

   private  int retryTimes;

   private String tag;

    public String getSaveDir() {
        return saveDir;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }



    public void setSaveDir(String saveDir) {
        this.saveDir = saveDir;
    }

    private String saveDir;

    public boolean isRequestSync() {
        return requestSync;
    }

    private  boolean requestSync;
   private Map<String,Object> tags;

    public String getUrl() {
        return url;
    }

    public String getFilePath() {
        if(filePath ==null && saveDir !=null && fileName !=null){
            filePath = saveDir + File.separator+fileName;
        }
        return filePath;
    }

    public boolean isForceRedownload() {
        return forceRedownload;
    }

    public boolean isNotAcceptRanges() {
        return notAcceptRanges;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public Long getFileSizeAlreadyKnown() {
        return fileSizeAlreadyKnown;
    }

    public void setCallback(IDownloadCallback callback) {
        this.callback = callback;
    }

    public IDownloadCallback getCallback() {
        return callback;
    }

    public int getProgressCallbackIntervalMills() {
        return progressCallbackIntervalMills;
    }

    public int getRetryTimes() {
        return retryTimes;
    }

    public String getTag() {
        return tag;
    }

    public Map<String, Object> getTags() {
        return tags;
    }

    private DownloadConfig(Builder builder) {
        url = builder.url;
        filePath = builder.filePath;
        forceRedownload = builder.forceRedownload;
        notAcceptRanges = builder.notAcceptRanges;
        headers = builder.headers;
        fileSizeAlreadyKnown = builder.fileSizeAlreadyKnown;
        callback = builder.callback;
        progressCallbackIntervalMills = builder.progressCallbackIntervalMills;
        retryTimes = builder.retryTimes;
        tag = builder.tag;
        tags = builder.tags;
        requestSync = builder.requestSync;
        saveDir = builder.saveDir;
        fileName = builder.fileName;
    }
    public static Builder newBuilder() {
        return new Builder();
    }
    public static Builder newBuilder(DownloadConfig copy) {
        Builder builder = new Builder();
        builder.url = copy.getUrl();
        builder.filePath = copy.getFilePath();
        builder.forceRedownload = copy.isForceRedownload();
        builder.notAcceptRanges = copy.isNotAcceptRanges();
        builder.headers = copy.getHeaders();
        builder.fileSizeAlreadyKnown = copy.getFileSizeAlreadyKnown();
        builder.callback = copy.getCallback();
        builder.progressCallbackIntervalMills = copy.getProgressCallbackIntervalMills();
        builder.retryTimes = copy.getRetryTimes();
        builder.tag = copy.getTag();
        builder.tags = copy.getTags();
        builder.requestSync = copy.requestSync;
        builder.saveDir = copy.getSaveDir();
        builder.fileName = copy.fileName;
        return builder;
    }

    public static final class Builder {
        private String url;
        private String filePath;
        private boolean forceRedownload;
        private boolean notAcceptRanges;
        private Map<String, String> headers;
        private Long fileSizeAlreadyKnown;
        private IDownloadCallback callback;
        private int progressCallbackIntervalMills = 300;//ms

        private int retryTimes;
        private String tag;
        private Map<String, Object> tags;

        private  boolean requestSync = true;
        private String saveDir;

        public Builder fileName(String fileName) {
            this.fileName = fileName;
            return this;
        }

        private String fileName;

        private Builder() {
        }


        public Builder requestSync(boolean requestSync) {
            this.requestSync = requestSync;
            return this;
        }
        public Builder url(String val) {
            url = val;
            return this;
        }

        public Builder saveDir(String saveDir) {
            this.saveDir = saveDir;
            return this;
        }

        public Builder filePath(String val) {
            filePath = val;
            return this;
        }

        public Builder forceRedownload(boolean val) {
            forceRedownload = val;
            return this;
        }

        public Builder notAcceptRanges(boolean val) {
            notAcceptRanges = val;
            return this;
        }

        public Builder headers(Map<String, String> val) {
            headers = val;
            return this;
        }



        public Builder fileSizeAlreadyKnown(Long val) {
            fileSizeAlreadyKnown = val;
            return this;
        }



        public Builder progressCallbackIntervalMills(int val) {
            progressCallbackIntervalMills = val;
            return this;
        }

        public Builder retryTimes(int val) {
            retryTimes = val;
            return this;
        }

        public Builder tag(String val) {
            tag = val;
            return this;
        }

        public Builder tags(Map<String, Object> val) {
            tags = val;
            return this;
        }

        public Builder addTag(String key,Object val) {
            if(tags ==null){
                tags = new HashMap<>();
            }
            tags.put(key, val);
            return this;
        }

        public Builder addHeaders(String key,String val) {
            if(headers ==null){
                headers = new HashMap<>();
            }
            headers.put(key, val);
            return this;
        }

        public DownloadConfig build() {
            return new DownloadConfig(this);
        }

        public void start(IDownloadCallback val) {
            callback = val;
            DownloadConfig build = build();
            callback.onCodeStart(url, build.getFilePath());
            // sync async
            if(build.isRequestSync()){
                OkhttpDownloadUtil.downloadSync(build);
            }else {
                OkhttpDownloadUtil.downloadAsync(build);
            }
        }
    }
}

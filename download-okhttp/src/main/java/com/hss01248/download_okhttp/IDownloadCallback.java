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

/**
 * @Despciption todo
 * @Author hss
 * @Date 8/27/24 3:32 PM
 * @Version 1.0
 */
public interface IDownloadCallback{

    void onSuccess(String url, String path);

    void onFailed(String url, String path, String code, String msg, Throwable e);

    default  void onProgress(String url, String path, long total, long alreadyReceived,long speed){}

    default  void onCancel(String url,String path){}

    /**
     * 线程执行开始
     * @param url
     * @param path
     */
    default void onStartReal(String url, String path){}

    /**
     * 代码启动
     * @param url
     * @param path
     */
    default void onCodeStart(String url, String path){}

}


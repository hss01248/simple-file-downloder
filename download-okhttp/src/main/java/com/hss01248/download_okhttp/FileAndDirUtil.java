package com.hss01248.download_okhttp;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @Despciption //默认文件名和文件路径功能:
 *              //文件名太长问题-导致创建文件失败
 *              //文件名不合法-导致创建文件失败
 *
 *              外面指定路径,则一定下载到那个路径,没有指定,则会通过内部算法选择合适的路径和文件名
 *              路径选择: 单个文件夹下文件数<1000, 超过则建立同级文件夹-1/2/3...
 *              文件名: 过滤非法字符->截断解决文件名太长问题->文件后缀名判定,如果不等于实际的,就修改
 * @Author hss
 * @Date 8/29/24 3:09 PM
 * @Version 1.0
 */
public class FileAndDirUtil {

    static String dealFilePath(DownloadConfig config) throws Exception {
        // 外面指定路径,则一定下载到那个路径,除非这个路径是一个文件夹同名->下载失败
        if (config.getFilePath() != null && !"".equals(config.getFilePath())) {
            return config.getFilePath();
        }

        String saveDir = dealSaveDir(config);
        config.setSaveDir(saveDir);

        String name = dealFileName(config.getUrl(), config.getFileName());
        config.setFileName(name);

        // 整体文件路径太长的问题:
        // 在 Windows API (（以下段落) 中介绍的一些例外情况）中，路径的最大长度为 MAX_PATH，定义为 260 个字符。
        // 本地路径按以下顺序构建：驱动器号、冒号、反斜杠、用反斜杠分隔的名称组件以及终止 null 字符。
        // https://www.php.cn/faq/598218.html
        // 使用Java 11的新特性 Path类的toRealPath
        File file = new File(saveDir, name);
        if (file.exists()) {
            config.setFilePath(file.getAbsolutePath());
        } else {
            try {
                file.createNewFile();
                config.setFilePath(file.getAbsolutePath());
            } catch (Throwable throwable) {
                OkhttpDownloadUtil.w(throwable.getClass().getSimpleName() + ", " + throwable.getMessage() + " ,"
                        + file.getAbsolutePath());
                throw throwable;
            }
        }

        return file.getAbsolutePath();
    }

    private static String dealSaveDir(DownloadConfig config) throws Exception {
        String saveDir = config.getSaveDir();
        if (saveDir == null || "".equals(saveDir)) {
            saveDir = OkhttpDownloadUtil.globalSaveDir;
        }
        if (saveDir == null || "".equals(saveDir)) {
            throw new IOException("OkhttpDownloadUtil.globalSaveDir is not set!!!!");
        }
        File dir = new File(saveDir);
        if (!dir.exists()) {
            boolean mkdirs = dir.mkdirs();
            if (!mkdirs) {
                // System.out.println("warn: mkdirs failed : "+saveDir);
                throw new IOException("mkdirs failed: " + saveDir);
            }
        } else {
            if (dir.isFile()) {
                System.out.println("warn: saveDir is a File, will try another name: " + saveDir);
                dir = new File(dir.getParentFile(), dir.getName() + "-dir");
                boolean mkdirs = dir.mkdirs();
                if (!mkdirs) {
                    throw new IOException("mkdirs failed: " + dir.getAbsolutePath());
                }
            } else {
                // 已经存在,且为文件夹,那么看文件个数,如果>1000,用子文件夹
                String[] list = dir.list();
                int i = 0;
                while (list != null && list.length > 1000) {
                    i++;
                    dir = new File(dir.getParentFile(), dir.getName() + "-" + i);
                    if (!dir.exists()) {
                        list = null;
                    } else {
                        if (!dir.isDirectory()) {
                            // 一个破文件跟我重名? 直接干掉你
                            dir.delete();
                            list = null;
                        } else {
                            list = dir.list();
                        }
                    }
                }
            }
        }
        return dir.getAbsolutePath();
    }

    static String dealFileName(String url, String fileName) {
        if (fileName != null && !fileName.equals("")) {
            return fileName;
        }
        try {
            url = URLDecoder.decode(url, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            System.out.println(url + ", UnsupportedEncodingException: " + e.getMessage());
        }
        if (url.contains("?")) {
            url = url.substring(0, url.indexOf("?"));
        }
        if (url.contains("/")) {
            if (url.endsWith("/")) {
                url = "unknown.bin";
            } else {
                url = url.substring(url.lastIndexOf("/") + 1);
            }
        }
        url = replaceSpecialCharacters(url);

        // 文件名字符小于255:
        String suffix = "";
        String name = url;
        String finalName = url;
        if (url.contains(".")) {
            suffix = url.substring(url.lastIndexOf("."));
            name = url.substring(0, url.lastIndexOf("."));
        }
        // linux系统下ext3文件系统内给文件/目录命名，最长只能支持127个中文字符，英文则可以支持255个字符--
        // ext3文件系统一级子目录的个数为31998(个)。
        // Android操作系统依托于Linux，所以主要的文件系统也是从Linux中发展而来，包括exFAT、ext3、ext4等，目前大多数手机仍使用Ext4
        // 在计算的字符串长度的时候，若有汉字，直接用String.length()方法是没法计算出准确的长度

        // 如果为英文字符，最多为255个，包括短横线连接符 - 。
        // 如果为纯汉字，最多为 85 个汉字，是 255 的 1/3，说明每个汉字占 3 个字节。
        // 即，文件名称长度不可超过 255 个字节。
        if (name.length() > 150) {
            name = name.substring(0, 150);
            finalName = name + suffix;
        }
        return finalName;
    }

    static Pattern pattern = Pattern.compile("[\\s\\\\/:\\*\\?\\\"<>\\|]");

    static String replaceSpecialCharacters(String dirPath) {

        /*
         * windows下文件名中不能含有：\ / : * ? " < > | 英文的这些字符 ，这里使用"."、"'"进行替换。
         * \/:?| 用.替换
         * "<> 用'替换
         * 如果字符串中含有 Unicode 字符，也会密码失败
         */
        // dirPath = dirPath.replaceAll("[/\\\\:*?|]", "");
        // dirPath = dirPath.replaceAll("[\"<>]", "");

        Matcher matcher = pattern.matcher(dirPath);
        dirPath = matcher.replaceAll("");

        dirPath = dirPath.replaceAll(" ", "-");
        dirPath = dirPath.replaceAll("[^\\u0009\\u000a\\u000d\\u0020-\\uD7FF\\uE000-\\uFFFD]", "");
        dirPath = dirPath.replaceAll(
                "[\\uD83D\\uFFFD\\uFE0F\\u203C\\u3010\\u3011\\u300A\\u166D\\u200C\\u202A\\u202C\\u2049\\u20E3\\u300B\\u300C\\u3030\\u065F\\u0099\\u0F3A\\u0F3B\\uF610\\uFFFC]",
                "");

        return dirPath;
    }

    // ==================== 临时文件和历史版本管理方法 ====================

    /**
     * 生成带 ContentLength 和 ETag 的临时文件路径
     * 格式: 文件名.后缀.{ContentLength}_{ETag缩写}.tmp
     * 例如: video.mp4.12345678_abc123.tmp
     *
     * @param finalFilePath 最终文件路径
     * @param contentLength 内容长度
     * @param etag          ETag 缩写（前 8 位）
     * @return 临时文件路径
     */
    static String getTempFilePath(String finalFilePath, long contentLength, String etag) {
        if (etag != null && !etag.isEmpty()) {
            return finalFilePath + "." + contentLength + "_" + etag + ".tmp";
        }
        return finalFilePath + "." + contentLength + ".tmp";
    }

    @Deprecated
    static String getTempFilePath(String finalFilePath, long contentLength) {
        return getTempFilePath(finalFilePath, contentLength, null);
    }

    /**
     * 从临时文件名中提取 ContentLength
     * 
     * @param tempFileName 临时文件名，如 video.mp4.12345678.tmp
     * @return ContentLength，如果无法解析则返回 -1
     */
    static long extractContentLengthFromTempFile(String tempFileName) {
        if (tempFileName == null || !tempFileName.endsWith(".tmp")) {
            return -1;
        }
        // 去掉 .tmp 后缀
        String withoutTmp = tempFileName.substring(0, tempFileName.length() - 4);
        int lastDot = withoutTmp.lastIndexOf('.');
        if (lastDot < 0) {
            return -1;
        }
        String suffixAndEtag = withoutTmp.substring(lastDot + 1);
        // 处理 12345678_etag 格式
        int underscoreIdx = suffixAndEtag.indexOf('_');
        String lengthStr = underscoreIdx > 0 ? suffixAndEtag.substring(0, underscoreIdx) : suffixAndEtag;
        try {
            return Long.parseLong(lengthStr);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * 从临时文件名中提取 ETag
     *
     * @param tempFileName 临时文件名，如 video.mp4.12345678_abc123.tmp
     * @return ETag 缩写，如果没有则返回空字符串
     */
    static String extractEtagFromTempFile(String tempFileName) {
        if (tempFileName == null || !tempFileName.endsWith(".tmp")) {
            return "";
        }
        String withoutTmp = tempFileName.substring(0, tempFileName.length() - 4);
        int lastDot = withoutTmp.lastIndexOf('.');
        if (lastDot < 0) {
            return "";
        }
        String suffixAndEtag = withoutTmp.substring(lastDot + 1);
        int underscoreIdx = suffixAndEtag.indexOf('_');
        if (underscoreIdx > 0) {
            return suffixAndEtag.substring(underscoreIdx + 1);
        }
        return "";
    }

    /**
     * 查找与最终文件项匹配的临时文件
     *
     * @param finalFile             最终文件
     * @param expectedContentLength 期望的 ContentLength
     * @param expectedEtag          期望的 ETag（前 8 位）
     * @return 匹配的临时文件，如果没有则返回 null
     */
    static File findMatchingTempFile(File finalFile, Long expectedContentLength, String expectedEtag) {
        File parentDir = finalFile.getParentFile();
        if (parentDir == null || !parentDir.exists()) {
            return null;
        }
        String baseName = finalFile.getName();
        File[] files = parentDir.listFiles((dir, name) -> name.startsWith(baseName + ".") && name.endsWith(".tmp"));

        if (files == null || files.length == 0) {
            return null;
        }

        // 如果知道期望的参数，优先返回匹配的
        if (expectedContentLength != null && expectedContentLength > 0) {
            // 构造带 ETag 的预期名称
            String nameWithEtag = null;
            if (expectedEtag != null && !expectedEtag.isEmpty()) {
                nameWithEtag = baseName + "." + expectedContentLength + "_" + expectedEtag + ".tmp";
            }
            // 构造不带 ETag 的预期名称
            String nameWithoutEtag = baseName + "." + expectedContentLength + ".tmp";

            for (File f : files) {
                String name = f.getName();
                if (nameWithEtag != null && name.equals(nameWithEtag)) {
                    return f;
                }
                if (name.equals(nameWithoutEtag)) {
                    // 如果我们期望 ETag 但文件没有 ETag，或者一致，则返回
                    // 如果期望 ETag 但文件名里有其他的 ETag（在循环中会被 nameWithEtag 捕获），则此处不匹配
                    if (expectedEtag == null || expectedEtag.isEmpty()) {
                        return f;
                    }
                }
            }
        }

        // 默认不随意返回，必须满足 Content-Length 匹配（如果没有提供 expectedContentLength 则返回第一个符合后缀的）
        if (expectedContentLength == null) {
            return files[0];
        }

        return null;
    }

    @Deprecated
    static File findMatchingTempFile(File finalFile, Long expectedContentLength) {
        return findMatchingTempFile(finalFile, expectedContentLength, null);
    }

    /**
     * 清理所有与最终文件相关的临时文件（除了指定的一个）
     * 
     * @param finalFile    最终文件
     * @param keepTempFile 要保留的临时文件，可以为 null
     */
    static void cleanupOtherTempFiles(File finalFile, File keepTempFile) {
        File parentDir = finalFile.getParentFile();
        if (parentDir == null || !parentDir.exists()) {
            return;
        }
        String baseName = finalFile.getName();
        File[] files = parentDir.listFiles((dir, name) -> name.startsWith(baseName + ".") && name.endsWith(".tmp"));

        if (files == null) {
            return;
        }

        for (File f : files) {
            if (keepTempFile == null || !f.equals(keepTempFile)) {
                f.delete();
            }
        }
    }

    /**
     * 将旧文件重命名为历史版本
     * 命名规则: 原文件名(-1).后缀, (-2).后缀, ...
     * 
     * @param file               要重命名的文件
     * @param maxHistoryVersions 最大保留版本数
     * @return 是否重命名成功
     */
    static boolean renameToHistoryVersion(File file, int maxHistoryVersions) {
        if (!file.exists()) {
            return true;
        }

        String fileName = file.getName();
        String parentPath = file.getParent();
        String baseName;
        String suffix;

        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0) {
            baseName = fileName.substring(0, dotIndex);
            suffix = fileName.substring(dotIndex);
        } else {
            baseName = fileName;
            suffix = "";
        }

        // 找到可用的版本号
        int version = 1;
        File targetFile;
        while (true) {
            String newName = baseName + "(-" + version + ")" + suffix;
            targetFile = new File(parentPath, newName);
            if (!targetFile.exists()) {
                break;
            }
            version++;
            if (version > 10000) {
                // 防止死循环
                OkhttpDownloadUtil.w("renameToHistoryVersion: too many versions", file.getAbsolutePath());
                return false;
            }
        }

        boolean success = file.renameTo(targetFile);
        if (success) {
            OkhttpDownloadUtil.d("renamed old file to history version", file.getName(), targetFile.getName());
            // 清理超出限制的旧版本
            cleanupOldHistoryVersions(parentPath, baseName, suffix, maxHistoryVersions);
        }
        return success;
    }

    /**
     * 清理超出限制的历史版本
     */
    static void cleanupOldHistoryVersions(String parentPath, String baseName, String suffix, int maxHistoryVersions) {
        if (maxHistoryVersions <= 0) {
            return;
        }

        File parentDir = new File(parentPath);
        if (!parentDir.exists()) {
            return;
        }

        // 匹配 baseName(-N).suffix 格式的文件
        final Pattern versionPattern = Pattern.compile(
                Pattern.quote(baseName) + "\\(-([0-9]+)\\)" + Pattern.quote(suffix));

        File[] historyFiles = parentDir.listFiles((dir, name) -> versionPattern.matcher(name).matches());

        if (historyFiles == null || historyFiles.length <= maxHistoryVersions) {
            return;
        }

        // 按版本号排序（降序），保留最新的 maxHistoryVersions 个
        java.util.Arrays.sort(historyFiles, (f1, f2) -> {
            int v1 = extractVersionNumber(f1.getName(), versionPattern);
            int v2 = extractVersionNumber(f2.getName(), versionPattern);
            return v2 - v1; // 降序
        });

        // 删除超出限制的旧版本
        for (int i = maxHistoryVersions; i < historyFiles.length; i++) {
            if (historyFiles[i].delete()) {
                OkhttpDownloadUtil.d("deleted old history version", historyFiles[i].getName());
            }
        }
    }

    /**
     * 从文件名中提取版本号
     */
    private static int extractVersionNumber(String fileName, Pattern versionPattern) {
        Matcher matcher = versionPattern.matcher(fileName);
        if (matcher.matches()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

}

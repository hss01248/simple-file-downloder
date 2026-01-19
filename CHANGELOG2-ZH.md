# download-okhttp 模块更新日志

> [English Changelog](./CHANGELOG2.md)

## Version 1.1.0

_2026-01-19_

### 新特性

#### 智能临时文件命名（Content-Length + ETag 指纹）
- 临时文件命名格式变更为 `filename.ext.{ContentLength}_{ETagFingerprint}.tmp`
- 示例: `video.mp4.12345678_abc12345.tmp`
- 利用 ETag 识别内容变更，即使文件大小完全一致也能准确识别。
- 如果服务端不返回 ETag，则自动回退到仅依赖 Content-Length 的命名格式。

#### 服务端文件变更检测（多维度）
- 自动检测远程文件是否已更新（通过对比 Content-Length 和 ETag 指纹）。
- 检测到变更后，自动清理大小不匹配或指纹不符的旧临时文件。
- 极大提高了断点续传的数据一致性和准确性。

#### 历史版本管理
- 新增配置: `keepHistoryVersions(boolean)` - 是否保留旧文件版本（默认: `false`）
- 新增配置: `maxHistoryVersions(int)` - 最大保留历史版本数（默认: `5`）
- 开启后，旧文件会被重命名为 `filename(-1).ext`, `filename(-2).ext` 等
- 自动清理超出限制的历史版本

### API 增强

#### DownloadConfig.Builder 新增方法

```java
DownloadConfig.newBuilder()
    .url("http://example.com/file.mp4")
    .saveDir("/downloads")
    .keepHistoryVersions(true)   // 开启历史版本保留
    .maxHistoryVersions(3)       // 最多保留3个版本
    .start(callback);
```

### 内部改进

- **修复进度计算 Bug**: 解决了断点续传时调试日志中已下载字节数被重复计算的问题（曾导致出现进度 >100% 的现象）。
- **FileAndDirUtil - 新增辅助方法**
- `getTempFilePath(String, long)` - 生成带 Content-Length 的临时文件路径
- `extractContentLengthFromTempFile(String)` - 从临时文件名提取 Content-Length
- `findMatchingTempFile(File, Long)` - 查找用于续传的匹配临时文件
- `cleanupOtherTempFiles(File, File)` - 清理过时的临时文件
- `renameToHistoryVersion(File, int)` - 将文件重命名为历史版本
- `cleanupOldHistoryVersions(...)` - 清理旧的历史版本

### 迁移指南

无破坏性变更，所有新特性均为可选：
- `keepHistoryVersions` 默认为 `false`（与之前行为一致）
- 临时文件格式变更向后兼容（旧的 `.tmp` 文件会被自动清理）

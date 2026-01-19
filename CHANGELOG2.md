# Changelog for download-okhttp module

> [中文更新日志](./CHANGELOG2-ZH.md)

## Version 1.1.0

_2026-01-19_

### New Features

#### Smart Temp File Naming with Content-Length & ETag 指纹
- Temp file naming format changed to `filename.ext.{ContentLength}_{ETagFingerprint}.tmp`
- Example: `video.mp4.12345678_abc12345.tmp`
- Uses ETag to detect file content changes even if the file size remains identical.
- Falls back to `filename.ext.{ContentLength}.tmp` if no ETag is provided by the server.

#### Server File Change Detection (Multi-factor)
- Detects changes via `Content-Length` and `ETag` fingerprint.
- Automatically cleans up stale temp files when a change is detected.
- Enhances data integrity for resumed downloads.

#### History Version Management
- New configuration: `keepHistoryVersions(boolean)` - Enable/disable keeping old file versions (default: `false`)
- New configuration: `maxHistoryVersions(int)` - Maximum number of history versions to keep (default: `5`)
- When enabled, old files are renamed to `filename(-1).ext`, `filename(-2).ext`, etc.
- Automatically cleans up versions exceeding the limit

### Enhanced APIs

#### DownloadConfig.Builder

```java
DownloadConfig.newBuilder()
    .url("http://example.com/file.mp4")
    .saveDir("/downloads")
    .keepHistoryVersions(true)   // Enable history version keeping
    .maxHistoryVersions(3)       // Keep max 3 versions
    .start(callback);
```

### Internal Improvements

- **Fixed progress calculation bug**: Resolved an issue where initial downloaded bytes were double-counted in debug logs during resumed downloads (showing >100% progress).
- **FileAndDirUtil - New Helper Methods**
- `getTempFilePath(String, long)` - Generate temp file path with Content-Length
- `extractContentLengthFromTempFile(String)` - Extract Content-Length from temp file name
- `findMatchingTempFile(File, Long)` - Find matching temp file for resumption
- `cleanupOtherTempFiles(File, File)` - Clean up obsolete temp files
- `renameToHistoryVersion(File, int)` - Rename file to history version
- `cleanupOldHistoryVersions(...)` - Clean up old history versions

### Migration Guide

No breaking changes. All new features are opt-in:
- `keepHistoryVersions` defaults to `false` (same behavior as before)
- Temp file format change is backward compatible (old `.tmp` files will be cleaned up)

# Changelog for download-okhttp module

> [中文更新日志](./CHANGELOG2-ZH.md)

## Version 1.1.0

_2026-01-19_

### New Features

#### Smart Temp File Naming with Content-Length
- Temp file naming format changed from `filename.ext.tmp` to `filename.ext.{ContentLength}.tmp`
- Example: `video.mp4.12345678.tmp`
- This enables intelligent detection of server-side file changes

#### Server File Change Detection
- Automatically detects when the remote file has been updated (by comparing Content-Length)
- When detected, old temp files with mismatched sizes are automatically cleaned up
- Prevents corrupted downloads from resuming with stale data

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

#### FileAndDirUtil - New Helper Methods
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

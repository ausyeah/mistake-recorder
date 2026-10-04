package com.mistakebook.util

import java.io.File

/**
 * 安全校验：检查 [candidate] 文件路径在规范化（canonical）后是否严格位于 [directory] 目录内部。
 * 防止 Zip Slip 等路径遍历漏洞。
 */
fun isWithinDirectory(directory: File, candidate: File): Boolean {
    val directoryPath = directory.canonicalFile.path
    val boundary = if (directoryPath.endsWith(File.separatorChar)) {
        directoryPath
    } else {
        directoryPath + File.separatorChar
    }
    return candidate.canonicalFile.path.startsWith(
        boundary,
        ignoreCase = File.separatorChar == '\\'
    )
}

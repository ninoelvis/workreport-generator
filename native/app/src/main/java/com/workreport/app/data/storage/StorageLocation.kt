package com.workreport.app.data.storage

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 用户自选的「周报存放目录」。
 *
 * 用的是 Android 官方的 **SAF（Storage Access Framework）**：
 *  - 用户在系统文件选择器里挑一个目录，我们拿到一个 `tree://` URI；
 *  - 持久化授权后，之后不用再问一次就能读写；
 *  - **不需要任何存储权限**（这是 API 26+ 上写用户指定目录的唯一正规做法）。
 *
 * ⚠️ 红线：
 * 这里只负责「文件落到哪个目录」，**绝不碰文件名和内容**。
 * 写出去的文件名永远是 [com.workreport.app.export.ReportNaming] 算出来的那个
 * `zipFileName`，一个字符都不多；ZIP 内部结构和 Excel 内容也完全不动。
 */
class StorageLocation(private val context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    private val _treeUri = MutableStateFlow(prefs.getString(KEY_TREE_URI, null)?.let { runCatching { Uri.parse(it) }.getOrNull() })
    val treeUri: StateFlow<Uri?> = _treeUri.asStateFlow()

    /** 用户是否已经选过目录（选过也可能后来改成「默认下载目录」） */
    private val _customized = MutableStateFlow(prefs.getBoolean(KEY_CUSTOMIZED, false))
    val customized: StateFlow<Boolean> = _customized.asStateFlow()

    /** 目录的显示名（选目录时查一次并缓存，避免在界面上同步查 ContentResolver） */
    /** 外部存储主卷的 document id 前缀 */
    private val PRIMARY_PREFIX = "primary:"

    private val _displayName = MutableStateFlow(prefs.getString(KEY_DIR_NAME, null).orEmpty())
    val displayNameFlow: StateFlow<String> = _displayName.asStateFlow()

    /** 当前目录的可读路径，例如 `/storage/emulated/0/Download/周报` */
    private val _path = MutableStateFlow(prefs.getString(KEY_DIR_PATH, null).orEmpty())
    val pathFlow: StateFlow<String> = _path.asStateFlow()

    /**
     * 保存用户选中的目录，并**持久化读写授权**。
     *
     * 授权拿不到就返回 false（下次可以再选一次）。
     */
    fun select(uri: Uri): Boolean {
        val granted = try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            true
        } catch (e: Exception) {
            false
        }
        val name = queryDisplayName(uri)
        val path = queryPath(uri)
        prefs.edit()
            .putString(KEY_TREE_URI, uri.toString())
            .putString(KEY_DIR_NAME, name)
            .putString(KEY_DIR_PATH, path)
            .putBoolean(KEY_CUSTOMIZED, true)
            .apply()
        _treeUri.value = uri
        _customized.value = true
        _displayName.value = name
        _path.value = path
        return granted
    }

    /** 恢复成默认的「下载」目录 */
    fun reset() {
        prefs.edit()
            .remove(KEY_TREE_URI)
            .remove(KEY_DIR_NAME)
            .remove(KEY_DIR_PATH)
            .putBoolean(KEY_CUSTOMIZED, false)
            .apply()
        _treeUri.value = null
        _customized.value = false
        _displayName.value = ""
        _path.value = ""
    }

    /** 当前还有效的授权吗 */
    fun hasPermission(): Boolean {
        val uri = _treeUri.value ?: return false
        return try {
            context.contentResolver.persistedUriPermissions.any {
                it.uri == uri && it.isWritePermission
            }
        } catch (e: Exception) {
            false
        }
    }

    /** 目录里已有哪些文件（用于展示与统计） */
    fun listChildren(): List<ExternalFile> {
        val uri = _treeUri.value ?: return emptyList()
        return queryChildren(uri)
    }

    /** 目录里已有哪些压缩包 */
    fun listZips(): List<ExternalFile> =
        listChildren().filter { it.displayName.endsWith(".zip", ignoreCase = true) }

    /**
     * 把一个 ZIP 写进用户指定的目录。
     *
     * ⚠️ 文件名**原样使用**，不做任何改名。
     * 如果目录里已经有同名文件，就覆盖它 —— 加「(1)」后缀会破坏红线
     * （下载目录 / 邮件附件 / 系统分享必须是同一个名字）。
     *
     * @return 写入后的文档 URI；失败返回 null
     */
    fun writeZip(displayName: String, bytes: ByteArray): Uri? {
        val treeUri = _treeUri.value ?: return null
        return try {
            val resolver = context.contentResolver
            // 同名就复用那个文件（覆盖），文件名保证一个字都不变
            val existing = findChild(treeUri, displayName)
            val target = if (existing != null) {
                existing
            } else {
                val created = createChild(treeUri, displayName) ?: return null
                // ⚠️ 有些系统文件提供器在新建时会自动加「(1)」后缀，
                //    那会破坏红线（下载目录 / 邮件附件 / 系统分享必须是同一个名字），
                //    所以这里必须把名字改回去。
                if (!ensureName(resolver, created, displayName)) {
                    try { DocumentsContract.deleteDocument(resolver, created) } catch (e: Exception) { /* 忽略 */ }
                    return null
                }
                created
            }
            resolver.openOutputStream(target, "wt")?.use { out ->
                out.write(bytes)
                out.flush()
            } ?: return null
            target
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 确认这个文档的名字就是 [displayName]，不是就改回去。
     *
     * @return 名字是否正确
     */
    private fun ensureName(resolver: ContentResolver, uri: Uri, displayName: String): Boolean {
        val actual = try {
            resolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
        } catch (e: Exception) {
            null
        }
        if (actual == displayName) return true
        if (actual == null) return true
        // 名字被系统改过 → 改回来
        return try {
            DocumentsContract.renameDocument(resolver, uri, displayName) != null
        } catch (e: Exception) {
            false
        }
    }

    /** 读取一个外部文件的内容（用于预览外部压缩包） */
    fun readBytes(uri: Uri): ByteArray? = try {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
    } catch (e: Exception) {
        null
    }

    /** 删除一个外部文件 */
    fun delete(uri: Uri): Boolean = try {
        DocumentsContract.deleteDocument(context.contentResolver, uri)
    } catch (e: Exception) {
        false
    }

    /** 给用户看的目录名；没选过目录时返回「系统「下载」目录」 */
    fun displayName(): String =
        if (_treeUri.value == null) "系统「下载」目录"
        else _displayName.value.ifEmpty { "自定义目录" }

    /**
     * 查这个目录**本身**的名字。
     *
     * ⚠️ 千万别去查「子文档列表的第一条」—— 那样拿到的是**目录里第一个文件的文件名**，
     *    不是目录名。（v1.5.0 就是踩了这个坑，用户看到「存放位置」显示成了某个文件名。）
     *
     * 正确做法：用 `buildDocumentUriUsingTree` 把 tree URI 转成**目录自身**的 document URI 再查。
     */
    private fun queryDisplayName(uri: Uri): String {
        return try {
            val docId = DocumentsContract.getTreeDocumentId(uri)
            val docUri = DocumentsContract.buildDocumentUriUsingTree(uri, docId)
            val projection = arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            context.contentResolver.query(docUri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } catch (e: Exception) {
            null
        } ?: uri.lastPathSegment ?: "自定义目录"
    }

    /**
     * 这个目录的**可读路径**，尽量还原成 `/storage/emulated/0/Download/xxx` 这样的样子。
     *
     * SAF 不直接给真实路径，但外部存储 provider 的 tree document id 形如
     * `primary:Download/周报`，可以还原成 `/storage/emulated/0/Download/周报`。
     * 还原不出来就退回显示 document id（至少比空白强）。
     */
    fun queryPath(uri: Uri): String {
        return try {
            val docId = DocumentsContract.getTreeDocumentId(uri)
            when {
                docId.startsWith(PRIMARY_PREFIX) -> {
                    val rest = docId.removePrefix(PRIMARY_PREFIX)
                    "/storage/emulated/0" + if (rest.startsWith("/")) rest else "/$rest"
                }
                else -> docId.replace(':', '/')
            }
        } catch (e: Exception) {
            uri.lastPathSegment ?: ""
        }
    }

    // ---------------- 内部 ----------------

    private fun findChild(treeUri: Uri, displayName: String): Uri? {
        val docId = DocumentsContract.getTreeDocumentId(treeUri)
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        )
        return try {
            context.contentResolver.query(children, projection, null, null, null)?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    if (cursor.getString(nameIdx) == displayName) {
                        return DocumentsContract.buildDocumentUriUsingTree(treeUri, cursor.getString(idIdx))
                    }
                }
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun createChild(treeUri: Uri, displayName: String): Uri? {
        val docId = DocumentsContract.getTreeDocumentId(treeUri)
        val parent = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
        return try {
            DocumentsContract.createDocument(
                context.contentResolver,
                parent,
                MIME_ZIP,
                displayName,
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun queryChildren(treeUri: Uri): List<ExternalFile> {
        val docId = try {
            DocumentsContract.getTreeDocumentId(treeUri)
        } catch (e: Exception) {
            return emptyList()
        }
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
        val out = mutableListOf<ExternalFile>()
        try {
            context.contentResolver.query(children, projection, null, null, null)?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
                val timeIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                val mimeIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameIdx) ?: continue
                    if (mimeIdx >= 0 && cursor.getString(mimeIdx) == DocumentsContract.Document.MIME_TYPE_DIR) continue
                    out += ExternalFile(
                        documentId = cursor.getString(idIdx),
                        uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, cursor.getString(idIdx)),
                        displayName = name,
                        sizeBytes = if (sizeIdx >= 0) cursor.getLong(sizeIdx) else 0L,
                        lastModified = if (timeIdx >= 0) cursor.getLong(timeIdx) else 0L,
                    )
                }
            }
        } catch (e: Exception) {
            return emptyList()
        }
        return out
    }

    companion object {
        private const val PREF_NAME = "wr_storage"
        private const val KEY_TREE_URI = "tree_uri"
        private const val KEY_CUSTOMIZED = "customized"
        private const val KEY_DIR_NAME = "dir_name"
        private const val KEY_DIR_PATH = "dir_path"
        const val MIME_ZIP = "application/zip"

        /** 供测试与调试用：把 resolver 抽象出来方便替换 */
        internal fun resolverOf(context: Context): ContentResolver = context.contentResolver
    }
}

/**
 * 用户目录里的一个文件。
 */
data class ExternalFile(
    val documentId: String,
    val uri: Uri,
    val displayName: String,
    val sizeBytes: Long,
    val lastModified: Long,
)

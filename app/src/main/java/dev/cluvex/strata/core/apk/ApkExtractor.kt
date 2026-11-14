package dev.cluvex.strata.core.apk

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Environment
import androidx.core.graphics.drawable.toBitmap
import dev.cluvex.strata.R
import dev.cluvex.strata.core.fs.FsUtil
import dev.cluvex.strata.core.i18n.tr
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ApkExtractor {
    const val DEFAULT_PATTERN = "{A}_{V}"

    fun fileName(pattern: String, app: InstalledApp): String {
        val base = pattern.ifBlank { DEFAULT_PATTERN }
            .replace("{A}", app.label)
            .replace("{P}", app.packageName)
            .replace("{V}", app.versionName.ifEmpty { app.versionCode.toString() })
            .replace("{C}", app.versionCode.toString())
            .replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001F\\u007F]"), "_")
            .trim()
            .trimEnd('.')
            .ifEmpty { app.packageName }
        return base.take(180) + if (app.isSplit) ".apks" else ".apk"
    }

    /** Same fixed timestamp App Manager uses for every entry (2000-01-01 UTC), so output is reproducible. */
    private const val FAKE_TIMESTAMP = 946684800000L
    private const val META_FILE = "info.json"
    private const val ICON_FILE = "icon.png"
    private const val BUFFER = 1 shl 16

    /** Everything an .apks needs to know about one package; can be built from an installed app or from an APK on disk. */
    private class Source(
        val packageName: String,
        val label: String,
        val versionName: String,
        val versionCode: Long,
        val minSdk: Long,
        val targetSdk: Long,
        val apkPaths: List<String>,
        val sharedLibraryFiles: List<String>,
        val obbDir: File?,
        val icon: Drawable?,
    )

    private class Dependency(
        val packageName: String,
        val label: String,
        val versionName: String,
        val versionCode: Long,
        val path: String,
    )

    fun extract(context: Context, app: InstalledApp, destDir: File, pattern: String = DEFAULT_PATTERN): File {
        if (!destDir.isDirectory && !destDir.mkdirs()) throw IOException(tr(R.string.apk_error_cannot_create, destDir.path))
        val target = FsUtil.uniqueSibling(File(destDir, fileName(pattern, app)), isDirectory = false)
        try {
            if (app.isSplit) bundle(context, app, target) else File(app.sourceDir).copyTo(target)
        } catch (e: Exception) {
            target.delete()
            throw e
        }
        return target
    }

    private fun bundle(context: Context, app: InstalledApp, target: File) {
        val pm = context.packageManager
        val info = pm.getApplicationInfo(app.packageName, 0)
        val source = Source(
            packageName = app.packageName,
            label = app.label,
            versionName = app.versionName,
            versionCode = app.versionCode,
            minSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) info.minSdkVersion.toLong() else 0L,
            targetSdk = info.targetSdkVersion.toLong(),
            apkPaths = app.apkPaths,
            sharedLibraryFiles = info.sharedLibraryFiles?.toList().orEmpty(),
            obbDir = obbDir(app.packageName, app.uid),
            icon = runCatching { info.loadIcon(pm) }.getOrNull(),
        )
        target.outputStream().buffered(BUFFER).use { out -> writeApks(context, out, source, allowLibraries = true) }
    }

    /** Mirrors App Manager's SplitApkExporter: max Deflate, nested library .apks, info.json, icon.png, APKs, OBBs. */
    private fun writeApks(context: Context, out: java.io.OutputStream, source: Source, allowLibraries: Boolean) {
        ZipOutputStream(out).use { zip ->
            zip.setMethod(ZipOutputStream.DEFLATED)
            zip.setLevel(Deflater.BEST_COMPRESSION)
            val used = HashSet<String>()

            // 1. Shared libraries as nested <package>.apks (they are dependencies in info.json)
            val dependencies = ArrayList<Dependency>()
            if (allowLibraries) {
                val pm = context.packageManager
                for (file in source.sharedLibraryFiles) {
                    if (!file.endsWith(".apk")) continue
                    @Suppress("DEPRECATION")
                    val pi = pm.getPackageArchiveInfo(file, PackageManager.GET_SHARED_LIBRARY_FILES) ?: continue
                    val ai = pi.applicationInfo ?: continue
                    if (ai.sourceDir == null) ai.sourceDir = file
                    if (ai.publicSourceDir == null) ai.publicSourceDir = file
                    val code = if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else @Suppress("DEPRECATION") pi.versionCode.toLong()
                    val label = runCatching { ai.loadLabel(pm).toString() }.getOrDefault(pi.packageName)
                    val libSource = Source(
                        packageName = pi.packageName,
                        label = label,
                        versionName = pi.versionName.orEmpty(),
                        versionCode = code,
                        minSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) ai.minSdkVersion.toLong() else 0L,
                        targetSdk = ai.targetSdkVersion.toLong(),
                        apkPaths = listOf(file),
                        sharedLibraryFiles = emptyList(),
                        obbDir = null,
                        icon = runCatching { ai.loadIcon(pm) }.getOrNull(),
                    )
                    val tmp = File.createTempFile("lib-", ".apks", context.cacheDir)
                    try {
                        tmp.outputStream().buffered(BUFFER).use { writeApks(context, it, libSource, allowLibraries = false) }
                        val path = pi.packageName + ".apks"
                        if (used.add(path)) {
                            addFile(zip, tmp, path)
                            dependencies += Dependency(pi.packageName, label, pi.versionName.orEmpty(), code, path)
                        }
                    } finally {
                        tmp.delete()
                    }
                }
            }

            // 2. Metadata
            used.add(META_FILE)
            addBytes(zip, metadataJson(source, dependencies).toByteArray(Charsets.UTF_8), META_FILE)

            // 3. Icon
            source.icon?.let { drawable ->
                val png = runCatching { drawableToPng(drawable) }.getOrNull()
                if (png != null) {
                    used.add(ICON_FILE)
                    addBytes(zip, png, ICON_FILE)
                }
            }

            // 4. APKs (sorted like App Manager does)
            for (path in source.apkPaths.map(::File).sortedBy { it.path }) {
                var name = path.name
                var i = 1
                while (!used.add(name)) name = "${path.nameWithoutExtension}_${i++}.apk"
                addFile(zip, path, name)
            }

            // 5. OBB files, when readable
            source.obbDir?.listFiles()?.filter { it.isFile }?.sortedBy { it.name }?.forEach { obb ->
                var name = obb.name
                var i = 1
                while (!used.add(name)) name = "${obb.nameWithoutExtension}_${i++}.${obb.extension}"
                addFile(zip, obb, name)
            }
        }
    }

    private fun metadataJson(source: Source, dependencies: List<Dependency>): String {
        val json = JSONObject()
        json.put("info_version", 1L)
        json.put("package_name", source.packageName)
        json.put("display_name", source.label)
        json.put("version_name", source.versionName)
        json.put("version_code", source.versionCode)
        json.put("min_sdk", source.minSdk)
        json.put("target_sdk", source.targetSdk)
        if (dependencies.isNotEmpty()) {
            val array = JSONArray()
            for (d in dependencies) {
                array.put(
                    JSONObject()
                        .put("package_name", d.packageName)
                        .put("display_name", d.label)
                        .put("version_name", d.versionName)
                        .put("version_code", d.versionCode)
                        .put("match", "exact")
                        .put("required", true)
                        .put("path", d.path),
                )
            }
            json.put("dependencies", array)
        }
        return json.toString()
    }

    private fun drawableToPng(drawable: Drawable): ByteArray {
        val w = drawable.intrinsicWidth.takeIf { it > 0 } ?: 192
        val h = drawable.intrinsicHeight.takeIf { it > 0 } ?: 192
        val bitmap = drawable.toBitmap(w, h)
        return ByteArrayOutputStream().use { bos ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, bos)
            bos.toByteArray()
        }
    }

    private fun obbDir(packageName: String, uid: Int): File? {
        val userId = uid / 100000
        val root = if (userId == 0) Environment.getExternalStorageDirectory() else File("/storage/emulated/$userId")
        return File(root, "Android/obb/$packageName").takeIf { it.isDirectory }
    }

    private fun newEntry(name: String) = ZipEntry(name).apply {
        method = ZipEntry.DEFLATED
        time = FAKE_TIMESTAMP
    }

    private fun addFile(zip: ZipOutputStream, file: File, name: String) {
        zip.putNextEntry(newEntry(name))
        file.inputStream().use { it.copyTo(zip, BUFFER) }
        zip.closeEntry()
    }

    private fun addBytes(zip: ZipOutputStream, bytes: ByteArray, name: String) {
        zip.putNextEntry(newEntry(name))
        zip.write(bytes)
        zip.closeEntry()
    }
}

package kscript.app.appdir

import kscript.app.model.ScriptType
import kscript.app.util.ScriptUtils
import org.apache.commons.codec.digest.DigestUtils
import java.net.URI
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

data class UriItem(
    val content: String,
    val scriptType: ScriptType,
    val fileName: String,
    val uri: URI, //Real one from Web, not the cached file
    val contextUri: URI, //Real one from Web, not the cached file
    val path: Path //Path to local file
)

class Cache(private val path: Path) {
    fun findOrCreateIdea(digest: String): Path {
        val directory = path.resolve("idea_$digest")
        return if (directory.exists()) directory else directory.createDirectories()
    }

    fun findOrCreateJar(digest: String): Path {
        val directory = path.resolve("jar_$digest")
        return if (directory.exists()) directory else directory.createDirectories()
    }

    fun findOrCreatePackage(digest: String): Path {
        val directory = path.resolve("package_$digest")
        return if (directory.exists()) directory else directory.createDirectories()
    }

    fun readUri(uri: URI): UriItem {
        val digest = DigestUtils.md5Hex(uri.toString())

        if (uri.scheme == "file") {
            val content = uri.toURL().readText()
            val scriptType = ScriptUtils.resolveScriptType(uri) ?: ScriptUtils.resolveScriptType(content)
            val fileName = ScriptUtils.extractFileName(uri)
            val contextUri = uri.resolve(".")
            return UriItem(content, scriptType, fileName, uri, contextUri, Paths.get(uri))
        }

        val directory = path.resolve("uri_$digest").createDirectories()
        val descriptorFile = directory.resolve("uri.descriptor")
        val contentFile = directory.resolve("uri.content")

        if (descriptorFile.exists() && contentFile.exists()) {
            //Cache hit
            val descriptor = descriptorFile.readText().split(" ")
            val scriptType = ScriptType.valueOf(descriptor[0])
            val fileName = descriptor[1]
            val cachedUri = URI.create(descriptor[2])
            val contextUri = URI.create(descriptor[3])
            val content = contentFile.readText()

            return UriItem(content, scriptType, fileName, cachedUri, contextUri, contentFile)
        }

        //Otherwise, resolve web file and cache it...
        val resolvedUri = ScriptUtils.resolveRedirects(uri.toURL()).toURI()
        val content = resolvedUri.toURL().readText()
        val scriptType = ScriptUtils.resolveScriptType(resolvedUri) ?: ScriptUtils.resolveScriptType(content)
        val fileName = ScriptUtils.extractFileName(resolvedUri)
        val contextUri = resolvedUri.resolve(".")

        descriptorFile.writeText("$scriptType $fileName $resolvedUri $contextUri")
        contentFile.writeText(content)

        return UriItem(content, scriptType, fileName, resolvedUri, contextUri, contentFile)
    }
}

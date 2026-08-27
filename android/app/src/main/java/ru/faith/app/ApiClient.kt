package ru.faith.app

import android.content.ContentResolver
import android.net.Uri
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.BufferedSink
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class AnalysisResult(
    val verdict: String,
    val probability: Double,
    val modelVersion: String,
    val cached: Boolean,
)

data class AnalysisHistoryItem(
    val id: String,
    val fileName: String,
    val verdict: String,
    val probability: Double,
    val modelVersion: String,
    val createdAt: String,
)

data class AuthSession(val token: String, val account: String, val provider: String = "password")

class AudioReadException : IOException()
class ApiServerException(val statusCode: Int) : IOException()

class ApiClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .callTimeout(150, TimeUnit.SECONDS)
        .build(),
    baseUrl: String = BuildConfig.API_BASE_URL,
    private val tokenProvider: () -> String? = { null },
) {
    private val baseUrl = baseUrl.normalizeServerUrl()
    suspend fun analyze(
        contentResolver: ContentResolver,
        uri: Uri,
        onUploadProgress: (Float) -> Unit = {},
    ): AnalysisResult =
        withContext(Dispatchers.IO) {
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw AudioReadException()
            onUploadProgress(0f)
            val fileName = queryFileName(contentResolver, uri) ?: "audio.wav"
            val mediaType = contentResolver.getType(uri) ?: when (fileName.substringAfterLast('.', "").lowercase()) {
                "mp3" -> "audio/mpeg"
                "m4a" -> "audio/mp4"
                "aac" -> "audio/aac"
                "ogg" -> "audio/ogg"
                "flac" -> "audio/flac"
                else -> "audio/wav"
            }
            val audioBody = ProgressRequestBody(
                bytes = bytes,
                mediaType = mediaType,
                onProgress = onUploadProgress,
            )
            val multipart = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("audio", fileName, audioBody)
                .build()
            val request = Request.Builder()
                .url("${baseUrl}api/v1/analyses")
                .header("Connection", "close")
                .withBearerToken()
                .post(multipart)
                .build()

            suspendCancellableCoroutine { continuation ->
                val call = client.newCall(request)
                call.timeout().timeout(60, TimeUnit.SECONDS)
                continuation.invokeOnCancellation { call.cancel() }
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (continuation.isActive) continuation.resumeWithException(e)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val parsed = runCatching {
                            response.use {
                                val body = it.body.string()
                                if (!it.isSuccessful) throw ApiServerException(it.code)
                                val json = JSONObject(body)
                                AnalysisResult(
                                    verdict = json.getString("verdict"),
                                    probability = json.getDouble("synthetic_probability"),
                                    modelVersion = json.getString("model_version"),
                                    cached = json.optBoolean("cached", false),
                                )
                            }
                        }
                        if (!continuation.isActive) return
                        parsed.onSuccess { continuation.resume(it) }
                            .onFailure { continuation.resumeWithException(it) }
                    }
                })
            }
        }

    suspend fun analysisHistory(): List<AnalysisHistoryItem> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${baseUrl}api/v1/analyses")
            .header("Connection", "close")
            .withBearerToken()
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body.string()
            if (!response.isSuccessful) throw ApiServerException(response.code)

            val json = JSONArray(body)
            buildList {
                for (index in 0 until json.length()) {
                    val item = json.getJSONObject(index)
                    add(
                        AnalysisHistoryItem(
                            id = item.getString("id"),
                            fileName = item.getString("file_name"),
                            verdict = item.getString("verdict"),
                            probability = item.getDouble("synthetic_probability"),
                            modelVersion = item.getString("model_version"),
                            createdAt = item.getString("created_at"),
                        )
                    )
                }
            }
        }
    }

    suspend fun authenticate(identifier: String, password: String, register: Boolean): AuthSession =
        withContext(Dispatchers.IO) {
            val payload = JSONObject()
                .put("identifier", identifier)
                .put("password", password)
                .apply {
                    // Keep email authentication compatible with the deployed
                    // API until phone authentication is rolled out there.
                    if (identifier.contains('@')) put("email", identifier)
                }
            val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val endpoint = if (register) "register" else "login"
            val request = Request.Builder()
                .url("${baseUrl}api/v1/auth/$endpoint")
                .post(body)
                .build()
            val call = client.newCall(request)
            call.timeout().timeout(25, TimeUnit.SECONDS)
            call.execute().use { response ->
                val text = response.body.string()
                if (!response.isSuccessful) throw ApiServerException(response.code)
                val json = JSONObject(text)
                val user = json.getJSONObject("user")
                AuthSession(
                    token = json.getString("access_token"),
                    account = if (!user.isNull("email")) user.getString("email") else user.getString("phone"),
                )
            }
        }

    suspend fun authenticateYandex(oauthToken: String): AuthSession = withContext(Dispatchers.IO) {
        val body = JSONObject().put("oauth_token", oauthToken).toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url("${baseUrl}api/v1/auth/yandex")
            .post(body)
            .build()
        client.newCall(request).execute().use { response ->
            val text = response.body.string()
            if (!response.isSuccessful) throw ApiServerException(response.code)
            val json = JSONObject(text)
            val user = json.getJSONObject("user")
            val account = when {
                !user.isNull("email") -> user.getString("email")
                !user.isNull("phone") -> user.getString("phone")
                else -> "Yandex ID"
            }
            AuthSession(json.getString("access_token"), account, provider = "yandex")
        }
    }

    suspend fun deleteAccount(password: String?) = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply { password?.let { put("password", it) } }
        val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url("${baseUrl}api/v1/auth/account")
            .withBearerToken()
            .delete(body)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw ApiServerException(response.code)
        }
    }

    private fun Request.Builder.withBearerToken(): Request.Builder = apply {
        tokenProvider()?.takeIf { it.isNotBlank() }?.let { header("Authorization", "Bearer $it") }
    }

    private fun queryFileName(contentResolver: ContentResolver, uri: Uri): String? {
        if (uri.scheme == "file") return uri.path?.let(::File)?.name
        return runCatching {
            contentResolver.query(uri, arrayOf("_display_name"), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()
    }
}

private class ProgressRequestBody(
    private val bytes: ByteArray,
    mediaType: String,
    private val onProgress: (Float) -> Unit,
) : RequestBody() {
    private val contentType = mediaType.toMediaType()

    override fun contentType() = contentType

    override fun contentLength() = bytes.size.toLong()

    override fun writeTo(sink: BufferedSink) {
        val chunkSize = 64 * 1024
        var offset = 0
        while (offset < bytes.size) {
            val count = minOf(chunkSize, bytes.size - offset)
            sink.write(bytes, offset, count)
            offset += count
            onProgress(offset.toFloat() / bytes.size.coerceAtLeast(1))
        }
    }
}

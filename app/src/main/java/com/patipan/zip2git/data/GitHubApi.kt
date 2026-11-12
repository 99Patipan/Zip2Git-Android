package com.patipan.zip2git.data

import android.net.Uri
import android.util.Base64
import com.patipan.zip2git.R
import com.patipan.zip2git.domain.AppException
import com.patipan.zip2git.domain.PushFile
import com.patipan.zip2git.domain.RemoteEntry
import com.patipan.zip2git.domain.RemoteSnapshot
import com.patipan.zip2git.domain.RepoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

private const val BASE = "https://api.github.com"
private const val SMALL_BLOB_BYTES = 2L * 1024 * 1024
private const val MAX_RETRIES = 3

/** One shared client for the whole app (connection pool + threads are reused). */
private val httpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()
}
private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()

internal class Raw(val code: Int, val body: String, val link: String?, val retryAfterSec: Long?)

class GitHubApi(private val token: String) {

    // ---------- public API ----------

    suspend fun profile(): JSONObject = getObject("/user")

    /** Repositories the token can push to (all pages). */
    suspend fun repositories(): List<RepoInfo> =
        pagedArray("$BASE/user/repos?per_page=100&sort=updated")
            .filter { !it.optBoolean("archived") }
            .filter { it.optJSONObject("permissions")?.optBoolean("push", true) ?: true }
            .map { RepoInfo(it.getString("full_name"), it.optString("default_branch", "")) }

    suspend fun branches(owner: String, repo: String): List<String> =
        pagedArray("$BASE/repos/$owner/$repo/branches?per_page=100").map { it.getString("name") }

    suspend fun snapshot(owner: String, repo: String, branch: String): RemoteSnapshot {
        val head = headSha(owner, repo, branch)
        val treeSha = commitTreeSha(owner, repo, head)
        return RemoteSnapshot(head, readTree(owner, repo, treeSha))
    }

    /**
     * Uploads blobs, builds one tree + one commit and moves the branch.
     * Fails with err_stale_branch if the branch moved since [expectedHead].
     * [onProgress] runs on the caller's dispatcher (so it can touch UI state).
     */
    suspend fun push(
        owner: String,
        repo: String,
        branch: String,
        message: String,
        expectedHead: String,
        additions: List<PushFile>,
        deletions: List<String>,
        onProgress: (done: Int, total: Int) -> Unit,
    ): String {
        if (headSha(owner, repo, branch) != expectedHead) throw AppException(R.string.err_stale_branch)
        val baseTree = commitTreeSha(owner, repo, expectedHead)

        val total = additions.size
        val done = AtomicInteger(0)
        val shas = ConcurrentHashMap<String, String>()
        val gate = Semaphore(4)
        val (small, large) = additions.partition { it.size <= SMALL_BLOB_BYTES }

        coroutineScope {
            small.map { f ->
                async {
                    gate.withPermit { shas[f.path] = uploadBlob(owner, repo, f) }
                    onProgress(done.incrementAndGet(), total)
                }
            }.awaitAll()
        }
        // Big files go one at a time to keep peak memory low (bytes + base64 + JSON copies).
        for (f in large) {
            shas[f.path] = uploadBlob(owner, repo, f)
            onProgress(done.incrementAndGet(), total)
        }

        val tree = JSONArray()
        for (f in additions) {
            tree.put(
                JSONObject().put("path", f.path).put("mode", f.mode).put("type", "blob").put("sha", shas.getValue(f.path)),
            )
        }
        for (p in deletions) {
            tree.put(JSONObject().put("path", p).put("mode", "100644").put("type", "blob").put("sha", JSONObject.NULL))
        }
        val newTree = postObject(
            "/repos/$owner/$repo/git/trees",
            JSONObject().put("base_tree", baseTree).put("tree", tree),
        ).getString("sha")

        val commit = postObject(
            "/repos/$owner/$repo/git/commits",
            JSONObject().put("message", message).put("tree", newTree).put("parents", JSONArray().put(expectedHead)),
        ).getString("sha")

        // Updating a ref is PATCH .../git/refs/... (plural); reading is .../git/ref/... (singular).
        execute(
            "$BASE/repos/$owner/$repo/git/refs/heads/${encodeRef(branch)}",
            "PATCH",
            JSONObject().put("sha", commit).put("force", false),
        )
        return commit
    }

    /**
     * Pushes into a repository with no commits. Git Data API calls fail on an empty repo
     * (409 "Git Repository is empty"), so the smallest file is created through the Contents API,
     * which makes the repository's default branch; everything else follows as one normal commit
     * on top of it. Result: two commits. Always targets the repository's default branch.
     */
    suspend fun pushToEmpty(
        owner: String,
        repo: String,
        branch: String,
        message: String,
        additions: List<PushFile>,
        onProgress: (done: Int, total: Int) -> Unit,
    ): String {
        // The repository may have received a commit since it was selected.
        if (branches(owner, repo).isNotEmpty()) throw AppException(R.string.err_stale_branch)
        val first = additions.minByOrNull { it.size } ?: throw AppException(R.string.err_source_empty)
        val total = additions.size

        val encoded = withContext(Dispatchers.IO) { Base64.encodeToString(first.file.readBytes(), Base64.NO_WRAP) }
        val created = JSONObject(
            execute(
                "$BASE/repos/$owner/$repo/contents/${Uri.encode(first.path, "/")}",
                "PUT",
                // No "branch": on an empty repo this creates the default branch.
                JSONObject().put("message", message).put("content", encoded),
            ).body,
        )
        val firstCommit = created.getJSONObject("commit").getString("sha")
        onProgress(1, total)

        val rest = additions.filter { it !== first }
        if (rest.isEmpty()) return firstCommit
        return push(owner, repo, branch, message, firstCommit, rest, emptyList()) { done, _ ->
            onProgress(done + 1, total)
        }
    }

    // ---------- internals ----------

    private suspend fun uploadBlob(owner: String, repo: String, f: PushFile): String =
        withContext(Dispatchers.IO) {
            val encoded = Base64.encodeToString(f.file.readBytes(), Base64.NO_WRAP)
            postObject(
                "/repos/$owner/$repo/git/blobs",
                JSONObject().put("content", encoded).put("encoding", "base64"),
            ).getString("sha")
        }

    private suspend fun headSha(owner: String, repo: String, branch: String): String =
        getObject("/repos/$owner/$repo/git/ref/heads/${encodeRef(branch)}").getJSONObject("object").getString("sha")

    private suspend fun commitTreeSha(owner: String, repo: String, commitSha: String): String =
        getObject("/repos/$owner/$repo/git/commits/$commitSha").getJSONObject("tree").getString("sha")

    /** Slashes stay literal (documented form); everything else unsafe is percent-encoded. */
    private fun encodeRef(branch: String): String = Uri.encode(branch, "/")

    private suspend fun readTree(owner: String, repo: String, rootSha: String): Map<String, RemoteEntry> {
        val out = linkedMapOf<String, RemoteEntry>()
        val whole = getObject("/repos/$owner/$repo/git/trees/$rootSha?recursive=1")
        if (!whole.optBoolean("truncated", false)) {
            collectBlobs(whole.getJSONArray("tree"), "", out, null)
            return out
        }
        // Recursive listing was truncated: walk the tree one directory at a time.
        out.clear()
        val stack = ArrayDeque<Pair<String, String>>()
        stack.add("" to rootSha)
        while (stack.isNotEmpty()) {
            val (prefix, sha) = stack.removeLast()
            val t = getObject("/repos/$owner/$repo/git/trees/$sha")
            if (t.optBoolean("truncated", false)) throw AppException(R.string.err_tree_too_large)
            collectBlobs(t.getJSONArray("tree"), prefix, out, stack)
        }
        return out
    }

    private fun collectBlobs(
        arr: JSONArray,
        prefix: String,
        out: MutableMap<String, RemoteEntry>,
        stack: ArrayDeque<Pair<String, String>>?,
    ) {
        for (i in 0 until arr.length()) {
            val item = arr.getJSONObject(i)
            val path = prefix + item.getString("path")
            when (item.optString("type")) {
                "blob" -> out[path] = RemoteEntry(path, item.getString("mode"), item.getString("sha"))
                "tree" -> stack?.add("$path/" to item.getString("sha"))
            }
        }
    }

    private suspend fun getObject(path: String): JSONObject = JSONObject(execute("$BASE$path", "GET", null).body)

    private suspend fun postObject(path: String, body: JSONObject): JSONObject =
        JSONObject(execute("$BASE$path", "POST", body).body)

    private suspend fun pagedArray(firstUrl: String, maxPages: Int = 20): List<JSONObject> {
        val out = mutableListOf<JSONObject>()
        var url: String? = firstUrl
        var page = 0
        while (url != null && page < maxPages) {
            val r = execute(url, "GET", null)
            val arr = JSONArray(r.body)
            for (i in 0 until arr.length()) out += arr.getJSONObject(i)
            url = nextLink(r.link)
            page++
        }
        return out
    }

    private fun nextLink(link: String?): String? {
        if (link == null) return null
        for (part in link.split(",")) {
            if (!part.contains("rel=\"next\"")) continue
            val url = Regex("<([^>]+)>").find(part)?.groupValues?.get(1) ?: continue
            // Never send the token anywhere except the GitHub API.
            if (url.startsWith("$BASE/")) return url
        }
        return null
    }

    /** Executes a request; retries rate limits / 5xx with backoff (honouring Retry-After). */
    private suspend fun execute(url: String, method: String, body: JSONObject?): Raw {
        var attempt = 0
        while (true) {
            val raw = withContext(Dispatchers.IO) {
                val builder = Request.Builder().url(url)
                    .header("Authorization", "Bearer $token")
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .header("User-Agent", "Zip2Git-Android")
                when (method) {
                    "GET" -> builder.get()
                    else -> builder.method(method, (body?.toString() ?: "{}").toRequestBody(JSON_TYPE))
                }
                httpClient.newCall(builder.build()).execute().use { r ->
                    Raw(r.code, r.body?.string().orEmpty(), r.header("Link"), r.header("Retry-After")?.toLongOrNull())
                }
            }
            if (raw.code in 200..299) return raw
            val rateLimited = raw.code == 429 ||
                (raw.code == 403 && (raw.retryAfterSec != null || raw.body.contains("rate limit", ignoreCase = true)))
            val retryable = rateLimited || raw.code in 502..504
            if (retryable && attempt < MAX_RETRIES) {
                attempt++
                val waitSec = (raw.retryAfterSec ?: (2L shl attempt)).coerceIn(1, 60)
                delay(waitSec * 1000)
                continue
            }
            throw mapError(raw)
        }
    }

    private fun mapError(r: Raw): AppException {
        val msg = runCatching { JSONObject(r.body).optString("message") }.getOrNull().orEmpty()
            .ifBlank { r.body.take(200) }
        return when {
            r.code == 401 -> AppException(R.string.err_unauthorized)
            r.code == 403 -> AppException(R.string.err_forbidden, listOf(msg))
            r.code == 404 -> AppException(R.string.err_not_found)
            r.code == 409 && msg.contains("empty", ignoreCase = true) -> AppException(R.string.err_empty_repo)
            else -> AppException(R.string.err_http, listOf(r.code, msg))
        }
    }
}

package furhatos.app.newskill.gemini

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import java.io.IOException
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URI
import java.net.URISyntaxException
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * Raised for every failure mode of a Gemini call: transport, HTTP status, or unusable payload.
 *
 * @param retryable whether trying the same request again could plausibly succeed — true for
 *   timeouts, rate limits and 5xx, false for anything the request itself got wrong.
 */
class GeminiException(
    message: String,
    cause: Throwable? = null,
    val retryable: Boolean = false
) : RuntimeException(message, cause)

/**
 * Calls the Gemini `generateContent` REST API and returns the answer already parsed into a Kotlin
 * data class.
 *
 * The client itself knows nothing about the shapes it returns. A caller passes a
 * [GeminiResponseFormat], which carries both the target class and the JSON schema the model is
 * constrained to, and gets that type back:
 *
 * ```
 * val gemini = GeminiClient()
 * val reply = gemini.generate(
 *     prompt = DuckyPrompt.forSpeech(it.text),
 *     format = DuckyReply,
 *     systemInstruction = DuckyPrompt.SYSTEM_INSTRUCTION
 * )
 * furhat.say(reply.response)
 * ```
 *
 * Deliberately built on [HttpURLConnection] and Gson: both are on the classpath of any Furhat
 * skill, so this drops into the SDK without new transitive dependencies to fight over.
 *
 * Instances are stateless and safe to share across a flow.
 */
class GeminiClient(
    private val apiKey: String = resolveApiKey(),
    private val model: String = DEFAULT_MODEL,
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val connectTimeoutMs: Int = 5_000,
    private val readTimeoutMs: Int = 30_000,
    private val maxRetries: Int = 2
) {

    private val gson = Gson()

    /** Resolved once, so a typo in [model] or [baseUrl] fails here rather than mid-conversation. */
    private val endpoint: URL = "$baseUrl/models/$model:generateContent".let { target ->
        try {
            URI(target).toURL()
        } catch (e: URISyntaxException) {
            throw GeminiException("Not a valid Gemini endpoint: $target", e)
        } catch (e: MalformedURLException) {
            throw GeminiException("Not a valid Gemini endpoint: $target", e)
        }
    }

    init {
        check(apiKey.isNotBlank()) {
            "No Gemini API key found. Set the GEMINI_API_KEY environment variable, pass -Dgemini.apiKey=..., " +
                "or put `apiKey=...` in src/main/resources/gemini.properties."
        }
    }

    /**
     * Sends [prompt] and returns the response parsed as [T].
     *
     * @param format binds the target data class to the schema Gemini must fill in.
     * @param systemInstruction persona and standing rules; kept separate from [prompt] so the
     *   per-turn input stays small and the model weights the instruction more heavily.
     * @param temperature 0.0 for repeatable answers, higher for variety.
     * @param maxOutputTokens hard cap; leave null to let the model decide. Too low truncates the
     *   JSON mid-object, which surfaces here as a `finishReason=MAX_TOKENS` failure.
     * @param thinkingBudget reasoning tokens spent before answering. Defaults to 0, asking for none,
     *   because a robot holding a conversation needs the round trip to be short. Pass null to omit
     *   the field and let the model choose.
     *
     *   The accepted range is per-model and not stated in the API reference — some models refuse a
     *   budget of 0, and newer ones configure this differently. If a call comes back as HTTP 400
     *   mentioning thinking, pass null and check the model card.
     *
     * @throws GeminiException if the call fails, is blocked, stops early, or returns JSON that does
     *   not fit [T].
     */
    fun <T : Any> generate(
        prompt: String,
        format: GeminiResponseFormat<T>,
        systemInstruction: String? = null,
        temperature: Double = 1.0,
        maxOutputTokens: Int? = null,
        thinkingBudget: Int? = 0
    ): T {
        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(prompt)), role = "user")),
            systemInstruction = systemInstruction?.let { Content(parts = listOf(Part(it))) },
            generationConfig = GenerationConfig(
                temperature = temperature,
                maxOutputTokens = maxOutputTokens,
                // These two together are what make the answer parseable rather than hopefully-parseable.
                responseMimeType = "application/json",
                responseSchema = format.schema,
                thinkingConfig = thinkingBudget?.let { ThinkingConfig(it) }
            )
        )

        val responseBody = postWithRetries(gson.toJson(request))
        val envelope = parse(responseBody, GenerateContentResponse::class.java, "response envelope")
        val payload = extractText(envelope)

        val value = parse(payload, format.type, format.type.simpleName)
        assertRequiredFieldsPresent(value, format.schema)
        return value
    }

    /**
     * Same as [generate] but returns null instead of throwing.
     *
     * Handy in a flow's `onResponse`, where a failed call should fall back to a canned line rather
     * than take the skill down mid-conversation.
     */
    fun <T : Any> generateOrNull(
        prompt: String,
        format: GeminiResponseFormat<T>,
        systemInstruction: String? = null,
        temperature: Double = 1.0,
        maxOutputTokens: Int? = null,
        thinkingBudget: Int? = 0
    ): T? = try {
        generate(prompt, format, systemInstruction, temperature, maxOutputTokens, thinkingBudget)
    } catch (e: GeminiException) {
        System.err.println("Gemini call failed: ${e.message}")
        null
    }

    /** Pulls the model's JSON out of the envelope, failing loudly on every "no usable answer" case. */
    private fun extractText(response: GenerateContentResponse): String {
        response.promptFeedback?.blockReason?.let {
            throw GeminiException("Gemini blocked the prompt (reason: $it)")
        }

        val candidate = response.candidates?.firstOrNull()
            ?: throw GeminiException("Gemini returned no candidates")

        // Anything other than STOP means the text we have is partial, so the JSON will not parse.
        val finishReason = candidate.finishReason
        if (finishReason != null && finishReason != "STOP") {
            throw GeminiException(
                "Gemini stopped before finishing (finishReason: $finishReason), so the JSON is incomplete"
            )
        }

        val text = candidate.content?.parts.orEmpty().joinToString("") { it.text.orEmpty() }.trim()
        if (text.isEmpty()) throw GeminiException("Gemini returned an empty response body")
        return text
    }

    private fun <T : Any> parse(json: String, type: Class<T>, what: String): T = try {
        gson.fromJson(json, type)
            ?: throw GeminiException("Gemini returned JSON null where a $what was expected")
    } catch (e: JsonSyntaxException) {
        throw GeminiException("Could not parse $what from Gemini: ${json.take(500)}", e)
    }

    /**
     * Verifies the schema's required fields actually arrived.
     *
     * Gson instantiates without running constructors, so a field declared non-null in Kotlin is
     * silently null when absent from the JSON. Catching that here names the missing field instead of
     * leaving an NPE to surface later at the call site.
     */
    private fun <T : Any> assertRequiredFieldsPresent(value: T, schema: GeminiSchema) {
        for (name in schema.required.orEmpty()) {
            val field = try {
                value.javaClass.getDeclaredField(name)
            } catch (e: NoSuchFieldException) {
                // Schema describes a field the data class does not declare — a mismatch worth naming.
                throw GeminiException(
                    "Schema requires '$name' but ${value.javaClass.simpleName} has no such field", e
                )
            }
            field.isAccessible = true
            if (field.get(value) == null) {
                throw GeminiException("Gemini omitted required field '$name' from the response")
            }
        }
    }

    private fun postWithRetries(body: String): String {
        var attempt = 0
        while (true) {
            try {
                return post(body)
            } catch (e: GeminiException) {
                // Rate limits and 5xx are worth a second look; a bad request never is.
                if (!e.retryable || attempt >= maxRetries) throw e
                Thread.sleep(RETRY_BASE_DELAY_MS shl attempt)
                attempt++
            }
        }
    }

    private fun post(body: String): String {
        val connection = try {
            endpoint.openConnection() as HttpURLConnection
        } catch (e: IOException) {
            throw GeminiException("Could not open a connection to $endpoint", e, retryable = true)
        }

        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Accept", "application/json")
            // Header auth, so the key never lands in a URL that might be logged.
            connection.setRequestProperty("x-goog-api-key", apiKey)

            try {
                connection.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }

                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val payload = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()

                if (status !in 200..299) {
                    throw GeminiException(
                        "Gemini request failed (HTTP $status): ${describe(status, payload)}",
                        retryable = status == 408 || status == 429 || status >= 500
                    )
                }
                return payload
            } catch (e: IOException) {
                throw GeminiException("Gemini request to $model failed: ${e.message}", e, retryable = true)
            }
        } finally {
            connection.disconnect()
        }
    }

    /** Surfaces Gemini's own error message when there is one, rather than a bare status code. */
    private fun describe(status: Int, payload: String): String {
        val message = try {
            gson.fromJson(payload, ErrorEnvelope::class.java)?.error?.message
        } catch (e: JsonSyntaxException) {
            null
        }
        return message ?: when (status) {
            400 -> "bad request — often an invalid responseSchema. Body: ${payload.take(500)}"
            401, 403 -> "the API key was rejected"
            404 -> "no such model: '$model'"
            429 -> "rate limit or quota exceeded"
            else -> payload.take(500).ifEmpty { "no response body" }
        }
    }

    companion object {
        /**
         * Fast and cheap, which is what turn-by-turn conversation needs.
         *
         * Verify this id still suits your API key before a workshop: Google serves `gemini-2.5-flash`
         * but has restricted it to projects with prior usage, so a freshly created key may get a 404
         * here and need a current model instead. Override per instance: `GeminiClient(model = "...")`.
         */
        const val DEFAULT_MODEL = "gemini-2.5-flash"
        const val DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com/v1beta"

        private const val RETRY_BASE_DELAY_MS = 500L
        private const val API_KEY_RESOURCE = "/gemini.properties"

        /**
         * Finds the API key, in order: `GEMINI_API_KEY` env var, `-Dgemini.apiKey=...`, then
         * `apiKey=...` in [API_KEY_RESOURCE] on the classpath. Returns "" when none is set, which
         * [GeminiClient]'s constructor turns into a readable error.
         *
         * The resource fallback is there because the Furhat SDK launcher makes environment
         * variables awkward; keep that file out of version control.
         */
        fun resolveApiKey(): String =
            System.getenv("GEMINI_API_KEY")?.takeIf { it.isNotBlank() }
                ?: System.getProperty("gemini.apiKey")?.takeIf { it.isNotBlank() }
                ?: apiKeyFromClasspath()
                ?: ""

        private fun apiKeyFromClasspath(): String? =
            GeminiClient::class.java.getResourceAsStream(API_KEY_RESOURCE)?.use { stream ->
                java.util.Properties().apply { load(stream) }.getProperty("apiKey")?.trim()?.takeIf { it.isNotEmpty() }
            }
    }
}

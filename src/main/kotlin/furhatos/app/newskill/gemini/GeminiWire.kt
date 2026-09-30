package furhatos.app.newskill.gemini

import furhatos.app.newskill.donottouch.GeminiSchema

/*
 * Wire format for the Gemini `generateContent` REST endpoint.
 *
 * Field names match the JSON exactly, so plain Gson handles both directions with no adapters.
 * Response fields are all nullable: what comes back depends on why generation stopped, and treating
 * it as optional keeps a surprising payload from turning into a confusing NPE deep in a flow.
 */

internal data class GenerateContentRequest(
    val contents: List<Content>,
    val systemInstruction: Content? = null,
    val generationConfig: GenerationConfig? = null
)

internal data class Content(
    val parts: List<Part>? = null,
    val role: String? = null
)

internal data class Part(
    val text: String? = null
)

internal data class GenerationConfig(
    val temperature: Double? = null,
    val maxOutputTokens: Int? = null,
    val responseMimeType: String? = null,
    val responseSchema: GeminiSchema? = null,
    val thinkingConfig: ThinkingConfig? = null
)

internal data class ThinkingConfig(
    val thinkingBudget: Int
)

internal data class GenerateContentResponse(
    val candidates: List<Candidate>? = null,
    val promptFeedback: PromptFeedback? = null,
    val usageMetadata: UsageMetadata? = null
)

internal data class Candidate(
    val content: Content? = null,
    val finishReason: String? = null
)

internal data class PromptFeedback(
    val blockReason: String? = null
)

internal data class UsageMetadata(
    val promptTokenCount: Int? = null,
    val candidatesTokenCount: Int? = null,
    val thoughtsTokenCount: Int? = null,
    val totalTokenCount: Int? = null
)

/** Shape of a non-2xx body, e.g. `{"error": {"code": 400, "message": "...", "status": "..."}}`. */
internal data class ErrorEnvelope(
    val error: ErrorBody? = null
)

internal data class ErrorBody(
    val code: Int? = null,
    val message: String? = null,
    val status: String? = null
)

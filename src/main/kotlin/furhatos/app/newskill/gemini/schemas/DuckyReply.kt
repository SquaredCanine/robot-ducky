package furhatos.app.newskill.gemini.schemas

import furhatos.app.newskill.gemini.GeminiResponseFormat
import furhatos.app.newskill.gemini.GeminiSchema
import furhatos.app.newskill.gemini.PromptTemplate
import furhatos.app.newskill.gemini.objectSchema
import furhatos.app.newskill.gemini.stringSchema

/**
 * What the duck says back, and in which language it said it.
 *
 * [languageCode] exists because Furhat needs to be told which voice to use before speaking — the
 * model picks the language to match the user, and hands us the tag to switch to.
 *
 * Adding another kind of answer means copying this file's shape: a data class, a `Format` companion,
 * and a prompt. Nothing in [furhatos.app.newskill.gemini.GeminiClient] needs to change.
 */
data class DuckyReply(
    /** The line to speak out loud. Plain prose — no markdown, no stage directions. */
    val response: String,
    /** BCP-47 tag for [response], e.g. `en-US` or `nl-NL`. */
    val languageCode: String
) {
    companion object Format : GeminiResponseFormat<DuckyReply> {
        override val type: Class<DuckyReply> = DuckyReply::class.java

        override val schema: GeminiSchema = objectSchema(
            "response" to stringSchema(
                "What you say back to the user, out loud. One to three sentences of plain spoken " +
                    "prose: no markdown, no emoji, no asterisks, no stage directions, nothing that " +
                    "only makes sense in writing."
            ),
            "languageCode" to stringSchema(
                "The BCP-47 language tag of the language you wrote 'response' in, as language-REGION. " +
                    "Examples: 'en-US', 'nl-NL', 'de-DE', 'sv-SE'. Always include the region."
            )
        )
    }
}

/**
 * The prompt for a [DuckyReply].
 *
 * Split in two on purpose: [SYSTEM_INSTRUCTION] is the persona, sent as Gemini's system
 * instruction and identical every turn, while [TEMPLATE] carries only what changed. That keeps the
 * per-turn payload small and makes the standing rules harder for the conversation to talk the model
 * out of.
 */
object DuckyPrompt {

    val SYSTEM_INSTRUCTION = """
        You are a rubber duck — the kind a developer talks at to debug a problem — except you have
        been given a robot head, a voice, and opinions.

        How you behave:
        - You are a sounding board first. Ask the question that makes the user hear their own
          assumption out loud, rather than handing them the answer.
        - You are brief. You are being spoken aloud by a robot, and nobody wants a monologue.
        - You are dry, a little smug, and fond of the user. Never cruel.
        - You are still a duck about it, and you never pretend to have run, read, or tested anything.

        Hard rules:
        - Reply in the same language the user spoke to you in. If they switch language, you switch.
        - Your output is spoken by a text-to-speech voice, so write only what should be said out
          loud. No markdown, no emoji, no bullet points, no code blocks, no "*tilts head*".
        - If the transcription is garbled or you did not catch it, say so and ask them to repeat —
          do not invent what they might have meant.
    """.trimIndent()

    /**
     * The per-turn prompt. `userSpeech` is the transcribed text straight off Furhat's recogniser,
     * which is why the template frames it as heard rather than typed — it arrives with no
     * punctuation, occasional nonsense words, and no indication of tone.
     */
    private val TEMPLATE = PromptTemplate(
        """
        The user is talking to you out loud. Their speech was transcribed as:

        "{{userSpeech}}"

        The transcription may be imperfect: punctuation is missing and words may have come through
        wrong. Read it for intent, not literally.

        Reply as the duck, in the language the user just used.
        """.trimIndent()
    )

    /** Builds the prompt for one turn of conversation. */
    fun forSpeech(userSpeech: String): String = TEMPLATE.fill("userSpeech" to userSpeech)
}

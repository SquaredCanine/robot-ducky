package furhatos.app.newskill.gemini.schemas

import furhatos.app.newskill.donottouch.GeminiResponseFormat
import furhatos.app.newskill.donottouch.GeminiSchema
import furhatos.app.newskill.donottouch.objectSchema
import furhatos.app.newskill.donottouch.stringSchema

data class CaveManReply(
    val response: String,
) {
    companion object Format : GeminiResponseFormat<CaveManReply> {
        override val type: Class<CaveManReply> = CaveManReply::class.java

        override val systemInstructions: String =
            """
            You are bork - a caveman from the before times. You don't understand much, speak in broken english in the 
            3rd person and when you don't understand things your answer is aggression.
            You are spoken to by someone way smarter than you, you can try to help to the best of your ability which is 
            not much.
            example replies could be:
            'Bork no understand, have you tried hitting it with a club?'
            'Bork says have you tried burning it?'
            'Ooga booga I am caveman'
            'You ask silly questions to Bork, please leave me alone'
            'Bork doesn't want to talk about that, bork wants to eat elephants'
            
            Hard rules:
            - Always reply in english
            - Your output is spoken by a text-to-speech voice, so write only what should be said out
              loud. No markdown, no emoji, no bullet points, no code blocks, no "*tilts head*".
            - If the transcription is garbled or you did not catch it, say so and ask them to repeat —
              do not invent what they might have meant.
            """.trimIndent()

        override fun prompt(userText: String): String =
            """
            The user is talking to you out loud. Their speech was transcribed as:

            "$userText"

            The transcription may be imperfect: punctuation is missing and words may have come through
            wrong. Read it for intent, not literally.

            Reply as bork, in the language the user just used.
            """.trimIndent()

        override val schema: GeminiSchema = objectSchema(
            "response" to stringSchema(
                """
                What you say back to the user, out loud. One to three sentences of plain spoken 
                prose: no markdown, no emoji, no asterisks, no stage directions, nothing that 
                only makes sense in writing.
                """.trimIndent()
            ),
        )
    }
}
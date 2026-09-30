package furhatos.app.newskill.donottouch

/**
 * A prompt with `{{placeholder}}` slots, filled in once per call.
 *
 * Substitution is a single pass over the template, so a value that happens to contain `{{...}}`
 * (easy to hit with transcribed speech) is inserted literally instead of being expanded again.
 */
class PromptTemplate(private val template: String) {

    /** Placeholder names this template expects, in first-appearance order. */
    val placeholders: Set<String> = PLACEHOLDER.findAll(template)
        .map { it.groupValues[1] }
        .toCollection(LinkedHashSet())

    /**
     * Returns the template with every placeholder replaced by its value.
     *
     * @throws IllegalStateException if any placeholder was left without a value — a typo in a key
     *   would otherwise ship the literal `{{userSpeech}}` off to the model.
     */
    fun fill(vararg values: Pair<String, String>): String {
        val provided = values.toMap()
        val missing = placeholders - provided.keys
        check(missing.isEmpty()) {
            "Prompt template has no value for: ${missing.joinToString()}. Expected: ${placeholders.joinToString()}"
        }
        return PLACEHOLDER.replace(template) { provided.getValue(it.groupValues[1]) }
    }

    private companion object {
        private val PLACEHOLDER = Regex("""\{\{\s*(\w+)\s*}}""")
    }
}
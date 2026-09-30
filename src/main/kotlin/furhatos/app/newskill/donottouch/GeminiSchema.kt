package furhatos.app.newskill.donottouch

import java.lang.Class

/**
 * A response schema in the OpenAPI subset that Gemini accepts for `generationConfig.responseSchema`.
 *
 * Serialises straight to the wire format, so this class doubles as our schema builder output.
 * Null fields are omitted by Gson, which is what Gemini expects.
 */
data class GeminiSchema(
    val type: String,
    val description: String? = null,
    val nullable: Boolean? = null,
    val enum: List<String>? = null,
    val format: String? = null,
    val items: GeminiSchema? = null,
    val properties: Map<String, GeminiSchema>? = null,
    val required: List<String>? = null,
    val propertyOrdering: List<String>? = null
)

interface GeminiResponseFormat<T : Any> {
    val type: Class<T>

    val systemInstructions: String

    fun prompt(userText: String): String

    /** The schema Gemini is constrained to. Must describe [type]'s fields by their exact names. */
    val schema: GeminiSchema
}

/*
 * Field builders.
 *
 * `description` is not decoration — Gemini reads it as part of the instruction, so it is the best
 * place to pin down formats ("BCP-47 tag") and ranges. That is also why schemas are declared by
 * hand here rather than derived from the data class by reflection: reflection can recover the field
 * names and types, but not the guidance that makes the model fill them in correctly.
 */

fun stringSchema(description: String? = null, nullable: Boolean = false): GeminiSchema =
    GeminiSchema(type = "STRING", description = description, nullable = nullable.orNullIfFalse())

fun integerSchema(description: String? = null, nullable: Boolean = false): GeminiSchema =
    GeminiSchema(type = "INTEGER", description = description, nullable = nullable.orNullIfFalse())

fun numberSchema(description: String? = null, nullable: Boolean = false): GeminiSchema =
    GeminiSchema(type = "NUMBER", description = description, nullable = nullable.orNullIfFalse())

fun booleanSchema(description: String? = null, nullable: Boolean = false): GeminiSchema =
    GeminiSchema(type = "BOOLEAN", description = description, nullable = nullable.orNullIfFalse())

/** A closed set of string values. Use this over [stringSchema] whenever the options are known. */
fun enumSchema(vararg values: String, description: String? = null, nullable: Boolean = false): GeminiSchema {
    require(values.isNotEmpty()) { "An enum schema needs at least one value" }
    return GeminiSchema(
        type = "STRING",
        description = description,
        nullable = nullable.orNullIfFalse(),
        enum = values.toList(),
        format = "enum"
    )
}

fun arraySchema(items: GeminiSchema, description: String? = null, nullable: Boolean = false): GeminiSchema =
    GeminiSchema(
        type = "ARRAY",
        description = description,
        nullable = nullable.orNullIfFalse(),
        items = items
    )

/**
 * An object schema built from [fields] in declaration order.
 *
 * Every non-nullable field is marked `required`, and `propertyOrdering` follows the order given —
 * Gemini generates fields in that order, so list them the way you want the model to think: reasons
 * before conclusions.
 */
fun objectSchema(vararg fields: Pair<String, GeminiSchema>, description: String? = null): GeminiSchema {
    require(fields.isNotEmpty()) { "An object schema needs at least one field" }
    val names = fields.map { it.first }
    require(names.distinct().size == names.size) { "Duplicate field names in object schema: $names" }

    return GeminiSchema(
        type = "OBJECT",
        description = description,
        properties = fields.toMap(LinkedHashMap()),
        required = fields.filter { it.second.nullable != true }.map { it.first },
        propertyOrdering = names
    )
}

/** Keeps `nullable: false` out of the payload, since absent means the same thing. */
private fun Boolean.orNullIfFalse(): Boolean? = if (this) true else null

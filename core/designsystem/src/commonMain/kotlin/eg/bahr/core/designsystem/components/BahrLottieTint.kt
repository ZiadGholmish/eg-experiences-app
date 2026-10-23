package eg.bahr.core.designsystem.components

import androidx.compose.ui.graphics.Color
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

/*
 * The theme's colours poured into a Lottie file (M3-M8). Each fill and stroke in the shipped files is
 * named after the colour role it takes (`bahr.primary`, `bahr.surface`, …) and holds the light theme's
 * colour, so the file looks right on its own; at load the role's colour from the current theme replaces
 * it, so dark and HighContrast follow.
 *
 * Why the JSON and not Compottie's dynamic properties: with dynamic properties the player copies the
 * composition and builds its painter asynchronously every time the screen is entered, a blank frame
 * each visit. A re-tinted file is parsed once per theme and then comes from the player's cache.
 */

/** A fill or stroke's name prefix that marks the colour role it takes. */
internal const val LOTTIE_ROLE_PREFIX = "bahr."

private const val TYPE_FILL = "fl"
private const val TYPE_STROKE = "st"

/**
 * [json] with every role-named fill and stroke set to [roles]' colour for that role (key: the role
 * after [LOTTIE_ROLE_PREFIX]). A role [roles] does not list keeps the file's colour. Static colours
 * only: a role-named colour is never animated in the shipped files.
 */
internal fun tintLottieJson(
    json: String,
    roles: Map<String, Color>,
): String = Json.encodeToString(JsonElement.serializer(), tint(Json.parseToJsonElement(json), roles))

private fun tint(
    element: JsonElement,
    roles: Map<String, Color>,
): JsonElement =
    when (element) {
        is JsonArray -> JsonArray(element.map { tint(it, roles) })
        is JsonObject -> {
            val color = roleColor(element, roles)
            JsonObject(
                element.mapValues { (key, value) ->
                    if (key == "c" && color != null) colorProperty(color) else tint(value, roles)
                },
            )
        }
        else -> element
    }

private fun roleColor(
    shape: JsonObject,
    roles: Map<String, Color>,
): Color? {
    val type = (shape["ty"] as? JsonPrimitive)?.takeIf { it.isString }?.content
    if (type != TYPE_FILL && type != TYPE_STROKE) return null
    val name = shape["nm"]?.jsonPrimitive?.content ?: return null
    if (!name.startsWith(LOTTIE_ROLE_PREFIX)) return null
    return roles[name.removePrefix(LOTTIE_ROLE_PREFIX)]
}

/** A Lottie static colour property: `{"a":0,"k":[r,g,b,a]}`, channels 0..1. */
private fun colorProperty(color: Color): JsonObject =
    JsonObject(
        mapOf(
            "a" to JsonPrimitive(0),
            "k" to JsonArray(listOf(color.red, color.green, color.blue, color.alpha).map { JsonPrimitive(it) }),
        ),
    )

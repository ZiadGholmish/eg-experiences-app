package eg.bahr.core.designsystem.components

import androidx.compose.ui.graphics.Color
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

/** M3-M8: the theme's colours go into the role-named fills and strokes, and nowhere else. */
class BahrLottieTintTest {
    private val file =
        """
        {"v":"5.9.0","layers":[{"nm":"Disc","shapes":[{"ty":"gr","it":[
          {"ty":"el","nm":"Ellipse"},
          {"ty":"fl","nm":"bahr.primaryContainer","c":{"a":0,"k":[0.9,0.9,0.9,1],"ix":4}},
          {"ty":"st","nm":"bahr.primary","c":{"a":0,"k":[0.1,0.5,0.5,1]}},
          {"ty":"fl","nm":"Fill 1","c":{"a":0,"k":[1,0,0,1]}},
          {"ty":"fl","nm":"bahr.unknownRole","c":{"a":0,"k":[0,1,0,1]}}
        ]}]}]}
        """.trimIndent()

    @Test
    fun `role-named fills and strokes take the theme's colour`() {
        val items = items(tintLottieJson(file, mapOf("primaryContainer" to Color.Black, "primary" to Color.White)))
        assertEquals(listOf(0f, 0f, 0f, 1f), color(items[1]))
        assertEquals(listOf(1f, 1f, 1f, 1f), color(items[2]))
    }

    @Test
    fun `other fills and unknown roles and the rest of the file are left alone`() {
        val items = items(tintLottieJson(file, mapOf("primary" to Color.White)))
        assertEquals(listOf(.9f, .9f, .9f, 1f), color(items[1]))
        assertEquals(listOf(1f, 0f, 0f, 1f), color(items[3]))
        assertEquals(listOf(0f, 1f, 0f, 1f), color(items[4]))
        assertEquals("Ellipse", items[0].jsonObject["nm"]!!.jsonPrimitive.content)
    }

    private fun items(json: String): JsonArray =
        Json
            .parseToJsonElement(json)
            .jsonObject["layers"]!!
            .jsonArray[0]
            .jsonObject["shapes"]!!
            .jsonArray[0]
            .jsonObject["it"]!!
            .jsonArray

    private fun color(item: kotlinx.serialization.json.JsonElement): List<Float> =
        (item as JsonObject)["c"]!!.jsonObject["k"]!!.jsonArray.map { it.jsonPrimitive.float }
}

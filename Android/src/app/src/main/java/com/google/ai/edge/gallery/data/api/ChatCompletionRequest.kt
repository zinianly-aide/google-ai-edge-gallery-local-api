/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.ai.edge.gallery.data.api

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Chat completion request (compatible with OpenAI API format)
 */
@Serializable
data class ChatCompletionRequest(
    val model: String = "",
    val messages: List<ChatMessage>,
    val temperature: Double? = null,
    val max_tokens: Int? = null,
    val top_p: Double? = null,
    val top_k: Int? = null,
    val accelerator: String? = null,
    val vision_accelerator: String? = null,
    val stream: Boolean = false,
    val stop: List<String>? = null
)

/**
 * Chat message in conversation.
 *
 * OpenAI clients may send content either as a legacy string or as an array of
 * content parts. The API currently supports text parts and normalizes both
 * representations to a String before inference.
 */
@Serializable
data class ChatMessage(
    val role: String,  // "user", "assistant", "system"
    @Serializable(with = ChatMessageContentSerializer::class)
    val content: String
)

/**
 * Accepts both OpenAI message content representations while keeping the
 * internal ChatMessage model string-based for the existing inference path.
 */
object ChatMessageContentSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("ChatMessageContent", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String {
        val jsonDecoder = decoder as? JsonDecoder ?: return decoder.decodeString()
        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonPrimitive -> decodeStringContent(element)
            is JsonArray -> element.joinToString(separator = "") { decodeTextPart(it) }
            else -> throw invalidContent()
        }
    }

    override fun serialize(encoder: Encoder, value: String) {
        // Preserve the existing API response shape: content is serialized as a string.
        encoder.encodeString(value)
    }

    private fun decodeStringContent(element: JsonPrimitive): String {
        if (!element.isString) {
            throw invalidContent()
        }
        return element.content
    }

    private fun decodeTextPart(element: kotlinx.serialization.json.JsonElement): String {
        val part = element as? JsonObject
            ?: throw SerializationException("Message content array items must be objects")

        val typeElement = part["type"] as? JsonPrimitive
        if (typeElement == null || !typeElement.isString) {
            throw SerializationException("Message content part must include a string 'type'")
        }

        val type = typeElement.content
        if (type != "text") {
            throw SerializationException(
                "Unsupported message content type '$type'. Only 'text' content parts are supported."
            )
        }

        val textElement = part["text"] as? JsonPrimitive
        if (textElement == null || !textElement.isString) {
            throw SerializationException("Text content part must include a string 'text'")
        }
        return textElement.content
    }

    private fun invalidContent(): SerializationException =
        SerializationException(
            "Message content must be a string or an array of text content parts"
        )
}

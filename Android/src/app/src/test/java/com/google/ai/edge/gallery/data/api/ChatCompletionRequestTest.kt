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

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatCompletionRequestTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun decodesLegacyStringContent() {
        val request = json.decodeFromString<ChatCompletionRequest>(
            """{"model":"gemma","messages":[{"role":"user","content":"hello"}]}""".replace("\\\"", "\"")
        )

        assertEquals("hello", request.messages.single().content)
    }

    @Test
    fun decodesTextContentArray() {
        val request = json.decodeFromString<ChatCompletionRequest>(
            """{"model":"gemma","messages":[{"role":"user","content":[{"type":"text","text":"hello"}]}]}""".replace("\\\"", "\"")
        )

        assertEquals("hello", request.messages.single().content)
    }

    @Test
    fun decodesMultipleMessagesAndConcatenatesTextParts() {
        val request = json.decodeFromString<ChatCompletionRequest>(
            """{"model":"gemma","messages":[{"role":"system","content":[{"type":"text","text":"Return JSON "},{"type":"text","text":"with text"}]},{"role":"user","content":"hello"}]}""".replace("\\\"", "\"")
        )

        assertEquals(2, request.messages.size)
        assertEquals("Return JSON with text", request.messages[0].content)
        assertEquals("hello", request.messages[1].content)
    }

    @Test
    fun rejectsUnsupportedContentType() {
        val error = assertThrows(SerializationException::class.java) {
            json.decodeFromString<ChatCompletionRequest>(
                """{"model":"gemma","messages":[{"role":"user","content":[{"type":"image_url","image_url":{"url":"https://example.invalid/image.png"}}]}]}""".replace("\\\"", "\"")
            )
        }

        assertTrue(error.message.orEmpty().contains("Unsupported message content type 'image_url'"))
    }

    @Test
    fun rejectsMalformedTextContentPart() {
        val error = assertThrows(SerializationException::class.java) {
            json.decodeFromString<ChatCompletionRequest>(
                """{"model":"gemma","messages":[{"role":"user","content":[{"type":"text"}]}]}""".replace("\\\"", "\"")
            )
        }

        assertTrue(error.message.orEmpty().contains("string 'text'"))
    }

    @Test
    fun serializesMessageContentAsLegacyString() {
        val encoded = json.encodeToString(
            ChatMessage.serializer(),
            ChatMessage(role = "assistant", content = "hello")
        )

        assertEquals("""{"role":"assistant","content":"hello"}""".replace("\\\"", "\""), encoded)
    }
}

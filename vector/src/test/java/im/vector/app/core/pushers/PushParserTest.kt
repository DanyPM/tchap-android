/*
 * Copyright (c) 2022 New Vector Ltd
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

package im.vector.app.core.pushers

import im.vector.app.core.pushers.model.PushData
import org.amshove.kluent.shouldBe
import org.amshove.kluent.shouldBeEqualTo
import org.junit.Test

class PushParserTest {
    private val validData = PushData(
            eventId = "\$anEventId",
            roomId = "!aRoomId:domain",
            unread = 1
    )

    private val emptyData = PushData(
            eventId = null,
            roomId = null,
            unread = null
    )

    @Test
    fun `test edge cases Firebase`() {
        val pushParser = PushParser()
        // Empty Json
        pushParser.parsePushDataFcm(emptyMap<String, Any>()) shouldBeEqualTo emptyData
        // Bad Json
        pushParser.parsePushDataFcm(FIREBASE_PUSH_DATA.mutate("room_id", 5)) shouldBe null
        pushParser.parsePushDataFcm(FIREBASE_PUSH_DATA.mutate("event_id", 5)) shouldBe null
        pushParser.parsePushDataFcm(FIREBASE_PUSH_DATA.mutate("unread", "str")) shouldBe null
        // Extra data
        pushParser.parsePushDataFcm(FIREBASE_PUSH_DATA.mutate("extra", 5)) shouldBeEqualTo validData
    }

    @Test
    fun `test edge cases UnifiedPush`() {
        val pushParser = PushParser()
        // Empty string
        pushParser.parsePushDataUnifiedPush("".toByteArray()) shouldBe null
        // Empty Json
        pushParser.parsePushDataUnifiedPush("{}".toByteArray()) shouldBeEqualTo emptyData
        // Bad Json
        pushParser.parsePushDataUnifiedPush("ABC".toByteArray()) shouldBe null
    }

    @Test
    fun `test UnifiedPush format`() {
        val pushParser = PushParser()
        pushParser.parsePushDataUnifiedPush(UNIFIED_PUSH_DATA.toByteArray()) shouldBeEqualTo validData
    }

    @Test
    fun `test Firebase format`() {
        val pushParser = PushParser()
        pushParser.parsePushDataFcm(FIREBASE_PUSH_DATA) shouldBeEqualTo validData
    }

    @Test
    fun `test empty roomId`() {
        val pushParser = PushParser()
        val expected = validData.copy(roomId = null)
        pushParser.parsePushDataFcm(FIREBASE_PUSH_DATA.mutate("room_id", "")) shouldBeEqualTo expected
        pushParser.parsePushDataUnifiedPush(UNIFIED_PUSH_DATA.replace("!aRoomId:domain", "").toByteArray()) shouldBeEqualTo expected
    }

    @Test
    fun `test invalid roomId`() {
        val pushParser = PushParser()
        val expected = validData.copy(roomId = null)
        pushParser.parsePushDataFcm(FIREBASE_PUSH_DATA.mutate("room_id", "aRoomId:domain")) shouldBeEqualTo expected
        pushParser.parsePushDataUnifiedPush(UNIFIED_PUSH_DATA.mutate("!aRoomId:domain", "aRoomId:domain")) shouldBeEqualTo expected
    }

    @Test
    fun `test empty eventId`() {
        val pushParser = PushParser()
        val expected = validData.copy(eventId = null)
        pushParser.parsePushDataFcm(FIREBASE_PUSH_DATA.mutate("event_id", "")) shouldBeEqualTo expected
        pushParser.parsePushDataUnifiedPush(UNIFIED_PUSH_DATA.mutate("\$anEventId", "")) shouldBeEqualTo expected
    }

    @Test
    fun `test invalid eventId`() {
        val pushParser = PushParser()
        val expected = validData.copy(eventId = null)
        pushParser.parsePushDataFcm(FIREBASE_PUSH_DATA.mutate("event_id", "anEventId")) shouldBeEqualTo expected
        pushParser.parsePushDataUnifiedPush(UNIFIED_PUSH_DATA.mutate("\$anEventId", "anEventId")) shouldBeEqualTo expected
    }

    companion object {
        private const val UNIFIED_PUSH_DATA =
                "{\"notification\":{\"event_id\":\"\$anEventId\",\"room_id\":\"!aRoomId:domain\",\"counts\":{\"unread\":1},\"prio\":\"high\"}}"
        private val FIREBASE_PUSH_DATA = mapOf(
                "event_id" to "\$anEventId",
                "room_id" to "!aRoomId:domain",
                "unread" to 1,
                "prio" to "high",
        )
    }
}

private fun <K, V> Map<K, V?>.mutate(key: K, value: V?): Map<K, V?> {
    return toMutableMap().apply { put(key, value) }
}

private fun String.mutate(oldValue: String, newValue: String): ByteArray {
    return replace(oldValue, newValue).toByteArray()
}

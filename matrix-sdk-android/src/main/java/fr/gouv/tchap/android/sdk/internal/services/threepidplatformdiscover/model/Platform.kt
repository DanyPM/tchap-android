/*
 * Copyright (c) 2021 New Vector Ltd
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

package fr.gouv.tchap.android.sdk.internal.services.threepidplatformdiscover.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Class to contain a Tchap platform configuration.
 */
@JsonClass(generateAdapter = true)
data class Platform(
        /**
         * The homeserver name.
         */
        @Json(name = "hs") val hs: String,

        /**
         * The publicly-accessible homeserver name.
         */
        @Json(name = "shadow_hs") val shadowHs: String? = null,

        /**
         * The new homeserver name.
         */
        @Json(name = "new_hs") val newHs: String? = null
)

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

package fr.gouv.tchap.features.roomprofile.settings.linkaccess.detail

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import im.vector.app.R
import im.vector.app.core.platform.VectorSharedAction
import im.vector.lib.strings.CommonStrings

sealed class TchapRoomLinkAccessBottomSheetSharedAction(
        @StringRes val titleRes: Int,
        @DrawableRes val iconResId: Int = 0
) : VectorSharedAction {

    data class CopyLink(val permalink: String) : TchapRoomLinkAccessBottomSheetSharedAction(
            CommonStrings.action_copy,
            R.drawable.ic_copy
    )

    data class ForwardLink(val permalink: String) : TchapRoomLinkAccessBottomSheetSharedAction(
            CommonStrings.tchap_room_settings_room_access_by_link_forward,
            com.android.dialer.dialpadview.R.drawable.quantum_ic_send_white_24
    )

    data class ShareLink(val permalink: String) : TchapRoomLinkAccessBottomSheetSharedAction(
            CommonStrings.tchap_room_settings_room_access_by_link_share,
            R.drawable.ic_share_link
    )
}

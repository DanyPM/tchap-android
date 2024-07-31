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

package im.vector.app.features.home

import com.airbnb.epoxy.EpoxyController
import fr.gouv.tchap.core.utils.TchapUtils
import im.vector.app.R
import im.vector.lib.strings.CommonStrings
import org.matrix.android.sdk.api.session.Session
import javax.inject.Inject

class HomeDrawerActionsController @Inject constructor(
        private val session: Session
) : EpoxyController() {

    interface Listener {
        fun inviteByEmail()
        fun openTermAndConditions()
        fun reportBug()
    }

    var listener: Listener? = null

    init {
        requestModelBuild()
    }

    override fun buildModels() {
        val host = this
        if (!TchapUtils.isExternalTchapUser(session.myUserId)) {
            homeDrawerActionItem {
                id("emailInvite")
                titleRes(CommonStrings.tchap_invite_to)
                iconRes(R.drawable.ic_tchap_invite)
                itemClickAction { host.listener?.inviteByEmail() }
            }
        }
        homeDrawerActionItem {
            id("openTAC")
            titleRes(CommonStrings.settings_app_term_conditions)
            iconRes(R.drawable.ic_tchap_term_conditions)
            itemClickAction { host.listener?.openTermAndConditions() }
        }
        homeDrawerActionItem {
            id("bugReport")
            titleRes(CommonStrings.send_bug_report)
            iconRes(R.drawable.ic_tchap_bug_report)
            itemClickAction { host.listener?.reportBug() }
        }
    }
}

/*
 * Copyright 2019 New Vector Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package im.vector.riotx.features.home.room.detail.readreceipts

import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import butterknife.BindView
import butterknife.ButterKnife
import com.airbnb.mvrx.MvRx
import com.airbnb.mvrx.args
import im.vector.riotx.R
import im.vector.riotx.core.di.ScreenComponent
import im.vector.riotx.core.platform.VectorBaseBottomSheetDialogFragment
import im.vector.riotx.features.home.room.detail.timeline.item.ReadReceiptData
import kotlinx.android.parcel.Parcelize
import kotlinx.android.synthetic.main.bottom_sheet_generic_list_with_title.*
import javax.inject.Inject

@Parcelize
data class DisplayReadReceiptArgs(
        val readReceipts: List<ReadReceiptData>
) : Parcelable

/**
 * Bottom sheet displaying list of read receipts for a given event ordered by descending timestamp
 */
class DisplayReadReceiptsBottomSheet : VectorBaseBottomSheetDialogFragment() {

    @Inject lateinit var epoxyController: DisplayReadReceiptsController

    @BindView(R.id.bottomSheetRecyclerView)
    lateinit var recyclerView: RecyclerView

    private val displayReadReceiptArgs: DisplayReadReceiptArgs by args()

    override fun injectWith(screenComponent: ScreenComponent) {
        screenComponent.inject(this)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.bottom_sheet_generic_list_with_title, container, false)
        ButterKnife.bind(this, view)
        return view
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        recyclerView.layoutManager = LinearLayoutManager(context, RecyclerView.VERTICAL, false)
        recyclerView.adapter = epoxyController.adapter
        bottomSheetTitle.text = getString(R.string.read_at)
        epoxyController.setData(displayReadReceiptArgs.readReceipts)
    }

    // we are not using state for this one as it's static, so no need to override invalidate()

    companion object {
        fun newInstance(readReceipts: List<ReadReceiptData>): DisplayReadReceiptsBottomSheet {
            val args = Bundle()
            val parcelableArgs = DisplayReadReceiptArgs(
                    readReceipts
            )
            args.putParcelable(MvRx.KEY_ARG, parcelableArgs)
            return DisplayReadReceiptsBottomSheet().apply { arguments = args }
        }
    }
}

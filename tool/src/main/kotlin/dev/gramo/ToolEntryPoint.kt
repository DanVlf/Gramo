package dev.gramo

import com.thelightphone.sdk.EntryPoint
import com.thelightphone.sdk.LightEntryPoint
import com.thelightphone.sdk.shared.LightServerData
import com.thelightphone.toolmanager.ClientLeafNode
import com.thelightphone.toolmanager.ClientToolManifest
import com.thelightphone.toolmanager.FileBrowserSpec
import kotlinx.coroutines.flow.StateFlow

@EntryPoint
object ToolEntryPoint : LightEntryPoint {

    override fun getToolManagerManifest(): ClientToolManifest = ClientToolManifest(
        title = "Gramo",
        roots = listOf(
            ClientLeafNode(
                FileBrowserSpec(
                    label = "Upload Archives",
                    path = UPLOADS_PATH,
                    headerText =
                        "Drop Google Takeout .zip files (or any zip with music) here, " +
                            "then open Gramo and import them.",
                ),
            ),
        ),
    )

    override suspend fun onToolCreate(
        serverData: StateFlow<LightServerData?>,
    ) {
    }

    override suspend fun onPushNotification(
        data: ByteArray,
    ) {
    }

    const val UPLOADS_PATH = "incoming"
}

package com.apolloauto.player.usb

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.apolloauto.player.ApolloApp
import com.apolloauto.player.companion.CompanionVoice
import kotlinx.coroutines.launch

class UsbAttachReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action == Intent.ACTION_MEDIA_MOUNTED ||
            action == "android.hardware.usb.action.USB_DEVICE_ATTACHED"
        ) {
            val app = ApolloApp.instance
            app.themeStore.speak(CompanionVoice.usbInLine())
            app.libraryStore.setStatus(CompanionVoice.usbInLine())
            // Do not hold a BroadcastReceiver pending for a potentially long USB walk.
            app.appScope.launch {
                app.libraryScanCoordinator.scanDue()
            }
        }
    }
}

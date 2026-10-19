package com.iqoo.tweaker.service

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.iqoo.tweaker.core.RmsController
import com.iqoo.tweaker.core.ShizukuShell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class Quick144TileService : TileService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        if (!ShizukuShell.hasPermission()) {
            Toast.makeText(this, "Autorize o Shizuku no app primeiro!", Toast.LENGTH_LONG).show()
            return
        }

        val currentTile = qsTile ?: return
        val turnOn = currentTile.state != Tile.STATE_ACTIVE

        serviceScope.launch {
            val message = if (turnOn) {
                RmsController.enable144HzNative(applicationContext)
            } else {
                RmsController.disable144HzNative(applicationContext)
            }

            Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
            updateTileState()
        }
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        if (!ShizukuShell.hasPermission()) {
            tile.state = Tile.STATE_UNAVAILABLE
            tile.label = "144Hz Nativo"
            tile.subtitle = "Sem Shizuku"
            tile.updateTile()
            return
        }

        serviceScope.launch {
            val status = withContext(Dispatchers.IO) {
                RmsController.queryStatus(applicationContext, fastMode = true)
            }
            tile.state = if (status.isLocked144) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            tile.label = "144Hz Nativo"
            tile.subtitle = status.activeRate.replace("rate=", "") + " Hz"
            tile.updateTile()
        }
    }
}

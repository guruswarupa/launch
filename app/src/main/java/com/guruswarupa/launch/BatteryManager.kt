
package com.guruswarupa.launch

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.widget.TextView

class BatteryManager(private val context: Context) {

    fun updateBatteryInfo(batteryText: TextView) {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

        if (batteryIntent != null) {
            val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)

            val batteryPct = (level * 100 / scale.toFloat()).toInt()
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL

            val chargingText = if (isCharging) " ⚡" else ""
            batteryText.text = "Battery: $batteryPct%$chargingText"
        }
    }
}

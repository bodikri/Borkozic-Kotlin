package com.borkozic

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.borkozic.waypoint.CoordinatesReceived

class ActionsReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "ActionsReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.i(TAG, "Action received: $action")

        val activity = Intent(context, MapActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)

        if (action == "com.borkozic.COORDINATES_RECEIVED") {
            activity.putExtras(intent)
            activity.putExtra("launch", CoordinatesReceived::class.java)
        }
        if (action == "com.borkozic.CENTER_ON_COORDINATES") {
            activity.putExtras(intent)
        }
        context.startActivity(activity)
    }
}

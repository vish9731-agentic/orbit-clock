package dev.orbit.clock.core

import android.content.Context
import com.google.android.gms.wearable.*
import org.json.JSONObject

object Sync {
    fun push(context: Context, alarm: Alarm) {
        val request = PutDataMapRequest.create("/orbit/alarms/${alarm.id}").apply {
            dataMap.putString("record", Store.encode(alarm).toString())
        }.asPutDataRequest().setUrgent()
        Wearable.getDataClient(context).putDataItem(request).addOnFailureListener {
            context.getSharedPreferences("sync", 0).edit().putString("error", "Sync pending. Open Orbit Clock on both devices.").apply()
        }.addOnSuccessListener {
            context.getSharedPreferences("sync", 0).edit().remove("error").apply()
        }
    }
    fun refresh(context: Context) {
        Store(context).all().forEach { push(context, it) }
        Wearable.getDataClient(context).dataItems.addOnSuccessListener { items ->
            try { items.forEach { accept(context, it) } } finally { items.release() }
        }
    }
    fun accept(context: Context, item: DataItem) {
        if (item.uri.path?.startsWith("/orbit/alarms/") != true) return
        val raw = DataMapItem.fromDataItem(item).dataMap.getString("record") ?: return
        runCatching { Store(context).merge(Store.decode(JSONObject(raw))) }
    }
}
class SyncService : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        events.forEach { if (it.type == DataEvent.TYPE_CHANGED) Sync.accept(this, it.dataItem) }
    }
}

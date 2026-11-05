package app.glass.music

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class HomeWidget:AppWidgetProvider(){
    override fun onUpdate(context:Context,manager:AppWidgetManager,ids:IntArray){
        for(id in ids){
            val pending=PendingIntent.getActivity(context,id,Intent(context,MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val views=RemoteViews(context.packageName,R.layout.home_widget)
            views.setOnClickPendingIntent(R.id.widget_root,pending)
            manager.updateAppWidget(id,views)
        }
    }
}

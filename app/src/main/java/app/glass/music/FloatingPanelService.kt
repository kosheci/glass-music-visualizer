package app.glass.music

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager

/** Floating UI is the real-time alternative to static home-screen RemoteViews. */
class FloatingPanelService : Service() {
    companion object { const val ACTION_STOP="app.glass.music.STOP_FLOATING" }
    private var window: WindowManager? = null
    private var view: GlassSpectrumView? = null
    private var params: WindowManager.LayoutParams? = null
    private val visualizer = VisualizerInput()
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel("glass_panel", "Живой виджет", NotificationManager.IMPORTANCE_LOW)
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        val stopIntent=Intent(this,FloatingPanelService::class.java).setAction(ACTION_STOP)
        val stop=PendingIntent.getService(this,1,stopIntent,PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notif=Notification.Builder(this,"glass_panel")
            .setContentTitle("Glass Music · визуализация")
            .setContentText("Живая панель активна")
            .setSmallIcon(R.drawable.ic_launcher)
            .addAction(Notification.Action.Builder(null,"Закрыть",stop).build())
            .build()
        if(Build.VERSION.SDK_INT>=34)startForeground(101,notif,ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(101,notif)
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if(intent?.action==ACTION_STOP){stopSelf();return START_NOT_STICKY}
        if(!Settings.canDrawOverlays(this)){stopSelf();return START_NOT_STICKY}
        if(view==null){
            window=getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val density=resources.displayMetrics.density
            val screenWidth=resources.displayMetrics.widthPixels
            val width = (screenWidth - (24*density).toInt()).coerceAtMost((420*density).toInt())
            val height = (width * 250f / 360f).toInt()
            val lp=WindowManager.LayoutParams(width,height,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT)
            lp.gravity=Gravity.TOP or Gravity.LEFT
            lp.x=((screenWidth-width)/2).coerceAtLeast(0)
            lp.y=(110*density).toInt()
            params=lp
            val v=GlassSpectrumView(this,true)
            v.onDrag={dx,dy->
                params?.let{it.x=(it.x+dx.toInt()).coerceAtLeast(0);it.y=(it.y+dy.toInt()).coerceAtLeast(0)}
                try{params?.let { window?.updateViewLayout(v,it) }}catch(_:Exception){}
            }
            v.onClose={stopSelf()}
            try{window?.addView(v,lp);view=v}catch(_:Exception){stopSelf()}
            visualizer.start()
        }
        return START_NOT_STICKY
    }
    override fun onDestroy(){
        visualizer.stop()
        view?.let{try{window?.removeView(it)}catch(_:Exception){}}
        view=null
        super.onDestroy()
    }
}

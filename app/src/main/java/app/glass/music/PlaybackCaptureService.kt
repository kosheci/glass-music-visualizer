package app.glass.music

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.*
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.os.Handler
import android.os.Looper

/** Android 10+ public playback capture, opt-in. Works only if the playback app allows capture. */
class PlaybackCaptureService: Service() {
    companion object {
        const val ACTION_STOP="app.glass.music.STOP_CAPTURE"
        const val EXTRA_CODE="result_code"
        const val EXTRA_DATA="result_data"
    }
    private var projection: MediaProjection? = null
    private var record: AudioRecord? = null
    @Volatile private var running=false
    private var worker: Thread? = null
    override fun onBind(intent:Intent?):IBinder?=null
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        if(intent?.action==ACTION_STOP){stopSelf();return START_NOT_STICKY}
        val code=intent?.getIntExtra(EXTRA_CODE,Activity.RESULT_CANCELED)?:Activity.RESULT_CANCELED
        @Suppress("DEPRECATION")
        val data=if(Build.VERSION.SDK_INT>=33)intent?.getParcelableExtra(EXTRA_DATA,Intent::class.java)
            else intent?.getParcelableExtra(EXTRA_DATA)
        if(code!=Activity.RESULT_OK||data==null){stopSelf();return START_NOT_STICKY}
        val channel=NotificationChannel("glass_capture","Захват аудио",NotificationManager.IMPORTANCE_LOW)
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        val stop=PendingIntent.getService(this,3,Intent(this,PlaybackCaptureService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notif=Notification.Builder(this,"glass_capture")
            .setSmallIcon(R.drawable.ic_launcher).setContentTitle("Glass Music · захват аудио")
            .setContentText("Обрабатывается разрешённый аудиопоток · остановить можно здесь")
            .addAction(Notification.Action.Builder(null,"Остановить",stop).build()).build()
        if(Build.VERSION.SDK_INT>=34)startForeground(102,notif,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        else startForeground(102,notif)
        try{
            val manager=getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projection=manager.getMediaProjection(code,data)
            projection?.registerCallback(object:MediaProjection.Callback(){override fun onStop(){stopSelf()}},Handler(Looper.getMainLooper()))
            val config=AudioPlaybackCaptureConfiguration.Builder(projection!!)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .build()
            val format=AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(44100).setChannelMask(AudioFormat.CHANNEL_IN_MONO).build()
            val min=AudioRecord.getMinBufferSize(44100,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT)
            record=AudioRecord.Builder().setAudioFormat(format).setBufferSizeInBytes(maxOf(8192,min))
                .setAudioPlaybackCaptureConfig(config).build()
            if(record?.state!=AudioRecord.STATE_INITIALIZED)throw IllegalStateException("AudioRecord not initialized")
            running=true
            SpectrumBus.captureActive=true
            record?.startRecording()
            worker=Thread({
                val samples=ShortArray(2048)
                while(running){
                    val count=try{record?.read(samples,0,samples.size,AudioRecord.READ_BLOCKING)?:-1}catch(_:Exception){-1}
                    if(count>=1024)SpectrumBus.pcm(samples,count,44100)
                    if(count<0)break
                }
            },"GlassFFT-AudioCapture").also{it.start()}
        }catch(_:Exception){stopSelf()}
        return START_NOT_STICKY
    }
    override fun onDestroy(){
        running=false
        SpectrumBus.captureActive=false
        try{record?.stop()}catch(_:Exception){}
        worker?.interrupt()
        try{record?.release()}catch(_:Exception){}
        record=null
        try{projection?.stop()}catch(_:Exception){}
        projection=null
        super.onDestroy()
    }
}

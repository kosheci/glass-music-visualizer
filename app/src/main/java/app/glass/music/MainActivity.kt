package app.glass.music

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private val visualizer=VisualizerInput()
    private val projectionRequest=410
    private val permissionsRequest=411
    private fun dp(value:Int)=(value*resources.displayMetrics.density).toInt()
    private fun bg(color:Int,radius:Float):GradientDrawable=GradientDrawable().apply{
        setColor(color);cornerRadius=dp(radius.toInt()).toFloat()
        setStroke(dp(1),0x68FFFFFF)
    }
    private fun label(s:String,size:Float=14f):TextView=TextView(this).apply{
        text=s;textSize=size;setTextColor(Color.WHITE)
    }
    private fun button(text:String,onClick:()->Unit):Button=Button(this).apply{
        this.text=text;isAllCaps=false;setTextColor(Color.WHITE);textSize=14f
        background=bg(0x594F4A80,17f)
        setOnClickListener{onClick()}
    }
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        window.statusBarColor=0xFF22213B.toInt()
        window.navigationBarColor=0xFF22213B.toInt()
        val scroll=ScrollView(this)
        scroll.setBackgroundColor(0xFF22213B.toInt())
        val layout=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL
            setPadding(dp(17),dp(26),dp(17),dp(24))
        }
        scroll.addView(layout)
        val heading=label("GLASS MUSIC",25f).apply{setTypeface(typeface,1)}
        layout.addView(heading)
        layout.addView(label("Живой спектр · Яндекс Музыка · Android",13f).apply{setTextColor(0xFFCAC5E6.toInt())})
        val visual=GlassSpectrumView(this)
        layout.addView(visual,LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(250)).apply{topMargin=dp(24);bottomMargin=dp(18)})
        layout.addView(label("36 частотных полос анализируют реальный звук. Если Android блокирует захват — столбики останутся неподвижными.",13f).apply{
            setTextColor(0xFFE1DAF4.toInt());setPadding(0,0,0,dp(18))
        })
        fun addButton(title:String,action:()->Unit){
            val b=button(title,action)
            layout.addView(b,LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(54)).apply{bottomMargin=dp(10)})
        }
        addButton("1. Разрешить доступ к аудио") {requestAudioPermissions()}
        addButton("2. Разрешить управление плеером") {startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))}
        addButton("3. Разрешить панель поверх экрана") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:$packageName")))
        }
        addButton("▶  Запустить плавающий виджет") {
            if(!Settings.canDrawOverlays(this)) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:$packageName")))
            }else{
                startForegroundService(Intent(this,FloatingPanelService::class.java))
            }
        }
        addButton("◉  Захват воспроизведения (если нет сигнала)") {
            if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestAudioPermissions()}
            else{
                val manager=getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                startActivityForResult(manager.createScreenCaptureIntent(),projectionRequest)
            }
        }
        addButton("■  Остановить захват и панель") {
            stopService(Intent(this,PlaybackCaptureService::class.java))
            stopService(Intent(this,FloatingPanelService::class.java))
        }
        addButton("Открыть Яндекс Музыку"){
            val intent=packageManager.getLaunchIntentForPackage("ru.yandex.music")
            if(intent!=null)startActivity(intent)
        }
        val note=label("Установите виджет «Glass Music» на домашний экран: он открывает эту панель. Стандартный AppWidget не поддерживает непрерывную пользовательскую FFT-анимацию. При запуске плавающей панели доступ к звуку работает, только если устройство/плеер его разрешают.",12f)
        note.setTextColor(0xFFAFAAC6.toInt());note.setPadding(0,dp(8),0,dp(16))
        layout.addView(note)
        setContentView(scroll)
        // Permissions are requested only after the user taps the corresponding button.
    }
    private fun requestAudioPermissions(){
        val permissions=mutableListOf<String>()
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)
            permissions.add(Manifest.permission.RECORD_AUDIO)
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        if(permissions.isNotEmpty())requestPermissions(permissions.toTypedArray(),permissionsRequest)
        else visualizer.start()
    }
    override fun onResume(){super.onResume();if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)visualizer.start()}
    override fun onPause(){visualizer.stop();super.onPause()}
    override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<out String>,grantResults:IntArray){
        super.onRequestPermissionsResult(requestCode,permissions,grantResults)
        if(requestCode==permissionsRequest&&checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)visualizer.start()
    }
    @Deprecated("Use Activity Result API in production")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode==projectionRequest&&resultCode==RESULT_OK&&data!=null){
            val intent=Intent(this,PlaybackCaptureService::class.java)
            intent.putExtra(PlaybackCaptureService.EXTRA_CODE,resultCode)
            intent.putExtra(PlaybackCaptureService.EXTRA_DATA,data)
            startForegroundService(intent)
        }
    }
}

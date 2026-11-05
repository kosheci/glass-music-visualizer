package app.glass.music

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.text.TextPaint
import android.text.TextUtils
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View
import kotlin.math.*

/** 36 real FFT frequency bands, rendered as an acrylic floating music card at screen refresh rate. */
class GlassSpectrumView(context: Context, private val floating: Boolean = false) : View(context), Choreographer.FrameCallback {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val t = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val session = MusicSession(context)
    private val display = FloatArray(SpectrumBus.BAR_COUNT)
    private val tops = FloatArray(SpectrumBus.BAR_COUNT)
    private var attached = false
    private var downX = 0f
    private var downY = 0f
    private var lastRawX = 0f
    private var lastRawY = 0f
    private var dragging = false
    var onDrag: ((Float, Float) -> Unit)? = null
    var onClose: (() -> Unit)? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        attached = true
        Choreographer.getInstance().postFrameCallback(this)
    }
    override fun onDetachedFromWindow() {
        attached = false
        Choreographer.getInstance().removeFrameCallback(this)
        super.onDetachedFromWindow()
    }
    override fun doFrame(frameTimeNanos: Long) {
        if (attached) {
            invalidate()
            Choreographer.getInstance().postFrameCallback(this)
        }
    }
    private fun fill(color: Int) { p.reset(); p.isAntiAlias = true; p.color = color; p.style = Paint.Style.FILL }
    private fun text(canvas: Canvas, text: String, x: Float, y: Float, size: Float, color: Int, bold: Boolean = false, maxWidth: Float = 1000f) {
        t.reset(); t.isAntiAlias = true; t.color = color; t.textSize = size
        t.typeface = Typeface.create(if (bold) "sans-serif-medium" else "sans-serif", Typeface.NORMAL)
        val label = TextUtils.ellipsize(text, t, maxWidth, TextUtils.TruncateAt.END).toString()
        canvas.drawText(label, x, y, t)
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return
        session.refresh()
        val sx = width / 360f; val sy = height / 250f
        canvas.save(); canvas.scale(sx, sy)
        val card = RectF(1f,1f,359f,249f)
        fill(Color.rgb(54,51,83))
        p.shader = LinearGradient(0f, 0f, 360f, 250f, intArrayOf(0xE955537E.toInt(),0xE77F668F.toInt(),0xE8355168.toInt()),null,Shader.TileMode.CLAMP)
        canvas.drawRoundRect(card,31f,31f,p)
        p.shader=null
        fill(0x40F4C4F4); p.shader=RadialGradient(15f,0f,230f,0x55DAACFA,0x002C2954,Shader.TileMode.CLAMP)
        canvas.drawRoundRect(card,31f,31f,p);p.shader=null
        fill(0xA7FFFFFF);p.style=Paint.Style.STROKE;p.strokeWidth=1.3f
        canvas.drawRoundRect(card,31f,31f,p);p.style=Paint.Style.FILL
        fill(0x38FFFFFF);canvas.drawRoundRect(RectF(8f,6f,352f,9f),2f,2f,p)
        // Brand header + yellow glyph
        fill(0xFFFFD45E.toInt());canvas.drawRoundRect(RectF(19f,17f,42f,40f),8f,8f,p)
        fill(0xFF38304C.toInt())
        for (i in 0..7) {
            val a=i*Math.PI/4
            canvas.drawRoundRect(RectF(29.5f + cos(a).toFloat()*6-1.2f, 27.5f + sin(a).toFloat()*6-3.1f,
                29.5f + cos(a).toFloat()*6+1.2f, 27.5f + sin(a).toFloat()*6+3.1f),1.2f,1.2f,p)
        }
        text(canvas,"Яндекс Музыка",51f,33f,13f,Color.WHITE,true)
        val live = SystemClock.elapsedRealtime()-SpectrumBus.lastAudioMs < 1600
        fill(if (live) 0xFFB2F5BC.toInt() else 0xFFFFDB97.toInt())
        canvas.drawCircle(267f,28f,3f,p)
        val signalText = if (floating) { if (live) "LIVE" else "ТИШИНА" } else { if (live) "LIVE FFT" else "НЕТ СИГНАЛА" }
        text(canvas,signalText,277f,32f,9f,0xEFFFFFFF.toInt(),true,if(floating) 49f else 75f)
        if(floating){text(canvas,"×",335f,33f,21f,0xCBFFFFFF.toInt(),true)}
        // Album cover: native artwork if the session exposes it, otherwise glass-art gradient.
        val cover = RectF(20f,52f,98f,125f)
        canvas.save()
        val coverClip=Path().apply{addRoundRect(cover,16f,16f,Path.Direction.CW)}
        canvas.clipPath(coverClip)
        val bmp=session.artwork
        if(bmp!=null && !bmp.isRecycled) {
            fill(Color.WHITE)
            val src=Rect(0,0,bmp.width,bmp.height)
            canvas.drawBitmap(bmp,src,cover,p)
        } else {
            fill(0xFF7E78BE.toInt())
            p.shader=LinearGradient(20f,52f,100f,125f, intArrayOf(0xFFDD9BB6.toInt(),0xFF8579BF.toInt(),0xFF3C315F.toInt()),null,Shader.TileMode.CLAMP)
            canvas.drawRect(cover,p);p.shader=null
            fill(0x81FFC8D5.toInt());canvas.drawCircle(76f,86f,26f,p)
            fill(0x547B6BDF);canvas.drawCircle(82f,99f,22f,p)
        }
        canvas.restore()
        text(canvas,"СЕЙЧАС ИГРАЕТ",111f,64f,9f,0xBFFFFFFF.toInt(),true)
        text(canvas,session.title,111f,87f,20f,Color.WHITE,true,229f)
        text(canvas,session.artist,111f,108f,12f,0xCFFFFFFF.toInt(),false,229f)
        // Live FFT bars. Silent input yields almost-flat bars; no fake or randomized movement.
        val target = SpectrumBus.levels
        val baseline = 203f
        val barsTop = 139f
        val step = 8.7f
        val w = 5.3f
        for(i in display.indices) {
            val x=23f+i*step
            val want=target.getOrElse(i){0f}
            display[i] += (want-display[i]) * (if(want>display[i]) .43f else .10f)
            tops[i] = max(display[i],tops[i]-.022f)
            val heightBar = 2.0f + display[i].coerceIn(0f,1f)*60f
            val y=baseline-heightBar
            fill(0xFFFFFFFF.toInt())
            p.shader=LinearGradient(x,barsTop,x,baseline, intArrayOf(0xFFEACBFF.toInt(),0xFFFFA6BC.toInt(),0xFFFFD86C.toInt()),null,Shader.TileMode.CLAMP)
            canvas.drawRoundRect(RectF(x,y,x+w,baseline),3f,3f,p)
            p.shader=null
            fill(0xB3FFF8FE.toInt())
            val peakY=baseline-2f-tops[i]*60f
            canvas.drawRoundRect(RectF(x,peakY,x+w,peakY+2f),1f,1f,p)
        }
        // Transparent dividing line and transport buttons.
        fill(0x48FFFFFF);canvas.drawRoundRect(RectF(19f,213f,341f,214f),1f,1f,p)
        text(canvas,"←",121f,239f,22f,Color.WHITE,true)
        fill(0xF6FFFFFF.toInt());canvas.drawCircle(183f,232f,15f,p)
        fill(0xFF454260.toInt())
        if(session.playing){
            canvas.drawRoundRect(RectF(177f,224f,181f,240f),1f,1f,p)
            canvas.drawRoundRect(RectF(185f,224f,189f,240f),1f,1f,p)
        } else {
            val tri=Path().apply{moveTo(180f,223f);lineTo(180f,241f);lineTo(192f,232f);close()}
            canvas.drawPath(tri,p)
        }
        text(canvas,"→",230f,239f,22f,Color.WHITE,true)
        canvas.restore()
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x=event.x*360f/width.coerceAtLeast(1)
        val y=event.y*250f/height.coerceAtLeast(1)
        when(event.actionMasked){
            MotionEvent.ACTION_DOWN->{downX=x;downY=y;lastRawX=event.rawX;lastRawY=event.rawY;dragging=false;return true}
            MotionEvent.ACTION_MOVE->{
                if(floating && onDrag!=null){
                    if(abs(event.rawX-lastRawX)>2f||abs(event.rawY-lastRawY)>2f){
                        if(hypot((x-downX).toDouble(),(y-downY).toDouble())>7.0)dragging=true
                        if(dragging)onDrag?.invoke(event.rawX-lastRawX,event.rawY-lastRawY)
                    }
                }
                lastRawX=event.rawX;lastRawY=event.rawY
                return true
            }
            MotionEvent.ACTION_UP->{
                if(!dragging){
                    when {
                        floating && x>320f && y<47f -> onClose?.invoke()
                        y>214f && x in 106f..158f -> session.previous()
                        y>214f && x in 160f..209f -> session.toggle()
                        y>214f && x in 212f..270f -> session.next()
                    }
                }
                return true
            }
            MotionEvent.ACTION_CANCEL->return true
        }
        return super.onTouchEvent(event)
    }
}

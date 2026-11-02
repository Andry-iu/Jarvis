package it.jarviswake

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable

/** Small monochrome line symbols; no bitmaps, shading, animation or text changes. */
internal class FlatMenuIcon(private val label:String,private val size:Int):Drawable() {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=Color.rgb(116,190,200);style=Paint.Style.STROKE;strokeWidth=1.65f;strokeCap=Paint.Cap.ROUND;strokeJoin=Paint.Join.ROUND}
    override fun getIntrinsicWidth()=size
    override fun getIntrinsicHeight()=size
    override fun draw(c:Canvas) {
        c.save();c.translate(bounds.left.toFloat(),bounds.top.toFloat());c.scale(bounds.width()/24f,bounds.height()/24f)
        val l=label.lowercase()
        when {
            l.contains("indietro") -> {c.drawLine(19f,12f,5f,12f,paint);c.drawLine(5f,12f,11f,6f,paint);c.drawLine(5f,12f,11f,18f,paint)}
            l.contains("preferit") -> {val path=Path();for(i in 0..9) {val a=Math.PI*(i/5.0)-Math.PI/2;val r=if(i%2==0) 9.0 else 4.2;val x=(12+r*kotlin.math.cos(a)).toFloat();val y=(12+r*kotlin.math.sin(a)).toFloat();if(i==0)path.moveTo(x,y)else path.lineTo(x,y)};path.close();c.drawPath(path,paint)}
            l.contains("jarvis") || l.contains("ascolto") || l.contains("voce") -> {for(i in 0..4) {val h=listOf(4,9,15,9,4)[i];val x=4f+i*4;c.drawLine(x,12-h/2f,x,12+h/2f,paint)}}
            l.contains("configur") || l.contains("servizio") -> {for(i in 0..2) {val y=5f+i*7;c.drawLine(3f,y,21f,y,paint);c.drawCircle(if(i==1)8f else 16f,y,2f,paint)}}
            l.contains("cache") || l.contains("aggiorna") -> {c.drawArc(4f,4f,20f,20f,35f,285f,false,paint);c.drawLine(20f,4f,20f,10f,paint);c.drawLine(20f,10f,14f,10f,paint)}
            else -> {c.drawRoundRect(5f,3f,19f,21f,2f,2f,paint);c.drawLine(9f,8f,15f,8f,paint);c.drawLine(9f,12f,15f,12f,paint);c.drawLine(9f,16f,13f,16f,paint)}
        }
        c.restore()
    }
    override fun setAlpha(alpha:Int) {paint.alpha=alpha}
    override fun setColorFilter(filter:ColorFilter?) {paint.colorFilter=filter}
    @Deprecated("Drawable API") override fun getOpacity()=PixelFormat.TRANSLUCENT
}
internal data class MenuContextAction(val title:String,val action:()->Unit)

/** Flat HUD frame: solid fill, fine rules and two small cyan corner marks. */
internal class FlatTechPanel(private val density:Float):Drawable() {
    private val fill=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=Color.rgb(19,30,41)}
    private val line=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=Color.rgb(44,68,84);style=Paint.Style.STROKE;strokeWidth=density.coerceAtLeast(1f)}
    private val accent=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=Color.rgb(81,185,204);style=Paint.Style.STROKE;strokeWidth=density.coerceAtLeast(1f)}
    override fun draw(c:Canvas) {
        val r=android.graphics.RectF(bounds);r.inset(density,density)
        c.drawRoundRect(r,12*density,12*density,fill);c.drawRoundRect(r,12*density,12*density,line)
        c.drawLine(r.left+14*density,r.top,r.left+40*density,r.top,accent)
        c.drawLine(r.right-40*density,r.bottom,r.right-14*density,r.bottom,accent)
    }
    override fun setAlpha(alpha:Int) {fill.alpha=alpha;line.alpha=alpha;accent.alpha=alpha}
    override fun setColorFilter(filter:ColorFilter?) {fill.colorFilter=filter;line.colorFilter=filter;accent.colorFilter=filter}
    @Deprecated("Drawable API") override fun getOpacity()=PixelFormat.TRANSLUCENT
}

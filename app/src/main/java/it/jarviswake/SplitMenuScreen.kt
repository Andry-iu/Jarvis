package it.jarviswake

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.*

/** Controls and navigation above; independently scrollable contextual instructions below. */
internal class SplitMenuScreen(context:Context,title:String,instructions:String,back:(()->Unit)?=null):LinearLayout(context) {
    val controls=LinearLayout(context).apply { orientation=VERTICAL;setPadding(dp(16),dp(8),dp(16),dp(12)) }
    private val upper=LinearLayout(context).apply { orientation=VERTICAL }
    private var backControl:Button?=null
    private var buttonGroup:MenuButtonGrid?=null
    private val guideTitle=TextView(context)
    private val guideBody=TextView(context)
    private val lower=LinearLayout(context).apply { orientation=VERTICAL;setPadding(dp(20),dp(14),dp(20),dp(12)) }
    init {
        orientation=VERTICAL;setBackgroundColor(Color.rgb(9,19,29))
        val toolbar=LinearLayout(context).apply { orientation=HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(12),dp(4),dp(16),dp(4)) }
        toolbar.addView(TextView(context).apply {
            text=title;textSize=22f;setTextColor(Color.WHITE);setTypeface(null,Typeface.BOLD)
            setPadding(dp(12),dp(10),0,dp(10));isAccessibilityHeading=true
        },LayoutParams(0,-2,1f))
        upper.addView(toolbar)
        upper.addView(ScrollView(context).apply { isFillViewport=false;addView(controls);isFocusableInTouchMode=true },LayoutParams(-1,0,1f))
        addView(upper,LayoutParams(-1,0,0.65f))
        addView(View(context).apply { setBackgroundColor(Color.rgb(46,77,96)) },LayoutParams(-1,dp(2)))
        lower.setBackgroundColor(Color.rgb(18,35,49))
        guideTitle.apply { textSize=18f;setTextColor(Color.rgb(99,226,241));setTypeface(null,Typeface.BOLD);isAccessibilityHeading=true }
        guideBody.apply { textSize=16f;setTextColor(Color.rgb(229,239,246));setLineSpacing(dp(3).toFloat(),1f);setPadding(0,dp(8),0,0) }
        val guide=LinearLayout(context).apply { orientation=VERTICAL;addView(guideTitle);addView(guideBody) }
        lower.addView(ScrollView(context).apply { addView(guide) },LayoutParams(-1,-1))
        addView(lower,LayoutParams(-1,0,0.35f))
        if(back!=null) {
            backControl=makeButton(context.getString(R.string.menu_back),square=false) { back() }
            controls.addView(backControl,LayoutParams(-1,-2).apply { bottomMargin=dp(8) })
        }
        explain(context.getString(R.string.menu_instructions),instructions)
        setOnApplyWindowInsetsListener { v,insets ->
            val bars=insets.getInsets(WindowInsets.Type.systemBars())
            val keyboard=insets.getInsets(WindowInsets.Type.ime())
            v.setPadding(bars.left,bars.top,bars.right,maxOf(bars.bottom,keyboard.bottom))
            val open=insets.isVisible(WindowInsets.Type.ime())
            (upper.layoutParams as LayoutParams).apply { weight=if(open) 0.75f else 0.65f;upper.layoutParams=this }
            (lower.layoutParams as LayoutParams).apply { weight=if(open) 0.25f else 0.35f;lower.layoutParams=this }
            insets
        }
        isFocusableInTouchMode=true
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow();requestApplyInsets() }
    private fun dp(n:Int)=(n*resources.displayMetrics.density).toInt()
    fun explain(title:String,body:String) { guideTitle.text=title;guideBody.text=body }
    fun explain(body:String)=explain(context.getString(R.string.menu_instructions),body)
    fun describe(view:View,body:String) { view.setOnFocusChangeListener { _,focused -> if(focused) explain(body) } }
    fun text(value:String,size:Float=16f)=TextView(context).apply {
        text=value;textSize=size;setTextColor(Color.WHITE);setPadding(0,dp(8),0,dp(10));addControl(this)
    }
    fun addControl(view:View) {
        buttonGroup=null
        val before=backControl?.let { controls.indexOfChild(it) } ?: -1
        controls.addView(view,before,LayoutParams(-1,-2).apply { bottomMargin=dp(8) })
    }
    private fun makeButton(label:String,square:Boolean=true,action:()->Unit):Button =
        (if(square) MenuTileButton(context) else Button(context)).apply {
        text=label;textSize=18f;isAllCaps=false;isSingleLine=false;ellipsize=null;minHeight=dp(64);minimumHeight=dp(64)
        gravity=Gravity.CENTER;setPadding(dp(14),dp(12),dp(14),dp(12));setTextColor(Color.WHITE)
        val shape=GradientDrawable().apply { cornerRadius=dp(14).toFloat();setColor(Color.rgb(24,65,85));setStroke(dp(1),Color.rgb(51,119,142)) }
        background=RippleDrawable(ColorStateList.valueOf(Color.rgb(47,119,145)),shape,null)
        setOnClickListener { action() }
    }
    fun button(label:String,help:String?=null,action:()->Unit):Button {
        val button=makeButton(label) { if(help!=null) explain(label,help);action() }
        if(help!=null) describe(button,help)
        val group=buttonGroup ?: MenuButtonGrid(context).also { addControl(it);buttonGroup=it }
        group.addTile(button);return button
    }
    fun toggle(label:String,value:Boolean,help:String,change:(Boolean)->Unit)=Switch(context).apply {
        text=label;textSize=18f;setTextColor(Color.WHITE);minHeight=dp(64);setPadding(dp(8),dp(10),dp(8),dp(10))
        buttonTintList=ColorStateList.valueOf(Color.rgb(99,226,241));isChecked=value
        describe(this,help)
        setOnCheckedChangeListener { _,checked -> explain(label,help);change(checked) }
        addControl(this)
    }
    fun field(hintText:String,value:String,help:String,multiline:Boolean=false)=EditText(context).apply {
        hint=hintText;setText(value);textSize=18f;setTextColor(Color.WHITE);setHintTextColor(Color.rgb(160,183,198))
        minHeight=dp(64);setPadding(dp(12),dp(12),dp(12),dp(12));isSingleLine=!multiline
        contentDescription=hintText
        if(multiline) { inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE;minLines=4 }
        describe(this,help);addControl(this)
    }
    fun navigation(previous:String,back:()->Unit,next:String,forward:()->Unit) {
        backControl?.apply { text=previous;setOnClickListener { back() } }
        button(next,action=forward)
    }
}

/** A half-width action keeps its place even when it is the only item in the last row. */
private class MenuButtonGrid(context:Context):LinearLayout(context) {
    private var row:LinearLayout?=null
    private var emptySlot:View?=null
    private fun dp(n:Int)=(n*resources.displayMetrics.density).toInt()
    init { orientation=VERTICAL }
    fun addTile(button:Button) {
        val current=row
        if(current!=null) {
            current.removeView(emptySlot)
            current.addView(button,LayoutParams(0,ViewGroup.LayoutParams.MATCH_PARENT,1f))
            row=null;emptySlot=null
        } else {
            val next=LinearLayout(context).apply { orientation=HORIZONTAL }
            next.addView(button,LayoutParams(0,ViewGroup.LayoutParams.MATCH_PARENT,1f).apply { rightMargin=dp(8) })
            val spacer=View(context)
            next.addView(spacer,LayoutParams(0,0,1f))
            addView(next,LayoutParams(-1,-2).apply { bottomMargin=dp(8) })
            row=next;emptySlot=spacer
        }
    }
}

/** Square at normal font size; expand rather than clip a long or enlarged label. */
private class MenuTileButton(context:Context):Button(context) {
    override fun onMeasure(widthMeasureSpec:Int,heightMeasureSpec:Int) {
        super.onMeasure(widthMeasureSpec,heightMeasureSpec)
        if(MeasureSpec.getMode(heightMeasureSpec)!=MeasureSpec.EXACTLY) {
            val height=maxOf(measuredWidth,measuredHeight)
            super.onMeasure(widthMeasureSpec,MeasureSpec.makeMeasureSpec(height,MeasureSpec.EXACTLY))
        }
    }
}

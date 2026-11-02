package it.jarviswake

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.*

/** Full-height controls. Help is a separate modal window, opened only by its own question mark. */
internal class SplitMenuScreen(context:Context,title:String,@Suppress("UNUSED_PARAMETER") instructions:String,back:(()->Unit)?=null):LinearLayout(context) {
    val controls=LinearLayout(context).apply { orientation=VERTICAL;setPadding(dp(16),dp(8),dp(16),dp(12)) }
    private var backControl:Button?=null
    private var backCard:View?=null
    private var buttonGroup:MenuButtonGrid?=null
    private var feedbackView:TextView?=null
    private var helpDialog:AlertDialog?=null
    private var contextMenu:PopupMenu?=null
    val helpShowing:Boolean get()=helpDialog?.isShowing==true
    init {
        orientation=VERTICAL;setBackgroundColor(Color.rgb(13,18,24))
        addView(TextView(context).apply {
            text=title;textSize=25f;setTextColor(Color.rgb(237,242,246));typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL);letterSpacing=0.01f
            setPadding(dp(22),dp(22),dp(22),dp(20));isAccessibilityHeading=true
        },LayoutParams(-1,-2))
        addView(ScrollView(context).apply { addView(controls);isFocusableInTouchMode=true },LayoutParams(-1,0,1f))
        if(back!=null) {
            val b=makeButton(context.getString(R.string.menu_back),false) { back() };backControl=b
            backCard=card(b,false,MenuCommandHelp.text("Indietro"))
            controls.addView(backCard,LayoutParams(-1,-2).apply { bottomMargin=dp(8) })
        }
        setOnApplyWindowInsetsListener { v,insets ->
            val bars=insets.getInsets(WindowInsets.Type.systemBars());val keyboard=insets.getInsets(WindowInsets.Type.ime())
            v.setPadding(bars.left,bars.top,bars.right,maxOf(bars.bottom,keyboard.bottom));insets
        }
        isFocusableInTouchMode=true
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow();requestApplyInsets() }
    override fun onDetachedFromWindow() { contextMenu?.dismiss();helpDialog?.dismiss();super.onDetachedFromWindow() }
    private fun dp(n:Int)=(n*resources.displayMetrics.density).toInt()
    /** Action feedback is visible status, never an automatically opened help panel. */
    fun feedback(title:String,body:String) {
        val label=feedbackView ?: TextView(context).apply {
            textSize=16f;setTextColor(Color.WHITE);setPadding(0,dp(8),0,dp(8))
            controls.addView(this,0,LayoutParams(-1,-2));feedbackView=this
        }
        label.text=title+"\n"+body
    }
    fun text(value:String,size:Float=16f)=TextView(context).apply {
        text=value;textSize=size;setTextColor(Color.WHITE);setPadding(0,dp(8),0,dp(10));addControl(this)
    }
    fun addControl(view:View) {
        buttonGroup=null
        val before=backCard?.let { controls.indexOfChild(it) } ?: -1
        controls.addView(view,before,LayoutParams(-1,-2).apply { bottomMargin=dp(8) })
    }
    fun buttonsArea()=LinearLayout(context).apply { orientation=VERTICAL;addControl(this) }
    private fun makeButton(label:String,square:Boolean=true,action:()->Unit)=Button(context).apply {
        text=label;textSize=17f;typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL);letterSpacing=0.01f;isAllCaps=false;isSingleLine=false;ellipsize=null;minHeight=dp(64);minimumHeight=dp(64)
        gravity=Gravity.CENTER
        setPadding(dp(14),dp(if(square) 24 else 12),dp(if(square) 14 else 56),dp(16));setTextColor(Color.rgb(231,238,243))
        elevation=0f;stateListAnimator=null;backgroundTintList=null
        val icon=FlatMenuIcon(label,dp(28));compoundDrawablePadding=dp(14)
        setCompoundDrawablesRelativeWithIntrinsicBounds(if(square) null else icon,if(square) icon else null,null,null)
        val shape=FlatTechPanel(resources.displayMetrics.density)
        background=RippleDrawable(ColorStateList.valueOf(Color.rgb(47,119,145)),shape,null)
        setOnClickListener { if(!helpShowing && contextMenu==null) action() }
    }
    private fun card(button:Button,square:Boolean,help:String,more:List<MenuContextAction> = emptyList())=MenuActionCard(context,square).apply {
        addView(button,FrameLayout.LayoutParams(-1,-1))
        addView(Button(context).apply {
            text="⋮";textSize=24f;isAllCaps=false;setTextColor(Color.rgb(155,179,193));elevation=0f;stateListAnimator=null
            minHeight=0;minimumHeight=0;minWidth=0;minimumWidth=0;setPadding(0,0,0,0)
            background=RippleDrawable(ColorStateList.valueOf(Color.rgb(47,119,145)),null,null)
            contentDescription="Opzioni: "+button.text
            button.addTextChangedListener(object:android.text.TextWatcher {
                override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int) {}
                override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) { contentDescription="Opzioni: "+s }
                override fun afterTextChanged(s:android.text.Editable?) {}
            })
            setOnClickListener {
                if(helpShowing || contextMenu!=null) return@setOnClickListener
                val popup=PopupMenu(context,this)
                popup.menu.add(0,0,0,"Aiuto")
                more.forEachIndexed {i,item->popup.menu.add(0,i+1,i+1,item.title)}
                popup.setOnMenuItemClickListener {chosen->
                    popup.dismiss();contextMenu=null
                    if(chosen.itemId==0) showHelp(button.text.toString(),help) else more.getOrNull(chosen.itemId-1)?.action?.invoke()
                    true
                }
                popup.setOnDismissListener {if(contextMenu===popup) contextMenu=null}
                contextMenu=popup;popup.show()
            }
        },FrameLayout.LayoutParams(dp(48),dp(48),Gravity.TOP or Gravity.END))
    }
    fun button(label:String,help:String?=null,area:LinearLayout?=null,more:List<MenuContextAction> = emptyList(),action:()->Unit):Button {
        val button=makeButton(label,action=action)
        val tile=card(button,true,help ?: MenuCommandHelp.text(label),more)
        val group=if(area!=null) (area.getChildAt(area.childCount-1) as? MenuButtonGrid)
            ?: MenuButtonGrid(context).also { area.addView(it,LayoutParams(-1,-2)) }
        else buttonGroup ?: MenuButtonGrid(context).also { addControl(it);buttonGroup=it }
        group.addTile(tile);return button
    }
    fun toggle(label:String,value:Boolean,help:String,change:(Boolean)->Unit)=Switch(context).apply {
        text=label;textSize=18f;setTextColor(Color.WHITE);minHeight=dp(64);setPadding(dp(12),dp(12),dp(12),dp(12))
        buttonTintList=ColorStateList.valueOf(Color.rgb(99,226,241));isChecked=value;contentDescription=label+". "+help
        setOnCheckedChangeListener { _,checked -> change(checked) };addControl(this)
    }
    fun field(hintText:String,value:String,help:String,multiline:Boolean=false,minRows:Int=4)=EditText(context).apply {
        hint=hintText;setText(value);textSize=18f;setTextColor(Color.WHITE);setHintTextColor(Color.rgb(160,183,198))
        minHeight=dp(64);setPadding(dp(16),dp(14),dp(16),dp(14));background=GradientDrawable().apply {cornerRadius=dp(12).toFloat();setColor(Color.rgb(21,29,38));setStroke(dp(1),Color.rgb(54,74,88))};isSingleLine=!multiline
        contentDescription=hintText
        if(multiline) {
            inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines=minRows;setHorizontallyScrolling(false);gravity=Gravity.TOP or Gravity.START
        }
        addControl(this)
    }
    fun navigation(previous:String,back:()->Unit,next:String,forward:()->Unit) {
        backControl?.apply { text=previous;setOnClickListener { if(!helpShowing) back() } }
        button(next,action=forward)
    }
    private fun showHelp(title:String,body:String) {
        if(helpShowing) return
        (context as? Activity)?.window?.hideMenuKeyboard()
        val panel=LinearLayout(context).apply {
            orientation=VERTICAL;setPadding(dp(16),dp(8),dp(16),dp(16))
            background=GradientDrawable().apply {
                cornerRadius=dp(24).toFloat();setColor(Color.argb(240,25,33,43));setStroke(dp(1),Color.rgb(68,92,108))
            }
        }
        val header=LinearLayout(context).apply { orientation=HORIZONTAL;gravity=Gravity.CENTER_VERTICAL }
        header.addView(TextView(context).apply {
            text=title;textSize=20f;setTypeface(null,Typeface.BOLD);setTextColor(Color.WHITE);isAccessibilityHeading=true
        },LayoutParams(0,-2,1f))
        val dialog=AlertDialog.Builder(context).setView(panel).create()
        header.addView(Button(context).apply {
            text="X";textSize=20f;minWidth=0;minimumWidth=0;minHeight=0;minimumHeight=0;setPadding(0,0,0,0)
            contentDescription="Chiudi informazioni";setOnClickListener { dialog.dismiss() }
        },LayoutParams(dp(48),dp(48)))
        panel.addView(header,LayoutParams(-1,-2))
        panel.addView(HelpScroll(context).apply {
            addView(TextView(context).apply { text=body;textSize=18f;setTextColor(Color.WHITE);setPadding(0,dp(12),0,dp(8));setLineSpacing(dp(3).toFloat(),1f) })
        },LayoutParams(-1,-2))
        dialog.setCanceledOnTouchOutside(false)
        dialog.setOnDismissListener { if(helpDialog===dialog) helpDialog=null }
        helpDialog=dialog;dialog.show()
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT));setGravity(Gravity.CENTER)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND or WindowManager.LayoutParams.FLAG_SECURE)
            clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
            setDimAmount(0.45f)
            setLayout(minOf(dp(560),resources.displayMetrics.widthPixels-dp(48)).coerceAtLeast(dp(200)),-2)
        }
    }
}

private class HelpScroll(context:Context):ScrollView(context) {
    override fun onMeasure(widthMeasureSpec:Int,heightMeasureSpec:Int) {
        val max=(resources.displayMetrics.heightPixels*0.6f).toInt().coerceAtLeast(1)
        val size=if(MeasureSpec.getMode(heightMeasureSpec)==MeasureSpec.UNSPECIFIED) max else minOf(max,MeasureSpec.getSize(heightMeasureSpec))
        super.onMeasure(widthMeasureSpec,MeasureSpec.makeMeasureSpec(size,MeasureSpec.AT_MOST))
    }
}

/** Explicit row measurement also handles tiles inserted after an empty asynchronous load. */
private class MenuButtonGrid(context:Context):LinearLayout(context) {
    private var row:MenuButtonRow?=null
    init { orientation=VERTICAL }
    fun addTile(tile:View) {
        val current=row
        if(current!=null) { current.addView(tile);row=null }
        else {
            val next=MenuButtonRow(context);next.addView(tile)
            addView(next,LayoutParams(-1,-2).apply { bottomMargin=(8*resources.displayMetrics.density).toInt() });row=next
        }
    }
}

private class MenuButtonRow(context:Context):ViewGroup(context) {
    private val gap=(8*resources.displayMetrics.density).toInt()
    override fun generateDefaultLayoutParams()=LayoutParams(-1,-2)
    override fun onMeasure(widthMeasureSpec:Int,heightMeasureSpec:Int) {
        val width=MeasureSpec.getSize(widthMeasureSpec)
        val tileWidth=((width-gap)/2).coerceAtLeast(0);var height=0
        for(i in 0 until childCount) {
            val tile=getChildAt(i)
            tile.measure(MeasureSpec.makeMeasureSpec(tileWidth,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED))
            height=maxOf(height,tile.measuredHeight)
        }
        for(i in 0 until childCount) getChildAt(i).measure(MeasureSpec.makeMeasureSpec(tileWidth,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(height,MeasureSpec.EXACTLY))
        setMeasuredDimension(width,resolveSize(height,heightMeasureSpec))
    }
    override fun onLayout(changed:Boolean,left:Int,top:Int,right:Int,bottom:Int) {
        val tileWidth=((width-gap)/2).coerceAtLeast(0)
        for(i in 0 until childCount) {
            val x=if(layoutDirection==View.LAYOUT_DIRECTION_RTL) width-tileWidth-i*(tileWidth+gap) else i*(tileWidth+gap)
            getChildAt(i).layout(x,0,x+tileWidth,height)
        }
    }
}

private class MenuActionCard(context:Context,private val square:Boolean):FrameLayout(context) {
    override fun onMeasure(widthMeasureSpec:Int,heightMeasureSpec:Int) {
        super.onMeasure(widthMeasureSpec,heightMeasureSpec)
        if(square && MeasureSpec.getMode(heightMeasureSpec)!=MeasureSpec.EXACTLY)
            super.onMeasure(widthMeasureSpec,MeasureSpec.makeMeasureSpec(maxOf(measuredWidth,measuredHeight),MeasureSpec.EXACTLY))
    }
}

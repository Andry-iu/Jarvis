package it.jarviswake

import android.app.AlertDialog
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.TextView

/** App-owned dialogs share the menu touch size; Android's external panels remain unchanged. */
internal fun AlertDialog.showLarge():AlertDialog {
    show()
    val size=(64*context.resources.displayMetrics.density).toInt()
    for(which in listOf(AlertDialog.BUTTON_POSITIVE,AlertDialog.BUTTON_NEGATIVE,AlertDialog.BUTTON_NEUTRAL)) {
        getButton(which)?.apply { minHeight=size;textSize=18f;isAllCaps=false;isSingleLine=false }
    }
    fun fields(view:View) {
        if(view is EditText) { view.minHeight=size;view.textSize=18f }
        if(view is ViewGroup) for(i in 0 until view.childCount) fields(view.getChildAt(i))
    }
    window?.decorView?.let { fields(it) }
    return this
}
internal fun AlertDialog.Builder.showLarge()=create().showLarge()
internal fun largeMenuChoices(context:Context,labels:List<String>)=object:ArrayAdapter<String>(context,android.R.layout.simple_list_item_1,labels) {
    override fun getView(position:Int,convertView:View?,parent:ViewGroup):View = super.getView(position,convertView,parent).apply {
        minimumHeight=(64*resources.displayMetrics.density).toInt()
        if(this is TextView) { textSize=18f;isSingleLine=false }
    }
}

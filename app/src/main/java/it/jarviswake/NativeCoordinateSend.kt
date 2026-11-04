package it.jarviswake

/** User-authorized tap geometry for the observed ChatGPT native composer, not a UI contract. */
internal object NativeCoordinateSend {
    data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val width get() = right-left
        val height get() = bottom-top
        val x get() = (left+right)/2f
        val y get() = (top+bottom)/2f
        fun contains(x: Float,y: Float) = x>=left && x<right && y>=top && y<bottom
    }
    data class Control(val box: Box,val enabled: Boolean,val labelAllowed: Boolean = true)
    data class Plan(val field: Box,val window: Box,val windowId: Int,val x: Float,val y: Float,val source: String)
    fun plan(field: Box,window: Box,windowId: Int,density: Float,controls: List<Control>,blocked: List<Box>,allowRowOverlap: Boolean = false): Plan? {
        if (!density.isFinite() || density<=0 || field.width<window.width*0.6f || field.height<=0 ||
            field.left<window.left || field.right>window.right || field.top<window.top || field.bottom>=window.bottom) return null
        val near = controls.filter { c ->
            val b=c.box
            b.width in (28*density).toInt()..(80*density).toInt() &&
                b.height in (28*density).toInt()..(80*density).toInt() &&
                b.x>=field.right-56*density && b.x<=field.right+24*density &&
                // The opening editor's accessibility bounds can overlap the lower action row
                // by a small padded edge (ZIP _10_). Keep the tap centre below the editor.
                ((b.top>=field.bottom-(if (allowRowOverlap) 12*density else 0f) && b.top<=field.bottom+32*density && b.y>field.bottom) ||
                    (field.height<=80*density && b.top>=field.top-8*density &&
                        b.bottom<=field.bottom+8*density && b.y>=field.top && b.y<=field.bottom))
        }
        if (near.any { !it.enabled || !it.labelAllowed }) return null
        val target=near.distinctBy { it.box }.singleOrNull()?.box ?: return null
        // Only the centre of a live, unique control. Never invent a point below the editor.
        if (target.left<window.left || target.right>window.right || target.top<window.top || target.bottom>window.bottom) return null
        val x=target.x
        val y=target.y
        if (!window.contains(x,y) || blocked.any { it.contains(x,y) }) return null
        return Plan(field,window,windowId,x,y,"control_bounds")
    }
}

/** One scheduled gesture may dispatch and finish once. Late callbacks cannot resurrect the HUD. */
internal class NativeTapGate {
    private var dispatched=false
    private var finished=false
    fun dispatch(valid: Boolean): Boolean {
        if (!valid || dispatched || finished) return false
        dispatched=true
        return true
    }
    fun finish(): Boolean {
        if (finished) return false
        finished=true
        return true
    }
    fun mayRestore(sameHud: Boolean,attached: Boolean,dismissed: Boolean) = sameHud && attached && !dismissed
}

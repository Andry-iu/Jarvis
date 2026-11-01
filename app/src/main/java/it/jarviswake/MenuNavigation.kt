package it.jarviswake

internal enum class MenuPage { HOME, CONFIGURATION, SERVICE, PERMISSIONS, CHATGPT, GREETING, AUDIO, COVER, LINK, SUMMARY, WEB_MAINTENANCE, DIAGNOSTICS }

/** Navigation changes pages only: no preferences, permission requests or phone actions. */
internal class MenuNavigation(savedPath:List<String> = emptyList(),savedStep:Int = -1) {
    companion object {
        val STEPS=listOf(MenuPage.PERMISSIONS,MenuPage.CHATGPT,MenuPage.GREETING,MenuPage.AUDIO,MenuPage.COVER,MenuPage.LINK,MenuPage.SUMMARY)
    }
    private val path=ArrayList<MenuPage>()
    var step=-1;private set
    init {
        val restored=savedPath.mapNotNull { name -> MenuPage.entries.find { it.name==name } }
        path.add(MenuPage.HOME)
        if(restored.firstOrNull()==MenuPage.HOME) path.addAll(restored.drop(1).filter { it!=MenuPage.HOME }.take(12))
        if(savedStep in STEPS.indices) step=savedStep
    }
    val current get()=if(step>=0) STEPS[step] else path.last()
    val guided get()=step>=0
    val canBack get()=guided || path.size>1
    fun savedPath()=path.map { it.name }
    fun open(page:MenuPage) { step=-1;if(page==MenuPage.HOME) { path.clear();path.add(page) } else if(path.last()!=page) path.add(page) }
    fun begin() { step=0 }
    fun next() {
        if(!guided) return
        if(step<STEPS.lastIndex) step++ else { step=-1;path.clear();path.add(MenuPage.HOME) }
    }
    fun back():Boolean {
        if(guided) { if(step>0) step-- else step=-1;return true }
        if(path.size<=1) return false
        path.removeAt(path.lastIndex);return true
    }
}

package it.jarviswake

import org.junit.Assert.*
import org.junit.Test

class MenuNavigationTest {
    @Test fun dailyUseAndServiceReturnThroughTheirOwnMenu() {
        val n=MenuNavigation();assertFalse(n.back())
        n.open(MenuPage.SERVICE);n.open(MenuPage.WEB_MAINTENANCE)
        assertTrue(n.back());assertEquals(MenuPage.SERVICE,n.current)
        assertTrue(n.back());assertEquals(MenuPage.HOME,n.current);assertFalse(n.canBack)
    }
    @Test fun directConfigurationDoesNotBecomeAWizard() {
        val n=MenuNavigation();n.open(MenuPage.CONFIGURATION);n.open(MenuPage.AUDIO)
        assertFalse(n.guided);n.next();assertEquals(MenuPage.AUDIO,n.current)
        n.back();assertEquals(MenuPage.CONFIGURATION,n.current)
    }
    @Test fun wizardIncludesEveryCategoryAndAnExplicitSummaryBeforeFinishing() {
        val n=MenuNavigation();n.open(MenuPage.CONFIGURATION);n.begin()
        val visited=ArrayList<MenuPage>()
        for(page in MenuNavigation.STEPS) { assertEquals(page,n.current);assertTrue(n.guided);visited.add(n.current);n.next() }
        assertEquals(MenuNavigation.STEPS,visited);assertEquals(MenuPage.HOME,n.current);assertFalse(n.guided)
    }
    @Test fun backingOutOfFirstWizardStepReturnsToItsCaller() {
        val n=MenuNavigation();n.open(MenuPage.CONFIGURATION);n.begin();n.next();n.next()
        n.back();assertEquals(MenuPage.CHATGPT,n.current);n.back();assertEquals(MenuPage.PERMISSIONS,n.current)
        n.back();assertFalse(n.guided);assertEquals(MenuPage.CONFIGURATION,n.current)
    }
    @Test fun recreationRestoresCategoryAndWizardPositionWithoutRestartingTheFlow() {
        val n=MenuNavigation();n.open(MenuPage.CONFIGURATION);n.begin();repeat(4) { n.next() }
        val restored=MenuNavigation(n.savedPath(),n.step)
        assertEquals(MenuPage.COVER,restored.current);restored.back();assertEquals(MenuPage.AUDIO,restored.current)
        val direct=MenuNavigation(listOf("HOME","SERVICE","DIAGNOSTICS"),-1);direct.back();assertEquals(MenuPage.SERVICE,direct.current)
    }
    @Test fun invalidSavedStateDoesNotInventASettingsRouteOrWizard() {
        val n=MenuNavigation(listOf("NOT_A_PAGE"),999)
        assertEquals(MenuPage.HOME,n.current);assertFalse(n.guided);assertFalse(n.canBack)
    }
    @Test fun guideSearchHandlesAccentsDuplicatesAndQuestionsWithoutInventingMatches() {
        assertEquals(listOf("come","attivo","accessibilita"),GuideSearch.tokens("Come attivo ACCESSIBILITÀ? Come?"))
        assertTrue(GuideSearch.score("accessibilita","Permessi","Accessibilità e HUD","Autorizza Android")>0)
        assertEquals(0,GuideSearch.score("inesistenteqwerty","Cover","swipe","Tocca al centro"))
        assertTrue(GuideSearch.score("wake wake","Wake","pronuncia","Hey Jarvis")>GuideSearch.score("wake","Audio","volume","player"))
    }
}

package com.otokit.noharayh.ui.shell.navigation

import org.junit.Assert.*
import org.junit.Test

class SettingsNavigationTest {
    @Test fun childCannotOpenOutsideSettings() {
        val controller = NavigationController()
        controller.openSettingsSection(SettingsSection.About)
        assertEquals(NavigationState(), controller.state)
    }
    @Test fun everyChildReturnsToRootBeforeClosing() {
        for (section in SettingsSection.entries) {
            val controller = NavigationController(NavigationState(Destination.MusicData))
            controller.openSettings()
            controller.openSettingsSection(section)
            assertTrue(controller.consumeBack())
            assertTrue(controller.state.settingsOpen)
            assertNull(controller.state.settingsSection)
            assertEquals(Destination.MusicData, controller.state.destination)
            assertTrue(controller.consumeBack())
            assertFalse(controller.state.settingsOpen)
            assertFalse(controller.consumeBack())
        }
    }
    @Test fun navigateAndCloseClearChild() {
        val controller = NavigationController()
        controller.openSettings()
        controller.openSettingsSection(SettingsSection.App)
        controller.navigate(Destination.ComingSoon)
        assertEquals(NavigationState(Destination.ComingSoon), controller.state)
        controller.openSettings()
        controller.openSettingsSection(SettingsSection.Personalization)
        controller.closeSettings()
        controller.openSettings()
        assertNull(controller.state.settingsSection)
    }
    @Test fun previewCannotCommitUntilTerminalEvent() {
        val controller = NavigationController()
        controller.openSettings()
        controller.openSettingsSection(SettingsSection.About)
        val state = controller.state
        val session = BackPreviewSession()
        session.begin(BackPreviewSession.Target.SettingsChild)
        session.update(0.65f)
        assertEquals(state, controller.state)
        session.take() // cancelled: no navigation command
        assertEquals(state, controller.state)
        session.begin(BackPreviewSession.Target.SettingsChild)
        session.update(0.4f)
        if (session.take()?.target == BackPreviewSession.Target.SettingsChild) controller.backToSettingsRoot()
        assertTrue(controller.state.settingsOpen)
        assertNull(controller.state.settingsSection)
    }
    @Test fun closedInitialStateCannotCarryChild() {
        val controller = NavigationController(NavigationState(settingsSection = SettingsSection.App))
        assertNull(controller.state.settingsSection)
    }
}

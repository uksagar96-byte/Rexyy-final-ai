package com.rexyy.app

import android.app.Application
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import com.rexyy.app.data.local.SecureStorage
import com.rexyy.app.repository.AssistantRepository
import com.rexyy.app.ui.chat.ChatViewModel
import com.rexyy.app.voice.VoiceCommand
import com.rexyy.app.voice.VoiceCommandParser
import com.rexyy.app.voice.VoiceState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RexyyRobolectricTest {

    @Test
    fun testAppNameResource() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("REXYY", appName)
    }

    @Test
    fun testSecureStorage() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = SecureStorage(context, includeBuildConfigKeys = false)

        assertFalse(storage.hasApiKey())
        storage.saveApiKey("sk-test-sample-api-key-12345")
        assertTrue(storage.hasApiKey())
        assertEquals("sk-test-sample-api-key-12345", storage.getApiKey())

        storage.clearApiKey()
        assertFalse(storage.hasApiKey())
    }

    @Test
    fun testSecureStorageVoicePreferences() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = SecureStorage(context)

        // Defaults
        assertTrue(storage.isVoiceCommandsEnabled())
        assertTrue(storage.isVoiceRepliesEnabled())
        assertEquals(SecureStorage.VOICE_LANG_DEFAULT, storage.getVoiceLanguage())

        // Updates
        storage.setVoiceCommandsEnabled(false)
        assertFalse(storage.isVoiceCommandsEnabled())

        storage.setVoiceRepliesEnabled(false)
        assertFalse(storage.isVoiceRepliesEnabled())

        storage.setVoiceLanguage(SecureStorage.VOICE_LANG_HI)
        assertEquals(SecureStorage.VOICE_LANG_HI, storage.getVoiceLanguage())
    }

    @Test
    fun testVoiceCommandParserAppsAndActions() {
        // YouTube open
        val ytEnglish = VoiceCommandParser.parse("Open YouTube")
        assertTrue(ytEnglish is VoiceCommand.OpenApp)
        assertEquals("YouTube", (ytEnglish as VoiceCommand.OpenApp).appName)

        val ytHindi = VoiceCommandParser.parse("YouTube kholo")
        assertTrue(ytHindi is VoiceCommand.OpenApp)
        assertEquals("YouTube", (ytHindi as VoiceCommand.OpenApp).appName)

        // Settings
        val settingsEn = VoiceCommandParser.parse("Open settings")
        assertTrue(settingsEn is VoiceCommand.OpenSettings)

        val settingsHi = VoiceCommandParser.parse("Settings kholo")
        assertTrue(settingsHi is VoiceCommand.OpenSettings)

        // Bluetooth
        val btOn = VoiceCommandParser.parse("Turn on bluetooth")
        assertTrue(btOn is VoiceCommand.BluetoothSettings)
        assertTrue((btOn as VoiceCommand.BluetoothSettings).turnOn)

        val btHi = VoiceCommandParser.parse("Bluetooth on karo")
        assertTrue(btHi is VoiceCommand.BluetoothSettings)

        // Volume
        val volUp = VoiceCommandParser.parse("Volume badhao")
        assertTrue(volUp is VoiceCommand.AdjustVolume)
        assertTrue((volUp as VoiceCommand.AdjustVolume).raise)

        val volDown = VoiceCommandParser.parse("Decrease volume")
        assertTrue(volDown is VoiceCommand.AdjustVolume)
        assertFalse((volDown as VoiceCommand.AdjustVolume).raise)
    }

    @Test
    fun testVoiceCommandParserAlarm() {
        val alarm1 = VoiceCommandParser.parse("Set an alarm for 7:30 AM")
        assertTrue(alarm1 is VoiceCommand.SetAlarm)
        val cmd1 = alarm1 as VoiceCommand.SetAlarm
        assertEquals(7, cmd1.hour)
        assertEquals(30, cmd1.minute)

        val alarm2 = VoiceCommandParser.parse("Alarm 8 baje lagao")
        assertTrue(alarm2 is VoiceCommand.SetAlarm)
        val cmd2 = alarm2 as VoiceCommand.SetAlarm
        assertEquals(8, cmd2.hour)
    }

    @Test
    fun testVoiceCommandParserSearchAndChat() {
        val search = VoiceCommandParser.parse("Search Google for Android updates")
        assertTrue(search is VoiceCommand.GoogleSearch)
        assertTrue((search as VoiceCommand.GoogleSearch).query.contains("Android updates"))

        // Conversational queries should map to AiChat
        val chat1 = VoiceCommandParser.parse("What is the weather today?")
        assertTrue(chat1 is VoiceCommand.AiChat)

        val chat2 = VoiceCommandParser.parse("Tell me a funny joke")
        assertTrue(chat2 is VoiceCommand.AiChat)
    }

    @Test
    fun testChatViewModelCreationWithFactory() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val viewModel = ViewModelProvider(activity, ChatViewModel.Factory)[ChatViewModel::class.java]
        assertNotNull(viewModel)
        assertNotNull(viewModel.uiState.value)
        assertEquals(VoiceState.IDLE, viewModel.uiState.value.voiceState)
        assertTrue(viewModel.uiState.value.isVoiceCommandsEnabled)
        assertTrue(viewModel.uiState.value.isVoiceRepliesEnabled)
    }

    @Test
    fun testChatViewModelDirectApplicationConstructor() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val constructor = ChatViewModel::class.java.getConstructor(Application::class.java)
        assertNotNull(constructor)
        val viewModel = constructor.newInstance(app)
        assertNotNull(viewModel)
        assertEquals(VoiceState.IDLE, viewModel.uiState.value.voiceState)
    }

    @Test
    fun testChatViewModelFullConstructorInjection() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val repository = AssistantRepository(app)
        val viewModel = ChatViewModel(app, repository)
        assertNotNull(viewModel)

        // Test Voice Settings in ViewModel
        viewModel.setVoiceCommandsEnabled(false)
        assertFalse(viewModel.uiState.value.isVoiceCommandsEnabled)

        viewModel.setVoiceRepliesEnabled(false)
        assertFalse(viewModel.uiState.value.isVoiceRepliesEnabled)

        viewModel.setVoiceLanguage(SecureStorage.VOICE_LANG_HI)
        assertEquals(SecureStorage.VOICE_LANG_HI, viewModel.uiState.value.voiceLanguage)

        // Test Provider Selection and Dual Keys in ViewModel
        viewModel.selectProvider(com.rexyy.app.network.provider.AiProviderType.GEMINI)
        assertEquals(com.rexyy.app.network.provider.AiProviderType.GEMINI, viewModel.uiState.value.selectedProvider)

        viewModel.updateGeminiApiKey("AIzaSy-sample-gemini-key-12345")
        assertTrue(viewModel.uiState.value.hasApiKey)
        assertTrue(viewModel.uiState.value.maskedGeminiApiKey.contains("2345"))

        viewModel.setAutoFallbackEnabled(false)
        assertFalse(viewModel.uiState.value.isAutoFallbackEnabled)
    }

    @Test
    fun testSecureStorageMultiProviderAndDualKeys() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = SecureStorage(context, includeBuildConfigKeys = false)

        // Default provider
        assertEquals(com.rexyy.app.network.provider.AiProviderType.OPENAI, storage.getSelectedProvider())

        // Save & get Gemini key
        assertFalse(storage.hasGeminiApiKey())
        storage.saveGeminiApiKey("AIzaSy-test-gemini-key")
        assertTrue(storage.hasGeminiApiKey())
        assertEquals("AIzaSy-test-gemini-key", storage.getGeminiApiKey())

        // Save & get OpenAI key
        storage.saveOpenAiApiKey("sk-test-openai-key")
        assertTrue(storage.hasOpenAiApiKey())
        assertEquals("sk-test-openai-key", storage.getOpenAiApiKey())

        // Provider switching
        storage.setSelectedProvider(com.rexyy.app.network.provider.AiProviderType.GEMINI)
        assertEquals(com.rexyy.app.network.provider.AiProviderType.GEMINI, storage.getSelectedProvider())

        // Fallback toggle
        assertTrue(storage.isAutoFallbackEnabled()) // default true
        storage.setAutoFallbackEnabled(false)
        assertFalse(storage.isAutoFallbackEnabled())

        // Model selection per provider
        storage.setGeminiModel("gemini-2.5-flash")
        assertEquals("gemini-2.5-flash", storage.getGeminiModel())
        storage.setOpenAiModel("gpt-4o")
        assertEquals("gpt-4o", storage.getOpenAiModel())
    }

    @Test
    fun testAiModelRegistry() {
        val openAiModels = com.rexyy.app.network.provider.AiModelRegistry.getAvailableModels(com.rexyy.app.network.provider.AiProviderType.OPENAI)
        assertTrue(openAiModels.contains("gpt-4o-mini"))
        assertTrue(openAiModels.contains("gpt-4o"))

        val geminiModels = com.rexyy.app.network.provider.AiModelRegistry.getAvailableModels(com.rexyy.app.network.provider.AiProviderType.GEMINI)
        assertTrue(geminiModels.contains("gemini-3.5-flash"))
        assertTrue(geminiModels.contains("gemini-2.5-flash"))

        assertEquals("gpt-4o-mini", com.rexyy.app.network.provider.AiModelRegistry.getDefaultModel(com.rexyy.app.network.provider.AiProviderType.OPENAI))
        assertEquals("gemini-3.5-flash", com.rexyy.app.network.provider.AiModelRegistry.getDefaultModel(com.rexyy.app.network.provider.AiProviderType.GEMINI))
    }

    @Test
    fun testVoiceCommandProviderOverrideDetection() {
        val geminiCmd = VoiceCommandParser.parse("Ask Gemini what is quantum computing")
        assertTrue(geminiCmd is VoiceCommand.AiChat)
        val geminiChat = geminiCmd as VoiceCommand.AiChat
        assertEquals(com.rexyy.app.network.provider.AiProviderType.GEMINI, geminiChat.providerOverride)
        assertTrue(geminiChat.prompt.contains("quantum computing"))

        val openAiCmd = VoiceCommandParser.parse("Ask OpenAI how to write a poem")
        assertTrue(openAiCmd is VoiceCommand.AiChat)
        val openAiChat = openAiCmd as VoiceCommand.AiChat
        assertEquals(com.rexyy.app.network.provider.AiProviderType.OPENAI, openAiChat.providerOverride)
        assertTrue(openAiChat.prompt.contains("poem"))
    }

    @Test
    fun testSetupStatePersistenceAndCompletion() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = SecureStorage(context)

        assertFalse(storage.isSetupCompleted())
        assertEquals("Commander", storage.getUserName())
        assertEquals("REXYY", storage.getAssistantName())

        storage.setUserName("Alex")
        storage.setAssistantName("Rexyy Prime")
        storage.setSetupCompleted(true)

        assertTrue(storage.isSetupCompleted())
        assertEquals("Alex", storage.getUserName())
        assertEquals("Rexyy Prime", storage.getAssistantName())
    }

    @Test
    fun testLocalTestProviderImmediateResponse() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = AssistantRepository(context)

        repository.setSelectedProvider(com.rexyy.app.network.provider.AiProviderType.LOCAL_TEST)
        val result = repository.sendMessage("What can you do?")
        assertTrue(result is com.rexyy.app.network.NetworkResult.Success)
        val responseText = (result as com.rexyy.app.network.NetworkResult.Success).data.content
        assertTrue(responseText.contains("Local Test Mode") || responseText.contains("REXYY"))
    }

    @Test
    fun testDeviceCapabilityManagerPermissions() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val hasMic = com.rexyy.app.device.CapabilityManager.hasPermission(context, android.Manifest.permission.RECORD_AUDIO)
        // Check method execution without exceptions
        assertNotNull(hasMic)
    }

    @Test
    fun testWhatsAppCommandParsingAndBodyCollection() {
        // "Ramzan ko WhatsApp message bhejo" -> Should route to WhatsAppMessage with empty body (requiring body input)
        val cmd = VoiceCommandParser.parse("Ramzan ko WhatsApp message bhejo")
        assertTrue(cmd is VoiceCommand.WhatsAppMessage)
        val waCmd = cmd as VoiceCommand.WhatsAppMessage
        assertEquals("Ramzan", waCmd.target)
        assertEquals("", waCmd.body)

        // "send WhatsApp message on Ramzan" -> target is Ramzan, not "on Ramzan"
        val cmd2 = VoiceCommandParser.parse("send WhatsApp message on Ramzan")
        assertTrue(cmd2 is VoiceCommand.WhatsAppMessage)
        val waCmd2 = cmd2 as VoiceCommand.WhatsAppMessage
        assertEquals("Ramzan", waCmd2.target)
        assertEquals("", waCmd2.body)
    }

    @Test
    fun testTaskPlannerCompoundCommand() {
        // "Open YouTube and search cricket"
        val compound = VoiceCommandParser.parse("Open YouTube and search cricket")
        assertTrue(compound is VoiceCommand.MultiStepTask)
        val multi = compound as VoiceCommand.MultiStepTask
        assertEquals(2, multi.steps.size)
        assertTrue(multi.steps[0] is VoiceCommand.OpenApp)
        assertTrue(multi.steps[1] is VoiceCommand.GoogleSearch)
    }

    @Test
    fun testSecureStorageOpenRouter() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = SecureStorage(context, includeBuildConfigKeys = false)

        assertFalse(storage.hasOpenRouterApiKey())
        storage.saveOpenRouterApiKey("sk-or-v1-abcdef1234567890")
        assertTrue(storage.hasOpenRouterApiKey())
        assertEquals("sk-or-v1-abcdef1234567890", storage.getOpenRouterApiKey())
        assertEquals("google/gemini-2.5-flash", storage.getOpenRouterModel())

        storage.setOpenRouterModel("anthropic/claude-3.5-sonnet")
        assertEquals("anthropic/claude-3.5-sonnet", storage.getOpenRouterModel())

        storage.clearOpenRouterApiKey()
        assertFalse(storage.hasOpenRouterApiKey())
    }

    @Test
    fun testRexyyCommandRouterOpenRouterOverride() {
        val route1 = com.rexyy.app.router.RexyyCommandRouter.route("Ask OpenRouter what is the distance to Mars")
        assertTrue(route1 is VoiceCommand.AiChat)
        val chat1 = route1 as VoiceCommand.AiChat
        assertEquals("what is the distance to Mars", chat1.prompt)
        assertEquals(com.rexyy.app.network.provider.AiProviderType.OPENROUTER, chat1.providerOverride)

        val route2 = com.rexyy.app.router.RexyyCommandRouter.route("Ask Claude write a poem")
        assertTrue(route2 is VoiceCommand.AiChat)
        val chat2 = route2 as VoiceCommand.AiChat
        assertEquals("write a poem", chat2.prompt)
        assertEquals(com.rexyy.app.network.provider.AiProviderType.OPENROUTER, chat2.providerOverride)

        val route3 = com.rexyy.app.router.RexyyCommandRouter.route("OpenRouter se pucho weather kaisa hai")
        assertTrue(route3 is VoiceCommand.AiChat)
        val chat3 = route3 as VoiceCommand.AiChat
        assertEquals("weather kaisa hai", chat3.prompt)
        assertEquals(com.rexyy.app.network.provider.AiProviderType.OPENROUTER, chat3.providerOverride)
    }
}

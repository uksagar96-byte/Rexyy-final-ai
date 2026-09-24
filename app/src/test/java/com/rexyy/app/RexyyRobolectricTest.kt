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

    @Test
    fun testPhase4LocalIntentParsingDeterministic() {
        // 1. Open App variants in Hindi, English, Hinglish
        val yt1 = com.rexyy.app.router.RexyyCommandRouter.route("YouTube kholo")
        assertTrue(yt1 is VoiceCommand.OpenApp)
        assertEquals("YouTube", (yt1 as VoiceCommand.OpenApp).appName)

        val yt2 = com.rexyy.app.router.RexyyCommandRouter.route("open YouTube")
        assertTrue(yt2 is VoiceCommand.OpenApp)
        assertEquals("YouTube", (yt2 as VoiceCommand.OpenApp).appName)

        val insta1 = com.rexyy.app.router.RexyyCommandRouter.route("Instagram khol do")
        assertTrue(insta1 is VoiceCommand.OpenApp)
        assertEquals("Instagram", (insta1 as VoiceCommand.OpenApp).appName)

        val insta2 = com.rexyy.app.router.RexyyCommandRouter.route("Instagram open kar do")
        assertTrue(insta2 is VoiceCommand.OpenApp)
        assertEquals("Instagram", (insta2 as VoiceCommand.OpenApp).appName)

        // 2. Battery & Status
        val bat1 = com.rexyy.app.router.RexyyCommandRouter.route("battery kitni hai")
        assertTrue(bat1 is VoiceCommand.GetBattery)

        val bat2 = com.rexyy.app.router.RexyyCommandRouter.route("battery status batao")
        assertTrue(bat2 is VoiceCommand.GetBattery)

        val bat3 = com.rexyy.app.router.RexyyCommandRouter.route("charge kitna hai")
        assertTrue(bat3 is VoiceCommand.GetBattery)

        // 3. Date & Time
        val d1 = com.rexyy.app.router.RexyyCommandRouter.route("aaj ki date kya hai")
        assertTrue(d1 is VoiceCommand.GetDate)

        val d2 = com.rexyy.app.router.RexyyCommandRouter.route("what is the date")
        assertTrue(d2 is VoiceCommand.GetDate)

        val t1 = com.rexyy.app.router.RexyyCommandRouter.route("time kya hua")
        assertTrue(t1 is VoiceCommand.GetTime)

        val t2 = com.rexyy.app.router.RexyyCommandRouter.route("kitne baje hain")
        assertTrue(t2 is VoiceCommand.GetTime)

        // 4. Flashlight / Torch
        val torchOn = com.rexyy.app.router.RexyyCommandRouter.route("torch on karo")
        assertTrue(torchOn is VoiceCommand.ToggleFlashlight)
        assertTrue((torchOn as VoiceCommand.ToggleFlashlight).turnOn == true)

        val torchOff = com.rexyy.app.router.RexyyCommandRouter.route("torch band karo")
        assertTrue(torchOff is VoiceCommand.ToggleFlashlight)
        assertTrue((torchOff as VoiceCommand.ToggleFlashlight).turnOn == false)

        // 5. Brightness
        val bright1 = com.rexyy.app.router.RexyyCommandRouter.route("brightness 50 percent karo")
        assertTrue(bright1 is VoiceCommand.AdjustBrightness)
        assertEquals(50, (bright1 as VoiceCommand.AdjustBrightness).percent)

        val bright2 = com.rexyy.app.router.RexyyCommandRouter.route("brightness badhao")
        assertTrue(bright2 is VoiceCommand.AdjustBrightness)
        assertTrue((bright2 as VoiceCommand.AdjustBrightness).raise == true)

        // 6. Volume
        val vol1 = com.rexyy.app.router.RexyyCommandRouter.route("volume 80% karo")
        assertTrue(vol1 is VoiceCommand.AdjustVolume)
        assertEquals(80, (vol1 as VoiceCommand.AdjustVolume).percent)

        val volMute = com.rexyy.app.router.RexyyCommandRouter.route("mute")
        assertTrue(volMute is VoiceCommand.AdjustVolume)
        assertTrue((volMute as VoiceCommand.AdjustVolume).mute)

        // 7. Alarms & Timers
        val alarm = com.rexyy.app.router.RexyyCommandRouter.route("7 baje alarm laga do")
        assertTrue(alarm is VoiceCommand.SetAlarm)
        assertEquals(7, (alarm as VoiceCommand.SetAlarm).hour)

        val timer = com.rexyy.app.router.RexyyCommandRouter.route("10 minute ka timer lagao")
        assertTrue(timer is VoiceCommand.SetTimer)
        assertEquals(600, (timer as VoiceCommand.SetTimer).seconds)

        // 8. Close / Exit / Minimize
        val close1 = com.rexyy.app.router.RexyyCommandRouter.route("exit")
        assertTrue(close1 is VoiceCommand.CloseApp)

        val close2 = com.rexyy.app.router.RexyyCommandRouter.route("background me jao")
        assertTrue(close2 is VoiceCommand.CloseApp)

        val close3 = com.rexyy.app.router.RexyyCommandRouter.route("close app")
        assertTrue(close3 is VoiceCommand.CloseApp)

        // 9. Camera & Contacts
        val cam = com.rexyy.app.router.RexyyCommandRouter.route("camera kholo")
        assertTrue(cam is VoiceCommand.OpenCamera)

        val cont = com.rexyy.app.router.RexyyCommandRouter.route("contacts kholo")
        assertTrue(cont is VoiceCommand.OpenContacts)

        // 10. Device Status
        val devStatus = com.rexyy.app.router.RexyyCommandRouter.route("device status")
        assertTrue(devStatus is VoiceCommand.GetDeviceInfo)
    }

    @Test
    fun testPhase4ExecutionAndDiagnostics() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        com.rexyy.app.router.CommandDiagnosticLogger.clear()

        // 1. Execute Date
        val dateCmd = VoiceCommand.GetDate("aaj ki date kya hai")
        val dateRes = com.rexyy.app.voice.VoiceCommandExecutor.execute(dateCmd, context)
        assertTrue(dateRes is com.rexyy.app.voice.VoiceCommandResult.Handled)
        assertTrue((dateRes as com.rexyy.app.voice.VoiceCommandResult.Handled).replyText.contains("date"))

        // Check diagnostic logger
        val latestLog = com.rexyy.app.router.CommandDiagnosticLogger.getLatest()
        assertNotNull(latestLog)
        assertEquals("GetDate", latestLog?.detectedIntent)
        assertEquals("VERIFIED", latestLog?.verificationResult)
        assertEquals("aaj ki date kya hai", latestLog?.rawCommand)

        // 2. Execute Time
        val timeCmd = VoiceCommand.GetTime("time kya hua")
        val timeRes = com.rexyy.app.voice.VoiceCommandExecutor.execute(timeCmd, context)
        assertTrue(timeRes is com.rexyy.app.voice.VoiceCommandResult.Handled)

        val latestLog2 = com.rexyy.app.router.CommandDiagnosticLogger.getLatest()
        assertNotNull(latestLog2)
        assertEquals("GetTime", latestLog2?.detectedIntent)
        assertEquals("VERIFIED", latestLog2?.verificationResult)
    }

    @Test
    fun testPhase5AppLaunchResolution() {
        val ytCmd = com.rexyy.app.router.RexyyCommandRouter.route("YouTube kholo")
        assertTrue(ytCmd is VoiceCommand.OpenApp)
        assertEquals("YouTube", (ytCmd as VoiceCommand.OpenApp).appName)

        val ytMusicCmd = com.rexyy.app.router.RexyyCommandRouter.route("YouTube Music kholo")
        assertTrue(ytMusicCmd is VoiceCommand.OpenApp)
        assertEquals("YouTube Music", (ytMusicCmd as VoiceCommand.OpenApp).appName)

        val instaCmd = com.rexyy.app.router.RexyyCommandRouter.route("Instagram kholo")
        assertTrue(instaCmd is VoiceCommand.OpenApp)
        assertEquals("Instagram", (instaCmd as VoiceCommand.OpenApp).appName)

        // Test AppMatcher strict disambiguation
        val mockInstalled = listOf(
            com.rexyy.app.launcher.AppInfo(
                label = "YouTube",
                packageName = "com.google.android.youtube",
                activityName = "com.google.android.youtube.HomeActivity",
                aliases = listOf("yt", "youtube")
            ),
            com.rexyy.app.launcher.AppInfo(
                label = "YouTube Music",
                packageName = "com.google.android.apps.youtube.music",
                activityName = "com.google.android.apps.youtube.music.activities.MusicActivity",
                aliases = listOf("yt music", "youtube music")
            ),
            com.rexyy.app.launcher.AppInfo(
                label = "Instagram",
                packageName = "com.instagram.android",
                activityName = "com.instagram.mainactivity.MainActivity",
                aliases = listOf("insta", "ig")
            )
        )

        // YouTube must match YouTube and NOT YouTube Music
        val ytMatch = com.rexyy.app.launcher.AppMatcher.matchApp("YouTube", mockInstalled)
        assertTrue(ytMatch is com.rexyy.app.launcher.AppMatchResult.Exact)
        assertEquals("com.google.android.youtube", (ytMatch as com.rexyy.app.launcher.AppMatchResult.Exact).app.packageName)

        // YouTube Music must match YouTube Music
        val ytMusicMatch = com.rexyy.app.launcher.AppMatcher.matchApp("YouTube Music", mockInstalled)
        assertTrue(ytMusicMatch is com.rexyy.app.launcher.AppMatchResult.Exact)
        assertEquals("com.google.android.apps.youtube.music", (ytMusicMatch as com.rexyy.app.launcher.AppMatchResult.Exact).app.packageName)

        // Instagram must match Instagram
        val instaMatch = com.rexyy.app.launcher.AppMatcher.matchApp("Instagram", mockInstalled)
        assertTrue(instaMatch is com.rexyy.app.launcher.AppMatchResult.Exact)
        assertEquals("com.instagram.android", (instaMatch as com.rexyy.app.launcher.AppMatchResult.Exact).app.packageName)
    }

    @Test
    fun testPhase5AppSpecificSearchCommands() {
        // Test Case 4: "YouTube pe cricket search karo"
        val cmd1 = com.rexyy.app.router.RexyyCommandRouter.route("YouTube pe cricket search karo")
        assertTrue(cmd1 is VoiceCommand.AppSearch)
        val s1 = cmd1 as VoiceCommand.AppSearch
        assertEquals("youtube", s1.targetApp)
        assertEquals("cricket", s1.query)

        // Test Case 5: "Chrome me REXYY search karo"
        val cmd2 = com.rexyy.app.router.RexyyCommandRouter.route("Chrome me REXYY search karo")
        assertTrue(cmd2 is VoiceCommand.AppSearch)
        val s2 = cmd2 as VoiceCommand.AppSearch
        assertEquals("chrome", s2.targetApp)
        assertEquals("REXYY", s2.query)

        // Test Case 6: "Maps me Bangalore airport search karo"
        val cmd3 = com.rexyy.app.router.RexyyCommandRouter.route("Maps me Bangalore airport search karo")
        assertTrue(cmd3 is VoiceCommand.AppSearch)
        val s3 = cmd3 as VoiceCommand.AppSearch
        assertEquals("maps", s3.targetApp)
        assertEquals("Bangalore airport", s3.query)

        // Test Case 7: "Spotify pe Arijit Singh search karo"
        val cmd4 = com.rexyy.app.router.RexyyCommandRouter.route("Spotify pe Arijit Singh search karo")
        assertTrue(cmd4 is VoiceCommand.AppSearch)
        val s4 = cmd4 as VoiceCommand.AppSearch
        assertEquals("spotify", s4.targetApp)
        assertEquals("Arijit Singh", s4.query)

        // Test Case 8: "YouTube kholo aur cricket search karo"
        val cmd5 = com.rexyy.app.router.RexyyCommandRouter.route("YouTube kholo aur cricket search karo")
        assertTrue(cmd5 is VoiceCommand.AppSearch)
        val s5 = cmd5 as VoiceCommand.AppSearch
        assertEquals("youtube", s5.targetApp)
        assertEquals("cricket", s5.query)
    }

    @Test
    fun testPhase5SearchExecutionAndDiagnostics() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        com.rexyy.app.router.CommandDiagnosticLogger.clear()

        val searchCmd = VoiceCommand.AppSearch(
            targetApp = "youtube",
            query = "cricket",
            rawInput = "YouTube pe cricket search karo"
        )
        val result = com.rexyy.app.voice.VoiceCommandExecutor.execute(searchCmd, context)

        val latestLog = com.rexyy.app.router.CommandDiagnosticLogger.getLatest()
        assertNotNull(latestLog)
        assertEquals("YouTube pe cricket search karo", latestLog?.rawCommand)
        assertEquals("AppSearch", latestLog?.detectedIntent)
        assertEquals("YouTube", latestLog?.targetApp)
        assertEquals("cricket", latestLog?.searchQuery)
        assertNotNull(latestLog?.verificationResult)
    }
}

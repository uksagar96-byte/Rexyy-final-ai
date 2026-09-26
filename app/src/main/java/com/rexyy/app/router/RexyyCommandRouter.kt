package com.rexyy.app.router

import com.rexyy.app.network.provider.AiProviderType
import com.rexyy.app.task.TaskPlanner
import com.rexyy.app.voice.VoiceCommand
import java.util.regex.Pattern

object RexyyCommandRouter {

    /**
     * Common entry point for BOTH voice and text input.
     * Routes local commands directly to local actions with zero AI latency,
     * or yields an AiChat command when generative AI is needed.
     */
    fun route(input: String): VoiceCommand {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return VoiceCommand.AiChat("")

        // 1. Explicit AI Provider overrides (e.g., "Ask Gemini what is quantum physics")
        val lower = trimmed.lowercase()
        if (lower.startsWith("ask gemini ") || lower.startsWith("gemini se pucho ")) {
            val query = trimmed.replace("(?i)^ask gemini\\s+(to\\s+)?".toRegex(), "")
                .replace("(?i)^gemini se pucho\\s+".toRegex(), "")
                .trim()
            return VoiceCommand.AiChat(prompt = query.ifBlank { trimmed }, providerOverride = AiProviderType.GEMINI)
        }
        if (lower.startsWith("ask openai ") || lower.startsWith("ask chatgpt ") || lower.startsWith("openai se pucho ")) {
            val query = trimmed.replace("(?i)^ask (openai|chatgpt)\\s+(to\\s+)?".toRegex(), "")
                .replace("(?i)^openai se pucho\\s+".toRegex(), "")
                .trim()
            return VoiceCommand.AiChat(prompt = query.ifBlank { trimmed }, providerOverride = AiProviderType.OPENAI)
        }
        if (lower.startsWith("ask openrouter ") || lower.startsWith("openrouter se pucho ") ||
            lower.startsWith("ask claude ") || lower.startsWith("claude se pucho ")) {
            val query = trimmed.replace("(?i)^ask (openrouter|claude)\\s+(to\\s+)?".toRegex(), "")
                .replace("(?i)^(openrouter|claude) se pucho\\s+".toRegex(), "")
                .trim()
            return VoiceCommand.AiChat(prompt = query.ifBlank { trimmed }, providerOverride = AiProviderType.OPENROUTER)
        }

        // 2. Check for universal app & web search (including compound patterns like "YouTube kholo aur cricket search karo")
        val appSearch = parseAppSearchCommand(trimmed, lower)
        if (appSearch != null) {
            return appSearch.toVoiceCommand()
        }

        // 3. Check for multi-step task conjunctions ("and", "aur", "then", "phir")
        val plannedTask = TaskPlanner.planTask(trimmed) { singleQuery -> routeSingleAction(singleQuery).toVoiceCommand() }
        if (plannedTask != null) {
            return VoiceCommand.MultiStepTask(
                steps = plannedTask.steps.map { it.command },
                description = plannedTask.title,
                rawInput = trimmed
            )
        }

        // 4. Single action routing via structured LocalIntent
        val intent = parseToLocalIntent(trimmed)
        return intent.toVoiceCommand()
    }

    /**
     * Parses input into strongly typed LocalIntent with structured entities and intent metadata.
     */
    fun parseToLocalIntent(input: String): LocalIntent {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return LocalIntent.FallbackToAi(prompt = "", rawCommand = "")
        return routeSingleAction(trimmed)
    }

    private fun routeSingleAction(rawInput: String): LocalIntent {
        val trimmed = rawInput.trim().trimEnd('.', '!', '?', ',')
        val lower = trimmed.lowercase().trim()

        // --- 0. Wake Word & Conversational Status ---
        if (isWakeWordCommand(lower)) {
            return LocalIntent.WakeWord(rawCommand = trimmed)
        }
        if (isStopCommand(lower)) {
            return LocalIntent.Stop(rawCommand = trimmed)
        }
        if (isRepeatCommand(lower)) {
            return LocalIntent.RepeatLast(rawCommand = trimmed)
        }
        if (isWhatCanYouDoCommand(lower)) {
            return LocalIntent.WhatCanYouDo(rawCommand = trimmed)
        }
        if (isAreYouThereCommand(lower)) {
            return LocalIntent.AreYouThere(rawCommand = trimmed)
        }

        // --- 0.1 Close / Exit / Minimize ---
        val closeIntent = parseCloseCommand(trimmed, lower)
        if (closeIntent != null) return closeIntent

        // --- 0.2 Local Device Telemetry & Sensors (Battery, Charging, Date, Time, Flashlight) ---
        if (isChargingCommand(lower)) {
            return LocalIntent.CheckCharging(rawCommand = trimmed)
        }
        if (isBatteryCommand(lower)) {
            return LocalIntent.GetBattery(rawCommand = trimmed)
        }
        if (isNotificationCommand(lower)) {
            return LocalIntent.ReadNotifications(rawCommand = trimmed)
        }
        if (isDateCommand(lower)) {
            return LocalIntent.GetDate(rawCommand = trimmed)
        }
        if (isTimeCommand(lower)) {
            return LocalIntent.GetTime(rawCommand = trimmed)
        }
        val flashlightIntent = parseFlashlightCommand(trimmed, lower)
        if (flashlightIntent != null) return flashlightIntent

        // --- 0.3 Universal First-Class App & Web Search ---
        val appSearchIntent = parseAppSearchCommand(trimmed, lower)
        if (appSearchIntent != null) return appSearchIntent

        // --- A. Incoming Call & Telephony Control ---
        if (isWhoIsCallingCommand(lower)) {
            return LocalIntent.CheckCaller(rawCommand = trimmed)
        }
        if (isAnswerCallCommand(lower)) {
            return LocalIntent.AnswerCall(rawCommand = trimmed)
        }
        if (isRejectCallCommand(lower)) {
            return LocalIntent.RejectCall(rawCommand = trimmed)
        }

        // --- B. Contacts Search Local ---
        val contactsSearchIntent = parseContactsSearchCommand(trimmed, lower)
        if (contactsSearchIntent != null) return contactsSearchIntent

        // --- C. Phone Calls ---
        val callIntent = parseCallCommand(trimmed, lower)
        if (callIntent != null) return callIntent

        // --- D. WhatsApp Actions ---
        val whatsAppIntent = parseWhatsAppCommand(trimmed, lower)
        if (whatsAppIntent != null) return whatsAppIntent

        // --- E. Camera & Media ---
        if (isCameraCommand(lower)) {
            return LocalIntent.OpenCamera(rawCommand = trimmed)
        }

        // --- F. Audio & Volume ---
        val volumeIntent = parseVolumeCommand(trimmed, lower)
        if (volumeIntent != null) return volumeIntent

        // --- G. Brightness ---
        val brightnessIntent = parseBrightnessCommand(trimmed, lower)
        if (brightnessIntent != null) return brightnessIntent

        // --- H. System Settings, Wi-Fi & Bluetooth ---
        if (isBluetoothCommand(lower)) {
            val turnOn = !lower.contains("off") && !lower.contains("band")
            return LocalIntent.BluetoothControl(turnOn = turnOn, rawCommand = trimmed)
        }
        if (isWifiCommand(lower)) {
            return LocalIntent.WifiControl(queryState = lower.contains("kya") || lower.contains("status"), rawCommand = trimmed)
        }
        val settingsIntent = parseSettingsCommand(trimmed, lower)
        if (settingsIntent != null) return settingsIntent

        // --- I. Alarms & Timers ---
        if (isCancelAlarmCommand(lower)) {
            return LocalIntent.CancelAlarm(rawCommand = trimmed)
        }
        if (isCancelTimerCommand(lower)) {
            return LocalIntent.CancelTimer(rawCommand = trimmed)
        }
        val timerIntent = parseTimerCommand(trimmed, lower)
        if (timerIntent != null) return timerIntent

        val alarmIntent = parseAlarmCommand(trimmed, lower)
        if (alarmIntent != null) return alarmIntent

        // --- J. Calendar & Contacts App ---
        if (isCalendarCommand(lower)) {
            return LocalIntent.OpenCalendar(rawCommand = trimmed)
        }
        if (isContactsCommand(lower)) {
            return LocalIntent.OpenContacts(rawCommand = trimmed)
        }

        // --- K. Device Info / Status ---
        if (isDeviceInfoCommand(lower)) {
            return LocalIntent.GetDeviceStatus(rawCommand = trimmed)
        }

        // --- L. SMS Messaging ---
        val smsIntent = parseSmsCommand(trimmed, lower)
        if (smsIntent != null) return smsIntent

        // --- M. Open App (Dynamic Installed Apps Matching & Aliases) ---
        val openAppIntent = parseOpenAppCommand(trimmed, lower)
        if (openAppIntent != null) return openAppIntent

        // --- N. Accessibility System Actions ---
        val accessIntent = parseAccessibilityAction(trimmed, lower)
        if (accessIntent != null) return accessIntent

        // --- O. Web Search ---
        val searchIntent = parseSearchCommand(trimmed, lower)
        if (searchIntent != null) return searchIntent

        // --- P. Reminders ---
        if (isReminderCommand(lower)) {
            val title = extractReminderTitle(trimmed, lower)
            return LocalIntent.SetReminder(title = title, rawCommand = trimmed)
        }

        // Default: Forward to Generative AI model
        return LocalIntent.FallbackToAi(prompt = trimmed, rawCommand = trimmed)
    }

    private fun isWakeWordCommand(lower: String): Boolean {
        return lower == "hello rexyy" || lower == "hello rex" ||
                lower == "hey rexyy" || lower == "hey rex" ||
                lower == "hi rexyy" || lower == "hi rex" ||
                lower == "rexyy" || lower == "rex" ||
                lower == "ok rexyy" || lower == "activate rexyy"
    }

    private fun isStopCommand(lower: String): Boolean {
        return lower == "stop" || lower == "ruko" || lower == "ruk ja" || lower == "bas" ||
                lower == "cancel" || lower == "cancel karo" || lower == "shut up" ||
                lower == "chup" || lower == "chup raho" || lower == "shant raho" ||
                lower == "roko" || lower == "stop listening" || lower == "stop speaking"
    }

    private fun parseCloseCommand(raw: String, lower: String): LocalIntent.CloseApp? {
        val closePhrases = listOf(
            "exit", "close", "minimize",
            "background me jao", "background mein jao", "go to background",
            "background me chale jao", "background mein chale jao", "send to background",
            "minimize karo", "minimize app", "close app", "app band karo",
            "band karo", "chale jao", "hide", "back jao",
            "go home", "home screen jao", "home jao", "home par jao", "go to home"
        )
        if (lower in closePhrases) {
            val target = if (lower.contains("home")) "home" else if (lower.contains("exit")) "exit" else "background"
            return LocalIntent.CloseApp(target = target, rawCommand = raw)
        }
        return null
    }

    private fun isChargingCommand(lower: String): Boolean {
        return lower.contains("charger connect") ||
                lower.contains("charger laga") ||
                lower.contains("charging ho rahi") ||
                lower.contains("charging status") ||
                lower.contains("is phone charging") ||
                lower.contains("is charger connected") ||
                lower == "charging"
    }

    private fun isNotificationCommand(lower: String): Boolean {
        return lower.contains("notification sunao") ||
                lower.contains("notifications sunao") ||
                lower.contains("notification padho") ||
                lower.contains("notifications padho") ||
                lower.contains("read notifications") ||
                lower.contains("read my notifications") ||
                lower.contains("read notification") ||
                lower.contains("show notifications") ||
                lower.contains("check notifications") ||
                lower.contains("notification status") ||
                lower.contains("kya notification aaya") ||
                lower == "notifications" ||
                lower == "notification"
    }

    private fun isBatteryCommand(lower: String): Boolean {
        // Exclude explicit settings triggers like "battery settings"
        if (lower.contains("battery setting") || lower.contains("battery saver setting")) return false

        return lower.contains("battery") || lower.contains("charge kitna") ||
                lower.contains("charging") || lower.contains("battery kitni") ||
                lower.contains("battery status") || lower.contains("battery level") ||
                lower.contains("battery percentage")
    }

    private fun isDateCommand(lower: String): Boolean {
        return lower.contains("date kya") || lower.contains("tarikh kya") || lower.contains("tareekh kya") ||
                lower.contains("what is the date") || lower.contains("what date is it") || lower.contains("today's date") ||
                lower == "date" || lower == "tarikh" || lower == "aaj ki date" || lower == "today date" ||
                lower.contains("aaj ki date") || lower.contains("aaj konsi tarikh") || lower.contains("aaj konsi date")
    }

    private fun isTimeCommand(lower: String): Boolean {
        return lower.contains("time kya") || lower.contains("samay kya") || lower.contains("kitne baje") ||
                lower.contains("what is the time") || lower.contains("what time is it") || lower.contains("current time") ||
                lower == "time" || lower == "samay" || lower.contains("kya time ho raha") || lower.contains("time batao")
    }

    private fun parseFlashlightCommand(raw: String, lower: String): LocalIntent.ToggleFlashlight? {
        if (lower.contains("torch") || lower.contains("flashlight") || lower.contains("flash light")) {
            val turnOff = lower.contains("off") || lower.contains("band") || lower.contains("bujhao")
            return LocalIntent.ToggleFlashlight(turnOn = !turnOff, rawCommand = raw)
        }
        return null
    }

    private fun parseAppSearchCommand(raw: String, lower: String): LocalIntent.AppSearch? {
        // 1. "YouTube pe cricket search karo" / "Chrome me REXYY search karo" / "Maps me Bangalore airport search karo" / "Spotify pe Arijit Singh search karo"
        val appPrefixHinglish = Regex("(?i)^(youtube\\s+music|yt\\s+music|youtube|yt|chrome|google\\s*chrome|browser|google\\s*maps|maps|spotify|instagram|insta|play\\s*store|google\\s*play|playstore|google)\\s+(pe|par|me|mein|main|par\\s+bhi)\\s+(.+?)\\s+(search\\s+karo|search\\s+kar\\s+do|search|dhundho|dhundo|play\\s+karo|dekho)$").find(raw)
        if (appPrefixHinglish != null) {
            val app = normalizeSearchApp(appPrefixHinglish.groupValues[1])
            val q = cleanSearchQuery(appPrefixHinglish.groupValues[3])
            if (q.isNotBlank()) {
                return LocalIntent.AppSearch(targetApp = app, query = q, rawCommand = raw)
            }
        }

        // 2. "Search cricket on YouTube" / "Search for cricket in YouTube" / "Search Bangalore airport on Maps"
        val searchOnApp = Regex("(?i)^search\\s+(for\\s+)?(.+?)\\s+(on|in|using)\\s+(youtube\\s+music|yt\\s+music|youtube|yt|chrome|google\\s*chrome|browser|google\\s*maps|maps|spotify|instagram|insta|play\\s*store|google\\s*play|playstore|google)$").find(raw)
        if (searchOnApp != null) {
            val q = cleanSearchQuery(searchOnApp.groupValues[2])
            val app = normalizeSearchApp(searchOnApp.groupValues[4])
            if (q.isNotBlank()) {
                return LocalIntent.AppSearch(targetApp = app, query = q, rawCommand = raw)
            }
        }

        // 3. "X kholo aur Y search karo" / "X open karo and search Y" (e.g. "YouTube kholo aur cricket search karo")
        val openAndSearch = Regex("(?i)^(.+?)\\s+(kholo|open karo|open|chalao|start karo)\\s+(aur|and|phir|then)\\s+(.+?)\\s+(search\\s+karo|search\\s+kar\\s+do|search|dhundho|dhundo)$").find(raw)
        if (openAndSearch != null) {
            val app = normalizeSearchApp(openAndSearch.groupValues[1])
            val q = cleanSearchQuery(openAndSearch.groupValues[4])
            if (q.isNotBlank()) {
                return LocalIntent.AppSearch(targetApp = app, query = q, rawCommand = raw)
            }
        }

        // 4. "Y ko X par dhundho" (e.g. "cricket ko YouTube par dhundho")
        val queryOnApp = Regex("(?i)^(.+?)\\s+ko\\s+(youtube\\s+music|yt\\s+music|youtube|yt|chrome|maps|google\\s*maps|spotify|instagram|play\\s*store|google)\\s+(par|pe|me|mein|main)\\s+(dhundho|dhundo|search\\s+karo|search)$").find(raw)
        if (queryOnApp != null) {
            val q = cleanSearchQuery(queryOnApp.groupValues[1])
            val app = normalizeSearchApp(queryOnApp.groupValues[2])
            if (q.isNotBlank()) {
                return LocalIntent.AppSearch(targetApp = app, query = q, rawCommand = raw)
            }
        }

        // 6. "Y search karo X pe" (e.g. "cricket search karo YouTube pe")
        val querySearchOnApp = Regex("(?i)^(.+?)\\s+(search\\s+karo|dhundho|dhundo)\\s+(youtube\\s+music|yt\\s+music|youtube|yt|chrome|maps|google\\s*maps|spotify|instagram|play\\s*store)\\s+(pe|par|me|mein|main)$").find(raw)
        if (querySearchOnApp != null) {
            val q = cleanSearchQuery(querySearchOnApp.groupValues[1])
            val app = normalizeSearchApp(querySearchOnApp.groupValues[3])
            if (q.isNotBlank()) {
                return LocalIntent.AppSearch(targetApp = app, query = q, rawCommand = raw)
            }
        }

        // 7. "Y ke videos search karo" (e.g. "cricket ke videos search karo")
        val videoSearch = Regex("(?i)^(.+?)\\s+ke\\s+videos?\\s+(search\\s+karo|search|dhundho|dikhao)$").find(raw)
        if (videoSearch != null) {
            val q = cleanSearchQuery(videoSearch.groupValues[1])
            if (q.isNotBlank()) {
                return LocalIntent.AppSearch(targetApp = "youtube", query = q, searchType = "video", rawCommand = raw)
            }
        }

        // 8. "Search karo REXYY kya hai" / "Google karo X"
        if (lower.startsWith("search karo ") || lower.startsWith("google karo ")) {
            val q = cleanSearchQuery(raw.substring(12))
            if (q.isNotBlank()) {
                return LocalIntent.AppSearch(targetApp = "web", query = q, rawCommand = raw)
            }
        }

        return null
    }

    private fun cleanSearchQuery(query: String): String {
        return query.trim()
            .removeSurrounding("\"")
            .removeSurrounding("'")
            .replace(Regex("(?i)^(for|about)\\s+"), "")
            .replace(Regex("(?i)\\s+(ko|ke|ka|ki)$"), "")
            .trim()
    }

    private fun normalizeSearchApp(app: String): String {
        val l = app.lowercase().trim()
        return when {
            l.contains("youtube music") || l.contains("yt music") || l == "ytmusic" -> "youtube music"
            l.contains("youtube") || l == "yt" -> "youtube"
            l.contains("chrome") || l.contains("browser") -> "chrome"
            l.contains("map") -> "maps"
            l.contains("spotify") -> "spotify"
            l.contains("instagram") || l.contains("insta") -> "instagram"
            l.contains("play") || l.contains("store") -> "playstore"
            l == "google" -> "web"
            else -> l
        }
    }

    private fun isRepeatCommand(lower: String): Boolean {
        return lower == "repeat that" || lower == "repeat" ||
                lower == "repeat karo" || lower == "dobara bolo" ||
                lower == "phir se bolo" || lower == "kya bola"
    }

    private fun isWhatCanYouDoCommand(lower: String): Boolean {
        return lower == "what can you do" || lower == "what can you do?" ||
                lower == "kya kar sakte ho" || lower == "tum kya kar sakte ho" ||
                lower == "help" || lower == "commands" || lower == "features"
    }

    private fun isAreYouThereCommand(lower: String): Boolean {
        return lower == "are you there" || lower == "are you there?" ||
                lower == "are you listening" || lower == "kya tum wahan ho" ||
                lower == "sun rahe ho"
    }

    private fun isCancelAlarmCommand(lower: String): Boolean {
        return lower.contains("alarm") && (
                lower.contains("cancel") || lower.contains("dismiss") ||
                        lower.contains("band") || lower.contains("hatao")
                )
    }

    private fun isCancelTimerCommand(lower: String): Boolean {
        return lower.contains("timer") && (
                lower.contains("cancel") || lower.contains("dismiss") ||
                        lower.contains("band") || lower.contains("hatao") || lower.contains("stop")
                )
    }

    private fun parseContactsSearchCommand(raw: String, lower: String): LocalIntent.FindContact? {
        val findMatcher = Regex("(?i)^(find|search|lookup)\\s+(.+?)\\s+in\\s+(my\\s+)?contacts$").find(raw)
        if (findMatcher != null) {
            val name = cleanTargetName(findMatcher.groupValues[2])
            return LocalIntent.FindContact(contactName = name, rawCommand = raw)
        }

        val hindiMatcher = Regex("(?i)^(.+?)\\s+(ka|ki)?\\s*(contact|number|phone)\\s+(dhundo|search karo|batao)$").find(raw)
        if (hindiMatcher != null) {
            val name = cleanTargetName(hindiMatcher.groupValues[1])
            return LocalIntent.FindContact(contactName = name, rawCommand = raw)
        }

        if (lower.startsWith("find contact ") || lower.startsWith("search contact ")) {
            val name = cleanTargetName(raw.substring(13))
            return LocalIntent.FindContact(contactName = name, rawCommand = raw)
        }

        return null
    }

    private fun cleanTargetName(raw: String): String {
        var clean = raw.trim()
        var changed = true
        while (changed) {
            val prev = clean
            clean = clean.replace("(?i)^\\s*(to|on|for|pe|par|ko|send|message|call)\\s+".toRegex(), "")
                .replace("(?i)\\s+(ko|pe|par|bhejo|to|on)\\s*$".toRegex(), "")
                .trim()
            changed = (clean != prev)
        }
        return clean.ifBlank { "Contact" }
    }

    private fun isWhoIsCallingCommand(lower: String): Boolean {
        return lower.contains("who is calling") ||
                lower.contains("kaun call kar raha hai") ||
                lower.contains("kiska phone hai") ||
                lower.contains("kiska call hai") ||
                lower.contains("who is on the call") ||
                lower.contains("caller kaun hai")
    }

    private fun isAnswerCallCommand(lower: String): Boolean {
        return lower.contains("answer call") ||
                lower.contains("answer the call") ||
                lower.contains("call uthao") ||
                lower.contains("call receive karo") ||
                lower.contains("phone uthao") ||
                lower.contains("receive call")
    }

    private fun isRejectCallCommand(lower: String): Boolean {
        return lower.contains("reject call") ||
                lower.contains("reject the call") ||
                lower.contains("call reject karo") ||
                lower.contains("call kato") ||
                lower.contains("phone kato") ||
                lower.contains("cut call") ||
                lower.contains("decline call")
    }

    private fun parseCallCommand(raw: String, lower: String): LocalIntent.CallContact? {
        val isCallTrigger = lower.startsWith("call ") ||
                lower.startsWith("phone ") ||
                lower.startsWith("dial ") ||
                lower.endsWith(" ko call karo") ||
                lower.endsWith(" ko call lagao") ||
                lower.endsWith(" ko phone lagao") ||
                lower.endsWith(" ko phone karo") ||
                lower.contains(" call karo")

        if (!isCallTrigger) return null

        var target = raw
        if (lower.startsWith("call ")) {
            target = raw.substring(5).trim()
        } else if (lower.startsWith("phone ")) {
            target = raw.substring(6).trim()
        } else if (lower.startsWith("dial ")) {
            target = raw.substring(5).trim()
        } else {
            target = raw.replace("(?i)\\s+(ko)?\\s+(call|phone)\\s+(karo|lagao|milao)".toRegex(), "").trim()
        }

        val cleanedTarget = cleanTargetName(target)

        // Avoid triggering on "call control" phrases
        if (cleanedTarget.isBlank() || cleanedTarget.equals("reject", ignoreCase = true) || cleanedTarget.equals("answer", ignoreCase = true)) {
            return null
        }

        return LocalIntent.CallContact(target = cleanedTarget, rawCommand = raw)
    }

    private fun parseWhatsAppCommand(raw: String, lower: String): LocalIntent? {
        if (!lower.contains("whatsapp") && !lower.contains("whats app")) return null

        // "Open WhatsApp" / "WhatsApp kholo"
        if (lower == "open whatsapp" || lower == "whatsapp kholo" || lower == "whatsapp open karo" ||
            lower == "open whats app" || lower == "whats app kholo" || lower == "whatsapp chalao" ||
            lower == "whatsapp" || lower == "whatsapp open kar do" || lower == "whatsapp khol do"
        ) {
            return LocalIntent.OpenWhatsApp(rawCommand = raw)
        }

        // Colon syntax: "Send WhatsApp to Ramzan: Hello there" or "Ramzan ko whatsapp message bhejo: Kal milte hain"
        val colonSplit = raw.split(":")
        if (colonSplit.size >= 2) {
            val header = colonSplit[0].trim()
            val body = colonSplit.subList(1, colonSplit.size).joinToString(":").trim()
            val target = cleanTargetName(extractWhatsAppTarget(header))
            return LocalIntent.WhatsAppMessage(target = target, body = body, rawCommand = raw)
        }

        // "send WhatsApp message on Ramzan" / "send whatsapp message to Ramzan" / "send whatsapp to Ramzan"
        val sendToMatch = Regex("(?i)^(send|bhejo)?\\s*whats\\s*app\\s+(message\\s+)?(to|on|for)?\\s*(.+)$").find(raw)
        if (sendToMatch != null) {
            val target = cleanTargetName(sendToMatch.groupValues[4])
            if (target.isNotBlank()) {
                return LocalIntent.WhatsAppMessage(target = target, body = "", rawCommand = raw)
            }
        }

        // Verb first with message: "Rahul ko WhatsApp message karo ki kal milte hain" / "Ramzan ko WhatsApp pe bol kal milte hain"
        val msgWithVerbPattern = Regex("(?i)^(.+?)\\s+ko\\s+whats\\s*app\\s*(?:par|pe|main|me\\b)?\\s*(?:message\\s+)?(?:karo|bhejo|bolo|bol|likho|send karo|kaho|likh do|bhej do)\\s+(?:ki\\s+|that\\s+)?(.+)$")
        val msgWithVerbMatch = msgWithVerbPattern.find(raw)
        if (msgWithVerbMatch != null) {
            val target = cleanTargetName(msgWithVerbMatch.groupValues[1])
            val message = msgWithVerbMatch.groupValues[2].trim()
            if (message.isNotBlank() && !message.equals("message", ignoreCase = true)) {
                return LocalIntent.WhatsAppMessage(target = target, body = message, rawCommand = raw)
            }
        }

        // "Ramzan ko whatsapp message bhejo" / "Rahul ko WhatsApp message karo" (no body specified)
        val hindiSendMatch = Regex("(?i)^(.+?)\\s+ko\\s+whats\\s*app\\s*(?:par|pe|main|me\\b)?\\s*(?:message\\s+)?(?:bhejo|karo|send karo)?$").find(raw)
        if (hindiSendMatch != null) {
            val target = cleanTargetName(hindiSendMatch.groupValues[1])
            if (target.isNotBlank()) {
                return LocalIntent.WhatsAppMessage(target = target, body = "", rawCommand = raw)
            }
        }

        // Verb at end: "Ramzan ko WhatsApp pe kal milte hain bhejo" / "Rahul ko WhatsApp pe hello bhejo"
        val verbAtEndPattern = Regex("(?i)^(.+?)\\s+ko\\s+whats\\s*app\\s*(?:par|pe|main|me\\b)?\\s*(?:message\\s+)?(.+?)\\s+(bhejo|bhej do|send karo|likho|likh do)$")
        val verbAtEndMatch = verbAtEndPattern.find(raw)
        if (verbAtEndMatch != null) {
            val target = cleanTargetName(verbAtEndMatch.groupValues[1])
            val message = verbAtEndMatch.groupValues[2].trim()
            if (message.isNotBlank() && !message.equals("message", ignoreCase = true)) {
                return LocalIntent.WhatsAppMessage(target = target, body = message, rawCommand = raw)
            }
        }

        // "Rahul ka whatsapp chat kholo" / "Open Rahul whatsapp chat"
        val chatMatch = Regex("(?i)^(.+?)\\s+(ka|ki)?\\s*whatsapp\\s+chat\\s+(kholo|open karo)$").find(raw)
        if (chatMatch != null) {
            val target = cleanTargetName(chatMatch.groupValues[1])
            return LocalIntent.WhatsAppChat(target = target, rawCommand = raw)
        }

        return LocalIntent.OpenWhatsApp(rawCommand = raw)
    }

    private fun extractWhatsAppTarget(header: String): String {
        return header.replace("(?i)(send|bhejo|message|whatsapp|whats app|par|pe|ko|to|on|for)".toRegex(), "")
            .trim()
            .ifBlank { "Contact" }
    }

    private fun isCameraCommand(lower: String): Boolean {
        return lower.contains("camera kholo") ||
                lower.contains("camera open karo") ||
                lower.contains("open camera") ||
                lower.contains("camera chalao") ||
                lower.contains("camera chala do") ||
                lower.contains("photo khicho") ||
                lower.contains("take photo") ||
                lower.contains("take a picture") ||
                lower == "camera"
    }

    private fun parseVolumeCommand(raw: String, lower: String): LocalIntent.AdjustVolume? {
        if (lower == "mute" || lower == "silent" || lower.contains("unmute")) {
            return LocalIntent.AdjustVolume(mute = (lower != "unmute"), rawCommand = raw)
        }

        if (!lower.contains("volume") && !lower.contains("awaaz") && !lower.contains("awaz") && !lower.contains("sound")) {
            return null
        }

        if (lower.contains("mute") || lower.contains("awaaz band") || lower.contains("silent") || lower == "mute") {
            return LocalIntent.AdjustVolume(mute = true, rawCommand = raw)
        }

        // Percentage check: "set volume to 50%" or "volume 50 percent karo" or "volume 80% karo"
        val percentMatcher = Pattern.compile("(\\d{1,3})\\s*(%|percent)").matcher(lower)
        if (percentMatcher.find()) {
            val p = percentMatcher.group(1)?.toIntOrNull()
            if (p != null) {
                return LocalIntent.AdjustVolume(percent = p.coerceIn(0, 100), rawCommand = raw)
            }
        }

        val isIncrease = lower.contains("increase") || lower.contains("up") ||
                lower.contains("raise") || lower.contains("badhao") ||
                lower.contains("jyada") || lower.contains("high") ||
                lower.contains("tez")
        val isDecrease = lower.contains("decrease") || lower.contains("down") ||
                lower.contains("lower") || lower.contains("kam") ||
                lower.contains("ghatao") || lower.contains("low") ||
                lower.contains("dheemi")

        return when {
            isIncrease -> LocalIntent.AdjustVolume(raise = true, rawCommand = raw)
            isDecrease -> LocalIntent.AdjustVolume(raise = false, rawCommand = raw)
            else -> LocalIntent.AdjustVolume(raise = true, rawCommand = raw)
        }
    }

    private fun parseBrightnessCommand(raw: String, lower: String): LocalIntent.SetBrightness? {
        if (!lower.contains("brightness") && !lower.contains("roshni")) return null

        // Percentage check: "brightness 50 percent karo", "brightness 50% karo", "brightness 80 karo"
        val percentMatcher = Pattern.compile("(\\d{1,3})\\s*(%|percent)?").matcher(lower)
        if (percentMatcher.find()) {
            val p = percentMatcher.group(1)?.toIntOrNull()
            if (p != null && (p in 0..100)) {
                return LocalIntent.SetBrightness(percent = p, rawCommand = raw)
            }
        }

        if (lower.contains("full") || lower.contains("100%")) {
            return LocalIntent.SetBrightness(percent = 100, rawCommand = raw)
        }
        if (lower.contains("zero") || lower.contains("minimum") || lower.contains("sabse kam")) {
            return LocalIntent.SetBrightness(percent = 10, rawCommand = raw)
        }
        if (lower.contains("aadhi") || lower.contains("half") || lower.contains("medium")) {
            return LocalIntent.SetBrightness(percent = 50, rawCommand = raw)
        }

        val isIncrease = lower.contains("increase") || lower.contains("up") || lower.contains("badhao") || lower.contains("jyada")
        val isDecrease = lower.contains("decrease") || lower.contains("down") || lower.contains("kam") || lower.contains("ghatao")

        return when {
            isIncrease -> LocalIntent.SetBrightness(raise = true, rawCommand = raw)
            isDecrease -> LocalIntent.SetBrightness(raise = false, rawCommand = raw)
            else -> LocalIntent.SetBrightness(raise = true, rawCommand = raw)
        }
    }

    private fun isBluetoothCommand(lower: String): Boolean {
        return lower.contains("bluetooth") && (
                lower.contains("on") || lower.contains("off") ||
                        lower.contains("kholo") || lower.contains("karo") ||
                        lower.contains("band") || lower.contains("chalu") ||
                        lower.contains("open") || lower.contains("settings")
                )
    }

    private fun isWifiCommand(lower: String): Boolean {
        return (lower.contains("wi-fi") || lower.contains("wifi")) && (
                lower.contains("kholo") || lower.contains("open") ||
                        lower.contains("settings") || lower.contains("on") ||
                        lower.contains("off") || lower.contains("chalu") ||
                        lower.contains("band")
                )
    }

    private fun parseSettingsCommand(raw: String, lower: String): LocalIntent.OpenSettings? {
        if (lower == "settings" || lower == "setting" || lower == "settings kholo" ||
            lower == "setting kholo" || lower == "open settings" || lower == "system settings" ||
            lower == "settings open karo" || lower == "settings open kar do"
        ) {
            return LocalIntent.OpenSettings(subSettings = "", rawCommand = raw)
        }
        if (lower.contains("display setting") || lower.contains("screen setting")) {
            return LocalIntent.OpenSettings(subSettings = "display", rawCommand = raw)
        }
        if (lower.contains("sound setting") || lower.contains("audio setting") || lower.contains("ringtone setting")) {
            return LocalIntent.OpenSettings(subSettings = "sound", rawCommand = raw)
        }
        if (lower.contains("battery setting") || lower.contains("battery saver setting")) {
            return LocalIntent.OpenSettings(subSettings = "battery", rawCommand = raw)
        }
        if (lower.contains("app setting") || lower.contains("manage apps") || lower.contains("installed apps")) {
            return LocalIntent.OpenSettings(subSettings = "apps", rawCommand = raw)
        }
        if (lower.contains("developer option")) {
            return LocalIntent.OpenSettings(subSettings = "developer", rawCommand = raw)
        }
        if (lower.contains("location setting") || lower.contains("gps setting")) {
            return LocalIntent.OpenSettings(subSettings = "location", rawCommand = raw)
        }
        if (lower.contains("storage setting") || lower.contains("internal storage")) {
            return LocalIntent.OpenSettings(subSettings = "storage", rawCommand = raw)
        }
        if (lower.contains("network setting") || lower.contains("data usage setting")) {
            return LocalIntent.OpenSettings(subSettings = "network", rawCommand = raw)
        }
        if (lower.endsWith(" settings") || lower.startsWith("settings ") || lower.contains("settings kholo") || lower.contains("setting kholo")) {
            val sub = raw.replace("(?i)(open|settings|setting|kholo|chalao)".toRegex(), "").trim()
            return LocalIntent.OpenSettings(subSettings = sub, rawCommand = raw)
        }
        return null
    }

    private fun parseTimerCommand(raw: String, lower: String): LocalIntent.SetTimer? {
        if (!lower.contains("timer")) return null

        val minMatcher = Pattern.compile("(\\d+)\\s*(minute|min|m)", Pattern.CASE_INSENSITIVE).matcher(lower)
        if (minMatcher.find()) {
            val mins = minMatcher.group(1)?.toIntOrNull() ?: 1
            return LocalIntent.SetTimer(seconds = mins * 60, message = "REXYY Timer", rawCommand = raw)
        }

        val secMatcher = Pattern.compile("(\\d+)\\s*(second|sec|s)", Pattern.CASE_INSENSITIVE).matcher(lower)
        if (secMatcher.find()) {
            val secs = secMatcher.group(1)?.toIntOrNull() ?: 30
            return LocalIntent.SetTimer(seconds = secs, message = "REXYY Timer", rawCommand = raw)
        }

        return LocalIntent.SetTimer(seconds = 300, message = "REXYY Timer", rawCommand = raw)
    }

    private fun parseAlarmCommand(raw: String, lower: String): LocalIntent.SetAlarm? {
        val isAlarmTrigger = lower.contains("alarm") || lower.contains("wake me up") ||
                lower.contains("baje uthao") || lower.contains("baje jagao")
        if (!isAlarmTrigger) return null

        // 1. Check for time with minutes like "7:30 am", "7:30 baje", "saadhe 7 baje"
        if (lower.contains("saadhe") || lower.contains("sadhe")) {
            val digitMatcher = Pattern.compile("(\\d{1,2})").matcher(lower)
            if (digitMatcher.find()) {
                var hour = digitMatcher.group(1)?.toIntOrNull() ?: 7
                if ((lower.contains("sham") || lower.contains("shaam") || lower.contains("raat") || lower.contains("pm")) && hour < 12) {
                    hour += 12
                }
                return LocalIntent.SetAlarm(hour = hour, minute = 30, message = "REXYY Alarm", rawCommand = raw)
            }
        }

        val colonMatcher = Pattern.compile("(\\d{1,2}):(\\d{2})\\s*(am|pm)?", Pattern.CASE_INSENSITIVE).matcher(lower)
        if (colonMatcher.find()) {
            var hour = colonMatcher.group(1)?.toIntOrNull() ?: 7
            val min = colonMatcher.group(2)?.toIntOrNull() ?: 0
            val amPm = colonMatcher.group(3)?.lowercase()
            if ((amPm == "pm" || lower.contains("sham") || lower.contains("shaam") || lower.contains("raat")) && hour < 12) {
                hour += 12
            }
            if (amPm == "am" && hour == 12) hour = 0
            return LocalIntent.SetAlarm(hour = hour, minute = min, message = "REXYY Alarm", rawCommand = raw)
        }

        // 2. Check for hour with "baje", "am", "pm", "o'clock"
        // e.g. "7 baje alarm laga do", "subah 6 baje ka alarm", "alarm lagao 8 baje"
        val hourMatcher = Pattern.compile("(\\d{1,2})\\s*(am|pm|baje|o'clock)", Pattern.CASE_INSENSITIVE).matcher(lower)
        if (hourMatcher.find()) {
            var hour = hourMatcher.group(1)?.toIntOrNull() ?: 7
            val modifier = hourMatcher.group(2)?.lowercase()
            val isPm = modifier == "pm" || lower.contains("sham") || lower.contains("shaam") || lower.contains("raat") || lower.contains("dophar")
            if (isPm && hour < 12) hour += 12
            if (modifier == "am" && hour == 12) hour = 0
            return LocalIntent.SetAlarm(hour = hour, minute = 0, message = "REXYY Alarm", rawCommand = raw)
        }

        // Fallback default alarm
        return LocalIntent.SetAlarm(hour = 7, minute = 0, message = "REXYY Alarm", rawCommand = raw)
    }

    private fun isCalendarCommand(lower: String): Boolean {
        return lower.contains("calendar kholo") ||
                lower.contains("open calendar") ||
                lower.contains("show calendar") ||
                lower == "calendar"
    }

    private fun isContactsCommand(lower: String): Boolean {
        return lower.contains("contacts kholo") ||
                lower.contains("open contacts") ||
                lower.contains("phonebook") ||
                lower == "contacts"
    }

    private fun isDeviceInfoCommand(lower: String): Boolean {
        return lower.contains("device information") ||
                lower.contains("device info") ||
                lower.contains("phone info") ||
                lower.contains("system info") ||
                lower.contains("phone details") ||
                lower.contains("device status") ||
                lower.contains("phone status")
    }

    private fun parseSmsCommand(raw: String, lower: String): LocalIntent.SendSms? {
        if (!lower.contains("message") && !lower.contains("sms") && !lower.contains("sandesh")) {
            return null
        }
        if (lower.contains("whatsapp") || lower.contains("whats app")) return null

        // 1. Colon syntax: "Send SMS to Rahul: kal milte hain" or "Rahul ko message bhejo: kal milte hain"
        val colonSplit = raw.split(":")
        if (colonSplit.size >= 2) {
            val header = colonSplit[0].trim()
            val body = colonSplit.subList(1, colonSplit.size).joinToString(":").trim()
            val target = cleanTargetName(header.replace("(?i)^(send|sms|message|bhejo|ko|to)\\s+".toRegex(), ""))
            return LocalIntent.SendSms(target = target, body = body, rawCommand = raw)
        }

        // 2. "Rahul ko message karo kal milte hain" / "Rahul ko SMS bhejo ki main 10 minute me aa raha hoon"
        val verbFirstPattern = Regex("(?i)^(.+?)\\s+ko\\s+(message|sms|sandesh)\\s*(karo|bhejo|bhej do|send karo|likho|likh do)\\s+(.+)$")
        val verbFirstMatch = verbFirstPattern.find(raw)
        if (verbFirstMatch != null) {
            val target = cleanTargetName(verbFirstMatch.groupValues[1])
            val rawBody = verbFirstMatch.groupValues[4].trim()
            val body = rawBody.replace("^(?i)(?:ki|that)\\s+".toRegex(), "").trim()
            return LocalIntent.SendSms(target = target, body = body, rawCommand = raw)
        }

        // 3. "Rahul ko kal milte hain message karo" / "Rahul ko kal milte hain SMS bhejo"
        val verbEndPattern = Regex("(?i)^(.+?)\\s+ko\\s+(.+?)\\s+(message|sms|sandesh)\\s*(karo|bhejo|bhej do|send karo)$")
        val verbEndMatch = verbEndPattern.find(raw)
        if (verbEndMatch != null) {
            val target = cleanTargetName(verbEndMatch.groupValues[1])
            val body = verbEndMatch.groupValues[2].trim()
            return LocalIntent.SendSms(target = target, body = body, rawCommand = raw)
        }

        // 4. English "send message to Rahul that kal milte hain" / "send sms to Rahul saying kal milte hain"
        val engSendPattern = Regex("(?i)^send\\s+(sms|message)\\s+(to\\s+)?(.+?)\\s+(that|saying)\\s+(.+)$")
        val engSendMatch = engSendPattern.find(raw)
        if (engSendMatch != null) {
            val target = cleanTargetName(engSendMatch.groupValues[3])
            val body = engSendMatch.groupValues[5].trim()
            return LocalIntent.SendSms(target = target, body = body, rawCommand = raw)
        }

        // 5. Empty body: "Rahul ko message karo" / "Rahul ko SMS bhejo"
        val emptyBodyPattern = Regex("(?i)^(.+?)\\s+ko\\s+(message|sms|sandesh)\\s*(karo|bhejo|bhej do|send karo)?$")
        val emptyBodyMatch = emptyBodyPattern.find(raw)
        if (emptyBodyMatch != null) {
            val target = cleanTargetName(emptyBodyMatch.groupValues[1])
            return LocalIntent.SendSms(target = target, body = "", rawCommand = raw)
        }

        // 6. English empty body: "send message to Rahul" / "send sms to Rahul"
        val engEmptyPattern = Regex("(?i)^send\\s+(sms|message)\\s+(to\\s+)?(.+)$")
        val engEmptyMatch = engEmptyPattern.find(raw)
        if (engEmptyMatch != null) {
            val target = cleanTargetName(engEmptyMatch.groupValues[3])
            return LocalIntent.SendSms(target = target, body = "", rawCommand = raw)
        }

        return null
    }

    private fun parseOpenAppCommand(raw: String, lower: String): LocalIntent.OpenApp? {
        // 1. Prefixes: "open <app>", "launch <app>", "start <app>", "kholo <app>", "khol do <app>", "chalao <app>"
        val openPrefixes = listOf(
            "open ", "launch ", "start ",
            "kholo ", "khol do ", "chalao ", "chalu karo "
        )
        for (prefix in openPrefixes) {
            if (lower.startsWith(prefix)) {
                val candidate = raw.substring(prefix.length).trim()
                val app = cleanAppName(candidate)
                if (app.isNotBlank() && !app.equals("settings", ignoreCase = true) && !app.equals("bluetooth", ignoreCase = true)) {
                    return LocalIntent.OpenApp(appName = app, rawCommand = raw)
                }
            }
        }

        // 2. Suffixes: "<app> kholo", "<app> open karo", "<app> chalao", "<app> khol do", "<app> chalu karo", "<app> open kar do"
        val hinglishSuffixes = listOf(
            " kholo", " open karo", " open kar do", " chalao", " chala do", " khol do", " chalu karo", " start karo"
        )
        for (suffix in hinglishSuffixes) {
            if (lower.endsWith(suffix)) {
                val candidate = raw.substring(0, raw.length - suffix.length).trim()
                val app = cleanAppName(candidate)
                if (app.isNotBlank() && !app.equals("settings", ignoreCase = true) && !app.equals("bluetooth", ignoreCase = true)) {
                    return LocalIntent.OpenApp(appName = app, rawCommand = raw)
                }
            }
        }

        return null
    }

    private fun cleanAppName(rawName: String): String {
        return rawName.replace("(?i)\\b(app|application)\\b".toRegex(), "").trim()
    }

    private fun parseSearchCommand(raw: String, lower: String): LocalIntent.WebSearch? {
        if (lower.startsWith("search google for ")) {
            return LocalIntent.WebSearch(raw.substring(18).trim(), raw)
        }
        if (lower.startsWith("search for ")) {
            return LocalIntent.WebSearch(raw.substring(11).trim(), raw)
        }
        if (lower.startsWith("search ")) {
            return LocalIntent.WebSearch(raw.substring(7).trim(), raw)
        }
        if (lower.endsWith(" search karo") || lower.endsWith(" google karo")) {
            val q = raw.replace("(?i)(search|google)\\s+karo".toRegex(), "").trim()
            if (q.isNotBlank()) {
                return LocalIntent.WebSearch(q, raw)
            }
        }
        return null
    }

    private fun isReminderCommand(lower: String): Boolean {
        return lower.startsWith("remind me ") || lower.contains("yaad dilao")
    }

    private fun extractReminderTitle(raw: String, lower: String): String {
        return raw.replace("(?i)^remind me to\\s+".toRegex(), "")
            .replace("(?i)^remind me\\s+".toRegex(), "")
            .replace("(?i)yaad dilao".toRegex(), "")
            .trim()
            .ifBlank { "Reminder" }
    }

    private fun parseAccessibilityAction(trimmed: String, lower: String): LocalIntent.AccessibilityAction? {
        if (lower == "scroll down" || lower.contains("neeche scroll") || lower.contains("scroll down")) {
            return LocalIntent.AccessibilityAction(VoiceCommand.AccessibilityAction.ActionType.SCROLL_DOWN, rawCommand = trimmed)
        }
        if (lower == "scroll up" || lower.contains("upar scroll") || lower.contains("scroll up")) {
            return LocalIntent.AccessibilityAction(VoiceCommand.AccessibilityAction.ActionType.SCROLL_UP, rawCommand = trimmed)
        }
        if (lower == "go back" || lower == "back" || lower == "back jao" || lower == "wapas jao" || lower == "back karo" || lower == "exit karo" || lower == "exit") {
            return LocalIntent.AccessibilityAction(VoiceCommand.AccessibilityAction.ActionType.GO_BACK, rawCommand = trimmed)
        }
        if (lower == "go home" || lower == "home" || lower == "home jao" || lower == "home screen" || lower == "home par jao") {
            return LocalIntent.AccessibilityAction(VoiceCommand.AccessibilityAction.ActionType.GO_HOME, rawCommand = trimmed)
        }
        if (lower == "recent apps" || lower == "recents" || lower == "recent apps kholo" || lower == "multitask") {
            return LocalIntent.AccessibilityAction(VoiceCommand.AccessibilityAction.ActionType.RECENTS, rawCommand = trimmed)
        }
        if (lower == "copy" || lower == "copy karo" || lower == "copy kar do") {
            return LocalIntent.AccessibilityAction(VoiceCommand.AccessibilityAction.ActionType.COPY, rawCommand = trimmed)
        }
        if (lower == "paste" || lower == "paste karo" || lower == "paste kar do") {
            return LocalIntent.AccessibilityAction(VoiceCommand.AccessibilityAction.ActionType.PASTE, rawCommand = trimmed)
        }
        if (lower == "search" || lower == "search karo" || lower == "search kar do" || lower == "submit search" || lower == "enter press karo" || lower == "search dabao") {
            return LocalIntent.AccessibilityAction(VoiceCommand.AccessibilityAction.ActionType.SUBMIT_SEARCH, rawCommand = trimmed)
        }
        if (lower.startsWith("replace ") || lower.contains("replace karo")) {
            val text = trimmed.replace("(?i)^(replace with|replace)\\s+".toRegex(), "")
                .replace("(?i)\\s+replace\\s+karo$".toRegex(), "")
                .trim()
            return LocalIntent.AccessibilityAction(VoiceCommand.AccessibilityAction.ActionType.REPLACE_TEXT, argument = text, rawCommand = trimmed)
        }
        if (lower.startsWith("type ") || lower.startsWith("write ") || lower.contains(" likho") || lower.startsWith("likho ") ||
            lower.endsWith(" type karo") || lower.endsWith(" type kar do") || lower.contains(" type karo")) {
            val text = trimmed.replace("(?i)^(type|write|likho)\\s+".toRegex(), "")
                .replace("(?i)\\s+(type|write)\\s+kar(o|\\s+do)$".toRegex(), "")
                .replace("(?i)\\s+likho$".toRegex(), "")
                .trim()
            if (text.isNotBlank()) {
                return LocalIntent.AccessibilityAction(VoiceCommand.AccessibilityAction.ActionType.TYPE_TEXT, argument = text, rawCommand = trimmed)
            }
        }
        return null
    }
}

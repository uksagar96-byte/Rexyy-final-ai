#!/usr/bin/env python3
"""
REXXY AI/NLP Engine (Python 3)
Standalone NLP preprocessing and intent/entity classification layer.
Receives versioned AiCommandRequest JSON, interprets user utterance,
and outputs structured AiCommandResult JSON.

Strict Security Rule:
This engine only INTERPRETS language into structured intent and entities.
It NEVER executes Android system actions, shell commands, or intents.
"""

import json
import re
import sys
from typing import Dict, Any, Optional, Tuple


APP_ALIASES = {
    "youtube": "YouTube",
    "yt": "YouTube",
    "instagram": "Instagram",
    "insta": "Instagram",
    "whatsapp": "WhatsApp",
    "wa": "WhatsApp",
    "chrome": "Google Chrome",
    "browser": "Google Chrome",
    "spotify": "Spotify",
    "settings": "Settings",
    "setting": "Settings",
    "camera": "Camera",
    "calculator": "Calculator",
    "clock": "Clock",
    "maps": "Google Maps",
    "gmail": "Gmail",
    "email": "Gmail",
    "gallery": "Gallery",
    "photos": "Google Photos"
}


def normalize_text(text: str) -> str:
    """Cleans and standardizes raw user speech/text."""
    if not text:
        return ""
    cleaned = text.strip()
    # Strip common assistant wake prefixes
    cleaned = re.sub(r'^(?:hello|hey|hi|ok|suno)\s+(?:rexxy|rexy)\s*', '', cleaned, flags=re.IGNORECASE)
    # Normalize punctuation
    cleaned = re.sub(r'[!?,.]+$', '', cleaned)
    return cleaned.strip()


def extract_app_and_query(text: str) -> Tuple[Optional[str], Optional[str]]:
    """Extracts target application name and search query if present."""
    lower = text.lower()
    
    # Pattern: "<app> kholo aur <query> search karo" / "open <app> and search <query>"
    m_compound = re.search(r'^(.*?)\s+(?:kholo|open)\s+(?:aur|and|then|phir)\s+(.*?)\s+(?:search|chalao|play|dhundo)', lower)
    if m_compound:
        app_raw = m_compound.group(1).strip()
        query_raw = m_compound.group(2).strip()
        canonical_app = APP_ALIASES.get(app_raw, app_raw.title())
        return canonical_app, query_raw

    # Pattern: "search <query> on/in <app>" / "<app> par/me <query> search karo"
    m_search_on = re.search(r'search\s+(.+?)\s+(?:on|in)\s+(\w+)', lower)
    if m_search_on:
        query_raw = m_search_on.group(1).strip()
        app_raw = m_search_on.group(2).strip()
        canonical_app = APP_ALIASES.get(app_raw, app_raw.title())
        return canonical_app, query_raw

    # Pattern: "<app> par/pe/me <query> search/chalao"
    m_search_hinglish = re.search(r'(\w+)\s+(?:par|pe|me|mein)\s+(.+?)\s+(?:search|chalao|play|dhundo)', lower)
    if m_search_hinglish:
        app_raw = m_search_hinglish.group(1).strip()
        query_raw = m_search_hinglish.group(2).strip()
        canonical_app = APP_ALIASES.get(app_raw, app_raw.title())
        return canonical_app, query_raw

    return None, None


def classify_intent(text: str) -> Tuple[str, Optional[str], Optional[str], Dict[str, str], float]:
    """Classifies natural language input into structured intent, app, query, entities, and confidence."""
    norm = normalize_text(text)
    lower = norm.lower()

    if not norm:
        return "FALLBACK_AI", None, None, {}, 0.0

    # 1. App search (universal video/web/audio search)
    target_app, search_query = extract_app_and_query(norm)
    if target_app and search_query:
        return "SEARCH_APP", target_app, search_query, {"app": target_app, "query": search_query}, 0.98

    # 2. Open App: "open <app>", "<app> kholo", "launch <app>", "start <app>"
    m_open = re.search(r'^(?:open|launch|start|chalao)\s+([a-zA-Z0-9\s]+)$', lower)
    if m_open:
        app_name = m_open.group(1).strip()
        canonical = APP_ALIASES.get(app_name, app_name.title())
        return "OPEN_APP", canonical, None, {"app": canonical}, 0.97

    m_kholo = re.search(r'^([a-zA-Z0-9\s]+?)\s+(?:kholo|open karo|start karo)$', lower)
    if m_kholo:
        app_name = m_kholo.group(1).strip()
        canonical = APP_ALIASES.get(app_name, app_name.title())
        return "OPEN_APP", canonical, None, {"app": canonical}, 0.97

    # 3. Close app / minimize / go home
    if lower in ["close", "close app", "band karo", "exit", "home", "go to home", "minimize"]:
        return "CLOSE_APP", None, None, {"target": "home"}, 0.99

    # 4. Device telemetry
    if any(k in lower for k in ["battery", "battery percentage", "charging", "battery kitni hai"]):
        return "BATTERY", None, None, {}, 0.99

    if any(k in lower for k in ["what time", "current time", "time kya", "samay kya", "time batao"]):
        return "TIME", None, None, {}, 0.99

    if any(k in lower for k in ["today's date", "what date", "aaj ki date", "tarikh kya", "date batao"]):
        return "DATE", None, None, {}, 0.99

    # 5. Flashlight / Torch
    if any(k in lower for k in ["turn on flashlight", "torch on", "flashlight on", "torch jalao", "torch chalao"]):
        return "FLASHLIGHT", None, None, {"state": "on"}, 0.98
    if any(k in lower for k in ["turn off flashlight", "torch off", "flashlight off", "torch band karo"]):
        return "FLASHLIGHT", None, None, {"state": "off"}, 0.98

    # Default fallback to conversational AI
    return "FALLBACK_AI", None, norm, {}, 0.70


def process_request(request_json_str: str) -> str:
    """
    Main entry point for processing structured request.
    Input: AiCommandRequest JSON string.
    Output: AiCommandResult JSON string.
    """
    try:
        req = json.loads(request_json_str)
        request_id = req.get("requestId", "req-unknown")
        raw_input = req.get("rawInput", "")

        intent, app, query, entities, confidence = classify_intent(raw_input)

        result = {
            "requestId": request_id,
            "intent": intent,
            "app": app,
            "query": query,
            "entities": entities,
            "confidence": confidence,
            "requiresConfirmation": False,
            "error": None,
            "version": 1
        }
    except Exception as e:
        result = {
            "requestId": "error",
            "intent": "ERROR",
            "app": None,
            "query": None,
            "entities": {},
            "confidence": 0.0,
            "requiresConfirmation": False,
            "error": f"Python NLP engine error: {str(e)}",
            "version": 1
        }

    return json.dumps(result, indent=2)


if __name__ == "__main__":
    if len(sys.argv) > 1:
        test_input = sys.argv[1]
        req_obj = {"requestId": "test-1", "rawInput": test_input, "language": "en", "version": 1}
        print(process_request(json.dumps(req_obj)))
    else:
        # Self-test sample
        sample_queries = [
            "Instagram kholo",
            "YouTube kholo aur Virat Kohli search karo",
            "battery kitni hai",
            "torch on karo",
            "what is the distance to mars"
        ]
        for q in sample_queries:
            r = json.dumps({"requestId": "self-test", "rawInput": q})
            print(f"QUERY: {q}\nRESULT: {process_request(r)}\n{'-'*40}")

package com.rexyy.app.voice

import com.rexyy.app.router.RexyyCommandRouter

object VoiceCommandParser {

    /**
     * Delegates directly to RexyyCommandRouter to ensure unified parsing across voice & text.
     */
    fun parse(input: String): VoiceCommand {
        return RexyyCommandRouter.route(input)
    }
}

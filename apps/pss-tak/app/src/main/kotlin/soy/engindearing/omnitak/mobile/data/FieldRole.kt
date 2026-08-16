package soy.engindearing.omnitak.mobile.data

/**
 * Field operator mode for progressive disclosure (Unified SAR client).
 * Stored in [UserPrefs]; gates radial actions and Mission Sync write UI.
 */
enum class FieldRole {
    /** Default — map, markers, photo/voice, chat, ack own task. */
    SEARCHER,
    /** Team lead — + sector cleared, roster check-in, route drawing. */
    LEAD,
    /** Field CP tablet — + create task, POST sector polygon, sector list. */
    FIELD_HQ,
    ;

    val canAckTasks: Boolean get() = true
    val canCheckInRoster: Boolean get() = this == LEAD || this == FIELD_HQ
    val canMarkSectorCleared: Boolean get() = this == LEAD || this == FIELD_HQ
    val canCreateTask: Boolean get() = this == FIELD_HQ
    val canDrawSectorToServer: Boolean get() = this == LEAD || this == FIELD_HQ
    val canSendAlert: Boolean get() = true
    val canOpenEvac: Boolean get() = this == LEAD || this == FIELD_HQ

    companion object {
        fun fromRaw(raw: String?): FieldRole =
            runCatching { valueOf((raw ?: "").trim().uppercase()) }.getOrDefault(SEARCHER)
    }
}

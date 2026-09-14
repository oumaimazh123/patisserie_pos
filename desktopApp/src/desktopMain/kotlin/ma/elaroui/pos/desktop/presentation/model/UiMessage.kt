package ma.elaroui.pos.desktop.presentation.model

/**
 * Explicit message severity levels.
 * Removes all fragile string-matching heuristics (e.g. contains("succès"), contains("erreur")).
 */
enum class MessageSeverity {
    SUCCESS,
    ERROR,
    WARNING,
    INFO
}

/**
 * Defines how and where a UI message is presented to the cashier/user.
 */
enum class MessagePresentation {
    /**
     * Self-dismissing toast/snackbar (disappears after ~3 seconds).
     * Used for non-blocking confirmations: "Caisse ouverte", "Paiement enregistré", etc.
     */
    TEMPORARY,

    /**
     * Displayed inline directly within a form, panel, or next to an input field.
     * Clears when the user modifies the input or retries.
     */
    INLINE,

    /**
     * Stays visible until resolved or dismissed (e.g. trial expiration warning banner).
     */
    PERSISTENT,

    /**
     * Requires explicit modal acknowledgment or confirmation before continuing.
     */
    BLOCKING
}

/**
 * Strongly typed UI message model for POS alerts and notifications.
 */
data class UiMessage(
    val text: String,
    val severity: MessageSeverity,
    val presentation: MessagePresentation = MessagePresentation.TEMPORARY,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
    val id: Long = System.nanoTime()
) {
    val isSuccess: Boolean get() = severity == MessageSeverity.SUCCESS
    val isError: Boolean get() = severity == MessageSeverity.ERROR
    val isWarning: Boolean get() = severity == MessageSeverity.WARNING
    val isInfo: Boolean get() = severity == MessageSeverity.INFO

    companion object {
        fun success(
            text: String,
            presentation: MessagePresentation = MessagePresentation.TEMPORARY,
            actionLabel: String? = null,
            onAction: (() -> Unit)? = null
        ): UiMessage = UiMessage(text, MessageSeverity.SUCCESS, presentation, actionLabel, onAction)

        fun error(
            text: String,
            presentation: MessagePresentation = MessagePresentation.INLINE,
            actionLabel: String? = null,
            onAction: (() -> Unit)? = null
        ): UiMessage = UiMessage(text, MessageSeverity.ERROR, presentation, actionLabel, onAction)

        fun warning(
            text: String,
            presentation: MessagePresentation = MessagePresentation.TEMPORARY,
            actionLabel: String? = null,
            onAction: (() -> Unit)? = null
        ): UiMessage = UiMessage(text, MessageSeverity.WARNING, presentation, actionLabel, onAction)

        fun info(
            text: String,
            presentation: MessagePresentation = MessagePresentation.TEMPORARY,
            actionLabel: String? = null,
            onAction: (() -> Unit)? = null
        ): UiMessage = UiMessage(text, MessageSeverity.INFO, presentation, actionLabel, onAction)
    }
}

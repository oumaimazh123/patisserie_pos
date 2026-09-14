package ma.elaroui.pos.desktop.presentation.settings

internal sealed interface ProtectedActionResult<out T> {
    data class Success<T>(val value: T) : ProtectedActionResult<T>
    data object InvalidCredential : ProtectedActionResult<Nothing>
    data class Failure(val error: Throwable) : ProtectedActionResult<Nothing>
}

/**
 * Runs a protected action while guaranteeing that the UI loading flag is reset.
 * Credentials are deliberately never included in results, exceptions or logs.
 */
internal suspend fun <T> runProtectedAction(
    onProcessingChanged: (Boolean) -> Unit,
    verifyCredential: suspend () -> Boolean,
    action: suspend () -> T
): ProtectedActionResult<T> {
    onProcessingChanged(true)
    return try {
        if (!verifyCredential()) ProtectedActionResult.InvalidCredential
        else ProtectedActionResult.Success(action())
    } catch (error: Throwable) {
        ProtectedActionResult.Failure(error)
    } finally {
        onProcessingChanged(false)
    }
}

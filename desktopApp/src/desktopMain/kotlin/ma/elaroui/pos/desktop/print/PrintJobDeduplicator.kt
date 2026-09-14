package ma.elaroui.pos.desktop.print

class PrintJobDeduplicator(
    private val retentionMillis: Long = 10 * 60 * 1_000L,
    private val now: () -> Long = System::currentTimeMillis
) {
    private val submitted = LinkedHashMap<String, Long>()

    @Synchronized
    fun acquire(key: String): Boolean {
        require(key.isNotBlank())
        val timestamp = now()
        submitted.entries.removeIf { timestamp - it.value > retentionMillis }
        if (submitted.containsKey(key)) return false
        submitted[key] = timestamp
        return true
    }

    @Synchronized
    fun releaseAfterFailure(key: String) {
        submitted.remove(key)
    }
}

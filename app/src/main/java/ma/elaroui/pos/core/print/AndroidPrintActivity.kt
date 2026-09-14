package ma.elaroui.pos.core.print

import android.app.Activity
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * Hosts Android's system print dialog. PrintManager requires an Activity context,
 * while receipt printers are application-scoped and therefore cannot open it directly.
 */
class AndroidPrintActivity : Activity() {

    private var printStarted = false
    private var pausedAfterPrint = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val html = intent.getStringExtra(EXTRA_HTML)
        val jobName = intent.getStringExtra(EXTRA_JOB_NAME)
        if (html.isNullOrBlank() || jobName.isNullOrBlank()) {
            finish()
            return
        }

        val webView = WebView(this)
        setContentView(webView)
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                if (printStarted || isFinishing || isDestroyed) return

                val printManager = getSystemService(PRINT_SERVICE) as? PrintManager
                if (printManager == null) {
                    finish()
                    return
                }

                try {
                    printStarted = true
                    val adapter = view.createPrintDocumentAdapter(jobName)
                    printManager.print(
                        jobName,
                        adapter,
                        PrintAttributes.Builder().build()
                    )
                } catch (_: RuntimeException) {
                    // A missing or unavailable print service must not crash the POS.
                    finish()
                }
            }
        }
        webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    }

    override fun onPause() {
        super.onPause()
        if (printStarted) pausedAfterPrint = true
    }

    override fun onResume() {
        super.onResume()
        if (printStarted && pausedAfterPrint) finish()
    }

    companion object {
        const val EXTRA_HTML = "ma.elaroui.pos.extra.PRINT_HTML"
        const val EXTRA_JOB_NAME = "ma.elaroui.pos.extra.PRINT_JOB_NAME"
    }
}

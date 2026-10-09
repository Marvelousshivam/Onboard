package com.boardsprep.onboard.ui.components

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.util.Base64
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Offline-first high performance Math & Chemical Formula Renderer powered by KaTeX.
 * Bundled inside app assets with zero network latency.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MathFormulaView(
    latex: String,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    fontSizeSp: Int = 15,
    height: Dp = 80.dp
) {
    val isDark = isSystemInDarkTheme()
    val colorHex = remember(textColor, isDark) {
        val argb = textColor.toArgb()
        String.format("#%06X", 0xFFFFFF and argb)
    }

    val base64Latex = remember(latex) {
        Base64.encodeToString(latex.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    val htmlContent = remember(base64Latex, colorHex, fontSizeSp) {
        """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
            <link rel="stylesheet" href="file:///android_asset/katex/katex.min.css">
            <script src="file:///android_asset/katex/katex.min.js"></script>
            <style>
                * { margin: 0; padding: 0; box-sizing: border-box; }
                html, body {
                    background-color: transparent !important;
                    background: transparent !important;
                    width: 100%;
                    height: 100%;
                    overflow-x: auto;
                    overflow-y: visible !important;
                }
                body {
                    color: $colorHex !important;
                    font-size: ${fontSizeSp}px;
                    display: flex;
                    align-items: center;
                    justify-content: safe center;
                    padding: 16px 8px;
                    -webkit-user-select: none;
                }
                #math {
                    display: inline-block;
                    margin: 0 auto;
                    white-space: nowrap;
                    text-align: center;
                    overflow: visible !important;
                }
                .katex {
                    color: $colorHex !important;
                    font-size: 1.08em;
                }
                .katex-html {
                    overflow: visible !important;
                }
                .katex-display {
                    margin: 0 !important;
                }
            </style>
        </head>
        <body>
            <div id="math"></div>
            <script>
                (function() {
                    function render() {
                        try {
                            var b64 = "$base64Latex";
                            var bin = window.atob(b64);
                            var bytes = new Uint8Array(bin.length);
                            for (var i = 0; i < bin.length; i++) {
                                bytes[i] = bin.charCodeAt(i);
                            }
                            var raw = new TextDecoder("utf-8").decode(bytes);
                            if (window.katex) {
                                var expr = raw.trim();
                                if (!expr.startsWith("\\displaystyle")) {
                                    expr = "\\displaystyle " + expr;
                                }
                                katex.render(expr, document.getElementById("math"), {
                                    throwOnError: false,
                                    displayMode: false
                                });
                            } else {
                                document.getElementById("math").textContent = raw;
                            }
                        } catch(e) {
                            try {
                                document.getElementById("math").textContent = window.atob("$base64Latex");
                            } catch(_) {}
                        }
                    }
                    if (document.readyState === 'loading') {
                        document.addEventListener('DOMContentLoaded', render);
                    } else {
                        render();
                    }
                })();
            </script>
        </body>
        </html>
        """.trimIndent()
    }

    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
        factory = { ctx ->
            WebView(ctx).apply {
                setBackgroundColor(AndroidColor.TRANSPARENT)
                setLayerType(View.LAYER_TYPE_HARDWARE, null)
                settings.apply {
                    javaScriptEnabled = true
                    allowFileAccess = true
                    allowContentAccess = true
                    allowFileAccessFromFileURLs = true
                    allowUniversalAccessFromFileURLs = true
                    loadWithOverviewMode = false
                    useWideViewPort = false
                }
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = true

                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(cm: ConsoleMessage): Boolean {
                        android.util.Log.d("MathFormulaDebug", "${cm.message()} (line ${cm.lineNumber()})")
                        return true
                    }
                }

                tag = htmlContent
                loadDataWithBaseURL("file:///android_asset/katex/", htmlContent, "text/html", "UTF-8", null)
            }
        },
        update = { webView ->
            if (webView.tag != htmlContent) {
                webView.tag = htmlContent
                webView.loadDataWithBaseURL("file:///android_asset/katex/", htmlContent, "text/html", "UTF-8", null)
            }
        }
    )
}

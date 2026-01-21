package com.daza.epub2views

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.webkit.WebSettingsCompat
import com.daza.epub2views.adapter.ChapterAdapter
import com.google.android.material.navigation.NavigationView
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

class MainActivity : AppCompatActivity() {

    private lateinit var webViewLeft: WebView
    private lateinit var webViewRight: WebView
    private lateinit var openEpubLauncher: ActivityResultLauncher<Array<String>>
    private val openEpubLauncher1 = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { loadEpubInWebView(it, 1) }
    }

    private val openEpubLauncher2 = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { loadEpubInWebView(it, 2) }
    }

    private var epubExtractedDir1: File? = null  // Variable para almacenar la carpeta extraída
    private var epubExtractedDir2: File? = null


    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var toolbar: Toolbar


    private val chapterNames1 = mutableListOf<String>()
    private val chapterNames2 = mutableListOf<String>()
    private val chapterFiles1 = mutableListOf<File>()
    private val chapterFiles2 = mutableListOf<File>()


    private var activeWebView: WebView? = null



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        webViewLeft = findViewById(R.id.webViewLeft)
        webViewRight = findViewById(R.id.webViewRight)
        drawerLayout = findViewById(R.id.drawer_layout)
        navigationView = findViewById(R.id.navigation_view)
        toolbar = findViewById(R.id.toolbar)

        // Configura la toolbar como ActionBar
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)


        navigationView.setBackgroundColor(getColor(R.color.colorBackground))

        webViewLeft.loadUrl("file:///android_asset/empty_state.html")
        webViewRight.loadUrl("file:///android_asset/empty_state.html")



        // Agrega el toggle (ícono hamburguesa)
        val toggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        //agregar un touchlistener a las webviews
        webViewLeft.setOnTouchListener { _, _ ->
            activeWebView = webViewLeft
            false
        }

        webViewRight.setOnTouchListener { _, _ ->
            activeWebView = webViewRight
            false
        }


// Configuración general para ambos WebView (JS habilitado, modo oscuro, etc)
        listOf(webViewLeft, webViewRight).forEach { webView ->
            webView.settings.javaScriptEnabled = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                WebSettingsCompat.setForceDark(webView.settings, WebSettingsCompat.FORCE_DARK_ON)
            }
            webView.settings.allowFileAccess = true
            webView.settings.allowFileAccessFromFileURLs = true
            webView.settings.allowUniversalAccessFromFileURLs = true

            webView.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val url = request?.url?.toString() ?: return false
                    if (url.startsWith("http://") || url.startsWith("https://")) {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        startActivity(intent)
                        return true
                    }
                    return false
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    view?.loadDataWithBaseURL(
                        null,
                        """
        <html>
        <body style="text-align:center;padding:40px;font-family:sans-serif;">
            <h3>Error loading content</h3>
            <p>Please try opening the EPUB again.</p>
        </body>
        </html>
        """.trimIndent(),
                        "text/html",
                        "utf-8",
                        null
                    )
                }



            }
        }

        // Aquí colocas el código para restaurar último EPUB abierto en webView1
        val lastId1 = getSharedPreferences("prefs", MODE_PRIVATE).getString("last_epub_id_1", null)
        val lastReadPath1 = getSharedPreferences("prefs", MODE_PRIVATE).getString("last_read_path_1", null)

        if (lastId1 != null) {
            val outputDir = File(filesDir, "epub_${lastId1}_1")
            if (outputDir.exists()) {
                epubExtractedDir1 = outputDir
                if (lastReadPath1 != null) {
                    val file = File(lastReadPath1)
                    if (file.exists()) {
                        webViewLeft.loadUrl("file://${file.absolutePath}")
                        chapters(epubExtractedDir1, webViewLeft)
                    }
                }
            }
        }

        // Aquí colocas el código para restaurar último EPUB abierto en webView2
        val lastId2 = getSharedPreferences("prefs", MODE_PRIVATE).getString("last_epub_id_2", null)
        val lastReadPath2 = getSharedPreferences("prefs", MODE_PRIVATE).getString("last_read_path_2", null)

        if (lastId2 != null) {
            val outputDir = File(filesDir, "epub_${lastId2}_2")
            if (outputDir.exists()) {
                epubExtractedDir2 = outputDir
                if (lastReadPath2 != null) {
                    val file = File(lastReadPath2)
                    if (file.exists()) {
                        webViewRight.loadUrl("file://${file.absolutePath}")
                        chapters(epubExtractedDir2, webViewRight)
                    }
                }
            }
        }
    }

    private fun loadEpubInWebView(uri: Uri, webViewNumber: Int) {

        // ✅ 1. Obtener el WebView correcto
        val webView = if (webViewNumber == 1) webViewLeft else webViewRight

        // ✅ 2. Mostrar estado de carga INMEDIATO (evita pantalla en blanco)
        webView.loadData(
            "<html><body style='text-align:center;padding:40px;font-family:sans-serif;'>" +
                    "<p>Loading EPUB…</p></body></html>",
            "text/html",
            "utf-8"
        )

        // ---- TU CÓDIGO EXISTENTE (SIN CAMBIOS) ----

        val epubId = uri.lastPathSegment?.hashCode()?.toString() ?: "default$webViewNumber"
        val outputDir = File(filesDir, "epub_${epubId}_$webViewNumber")

        if (webViewNumber == 1) epubExtractedDir1 = outputDir else epubExtractedDir2 = outputDir

        if (!outputDir.exists() || outputDir.listFiles().isNullOrEmpty()) {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val tempFile = File(cacheDir, "temp_$webViewNumber.epub")
                tempFile.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
                unzipEpub(tempFile, outputDir)
            }
        }

        val firstHtml = outputDir.walkTopDown()
            .firstOrNull { it.isFile && (it.extension == "xhtml" || it.extension == "html") }

        if (firstHtml != null) {
            webView.loadUrl("file://${firstHtml.absolutePath}")

            val epubDir = if (webViewNumber == 1) epubExtractedDir1 else epubExtractedDir2
            chapters(epubDir, webView)

            getSharedPreferences("prefs", MODE_PRIVATE).edit()
                .putString("last_epub_id_$webViewNumber", epubId)
                .putString("last_read_path_$webViewNumber", firstHtml.absolutePath)
                .apply()
        } else {
            webView.loadDataWithBaseURL(
                null,
                """
            <html>
            <body style="text-align:center;padding:40px;font-family:sans-serif;">
                <h3>Invalid EPUB</h3>
                <p>No readable HTML content was found.</p>
            </body>
            </html>
            """.trimIndent(),
                "text/html",
                "utf-8",
                null
            )
        }
    }


    fun chapters(epubDir: File?, webView: WebView) {
        if (epubDir == null) return

        val opfFile = findContentOpf(epubDir) ?: run {
            Toast.makeText(this, "No se encontró archivo .opf", Toast.LENGTH_SHORT).show()
            return
        }

        val spine = parseOpfForSpine(opfFile)
        val manifest = parseManifest(opfFile)
        val chapterPaths = spine.mapNotNull { manifest[it] }

        val chapterFiles = epubDir.walkTopDown()
            .filter { it.isFile && (it.extension == "xhtml" || it.extension == "html") }
            .filterNot { it.name.contains("cover", true) || it.name.contains("toc", true)
                    || it.name.contains("pagenav", true) || it.name.contains("extracted", true) }
            .toList()

        val titles = chapterFiles.map { extractTitleFromXhtml(it) }

        // Guardar por separado según WebView
        if (webView == webViewLeft) {
            chapterNames1.clear()
            chapterFiles1.clear()
            chapterNames1.addAll(titles)
            chapterFiles1.addAll(chapterFiles)
        } else {
            chapterNames2.clear()
            chapterFiles2.clear()
            chapterNames2.addAll(titles)
            chapterFiles2.addAll(chapterFiles)
        }

        updateDrawer()
    }

    private fun updateDrawer() {
        val allTitles = chapterNames1.map { "📘 $it" } + chapterNames2.map { "📗 $it" }

        val navigationView = findViewById<NavigationView>(R.id.navigation_view)
        if (navigationView.headerCount == 0) {
            val drawerContent = layoutInflater.inflate(R.layout.navigation_drawer_item, navigationView, false)
            navigationView.addHeaderView(drawerContent)
        }

        val headerView = navigationView.getHeaderView(0)
        val recyclerView = headerView.findViewById<RecyclerView>(R.id.chaptersRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        recyclerView.adapter = ChapterAdapter(allTitles) { position ->
            val total1 = chapterNames1.size

            if (position < total1) {
                val file = chapterFiles1[position]
                webViewLeft.loadUrl("file://${file.absolutePath}")

                getSharedPreferences("prefs", MODE_PRIVATE)
                    .edit()
                    .putString("last_read_path_1", file.absolutePath)
                    .apply()
            } else {
                val file = chapterFiles2[position - total1]
                webViewRight.loadUrl("file://${file.absolutePath}")

                getSharedPreferences("prefs", MODE_PRIVATE)
                    .edit()
                    .putString("last_read_path_2", file.absolutePath)
                    .apply()
            }

            drawerLayout.closeDrawers()
        }
    }


    private fun unzipEpub(epubFile: File, outputDir: File) {
        ZipInputStream(FileInputStream(epubFile)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val outFile = File(outputDir, entry.name)
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { output -> zip.copyTo(output) }
                }
                entry = zip.nextEntry
            }
        }
    }


    //busca el archivo .opf en la carpeta epub
    fun findContentOpf(epubDir: File): File? {
        return epubDir.walkTopDown()
            .firstOrNull { it.name.endsWith(".opf", ignoreCase = true) }
    }

    fun parseOpfForSpine(opfFile: File): List<String> {
        val spineItems = mutableListOf<String>()
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(opfFile.inputStream(), null)

        var insideSpine = false
        var insideItemref = false
        var idref: String? = null

        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "spine" -> insideSpine = true
                        "itemref" -> if (insideSpine) {
                            idref = parser.getAttributeValue(null, "idref")
                            if (idref != null) spineItems.add(idref)
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "spine") insideSpine = false
                }
            }
            parser.next()
        }

        return spineItems
    }


    fun parseManifest(opfFile: File): Map<String, String> {
        val idToHref = mutableMapOf<String, String>()
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(opfFile.inputStream(), null)

        var insideManifest = false

        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "manifest" -> insideManifest = true
                        "item" -> if (insideManifest) {
                            val id = parser.getAttributeValue(null, "id")
                            val href = parser.getAttributeValue(null, "href")
                            if (id != null && href != null) {
                                idToHref[id] = href
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "manifest") insideManifest = false
                }
            }
            parser.next()
        }

        return idToHref
    }

    //etraer el titulo de XHtml


    fun extractTitleFromXhtml(file: File): String {
        val text = file.readText()
        val regex = Regex("<title>(.*?)</title>", setOf(RegexOption.IGNORE_CASE,
            RegexOption.DOT_MATCHES_ALL))

        val matchResult = regex.find(text)
        return matchResult?.groups?.get(1)?.value?.trim() ?: file.name
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        // Puedes agregar lógica adicional aquí si necesitas ajustar UI manualmente
        Log.d("MainActivity", "Rotación detectada: $newConfig.orientation")
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_open_epub_left -> {
                // Lógica cuando se presiona el ícono de la carpeta
                openEpubLauncher1.launch(arrayOf("application/epub+zip"))
                true
            }

            R.id.action_open_epub_right -> {
                // Lógica cuando se presiona el ícono de la carpeta
                openEpubLauncher2.launch(arrayOf("application/epub+zip"))
                true
            }

            R.id.action_highlight_yellow -> {
                // Lógica cuando se presiona el ícono del circulo
                val js = """
            javascript:(function() {
                var sel = window.getSelection();
                if (sel.rangeCount > 0) {
                    var range = sel.getRangeAt(0);
                    var span = document.createElement("span");
                    span.style.backgroundColor = "#ffff00"; // amarillo
                    range.surroundContents(span);
                }
            })()
        """.trimIndent()

                activeWebView?.evaluateJavascript(js){
                    val currentUrl = activeWebView?.url
                    val currentFile = File(Uri.parse(currentUrl).path ?: "")
                    saveHighlightedContent(currentFile)
                }
                true
            }

            R.id.action_highlight_blue  -> {
                // Lógica cuando se presiona el ícono del circulo
                val js = """
            javascript:(function() {
                var sel = window.getSelection();
                if (sel.rangeCount > 0) {
                    var range = sel.getRangeAt(0);
                    var span = document.createElement("span");
                    span.style.backgroundColor = "#ADD8E6";
                    range.surroundContents(span);
                }
            })()
        """.trimIndent()

                activeWebView?.evaluateJavascript(js){
                    val currentUrl = activeWebView?.url
                    val currentFile = File(Uri.parse(currentUrl).path ?: "")
                    saveHighlightedContent(currentFile)
                }
                true
            }


            R.id.action_remove_selected_highlight  -> {
                val js = """
        (function() {
            var sel = window.getSelection();
            if (sel.rangeCount === 0) return;
    
            var node = sel.focusNode;
            if (!node) return;
    
            // Busca hacia arriba el span con color de fondo
            while (node && node !== document.body) {
                if (node.nodeType === 1 && node.tagName.toLowerCase() === 'span') {
                    var color = window.getComputedStyle(node).backgroundColor;
                    if (color === 'rgb(255, 255, 0)' || color === 'rgb(173, 216, 230)') {
                        var parent = node.parentNode;
                        while (node.firstChild) {
                            parent.insertBefore(node.firstChild, node);
                        }
                        parent.removeChild(node);
                        break;
                    }
                }
                node = node.parentNode;
            }
        })();
    """.trimIndent()

                activeWebView?.evaluateJavascript(js) {
                    val currentUrl = activeWebView?.url
                    val currentFile = File(Uri.parse(currentUrl).path ?: "")
                    saveHighlightedContent(currentFile)
                }
                true

            }



            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun saveHighlightedContent(file: File) {
        activeWebView?.evaluateJavascript(
            "(function() { return document.documentElement.outerHTML; })();"
        ) { htmlContent ->
            val cleanHtml = htmlContent
                .replace("\\u003C", "<")
                .replace("\\n", "\n")
                .replace("\\\"", "\"")
                .trim('"')

            file.writeText(cleanHtml)
            Toast.makeText(this, "guardado", Toast.LENGTH_SHORT).show()
        }
    }

}

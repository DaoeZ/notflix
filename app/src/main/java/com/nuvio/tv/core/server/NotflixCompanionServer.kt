package com.nuvio.tv.core.server

import android.util.Log
import fi.iki.elonen.NanoHTTPD
import org.json.JSONObject
import java.io.IOException
import java.security.SecureRandom

/**
 * Lightweight local LAN companion server (NanoHTTPD) for:
 *  - Notflix Mobile "Send to TV" (Cast title / episode / timestamp)
 *  - Phone browser or Mobile app Remote & Keyboard for TV search and playback control
 *
 * Protected by a short per-session pairing code; zero cloud/external dependency.
 */
data class CompanionWebStrings(
    val langTag: String = "en",
    val placeholder: String = "Search movie or show on TV…",
    val search: String = "Search",
    val back: String = "Back",
    val home: String = "Home",
    val sent: String = "Sent",
    val error: String = "Error",
    val offline: String = "Cannot reach TV",
    val enterCode: String = "Enter the 6-character code shown on your TV to connect the remote.",
    val codePlaceholder: String = "Code (e.g. A1B2C3)",
    val connect: String = "Connect",
    val invalidCode: String = "Invalid code. Check the code on your TV and try again."
)

class NotflixCompanionServer private constructor(
    port: Int,
    val code: String,
    private val onCommand: (CompanionCommand) -> Boolean,
    private val webStringsProvider: () -> CompanionWebStrings
) : NanoHTTPD(port) {

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri.orEmpty()
        if (session.method == Method.OPTIONS) {
            return cors(newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, ""))
        }
        val suppliedCode = session.headers["x-notflix-code"]?.trim()
            ?: session.parms["code"]?.trim()
            ?: session.parms["t"]?.trim()
        val isWebRemotePage = session.method == Method.GET && (uri == "/" || uri == "/remote")
        if (!suppliedCode.equals(code, ignoreCase = true)) {
            if (isWebRemotePage) {
                val showError = !suppliedCode.isNullOrBlank()
                return newFixedLengthResponse(
                    Response.Status.OK,
                    "text/html; charset=utf-8",
                    renderPairingHtml(webStringsProvider(), showError)
                )
            }
            return cors(newFixedLengthResponse(Response.Status.FORBIDDEN, "application/json", """{"ok":false,"error":"invalid_code"}"""))
        }

        return when {
            session.method == Method.GET && (uri == "/" || uri == "/remote") ->
                newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", renderWebRemoteHtml(code, webStringsProvider()))

            session.method == Method.GET && uri == "/api/ping" ->
                cors(newFixedLengthResponse(Response.Status.OK, "application/json", """{"ok":true,"app":"notflix-tv"}"""))

            session.method == Method.POST && uri == "/api/search" -> {
                val json = parseBodyJson(session)
                val query = json?.optString("query").orEmpty().trim()
                if (query.isEmpty()) {
                    cors(newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json", """{"ok":false,"error":"empty_query"}"""))
                } else {
                    val accepted = onCommand(CompanionCommand.Search(query))
                    cors(newFixedLengthResponse(Response.Status.OK, "application/json", """{"ok":$accepted}"""))
                }
            }

            session.method == Method.POST && uri == "/api/cast" -> {
                val json = parseBodyJson(session)
                val itemId = json?.optString("id").orEmpty()
                    .ifBlank { json?.optString("itemId").orEmpty() }
                    .trim()
                val itemType = json?.optString("type").orEmpty()
                    .ifBlank { json?.optString("itemType").orEmpty() }
                    .trim()
                    .ifEmpty { "movie" }
                if (itemId.isEmpty()) {
                    cors(newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json", """{"ok":false,"error":"missing_id"}"""))
                } else {
                    val cmd = CompanionCommand.Cast(
                        itemId = itemId,
                        itemType = itemType,
                        title = json?.optString("title")?.takeIf { it.isNotBlank() },
                        season = json?.takeIf { it.has("season") && !it.isNull("season") }?.optInt("season"),
                        episode = json?.takeIf { it.has("episode") && !it.isNull("episode") }?.optInt("episode"),
                        positionMs = json?.takeIf { it.has("positionMs") && !it.isNull("positionMs") }?.optLong("positionMs"),
                        autoPlay = json?.optBoolean("autoPlay", true) ?: true
                    )
                    val accepted = onCommand(cmd)
                    val status = if (accepted) Response.Status.OK else Response.Status.FORBIDDEN
                    cors(newFixedLengthResponse(status, "application/json", """{"ok":$accepted}"""))
                }
            }

            session.method == Method.POST && uri == "/api/remote" -> {
                val json = parseBodyJson(session)
                val action = json?.optString("action").orEmpty().trim().lowercase()
                if (action.isEmpty()) {
                    cors(newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json", """{"ok":false,"error":"missing_action"}"""))
                } else {
                    val accepted = onCommand(CompanionCommand.RemoteKey(action))
                    cors(newFixedLengthResponse(Response.Status.OK, "application/json", """{"ok":$accepted}"""))
                }
            }

            else -> cors(newFixedLengthResponse(Response.Status.NOT_FOUND, "application/json", """{"ok":false,"error":"not_found"}"""))
        }
    }

    private fun parseBodyJson(session: IHTTPSession): JSONObject? {
        return try {
            val files = HashMap<String, String>()
            session.parseBody(files)
            val body = files["postData"].orEmpty()
            if (body.isBlank()) null else JSONObject(body)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse companion body: ${e.message}")
            null
        }
    }

    private fun cors(response: Response): Response {
        response.addHeader("Access-Control-Allow-Origin", "*")
        response.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        response.addHeader("Access-Control-Allow-Headers", "Content-Type, X-Notflix-Code")
        return response
    }

    companion object {
        private const val TAG = "NotflixCompanionServer"
        private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        private val random = SecureRandom()

        fun generateCode(length: Int = 6): String = buildString(length) {
            repeat(length) { append(ALPHABET[random.nextInt(ALPHABET.length)]) }
        }

        fun pairingUri(ip: String, port: Int, code: String): String =
            "notflix://companion/$ip:$port/$code"

        fun webUrl(ip: String, port: Int, code: String): String =
            "http://$ip:$port/?code=$code"

        fun startOnAvailablePort(
            onCommand: (CompanionCommand) -> Boolean,
            startPort: Int = 8097,
            maxAttempts: Int = 10,
            webStringsProvider: () -> CompanionWebStrings = { CompanionWebStrings() },
            fixedCode: String? = null
        ): NotflixCompanionServer? {
            val code = fixedCode?.takeIf { it.isNotBlank() } ?: generateCode()
            for (offset in 0 until maxAttempts) {
                val port = startPort + offset
                try {
                    val server = NotflixCompanionServer(port, code, onCommand, webStringsProvider)
                    server.start(SOCKET_READ_TIMEOUT, false)
                    Log.i(TAG, "Companion server started on port $port")
                    return server
                } catch (_: IOException) {
                    // Try next port
                }
            }
            Log.w(TAG, "No available port for companion server")
            return null
        }

        private fun escHtml(s: String): String = s
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")

        private fun escJs(s: String): String = s
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", " ")

        private fun renderPairingHtml(s: CompanionWebStrings, invalidCode: Boolean): String = """
            <!doctype html>
            <html lang="${escHtml(s.langTag)}">
            <head>
              <meta charset="utf-8" />
              <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1" />
              <title>Notflix TV Remote</title>
              <style>
                body{margin:0;background:#0b0b0d;color:#f4f4f5;font-family:-apple-system,BlinkMacSystemFont,sans-serif;padding:28px 20px;max-width:420px;margin:0 auto;}
                h1{font-size:22px;margin:0 0 12px;color:#e50914;letter-spacing:1.2px;}
                p{font-size:14px;line-height:1.5;color:#c4c4cc;margin:0 0 18px;}
                form{display:flex;flex-direction:column;gap:12px;}
                input{background:#18181d;border:1px solid #2e2e36;color:#fff;padding:14px;border-radius:10px;font-size:20px;letter-spacing:4px;text-transform:uppercase;text-align:center;}
                button{background:#e50914;color:#fff;border:none;border-radius:10px;padding:14px;font-size:16px;font-weight:600;cursor:pointer;}
                button:active{transform:scale(.98);}
                .err{background:#2d1215;border:1px solid #e50914;color:#ffb4b8;padding:10px 12px;border-radius:8px;font-size:13px;margin-bottom:14px;}
              </style>
            </head>
            <body>
              <h1>NOTFLIX TV REMOTE</h1>
              <p>${escHtml(s.enterCode)}</p>
              ${if (invalidCode) """<div class="err">${escHtml(s.invalidCode)}</div>""" else ""}
              <form method="GET" action="/">
                <input name="code" type="text" maxlength="12" autocomplete="off" autocapitalize="characters" placeholder="${escHtml(s.codePlaceholder)}" required autofocus />
                <button type="submit">${escHtml(s.connect)}</button>
              </form>
            </body>
            </html>
        """.trimIndent()

        private fun renderWebRemoteHtml(code: String, s: CompanionWebStrings): String = """
            <!doctype html>
            <html lang="${escHtml(s.langTag)}">
            <head>
              <meta charset="utf-8" />
              <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1" />
              <title>Notflix TV Remote</title>
              <style>
                body{margin:0;background:#0b0b0d;color:#f4f4f5;font-family:-apple-system,BlinkMacSystemFont,sans-serif;padding:20px;max-width:440px;margin:0 auto;}
                h1{font-size:20px;margin:0 0 14px;color:#e50914;letter-spacing:1px;}
                .row{display:flex;gap:8px;margin-bottom:14px;}
                input{flex:1;background:#18181d;border:1px solid #2e2e36;color:#fff;padding:12px;border-radius:8px;font-size:16px;}
                button{background:#23232b;color:#fff;border:none;border-radius:10px;padding:12px 16px;font-size:15px;font-weight:600;cursor:pointer;}
                button.primary{background:#e50914;}
                button:active{transform:scale(.96);}
                .pad{display:grid;grid-template-columns:repeat(3,1fr);gap:10px;margin:18px 0;}
                .pad button{height:58px;font-size:18px;}
                .media{display:grid;grid-template-columns:repeat(3,1fr);gap:10px;}
                #st{font-size:13px;color:#9e9ea8;min-height:18px;margin-top:10px;}
              </style>
            </head>
            <body>
              <h1>NOTFLIX TV REMOTE</h1>
              <div class="row">
                <input id="q" type="search" placeholder="${escHtml(s.placeholder)}" />
                <button class="primary" onclick="sendSearch()">${escHtml(s.search)}</button>
              </div>
              <div class="pad">
                <div></div><button onclick="key('up')">▲</button><div></div>
                <button onclick="key('left')">◀</button><button class="primary" onclick="key('ok')">OK</button><button onclick="key('right')">▶</button>
                <button onclick="key('back')">${escHtml(s.back)}</button><button onclick="key('down')">▼</button><button onclick="key('home')">${escHtml(s.home)}</button>
              </div>
              <div class="media">
                <button onclick="key('seek_back')">-10s</button>
                <button class="primary" onclick="key('play_pause')">⏯</button>
                <button onclick="key('seek_fwd')">+10s</button>
              </div>
              <div id="st"></div>
              <script>
                const CODE='$code';
                const MSG_SENT='${escJs(s.sent)} ✓';
                const MSG_ERR='${escJs(s.error)}';
                const MSG_OFFLINE='${escJs(s.offline)}';
                async function post(path,data){
                  try{
                    const r=await fetch(path+'?code='+CODE,{method:'POST',headers:{'Content-Type':'application/json','X-Notflix-Code':CODE},body:JSON.stringify(data)});
                    document.getElementById('st').textContent=r.ok?MSG_SENT:(MSG_ERR+' ('+r.status+')');
                  }catch(e){document.getElementById('st').textContent=MSG_OFFLINE;}
                }
                function sendSearch(){const v=document.getElementById('q').value.trim();if(v)post('/api/search',{query:v});}
                function key(a){post('/api/remote',{action:a});}
                document.getElementById('q').addEventListener('keydown',e=>{if(e.key==='Enter')sendSearch();});
              </script>
            </body>
            </html>
        """.trimIndent()
    }
}

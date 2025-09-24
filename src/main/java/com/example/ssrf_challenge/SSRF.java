package com.example.ssrf_challenge;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

/**
 * Giữ nguyên logic fetch của bạn, chỉ thêm switch-case + filter classes để xử lý levels.
 * - Nếu level == 1 (mặc định) -> không filter (vẫn fetch như cũ)
 * - Nếu level >= 2 -> áp filter tương ứng trước khi fetch
 *
 * Bạn có thể chỉnh level bằng param "level" trong query (ví dụ ?url=...&level=3)
 */
@WebServlet(urlPatterns = {"/ssrf/preview", "/ssrf/openStream", "/ssrf/httpurlconn"})
public class SSRF extends HttpServlet {
    private static final int CONNECT_TIMEOUT = 5000;
    private static final int READ_TIMEOUT = 5000;
    private static final int DEFAULT_LEVEL = 1; // mặc định không filter

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        String path = req.getServletPath();
        String url = req.getParameter("url");
        int level = parseLevel(req.getParameter("level"));

        if ("/ssrf/preview".equals(path)) {
            previewHandler(url, req, resp, level);
            return;
        } else if ("/ssrf/openStream".equals(path)) {
            if (!FilterManager.check(url, level, resp)) return;
            openStreamHandler(url, resp);
            return;
        } else if ("/ssrf/httpurlconn".equals(path)) {
            if (!FilterManager.check(url, level, resp)) return;
            httpurlconnHandler(url, resp);
            return;
        }

        resp.setStatus(404);
        resp.getWriter().println("Not found");
    }

    private int parseLevel(String lv) {
        if (lv == null) return DEFAULT_LEVEL;
        try {
            int v = Integer.parseInt(lv);
            if (v < 1) return 1;
            if (v > 5) return 5;
            return v;
        } catch (Exception e) {
            return DEFAULT_LEVEL;
        }
    }

    // --- Preview: HIỆN NGUYÊN LOGIC FETCH CỦA BẠN (chỉ thêm check filter trước khi fetch) ---
    private void previewHandler(String urlParam, HttpServletRequest req, HttpServletResponse resp, int level)
            throws IOException {
        resp.setContentType("text/html;charset=UTF-8");
        PrintWriter out = resp.getWriter();

        // Header HTML + CSS inline (giữ như trước)
        out.println("<!doctype html><html><head><meta charset='utf-8'>");
        out.println("<style>body{font-family:Segoe UI,Arial,sans-serif;"
                + "background:#fff;color:#000;padding:12px;font-size:14px}"
                + "h1{color:#007bff;font-size:20px;margin:0 0 12px 0}"
                + "pre{white-space:pre-wrap;word-break:break-word;"
                + "color:#000;background:#f8f8f8;padding:10px;border-radius:6px;"
                + "border:1px solid #ccc;font-size:13px;} "
                + "small{color:gray;display:block;margin-bottom:8px}"
                + "</style></head><body>");

        out.println("<h1>Preview Service</h1>");
        out.println("<form method='get' action='" + req.getContextPath()
                + "/ssrf/preview'><input name='url' value='" + escapeHtml(urlParam)
                + "' style='width:70%;padding:6px;font-size:14px'><select name='level'>"
                + "<option value='1'" + (level==1?" selected":"") + ">Level1 (no filter)</option>"
                + "<option value='2'" + (level==2?" selected":"") + ">Level2</option>"
                + "<option value='3'" + (level==3?" selected":"") + ">Level3</option>"
                + "<option value='4'" + (level==4?" selected":"") + ">Level4</option>"
                + "<option value='5'" + (level==5?" selected":"") + ">Level5</option>"
                + "</select><button>Fetch</button></form>");

        if (urlParam == null || urlParam.isEmpty()) {
            out.println("<p style='color:gray'>No URL provided.</p></body></html>");
            return;
        }

        out.println("<small>Requested URL: " + escapeHtml(urlParam) + " (level " + level + ")</small>");

        // ---- CHỖ NÀY: chạy filter theo level ----
        if (!FilterManager.check(urlParam, level, resp)) {
            out.println("<pre style='color:red'>Blocked by filter (level " + level + ").</pre>");
            out.println("</body></html>");
            return;
        }

        try {
            // Create a URL object from user input without validation
            URL url = new URL(urlParam);

            // Open a connection and read the content
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(url.openStream())
            );
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
            reader.close();
            // Output the content to the response
            out.println(content.toString());
        } catch (Exception ex) {
            out.println("<pre style='color:red'>Fetch error: "
                    + escapeHtml(ex.toString()) + "</pre>");
        }

        out.println("</body></html>");
    }

    // --- openStream: tải về file (giữ nguyên) ---
    private void openStreamHandler(String urlParam, HttpServletResponse resp) {
        if (urlParam == null) {
            resp.setStatus(400);
            try { resp.getWriter().println("Missing url"); } catch (IOException ignored) {}
            return;
        }
        InputStream is = null;
        OutputStream os = null;
        try {
            URL u = new URL(urlParam);
            String name = new File(u.getPath()).getName();
            if (name == null || name.isEmpty()) name = "download.bin";
            resp.setHeader("Content-Disposition", "attachment; filename=\"" + name + "\"");
            is = u.openStream();
            os = resp.getOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) != -1) os.write(buf, 0, n);
            os.flush();
        } catch (Exception e) {
            resp.setStatus(500);
            try { resp.getWriter().println("Error: " + e.toString()); } catch (IOException ignored) {}
        } finally {
            try { if (is != null) is.close(); } catch (IOException ignored) {}
        }
    }

    // --- httpUrlConn: raw text/plain GET (giữ nguyên) ---
    private void httpurlconnHandler(String urlParam, HttpServletResponse resp) throws IOException {
        resp.setContentType("text/plain;charset=UTF-8");
        if (urlParam == null) {
            resp.getWriter().println("Missing url");
            return;
        }
        try {
            URL u = new URL(urlParam);
            HttpURLConnection conn = (HttpURLConnection) u.openConnection();
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            conn.setInstanceFollowRedirects(true);
            try (InputStream is = conn.getInputStream();
                 BufferedReader br = new BufferedReader(
                         new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                PrintWriter out = resp.getWriter();
                while ((line = br.readLine()) != null) out.println(line);
            }
        } catch (Exception e) {
            resp.getWriter().println("Error: " + e.toString());
        }
    }

    // -------------------------
    // FilterManager + inner filter classes (keeps simple, easy to modify)
    // -------------------------
    private static class FilterManager {
        // returns true if allowed; otherwise sends error via resp and returns false
        static boolean check(String urlParam, int level, HttpServletResponse resp) throws IOException {
            if (urlParam == null || urlParam.trim().isEmpty()) {
                resp.sendError(400, "Missing url parameter");
                return false;
            }

            // level 1 = no filter
            if (level <= 1) return true;

            // parse URI
            URI uri;
            try {
                uri = new URI(urlParam.trim());
            } catch (Exception e) {
                resp.sendError(400, "Invalid URL");
                return false;
            }
            String scheme = (uri.getScheme() == null) ? "" : uri.getScheme().toLowerCase();
            String host = uri.getHost();
            String lower = urlParam.toLowerCase();

            switch (level) {
                case 2:
                    return Level2.check(scheme, lower, resp);
                case 3:
                    if (!Level2.check(scheme, lower, resp)) return false;
                    return Level3.check(host, resp);
                case 4:
                    if (!Level2.check(scheme, lower, resp)) return false;
                    if (!Level3.check(host, resp)) return false;
                    return Level4.check(host, resp);
                case 5:
                    // for level5, require previous checks then allowlist
                    if (!Level2.check(scheme, lower, resp)) return false;
                    if (!Level3.check(host, resp)) return false;
                    if (!Level4.check(host, resp)) return false;
                    return Level5.check(host, resp);
                default:
                    resp.sendError(400, "Unsupported level");
                    return false;
            }
        }
    }

    // Level2: block dangerous schemes and naive substring detection
    private static class Level2 {
        static boolean check(String scheme, String lowerUrl, HttpServletResponse resp) throws IOException {
            if ("file".equals(scheme) || "gopher".equals(scheme) || "jar".equals(scheme) || "ftp".equals(scheme)) {
                resp.sendError(400, "Protocol not allowed (level2)");
                return false;
            }
            if (lowerUrl.contains("127.0.0.1") || lowerUrl.contains("localhost") || lowerUrl.contains("0.0.0.0")) {
                resp.sendError(403, "Access to loopback blocked (naive) (level2)");
                return false;
            }
            return true;
        }
    }

    // Level3: host exact match block
    private static class Level3 {
        static boolean check(String host, HttpServletResponse resp) throws IOException {
            if (host == null) return true; // cannot check
            String h = host.toLowerCase();
            if ("127.0.0.1".equals(h) || "localhost".equals(h) || "127.0.1".equals(h) || "[::1]".equals(h)) {
                resp.sendError(403, "Access to loopback denied (level3)");
                return false;
            }
            return true;
        }
    }

    // Level4: resolve and block private/loopback IPs
    private static class Level4 {
        static boolean check(String host, HttpServletResponse resp) throws IOException {
            String toResolve = host;
            if (toResolve == null) {
                resp.sendError(400, "Host missing for resolution (level4)");
                return false;
            }
            try {
                InetAddress[] addrs = InetAddress.getAllByName(toResolve);
                for (InetAddress a : addrs) {
                    if (a.isAnyLocalAddress() || a.isLoopbackAddress() || a.isSiteLocalAddress()) {
                        resp.sendError(403, "Access to internal network denied (resolved: " + a.getHostAddress() + ") (level4)");
                        return false;
                    }
                }
            } catch (UnknownHostException uhe) {
                resp.sendError(400, "Host resolution failed (level4)");
                return false;
            } catch (Exception e) {
                resp.sendError(400, "Host resolution error (level4)");
                return false;
            }
            return true;
        }
    }

    // Level5: allowlist only
    private static class Level5 {
        static boolean check(String host, HttpServletResponse resp) throws IOException {
            String[] allow = {
                    "hwykb-42-117-87-232.a.free.pinggy.link",
                    "static.example.net"
            };
            // chỉnh theo lab nếu cần
            if (host == null) {
                resp.sendError(403, "Host missing (level5)");
                return false;
            }
            for (String a : allow) {
                if (a.equalsIgnoreCase(host)) return true;
            }
            resp.sendError(403, "Host not in allowlist (level5)");
            return false;
        }
    }

    // helper escape
    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&","&amp;")
                .replace("<","&lt;")
                .replace(">","&gt;")
                .replace("\"","&quot;");
    }
}

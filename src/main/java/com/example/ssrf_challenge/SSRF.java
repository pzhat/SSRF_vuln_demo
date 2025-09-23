package com.example.ssrf_challenge;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * SSRF Lab servlet demo.
 * Endpoint:
 *   - /ssrf/preview?url=...   -> fetch URL và render (ảnh hoặc source code text)
 *   - /ssrf/openStream?url=... -> stream raw bytes (download)
 *   - /ssrf/httpurlconn?url=... -> raw GET và in text/plain
 */
@WebServlet(urlPatterns = {"/ssrf/preview", "/ssrf/openStream", "/ssrf/httpurlconn"})
public class SSRF extends HttpServlet {
    private static final int CONNECT_TIMEOUT = 5000;
    private static final int READ_TIMEOUT = 5000;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        String path = req.getServletPath();
        String url = req.getParameter("url");

        if ("/ssrf/preview".equals(path)) {
            previewHandler(url, req, resp);
            return;
        } else if ("/ssrf/openStream".equals(path)) {
            openStreamHandler(url, resp);
            return;
        } else if ("/ssrf/httpurlconn".equals(path)) {
            httpUrlConnHandler(url, resp);
            return;
        }

        resp.setStatus(404);
        resp.getWriter().println("Not found");
    }

    // --- Preview: hiển thị kết quả trong HTML wrapper ---
    private void previewHandler(String urlParam, HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        resp.setContentType("text/html;charset=UTF-8");
        PrintWriter out = resp.getWriter();

        // Header HTML + CSS inline
        out.println("<!doctype html><html><head><meta charset='utf-8'>");
        out.println("<style>body{font-family:Segoe UI,Arial,sans-serif;"
                + "background:#fff;color:#000;padding:12px;font-size:14px}"
                + "h1{color:#007bff;font-size:20px;margin:0 0 12px 0}"
                + "pre{white-space:pre-wrap;word-break:break-word;"
                + "color:#000;background:#f8f8f8;padding:10px;border-radius:6px;"
                + "border:1px solid #ccc;font-size:13px;}"
                + "</style></head><body>");

        out.println("<h1>Preview Service</h1>");
        out.println("<form method='get' action='" + req.getContextPath()
                + "/ssrf/preview'><input name='url' value='" + escapeHtml(urlParam)
                + "' style='width:70%;padding:6px;font-size:14px'><button>Fetch</button></form>");

        if (urlParam == null || urlParam.isEmpty()) {
            out.println("<p style='color:gray'>No URL provided.</p></body></html>");
            return;
        }

        out.println("<p style='color:gray'>Requested URL: " + escapeHtml(urlParam) + "</p>");

        try {
            URL u = new URL(urlParam);

            if ("file".equalsIgnoreCase(u.getProtocol())) {
                // đọc file local
                File f = new File(u.getPath());
                if (!f.exists()) throw new FileNotFoundException(u.getPath());
                byte[] data = java.nio.file.Files.readAllBytes(f.toPath());
                String text = new String(data, StandardCharsets.UTF_8);
                out.println("<pre>" + escapeHtml(text) + "</pre>");
            } else {
                HttpURLConnection conn = (HttpURLConnection) u.openConnection();
                conn.setConnectTimeout(CONNECT_TIMEOUT);
                conn.setReadTimeout(READ_TIMEOUT);
                conn.setInstanceFollowRedirects(true);
                conn.setRequestProperty("User-Agent", "SSRF-Lab/1.0");

                int code = conn.getResponseCode();
                out.println("<p style='color:gray'>HTTP response code: " + code + "</p>");

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                try (InputStream is = conn.getInputStream()) {
                    byte[] buf = new byte[4096];
                    int n;
                    while ((n = is.read(buf)) != -1) baos.write(buf, 0, n);
                }

                byte[] content = baos.toByteArray();
                String contentType = conn.getContentType();
                if (contentType != null && contentType.startsWith("image/")) {
                    String b64 = Base64.getEncoder().encodeToString(content);
                    out.println("<img src='data:" + escapeHtml(contentType)
                            + ";base64," + b64
                            + "' style='max-width:100%;border:1px solid #ccc;border-radius:4px'/>");
                } else {
                    // hiển thị source code dạng text
                    String text = new String(content, StandardCharsets.UTF_8);
                    out.println("<pre>" + escapeHtml(text) + "</pre>");
                }
            }
        } catch (Exception ex) {
            out.println("<pre style='color:red'>Fetch error: "
                    + escapeHtml(ex.toString()) + "</pre>");
        }

        out.println("</body></html>");
    }

    // --- openStream: tải về file ---
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

    // --- httpUrlConn: raw text/plain GET ---
    private void httpUrlConnHandler(String urlParam, HttpServletResponse resp) throws IOException {
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

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&","&amp;")
                .replace("<","&lt;")
                .replace(">","&gt;")
                .replace("\"","&quot;");
    }
}

<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%
    String context = request.getContextPath();
%>
<!doctype html>
<html lang="vi">
<head>
    <meta charset="utf-8"/>
    <meta name="viewport" content="width=device-width,initial-scale=1"/>
    <title>SSRF Lab</title>

    <!-- CSS viết trực tiếp -->
    <style>
        body {
            margin: 0;
            font-family: "Segoe UI", Arial, sans-serif;
            background: #ffffff;
            color: #000000;
            font-size: 16px;
        }
        .wrap {
            max-width: 1000px;
            margin: 40px auto;
            padding: 20px;
        }
        .title {
            font-size: 28px;
            font-weight: bold;
            margin-bottom: 20px;
            color: #007bff;
        }
        .form {
            display: flex;
            flex-direction: column;
            gap: 14px;
            margin-bottom: 20px;
        }
        .form input[name="url"] {
            width: 100%;
            padding: 14px;
            font-size: 16px;
            border-radius: 6px;
            border: 1px solid #ccc;
        }
        .buttons {
            display: flex;
            gap: 12px;
            flex-wrap: wrap;
        }
        .btn {
            padding: 12px 18px;
            border-radius: 6px;
            border: 1px solid #ccc;
            background: #f0f0f0;
            cursor: pointer;
            font-size: 15px;
            font-weight: 600;
        }
        .btn.primary {
            background: #007bff;
            color: #fff;
            border: 0;
        }
        .preview {
            border: 1px solid #ccc;
            border-radius: 6px;
            background: #fafafa;
            padding: 10px;
        }
        iframe#resultFrame {
            width: 100%;
            height: 700px; /* iframe cao */
            border: 0;
            background: #fff;
            font-size: 16px;
        }
    </style>
</head>
<body>
<main class="wrap">
    <h1 class="title">SSRF Lab</h1>

    <!-- Form fetch URL -->
    <form id="previewForm" action="<%=context%>/ssrf/preview" method="get" target="resultFrame" class="form">
        <input id="url" name="url" placeholder="Nhập URL (ví dụ: http://127.0.0.1:8080/ssrf-lab/admin)" />
        <div class="buttons">
            <button class="btn primary" type="submit">Fetch</button>
            <button type="button" class="btn"
                    onclick="document.getElementById('url').value='http://127.0.0.1:8080/ssrf-lab/admin'">
                Example: admin
            </button>
            <button type="button" class="btn"
                    onclick="document.getElementById('url').value='file:///etc/passwd'">
                Example: /etc/passwd
            </button>
        </div>
    </form>

    <!-- Khung hiển thị kết quả -->
    <div class="preview">
        <iframe id="resultFrame" name="resultFrame"
                sandbox="allow-same-origin allow-forms allow-scripts"></iframe>
    </div>
</main>

<script>
    // Nhấn Enter trong input sẽ submit form
    document.getElementById('url').addEventListener('keydown', function(e){
        if(e.key === 'Enter'){
            e.preventDefault();
            document.getElementById('previewForm').submit();
        }
    });
</script>
</body>
</html>

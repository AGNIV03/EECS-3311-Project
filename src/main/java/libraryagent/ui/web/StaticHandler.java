package libraryagent.ui.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

/** Serves the web interface's files from {@code src/main/resources/web}. */
final class StaticHandler implements HttpHandler {

    private static final Map<String, String> TYPES = Map.of(
            "html", "text/html; charset=utf-8",
            "css", "text/css; charset=utf-8",
            "js", "text/javascript; charset=utf-8",
            "svg", "image/svg+xml",
            "png", "image/png",
            "ico", "image/x-icon",
            "json", "application/json; charset=utf-8");

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if (path.equals("/") || path.isEmpty()) {
            path = "/index.html";
        }
        if (!exchange.getRequestMethod().equals("GET") || path.contains("..") || path.contains("\\")) {
            send(exchange, 404, "text/plain; charset=utf-8", "Not found".getBytes());
            return;
        }
        try (InputStream in = StaticHandler.class.getResourceAsStream("/web" + path)) {
            if (in == null) {
                send(exchange, 404, "text/plain; charset=utf-8", "Not found".getBytes());
                return;
            }
            String ext = path.substring(path.lastIndexOf('.') + 1);
            send(exchange, 200, TYPES.getOrDefault(ext, "application/octet-stream"), in.readAllBytes());
        }
    }

    private static void send(HttpExchange exchange, int status, String type, byte[] bytes) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.getResponseHeaders().set("Referrer-Policy", "no-referrer");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}

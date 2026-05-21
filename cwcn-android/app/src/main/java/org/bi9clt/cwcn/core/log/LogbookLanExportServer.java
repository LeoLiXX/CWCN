package org.bi9clt.cwcn.core.log;

import android.content.Context;

import org.bi9clt.cwcn.core.adif.CwAdifExporter;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class LogbookLanExportServer {
    private static final LogbookLanExportServer INSTANCE = new LogbookLanExportServer();

    private final Object lock = new Object();

    private Context appContext;
    private String programVersion;
    private ServerSocket serverSocket;
    private ExecutorService requestExecutor;
    private Thread acceptThread;
    private int port;
    private String hostAddress;
    private long startedAtEpochMs;

    private LogbookLanExportServer() {
    }

    public static LogbookLanExportServer getInstance() {
        return INSTANCE;
    }

    public void start(Context context, String programVersion) throws IOException {
        synchronized (lock) {
            if (isRunningLocked()) {
                return;
            }
            appContext = context.getApplicationContext();
            this.programVersion = programVersion == null ? "" : programVersion;
            serverSocket = new ServerSocket(0);
            serverSocket.setReuseAddress(true);
            port = serverSocket.getLocalPort();
            hostAddress = resolveHostAddress();
            startedAtEpochMs = System.currentTimeMillis();
            requestExecutor = Executors.newCachedThreadPool();
            acceptThread = new Thread(this::runAcceptLoop, "cwcn-logbook-lan-export");
            acceptThread.setDaemon(true);
            acceptThread.start();
        }
    }

    public void stop() {
        ServerSocket socketToClose;
        ExecutorService executorToShutdown;
        synchronized (lock) {
            socketToClose = serverSocket;
            executorToShutdown = requestExecutor;
            serverSocket = null;
            requestExecutor = null;
            acceptThread = null;
            port = 0;
            hostAddress = null;
            startedAtEpochMs = 0L;
        }
        if (socketToClose != null) {
            try {
                socketToClose.close();
            } catch (IOException ignored) {
                // Best-effort shutdown.
            }
        }
        if (executorToShutdown != null) {
            executorToShutdown.shutdownNow();
        }
    }

    public boolean isRunning() {
        synchronized (lock) {
            return isRunningLocked();
        }
    }

    public String getBaseUrl() {
        synchronized (lock) {
            if (!isRunningLocked()) {
                return null;
            }
            return "http://" + hostAddress + ":" + port + "/";
        }
    }

    public String getStatusSummary() {
        synchronized (lock) {
            if (!isRunningLocked()) {
                return "LAN export: OFF";
            }
            return "LAN export: ON  |  " + getBaseUrl();
        }
    }

    private boolean isRunningLocked() {
        return serverSocket != null && !serverSocket.isClosed() && requestExecutor != null;
    }

    private void runAcceptLoop() {
        while (true) {
            ServerSocket currentServerSocket;
            ExecutorService currentExecutor;
            synchronized (lock) {
                currentServerSocket = serverSocket;
                currentExecutor = requestExecutor;
            }
            if (currentServerSocket == null || currentExecutor == null) {
                return;
            }
            try {
                Socket clientSocket = currentServerSocket.accept();
                currentExecutor.execute(() -> handleClient(clientSocket));
            } catch (IOException exception) {
                synchronized (lock) {
                    if (!isRunningLocked()) {
                        return;
                    }
                }
            }
        }
    }

    private void handleClient(Socket clientSocket) {
        try (Socket socket = clientSocket;
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)
             )) {
            String requestLine = reader.readLine();
            if (requestLine == null || requestLine.trim().isEmpty()) {
                return;
            }
            String headerLine;
            while ((headerLine = reader.readLine()) != null && !headerLine.isEmpty()) {
                // Headers are not needed for this tiny export server.
            }
            String[] parts = requestLine.split(" ");
            if (parts.length < 2) {
                writeResponse(socket.getOutputStream(), 400, "text/plain; charset=UTF-8", "Bad request");
                return;
            }
            String target = parts[1];
            String path = target;
            String query = null;
            int querySeparator = target.indexOf('?');
            if (querySeparator >= 0) {
                path = target.substring(0, querySeparator);
                query = target.substring(querySeparator + 1);
            }
            if ("/".equals(path)) {
                writeResponse(
                        socket.getOutputStream(),
                        200,
                        "text/html; charset=UTF-8",
                        buildIndexHtml()
                );
                return;
            }
            if ("/adi".equals(path)) {
                writeAdifDownload(socket.getOutputStream(), parseQuery(query));
                return;
            }
            writeResponse(socket.getOutputStream(), 404, "text/plain; charset=UTF-8", "Not found");
        } catch (IOException ignored) {
            // Browser disconnected; nothing to recover here.
        }
    }

    private void writeAdifDownload(OutputStream outputStream, Map<String, String> query) throws IOException {
        LogbookExportRequest request = new LogbookExportRequest(
                parseConfirmationScope(query.get("confirm")),
                parseTimeRangeScope(query.get("time"))
        );
        Context context = appContext;
        if (context == null) {
            writeResponse(outputStream, 503, "text/plain; charset=UTF-8", "Server unavailable");
            return;
        }
        List<ConfirmedQsoLog> logs = LogbookExportSupport.loadLogs(new LocalLogRepository(context), request);
        String body = CwAdifExporter.buildAdifFile(logs, programVersion);
        String fileName = "cwcn-log-"
                + LogbookExportSupport.buildRequestLabel(request)
                + "-"
                + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date())
                + ".adi";
        writeResponse(outputStream, 200, "text/plain; charset=UTF-8", body, fileName);
    }

    private String buildIndexHtml() {
        Context context = appContext;
        List<String> entries = new ArrayList<>();
        if (context != null) {
            LocalLogRepository repository = new LocalLogRepository(context);
            for (LogbookExportRequest.TimeRangeScope timeRangeScope : LogbookExportRequest.TimeRangeScope.values()) {
                for (LogbookExportRequest.ConfirmationScope confirmationScope : LogbookExportRequest.ConfirmationScope.values()) {
                    LogbookExportRequest request = new LogbookExportRequest(confirmationScope, timeRangeScope);
                    List<ConfirmedQsoLog> logs = LogbookExportSupport.loadLogs(repository, request);
                    String href = "/adi?time="
                            + toQueryValue(timeRangeScope)
                            + "&confirm="
                            + toQueryValue(confirmationScope);
                    entries.add("<li><a href=\""
                            + href
                            + "\">"
                            + escapeHtml(LogbookExportSupport.buildRequestSummary(request))
                            + "</a> ("
                            + logs.size()
                            + ")</li>");
                }
            }
        }

        String startedText = startedAtEpochMs <= 0L
                ? "-"
                : new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date(startedAtEpochMs));
        StringBuilder builder = new StringBuilder();
        builder.append("<!doctype html><html><head><meta charset=\"utf-8\">");
        builder.append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">");
        builder.append("<title>CWCN LAN Export</title>");
        builder.append("<style>");
        builder.append("body{font-family:sans-serif;padding:20px;background:#f4f5f7;color:#1d2433;}");
        builder.append("h1{margin:0 0 8px;font-size:24px;}p{margin:6px 0;}ul{padding-left:20px;}");
        builder.append("li{margin:8px 0;}a{color:#0b57d0;text-decoration:none;}a:hover{text-decoration:underline;}");
        builder.append(".box{background:#fff;border-radius:10px;padding:16px;box-shadow:0 1px 6px rgba(0,0,0,.08);}");
        builder.append("</style></head><body><div class=\"box\">");
        builder.append("<h1>CWCN LAN Export</h1>");
        builder.append("<p>Started: ").append(escapeHtml(startedText)).append("</p>");
        builder.append("<p>Base URL: ").append(escapeHtml(getBaseUrl() == null ? "-" : getBaseUrl())).append("</p>");
        builder.append("<p>Downloads:</p><ul>");
        if (entries.isEmpty()) {
            builder.append("<li>No QSO records.</li>");
        } else {
            for (String entry : entries) {
                builder.append(entry);
            }
        }
        builder.append("</ul></div></body></html>");
        return builder.toString();
    }

    private void writeResponse(OutputStream outputStream, int statusCode, String contentType, String body)
            throws IOException {
        writeResponse(outputStream, statusCode, contentType, body, null);
    }

    private void writeResponse(
            OutputStream outputStream,
            int statusCode,
            String contentType,
            String body,
            String attachmentFileName
    ) throws IOException {
        byte[] bodyBytes = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)
        )) {
            writer.write("HTTP/1.1 " + statusCode + " " + reasonPhrase(statusCode) + "\r\n");
            writer.write("Content-Type: " + contentType + "\r\n");
            writer.write("Content-Length: " + bodyBytes.length + "\r\n");
            writer.write("Cache-Control: no-store\r\n");
            writer.write("Connection: close\r\n");
            if (attachmentFileName != null) {
                writer.write("Content-Disposition: attachment; filename=\"" + attachmentFileName + "\"\r\n");
            }
            writer.write("\r\n");
            writer.flush();
            outputStream.write(bodyBytes);
            outputStream.flush();
        }
    }

    private Map<String, String> parseQuery(String query) {
        if (query == null || query.isEmpty()) {
            return Collections.emptyMap();
        }
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            if (pair == null || pair.isEmpty()) {
                continue;
            }
            int separator = pair.indexOf('=');
            String key = separator >= 0 ? pair.substring(0, separator) : pair;
            String value = separator >= 0 ? pair.substring(separator + 1) : "";
            values.put(urlDecode(key), urlDecode(value));
        }
        return values;
    }

    private String urlDecode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8.name());
        } catch (Exception exception) {
            return value;
        }
    }

    private LogbookExportRequest.ConfirmationScope parseConfirmationScope(String rawValue) {
        if (rawValue == null) {
            return LogbookExportRequest.ConfirmationScope.ALL;
        }
        String normalized = rawValue.trim().toUpperCase(Locale.US);
        if ("CONFIRMED".equals(normalized)) {
            return LogbookExportRequest.ConfirmationScope.CONFIRMED;
        }
        if ("UNCONFIRMED".equals(normalized)) {
            return LogbookExportRequest.ConfirmationScope.UNCONFIRMED;
        }
        return LogbookExportRequest.ConfirmationScope.ALL;
    }

    private LogbookExportRequest.TimeRangeScope parseTimeRangeScope(String rawValue) {
        if (rawValue == null) {
            return LogbookExportRequest.TimeRangeScope.ALL;
        }
        String normalized = rawValue.trim().toUpperCase(Locale.US);
        if ("TODAY".equals(normalized)) {
            return LogbookExportRequest.TimeRangeScope.TODAY;
        }
        if ("THIS_MONTH".equals(normalized)) {
            return LogbookExportRequest.TimeRangeScope.THIS_MONTH;
        }
        return LogbookExportRequest.TimeRangeScope.ALL;
    }

    private String toQueryValue(LogbookExportRequest.ConfirmationScope scope) {
        return scope == null ? "all" : scope.name().toLowerCase(Locale.US);
    }

    private String toQueryValue(LogbookExportRequest.TimeRangeScope scope) {
        return scope == null ? "all" : scope.name().toLowerCase(Locale.US);
    }

    private String reasonPhrase(int statusCode) {
        switch (statusCode) {
            case 200:
                return "OK";
            case 400:
                return "Bad Request";
            case 404:
                return "Not Found";
            case 503:
                return "Service Unavailable";
            default:
                return "OK";
        }
    }

    private String resolveHostAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces == null) {
                return "127.0.0.1";
            }
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (!networkInterface.isUp() || networkInterface.isLoopback()) {
                    continue;
                }
                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (address instanceof Inet4Address && !address.isLoopbackAddress()) {
                        return address.getHostAddress();
                    }
                }
            }
        } catch (SocketException ignored) {
            // Fallback below.
        }
        return "127.0.0.1";
    }

    private String escapeHtml(String raw) {
        if (raw == null) {
            return "";
        }
        return raw
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}

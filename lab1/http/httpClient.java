import java.io.*;
import java.net.*;

public class httpClient {
    private String protocol;
    private String host;
    private String filename;
    private String path;
    private int port;

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java httpClient <URL>");
            System.err.println("Example: java httpClient http://www.faqs.org/rfcs/rfc2068.html");
            return;
        }

        httpClient client = new httpClient();
        client.readUrl(args[0]);
    }

    private void readUrl(String in) {
        try {
            // Parse URL using URI.create as specified
            URL url = URI.create(in).toURL();
            
            // Get URL components
            this.protocol = url.getProtocol();
            if (!"http".equalsIgnoreCase(this.protocol)) {
                System.err.println("Only HTTP protocol is supported");
                return;
            }

            this.host = url.getHost();
            this.port = (url.getPort() != -1) ? url.getPort() : 80; // Default HTTP port
            
            // Get full path and filename
            this.path = url.getPath();
            if (this.path.isEmpty()) {
                this.path = "/";
            }
            
            // Extract filename from path or use default
            String lastPart = this.path.substring(this.path.lastIndexOf('/') + 1);
            this.filename = lastPart.isEmpty() ? "index.html" : lastPart;

            System.out.println("Connecting to " + host + ":" + port);
            System.out.println("Retrieving file: " + filename);
            System.out.println("Full path: " + this.path);
            
            // Get the content
            getURL();

        } catch (IllegalArgumentException | MalformedURLException e) {
            System.err.println("Invalid URL format: " + e.getMessage());
        }
    }

    private void getURL() {
        try (Socket socket = new Socket(host, port)) {
            // Check if socket is open (as requested)
            if (!socket.isConnected()) {
                throw new IOException("Failed to connect to " + host);
            }
            Thread.sleep(1000); // Pause as requested to observe connection
            
            // Send HTTP GET request with full path
            String request = String.format("GET %s HTTP/1.1\r\n", this.path);
            request += String.format("Host: %s\r\n", host);  // Don't include port in Host header
            request += "User-Agent: Mozilla/5.0 JavaHTTPClient/1.0\r\n";
            request += "Accept: text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8\r\n";
            request += "Accept-Language: en-US,en;q=0.5\r\n";
            request += "Connection: close\r\n";
            request += "\r\n";
            
            // Send request using raw bytes to ensure proper CRLF
            OutputStream outStream = socket.getOutputStream();
            outStream.write(request.getBytes("UTF-8"));
            outStream.flush();

            // Read response
            InputStream from = socket.getInputStream();
            
            // First, read and parse headers to check status code
            BufferedReader headerReader = new BufferedReader(new InputStreamReader(from));
            String statusLine = headerReader.readLine();
            if (statusLine == null || !statusLine.contains("200 OK")) {
                System.err.println("Error: Server returned " + (statusLine != null ? statusLine : "no response"));
                return;
            }

            // Skip remaining headers
            String line;
            while ((line = headerReader.readLine()) != null && !line.isEmpty()) {
                // Skip headers
            }

            // Now read the body and save to file
            try (FileOutputStream out = new FileOutputStream(filename)) {
                byte[] buf = new byte[4096];
                int bytes_read;
                
                // Read the body directly from the same input stream
                while ((bytes_read = from.read(buf)) != -1) {
                    out.write(buf, 0, bytes_read);
                }
                
                System.out.println("File saved successfully as: " + filename);
            }

        } catch (IOException e) {
            System.err.println("Error during connection or I/O: " + e.getMessage());
        } catch (InterruptedException e) {
            System.err.println("Sleep interrupted: " + e.getMessage());
        }
    }
}
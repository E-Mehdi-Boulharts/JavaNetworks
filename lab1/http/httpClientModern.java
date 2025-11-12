import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;

public class httpClientModern {
    private String protocol;
    private String host;
    private String filename;
    private String path;
    private int port;

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java httpClientModern <URL>");
            System.err.println("Example: java httpClientModern http://www.faqs.org/rfcs/rfc2068.html");
            return;
        }

        httpClientModern client = new httpClientModern();
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
            this.port = (url.getPort() != -1) ? url.getPort() : 80;
            
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
        try {
            // Create an HTTP client with timeout and redirect following
            HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(20))
                .build();

            // Create the HTTP request
            HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI(protocol + "://" + host + 
                     (port != 80 ? ":" + port : "") + path))
                .header("User-Agent", "Mozilla/5.0 JavaHTTPClient/1.0")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .GET()
                .build();

            // Send the request and get response
            HttpResponse<Path> response = client.send(request, 
                HttpResponse.BodyHandlers.ofFile(Paths.get(filename)));

            // Check response status
            int statusCode = response.statusCode();
            if (statusCode == 200) {
                System.out.println("File saved successfully as: " + filename);
            } else {
                System.err.println("Error: Server returned HTTP/" + 
                    response.version() + " " + statusCode);
                // Delete the file if it was created but we got an error
                Files.deleteIfExists(Paths.get(filename));
            }

        } catch (IOException e) {
            System.err.println("Error during connection or I/O: " + e.getMessage());
        } catch (InterruptedException e) {
            System.err.println("Request interrupted: " + e.getMessage());
            Thread.currentThread().interrupt();
        } catch (URISyntaxException e) {
            System.err.println("Invalid URI syntax: " + e.getMessage());
        }
    }
}
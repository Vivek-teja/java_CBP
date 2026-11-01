package analytics;

import database.DatabaseConnection;
import java.awt.Desktop;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;

/**
 * Manages the background execution and lifecycle of the real-time R Shiny Live Dashboard.
 * Launches R_Analytics/app.R on local port 7860 and opens it in the browser or RStudio.
 */
public class RShinyLauncher {

    private static final RShinyLauncher INSTANCE = new RShinyLauncher();

    public static RShinyLauncher getInstance() {
        return INSTANCE;
    }

    private static final int PORT = 7860;
    private static final String SHINY_URL = "http://127.0.0.1:" + PORT + "/";

    private volatile Process shinyProcess = null;

    private RShinyLauncher() {
        Runtime.getRuntime().addShutdownHook(new Thread(this::stopServer, "R-Shiny-Shutdown-Hook"));
    }

    /**
     * Checks if the R Shiny server is currently active and responding on localhost:7860.
     */
    public boolean isServerRunning() {
        try {
            URL url = new URI(SHINY_URL).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(600);
            conn.setReadTimeout(600);
            conn.setRequestMethod("GET");
            int code = conn.getResponseCode();
            return (code >= 200 && code < 400);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Launches the R Shiny server process in the background (if not already running)
     * and opens http://127.0.0.1:7860/ in the default system web browser.
     */
    public synchronized void launchShinyDashboard() {
        new Thread(() -> {
            try {
                if (!isServerRunning()) {
                    startServerProcess();
                }

                // Wait up to 5 seconds for server to respond
                long start = System.currentTimeMillis();
                while (System.currentTimeMillis() - start < 5000) {
                    if (isServerRunning()) break;
                    Thread.sleep(400);
                }

                openBrowser(SHINY_URL);
            } catch (Exception e) {
                System.err.println("[RShinyLauncher] Failed to launch R Shiny dashboard: " + e.getMessage());
            }
        }, "R-Shiny-Launcher-Thread").start();
    }

    private void startServerProcess() throws Exception {
        String rScriptBin = DatabaseConnection.getRScriptPath();
        File appDir = new File("R_Analytics");
        if (!appDir.exists()) {
            appDir = new File("../R_Analytics");
        }

        System.out.println("[RShinyLauncher] Launching R Shiny Live server on port " + PORT + " via " + rScriptBin + "...");

        ProcessBuilder pb = new ProcessBuilder(
            rScriptBin,
            "-e",
            "shiny::runApp('" + appDir.getAbsolutePath() + "', port = " + PORT + ", host = '127.0.0.1', launch.browser = FALSE)"
        );
        pb.directory(new File("."));
        pb.redirectErrorStream(true);

        shinyProcess = pb.start();

        // Consume process output in a daemon thread so buffer doesn't fill up
        Thread logger = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(shinyProcess.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    // Output consumed silently
                }
            } catch (Exception ignored) {
            }
        }, "R-Shiny-Output-Logger");
        logger.setDaemon(true);
        logger.start();
    }

    private void openBrowser(String urlStr) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(urlStr));
            } else {
                new ProcessBuilder("open", urlStr).start();
            }
        } catch (Exception e) {
            try {
                new ProcessBuilder("open", urlStr).start();
            } catch (Exception ex) {
                System.err.println("[RShinyLauncher] Could not open browser automatically: " + ex.getMessage());
            }
        }
    }

    /**
     * Stops the R Shiny server process cleanly.
     */
    public synchronized void stopServer() {
        if (shinyProcess != null && shinyProcess.isAlive()) {
            System.out.println("[RShinyLauncher] Shutting down R Shiny server process...");
            shinyProcess.destroy();
            try {
                shinyProcess.waitFor();
            } catch (Exception ignored) {
            }
            shinyProcess = null;
        }
    }
}

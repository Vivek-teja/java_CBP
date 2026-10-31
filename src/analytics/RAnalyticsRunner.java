package analytics;

import database.DatabaseConnection;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.SwingUtilities;

/**
 * Asynchronously executes the R visualization script (R_Analytics/generate_graphs.R)
 * using Java ProcessBuilder without ever blocking the Swing Event Dispatch Thread.
 *
 * Generates run-specific graphs into R_Analytics/output/run_<runId>/
 * and notifies listeners upon completion.
 */
public class RAnalyticsRunner {

    public interface RGraphListener {
        void onGraphsUpdated(int runId, File outputDir);
        void onGraphError(int runId, String errorMessage);
    }

    private static final RAnalyticsRunner INSTANCE = new RAnalyticsRunner();

    public static RAnalyticsRunner getInstance() {
        return INSTANCE;
    }

    private final ExecutorService executor;
    private final AtomicBoolean isGenerating = new AtomicBoolean(false);
    private volatile long lastRunTimestamp = 0;

    public RAnalyticsRunner() {
        this.executor = Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "R-Analytics-Worker");
                t.setDaemon(true);
                return t;
            }
        });
    }

    public boolean isGenerating() {
        return isGenerating.get();
    }

    /**
     * Returns the run-specific output directory where graphs are saved.
     */
    public static File getRunOutputDirectory(int runId) {
        File dir = new File("R_Analytics/output/run_" + runId);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    /**
     * Triggers asynchronous graph generation for the given run_id.
     * Enforces non-blocking execution and debounces excessive rapid invocations.
     */
    public void generateGraphsAsync(int runId, RGraphListener listener) {
        if (runId <= 0) return;

        // Debounce: don't start a new generation if one is already running
        if (isGenerating.get()) {
            System.out.println("[RAnalyticsRunner] Graph generation already in progress for Run #" + runId + ". Skipping duplicate call.");
            return;
        }

        executor.submit(() -> {
            isGenerating.set(true);
            long startTime = System.currentTimeMillis();
            try {
                String rScriptBin = DatabaseConnection.getRScriptPath();
                File scriptFile = new File("R_Analytics/generate_graphs.R");
                if (!scriptFile.exists()) {
                    notifyError(listener, runId, "Script not found: " + scriptFile.getAbsolutePath());
                    return;
                }

                File outputDir = getRunOutputDirectory(runId);

                System.out.println("[RAnalyticsRunner] Invoking R for Run #" + runId + " via " + rScriptBin + "...");

                ProcessBuilder pb = new ProcessBuilder(
                        rScriptBin,
                        scriptFile.getAbsolutePath(),
                        String.valueOf(runId)
                );
                pb.directory(new File("."));
                pb.redirectErrorStream(true);

                Process process = pb.start();

                // Read output
                StringBuilder output = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");
                    }
                }

                int exitCode = process.waitFor();
                long elapsedMs = System.currentTimeMillis() - startTime;

                if (exitCode == 0) {
                    System.out.println("[RAnalyticsRunner] Successfully generated graphs for Run #" + runId + " in " + elapsedMs + "ms");
                    lastRunTimestamp = System.currentTimeMillis();
                    if (listener != null) {
                        SwingUtilities.invokeLater(() -> listener.onGraphsUpdated(runId, outputDir));
                    }
                } else {
                    String err = "R exited with code " + exitCode + ":\n" + output;
                    System.err.println("[RAnalyticsRunner] " + err);
                    notifyError(listener, runId, err);
                }
            } catch (Exception e) {
                System.err.println("[RAnalyticsRunner] Process error: " + e.getMessage());
                notifyError(listener, runId, e.getMessage());
            } finally {
                isGenerating.set(false);
            }
        });
    }

    private void notifyError(RGraphListener listener, int runId, String error) {
        if (listener != null) {
            SwingUtilities.invokeLater(() -> listener.onGraphError(runId, error));
        }
    }

    public long getLastRunTimestamp() {
        return lastRunTimestamp;
    }
}

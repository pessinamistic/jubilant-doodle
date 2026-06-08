package com.dbdeployer.startup;

import com.dbdeployer.config.DockerHealthChecker;
import com.dbdeployer.config.DockerHealthChecker.DockerStatus;
import com.dbdeployer.deploy.OsDetector;
import java.awt.Desktop;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.net.URI;
import javax.swing.JOptionPane;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Runs immediately after the Spring context starts.
 *
 * <ol>
 * <li>Probes the Docker daemon via a real socket ping.
 * <li>If Docker is unreachable, logs an OS-specific remediation message and
 * exits with code 1 (fail-fast — Port Wrangler requires Docker).
 * <li>If Docker is reachable, attempts to open the UI in the default browser
 * (only when running as a desktop app, i.e.\ a non-headless environment).
 * </ol>
 */
@Slf4j
@Component
public class DockerStartupCheck implements ApplicationRunner {

    private static final String SEPARATOR = "=".repeat(72);
    private static final String UI_URL = "http://localhost:8080";

    private final DockerHealthChecker dockerHealthChecker;
    private final OsDetector osDetector;

    public DockerStartupCheck(DockerHealthChecker dockerHealthChecker, OsDetector osDetector) {
        this.dockerHealthChecker = dockerHealthChecker;
        this.osDetector = osDetector;
    }

    @Override
    public void run(ApplicationArguments args) {
        DockerStatus status = dockerHealthChecker.check();

        if (!status.available()) {
            printDockerMissingBanner(status);
            showDockerMissingDialog(status);
            System.exit(1);
        }

        log.info("Docker daemon is reachable at {}", status.dockerHost());
        tryOpenBrowser();
    }

    // ── private helpers ───────────────────────────────────────────────────────

    private void printDockerMissingBanner(DockerStatus status) {
        String fix = remediation();
        System.err.println();
        System.err.println(SEPARATOR);
        System.err.println("  PORT WRANGLER — DOCKER DAEMON NOT FOUND");
        System.err.println(SEPARATOR);
        System.err.println();
        System.err.println("  Port Wrangler requires Docker to deploy and manage database");
        System.err.println("  containers. The Docker daemon was not reachable at:");
        System.err.println();
        System.err.println("      " + status.dockerHost());
        System.err.println();
        if (status.errorMessage() != null) {
            System.err.println("  Error: " + status.errorMessage());
            System.err.println();
        }
        System.err.println("  How to fix (" + osDetector.detectOs().name() + "):");
        System.err.println();
        System.err.println("      " + fix);
        System.err.println();
        System.err.println(SEPARATOR);
        System.err.println();
    }

    private void showDockerMissingDialog(DockerStatus status) {
        if (GraphicsEnvironment.isHeadless()) return;

        try {
            StringBuilder message = new StringBuilder();
            message.append("Port Wrangler requires Docker to manage database containers.\n\n")
                    .append("Docker daemon not reachable at:\n")
                    .append(status.dockerHost())
                    .append("\n\n");

            if (status.errorMessage() != null && !status.errorMessage().isBlank()) {
                message.append("Error: ").append(status.errorMessage()).append("\n\n");
            }

            message.append("How to fix (")
                    .append(osDetector.detectOs().name())
                    .append("):\n")
                    .append(remediation());

            JOptionPane.showMessageDialog(
                    null, message.toString(), "Port Wrangler - Docker Required", JOptionPane.ERROR_MESSAGE);
        } catch (RuntimeException e) {
            log.debug("Could not render Docker remediation dialog: {}", e.getMessage());
        }
    }

    private String remediation() {
        return switch (osDetector.detectOs()) {
            case MACOS -> "Start Docker Desktop, or run: colima start";
            case LINUX -> "Run: sudo systemctl start docker";
            case WINDOWS -> "Open Docker Desktop from the Start Menu or system tray";
            default -> "Start the Docker daemon for your platform";
        };
    }

    private void tryOpenBrowser() {
        if (GraphicsEnvironment.isHeadless()) return;
        if (!Desktop.isDesktopSupported()) return;

        Desktop desktop = Desktop.getDesktop();
        if (!desktop.isSupported(Desktop.Action.BROWSE)) return;

        try {
            desktop.browse(URI.create(UI_URL));
            log.info("Opened Port Wrangler in the default browser: {}", UI_URL);
        } catch (IOException e) {
            log.debug("Could not open browser automatically: {}", e.getMessage());
        }
    }
}

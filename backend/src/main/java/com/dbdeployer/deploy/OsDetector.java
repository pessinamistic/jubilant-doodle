package com.dbdeployer.deploy;

import com.dbdeployer.model.DeployMethod;
import java.io.IOException;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Detects the current OS and selects the best deploy method. Priority: Docker
 * (all platforms) > Homebrew (macOS) > apt (Linux) > choco/winget (Windows)
 */
@Component
public class OsDetector {

  public enum OsType {
    MACOS, LINUX, WINDOWS, UNKNOWN
  }

  public OsType detectOs() {
    String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    if (os.contains("mac"))
      return OsType.MACOS;
    if (os.contains("linux"))
      return OsType.LINUX;
    if (os.contains("windows"))
      return OsType.WINDOWS;
    return OsType.UNKNOWN;
  }

  /** Returns the preferred deploy method for this machine */
  public DeployMethod preferredMethod() {
    if (isCommandAvailable("docker"))
      return DeployMethod.DOCKER;
    return switch (detectOs()) {
      case MACOS -> isCommandAvailable("brew") ? DeployMethod.HOMEBREW : DeployMethod.DOCKER;
      case LINUX -> isCommandAvailable("apt") ? DeployMethod.APT : DeployMethod.DOCKER;
      case WINDOWS -> isCommandAvailable("choco")
          ? DeployMethod.CHOCOLATEY
          : isCommandAvailable("winget") ? DeployMethod.WINGET : DeployMethod.DOCKER;
      default -> DeployMethod.DOCKER;
    };
  }

  public boolean isDockerAvailable() {
    return isCommandAvailable("docker");
  }

  public boolean isBrewAvailable() {
    return isCommandAvailable("brew");
  }

  private boolean isCommandAvailable(
    String cmd) {
    try {
      String[] check = System.getProperty("os.name", "").toLowerCase().contains("windows")
          ? new String[]{"where", cmd}
          : new String[]{"which", cmd};
      Process p = Runtime.getRuntime().exec(check);
      return p.waitFor() == 0;
    } catch (IOException | InterruptedException e) {
      return false;
    }
  }

  public SystemInfo getSystemInfo() {
    return new SystemInfo(detectOs().name(), preferredMethod().name(), isDockerAvailable(), isBrewAvailable(),
        isCommandAvailable("apt"), isCommandAvailable("choco"), isCommandAvailable("winget"),
        System.getProperty("os.version"), System.getProperty("os.arch"));
  }

  public record SystemInfo(String osType, String preferredDeployMethod, boolean dockerAvailable, boolean brewAvailable,
      boolean aptAvailable, boolean chocoAvailable, boolean wingetAvailable, String osVersion, String arch) {
  }
}

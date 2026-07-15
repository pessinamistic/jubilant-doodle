package com.dbdeployer.runtime;

/**
 * A local LLM runtime that Port Wrangler can deploy as a managed container. Mirrors the shape of
 * {@link com.dbdeployer.deploy.DatabaseCatalog.DbDefinition} (image, default port, data volume) so
 * a runtime is deployed through the exact same {@code DeployStep} pipeline as any database.
 */
public enum ModelRuntime {
  OLLAMA("Ollama", "ollama/ollama", "latest", 11434, "/root/.ollama"),
  DOCKER_MODEL_RUNNER("Docker Model Runner", "docker/model-runner", "latest", 12434, "/models");

  private final String displayName;
  private final String dockerImage;
  private final String defaultTag;
  private final int defaultPort;
  private final String dataVolumePath;

  ModelRuntime(
      String displayName,
      String dockerImage,
      String defaultTag,
      int defaultPort,
      String dataVolumePath) {
    this.displayName = displayName;
    this.dockerImage = dockerImage;
    this.defaultTag = defaultTag;
    this.defaultPort = defaultPort;
    this.dataVolumePath = dataVolumePath;
  }

  public String displayName() {
    return displayName;
  }

  public String dockerImage() {
    return dockerImage;
  }

  public String defaultTag() {
    return defaultTag;
  }

  public int defaultPort() {
    return defaultPort;
  }

  public String dataVolumePath() {
    return dataVolumePath;
  }

  /**
   * Base URL for the runtime's OpenAI-compatible API on the given published host port.
   *
   * <p>PORTWRANGLER_RUNTIME_HOST overrides the host: when the app itself runs in a container
   * (docker-compose), runtimes are sibling containers publishing ports on the Docker host, so
   * "localhost" would point back at the app — compose sets it to "host.docker.internal".
   */
  public String baseUrl(int hostPort) {
    String host = System.getenv().getOrDefault("PORTWRANGLER_RUNTIME_HOST", "localhost");
    return "http://" + host + ":" + hostPort;
  }
}

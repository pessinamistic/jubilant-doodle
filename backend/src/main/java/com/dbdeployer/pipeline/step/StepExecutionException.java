package com.dbdeployer.pipeline.step;

import com.dbdeployer.pipeline.model.DeployErrorCode;

/**
 * Thrown by a {@link DeployStep} implementation when it encounters a known Docker failure. Carries
 * a {@link DeployErrorCode} so the pipeline runner can record a structured error code alongside the
 * human-readable message.
 */
public class StepExecutionException extends Exception {

  private final DeployErrorCode errorCode;

  public StepExecutionException(DeployErrorCode errorCode, String message) {
    super(message);
    this.errorCode = errorCode;
  }

  public StepExecutionException(DeployErrorCode errorCode, String message, Throwable cause) {
    super(message, cause);
    this.errorCode = errorCode;
  }

  public DeployErrorCode getErrorCode() {
    return errorCode;
  }
}

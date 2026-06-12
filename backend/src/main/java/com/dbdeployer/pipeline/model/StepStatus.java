package com.dbdeployer.pipeline.model;

import lombok.ToString;

@ToString
public enum StepStatus {
  PENDING,
  RUNNING,
  SUCCESS,
  FAILED,
  SKIPPED
}

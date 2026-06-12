package com.dbdeployer.pipeline.model;

import lombok.ToString;

@ToString
public enum PipelineStatus {
  PENDING,
  RUNNING,
  SUCCESS,
  FAILED
}

package com.dbdeployer.pipeline.model;

import lombok.ToString;

@ToString
public enum StepType {
  PULL_IMAGE, CREATE_CONTAINER, START_CONTAINER, FINALISE
}

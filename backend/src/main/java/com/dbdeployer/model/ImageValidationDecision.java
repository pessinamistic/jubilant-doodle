package com.dbdeployer.model;

import lombok.ToString;

/** Effective deploy decision derived from local and remote image checks. */
@ToString
public enum ImageValidationDecision {
  ALLOW, ALLOW_WITH_WARNING, BLOCK
}

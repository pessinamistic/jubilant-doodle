package com.dbdeployer.model;

/** Effective deploy decision derived from local and remote image checks. */
public enum ImageValidationDecision {
    ALLOW,
    ALLOW_WITH_WARNING,
    BLOCK
}

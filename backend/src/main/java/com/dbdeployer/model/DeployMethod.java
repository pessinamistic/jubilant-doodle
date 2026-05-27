package com.dbdeployer.model;

public enum DeployMethod {
    DOCKER,
    HOMEBREW,
    APT,
    CHOCOLATEY,
    WINGET,
    /** Built-in embedded database managed directly by Port Wrangler (e.g. H2). */
    EMBEDDED
}

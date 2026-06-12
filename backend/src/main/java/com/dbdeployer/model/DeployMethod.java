package com.dbdeployer.model;

import lombok.ToString;

@ToString
public enum DeployMethod {
  DOCKER, HOMEBREW, APT, CHOCOLATEY, WINGET,
  /** Built-in embedded database managed directly by Port Wrangler (e.g. H2). */
  EMBEDDED
}

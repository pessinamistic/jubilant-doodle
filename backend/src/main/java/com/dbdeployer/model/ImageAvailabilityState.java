package com.dbdeployer.model;

import lombok.ToString;

/** Availability state of an image tag in a specific source. */
@ToString
public enum ImageAvailabilityState {
  AVAILABLE, MISSING, UNKNOWN, NOT_APPLICABLE
}

package com.dbdeployer.ai.tools;

import java.util.List;

/**
 * "What's my stack" — every active instance with type, port, status, and connection string.
 *
 * @param count number of active instances
 * @param instances the instance summaries
 */
public record StackSummary(int count, List<InstanceSummary> instances) {}

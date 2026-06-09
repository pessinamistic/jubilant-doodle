package com.dbdeployer.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DeploymentResponse {
    DeploymentConfig deploymentConfig;
    DeployedContainer deployedContainer;
}

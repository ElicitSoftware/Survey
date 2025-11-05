package com.elicitsoftware.common.health;

/*-
 * ***LICENSE_START***
 * Elicit Survey
 * %%
 * Copyright (C) 2025 The Regents of the University of Michigan - Rogel Cancer Center
 * %%
 * PolyForm Noncommercial License 1.0.0
 * <https://polyformproject.org/licenses/noncommercial/1.0.0>
 * ***LICENSE_END***
 */

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;

import jakarta.enterprise.context.ApplicationScoped;
import java.nio.file.Files;
import java.nio.file.Paths;

@Liveness
@ApplicationScoped
public class BrandSystemHealthCheck implements HealthCheck {

    private static final String BRAND_PATH = "/brand";

    @Override
    public HealthCheckResponse call() {
        try {
            // Check if brand system is accessible
            boolean brandMounted = Files.exists(Paths.get(BRAND_PATH));
            boolean hasConfig = Files.exists(Paths.get(BRAND_PATH, "brand-config.json"));
            
            return HealthCheckResponse.named("brand-system")
                .status(brandMounted || hasEmbeddedResources())
                .withData("external_brand_mounted", brandMounted)
                .withData("brand_config_available", hasConfig)
                .withData("fallback_available", hasEmbeddedResources())
                .build();
        } catch (Exception e) {
            return HealthCheckResponse.down("Brand system check failed: " + e.getMessage());
        }
    }
    
    private boolean hasEmbeddedResources() {
        return getClass().getResourceAsStream("/META-INF/resources/images/HorizontalLogo.png") != null;
    }
}

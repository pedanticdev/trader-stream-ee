package fish.payara.trader.rest;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

/**
 * JAX-RS application configuration
 */
@ApplicationPath("/api")
public class ApplicationConfig extends Application {
    // All REST resources will be available at /api/*
}

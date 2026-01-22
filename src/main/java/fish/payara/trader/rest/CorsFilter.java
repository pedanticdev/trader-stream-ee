package fish.payara.trader.rest;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;

/**
 * CORS filter for cross-origin requests during comparison demo. Allows frontend on one port to fetch data from the other cluster.
 */
@Provider
public class CorsFilter implements ContainerResponseFilter {

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        String origin = requestContext.getHeaderString("Origin");

        // Allow requests from localhost for demo purposes
        if (origin != null && (origin.contains("localhost") || origin.contains("127.0.0.1"))) {
            responseContext.getHeaders().add("Access-Control-Allow-Origin", origin);
            responseContext.getHeaders().add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            responseContext.getHeaders().add("Access-Control-Allow-Headers", "Content-Type, Authorization");
            responseContext.getHeaders().add("Access-Control-Max-Age", "86400");
            responseContext.getHeaders().add("Access-Control-Allow-Credentials", "true");
        }
    }
}

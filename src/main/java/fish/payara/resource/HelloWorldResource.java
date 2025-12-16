package fish.payara.resource;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.metrics.annotation.Timed;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;

@Path("hello")
public class HelloWorldResource {

    @Inject
    @ConfigProperty(name = "defaultName", defaultValue = "world")
    private String defaultName;

    @GET
    @Operation(summary = "Get a personalized greeting")
    @APIResponses(value = {
        @APIResponse(responseCode = "200", description = "Successful operation"),
        @APIResponse(responseCode = "400", description = "Invalid input")
    })
    @Counted(name = "helloEndpointCount", description = "Count of calls to the hello endpoint")
    @Timed(name = "helloEndpointTime", description = "Time taken to execute the hello endpoint")
    @Timeout(3000) // Timeout after 3 seconds
    @Retry(maxRetries = 3) // Retry the request up to 3 times on failure
    @Fallback(fallbackMethod = "fallbackMethod")
    public Response hello(@QueryParam("name") @Parameter(name = "name", description = "Name to include in the greeting", required = false, example = "John") String name) {
        if ((name == null) || name.trim().isEmpty()) {
            name = defaultName;
        }
        return Response
                .ok(name)
                .build();
    }

    public Response fallbackMethod(@QueryParam("name") String name) {
        // Fallback logic when the hello method fails or exceeds retries
        return Response
                .ok("Fallback data")
                .build();
    }

}
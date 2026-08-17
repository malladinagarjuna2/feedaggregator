package dev.learn;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/test-stub")
@Produces(MediaType.APPLICATION_JSON)
public class StubFeedResource {

    private static volatile String payload = threeHits();

    public static void setPayload(String json) {
        payload = json;
    }

    public static void reset() {
        payload = threeHits();
    }

    @GET
    @Path("/hn")
    public String hn() {
        return payload;
    }

    @GET
    @Path("/broken")
    public Response broken() {
        return Response.serverError().entity("{\"error\":\"upstream exploded\"}").build();
    }

    @GET
    @Path("/garbage")
    public String garbage() {
        return "this is not json at all";
    }

    private static String threeHits() {
        return """
                {
                  "hits": [
                    {"objectID": "1", "title": "Rust ownership explained",
                     "url": "https://example.com/1", "created_at_i": 1700000300,
                     "points": 120, "author": "alice"},
                    {"objectID": "2", "title": "Why Go has no generics (2018)",
                     "url": "https://example.com/2", "created_at_i": 1700000200,
                     "points": 80, "author": "bob"},
                    {"objectID": "3", "title": "Postgres index internals",
                     "url": "https://example.com/3", "created_at_i": 1700000100,
                     "points": 45, "author": "carol"}
                  ],
                  "nbHits": 3,
                  "page": 0,
                  "some_field_we_never_declared": {"nested": true}
                }
                """;
    }
}

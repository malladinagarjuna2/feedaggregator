package dev.learn.api;

import dev.learn.api.dto.CreateFeedRequest;
import dev.learn.api.dto.FeedResponse;
import dev.learn.service.FeedService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

@Path("/feeds")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class FeedResource {

    private final FeedService feeds;

    @Inject
    public FeedResource(FeedService feeds) {
        this.feeds = feeds;
    }

    @GET
    public List<FeedResponse> list() {
        return feeds.list();
    }

    @POST
    public Response create(CreateFeedRequest request) {
        FeedResponse created = feeds.create(request);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") long id) {
        feeds.delete(id);
        return Response.noContent().build();
    }
}

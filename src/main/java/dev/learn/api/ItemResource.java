package dev.learn.api;

import dev.learn.api.dto.ItemPage;
import dev.learn.service.ItemSearchService;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

@Path("/items")
@Produces(MediaType.APPLICATION_JSON)
public class ItemResource {

    private final ItemSearchService search;

    @Inject
    public ItemResource(ItemSearchService search) {
        this.search = search;
    }

    @GET
    public ItemPage search(
            @QueryParam("q") String q,
            @QueryParam("feedId") Long feedId,
            @QueryParam("limit") @DefaultValue("20") int limit,
            @QueryParam("offset") @DefaultValue("0") int offset) {

        return search.search(q, feedId, limit, offset);
    }
}

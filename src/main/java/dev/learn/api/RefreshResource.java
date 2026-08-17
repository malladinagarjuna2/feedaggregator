package dev.learn.api;

import dev.learn.api.dto.RefreshSummary;
import dev.learn.service.RefreshService;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/refresh")
@Produces(MediaType.APPLICATION_JSON)
public class RefreshResource {

    private final RefreshService refresh;

    @Inject
    public RefreshResource(RefreshService refresh) {
        this.refresh = refresh;
    }

    @POST
    public RefreshSummary refresh() {
        return refresh.refreshAllEnabled();
    }
}

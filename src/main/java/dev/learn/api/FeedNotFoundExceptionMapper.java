package dev.learn.api;

import dev.learn.api.dto.ErrorResponse;
import dev.learn.service.FeedNotFoundException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class FeedNotFoundExceptionMapper implements ExceptionMapper<FeedNotFoundException> {

    @Override
    public Response toResponse(FeedNotFoundException exception) {
        return Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorResponse(exception.getMessage()))
                .build();
    }
}

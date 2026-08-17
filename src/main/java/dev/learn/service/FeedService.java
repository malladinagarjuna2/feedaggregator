package dev.learn.service;

import dev.learn.api.dto.CreateFeedRequest;
import dev.learn.api.dto.FeedResponse;
import dev.learn.domain.Feed;
import dev.learn.repository.FeedRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;

@ApplicationScoped
public class FeedService {

    private final FeedRepository feeds;

    @Inject
    public FeedService(FeedRepository feeds) {
        this.feeds = feeds;
    }

    public List<FeedResponse> list() {
        return feeds.listAll().stream().map(FeedResponse::from).toList();
    }

    @Transactional
    public FeedResponse create(CreateFeedRequest request) {
        if (request == null) {
            throw new ValidationException("request body is required");
        }
        Feed feed = new Feed(
                FeedValidation.requireName(request.name()),
                FeedValidation.requireUrl(request.url()));

        feeds.persist(feed);
        return FeedResponse.from(feed);
    }

    @Transactional
    public void delete(long id) {
        Feed feed = feeds.findById(id);
        if (feed == null) {
            throw new FeedNotFoundException(id);
        }
        feeds.delete(feed);
    }
}

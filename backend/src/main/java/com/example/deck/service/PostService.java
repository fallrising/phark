package com.example.deck.service;

import com.example.deck.dto.CreatePostRequest;
import com.example.deck.model.Post;
import com.example.deck.repository.PostRepository;
import jakarta.annotation.PostConstruct;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PostService {

    private final PostRepository postRepository;

    public PostService(PostRepository postRepository) {
        this.postRepository = postRepository;
    }

    public List<Post> getPosts(String channel) {
        if (channel == null || channel.isBlank()) {
            return postRepository.findAll();
        }
        return postRepository.findByChannel(channel);
    }

    public Post createPost(CreatePostRequest request) {
        return postRepository.insert(request.author().trim(), request.content().trim(), request.channel());
    }

    @PostConstruct
    public void seedData() {
        if (postRepository.count() > 0) {
            return;
        }

        postRepository.insertSeed("Alice", "Welcome to Stream Deck! This is the home feed.", "home");
        postRepository.insertSeed("Bob", "Morning standup notes are posted here.", "home");
        postRepository.insertSeed("Carol", "Deployed the latest release to staging.", "home");

        postRepository.insertSeed("Dave", "Exploring Kotlin coroutines for our next service.", "tech");
        postRepository.insertSeed("Eve", "SQLite WAL mode gives us better concurrent reads.", "tech");
        postRepository.insertSeed("Frank", "Benchmarking JdbcClient vs raw JDBC templates.", "tech");

        postRepository.insertSeed("Grace", "On-call rotation starts tonight at 18:00.", "ops");
        postRepository.insertSeed("Henry", "Disk usage on node-3 is back to normal.", "ops");
        postRepository.insertSeed("Ivy", "Scheduled maintenance window confirmed for Sunday.", "ops");
    }
}
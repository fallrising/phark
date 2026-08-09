package com.example.deck.controller;

import com.example.deck.dto.CreatePostRequest;
import com.example.deck.error.ApiErrorCode;
import com.example.deck.error.ApiException;
import com.example.deck.model.Post;
import com.example.deck.model.PostPage;
import com.example.deck.security.AccountPrincipal;
import com.example.deck.service.PostService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/posts")
public class PostController {

    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    @GetMapping
    public PostPage getPosts(
            @RequestParam(required = false) String channel,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String before) {
        return postService.getPosts(channel, limit, before);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Post createPost(
            @AuthenticationPrincipal AccountPrincipal principal,
            @Valid @RequestBody CreatePostRequest request) {
        if (principal == null) {
            throw new ApiException(ApiErrorCode.AUTHENTICATION_REQUIRED);
        }
        return postService.createPost(principal.getAccountId(), request);
    }
}

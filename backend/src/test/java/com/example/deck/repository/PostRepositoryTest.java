package com.example.deck.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.deck.model.Post;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class PostRepositoryTest {

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void newerIdComesFirstWhenPostsHaveTheSameTimestamp() {
        Post first = postRepository.insert("Tester", "First post", "home");
        Post second = postRepository.insert("Tester", "Second post", "home");

        jdbcClient
                .sql("UPDATE posts SET created_at = ? WHERE id IN (?, ?)")
                .param("9999-12-31 23:59:59")
                .param(first.id())
                .param(second.id())
                .update();

        assertThat(postRepository.findAll())
                .extracting(Post::id)
                .startsWith(second.id(), first.id());
        assertThat(postRepository.findByChannel("home"))
                .extracting(Post::id)
                .startsWith(second.id(), first.id());
    }
}

package com.example.deck.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.deck.model.Post;
import com.example.deck.model.PostCursor;
import java.util.List;
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
        Post first = insertAt("First post", "home", "9999-12-31 23:59:59");
        Post second = insertAt("Second post", "home", "9999-12-31 23:59:59");

        assertThat(postRepository.findPage(null, 20, null))
                .extracting(Post::id)
                .startsWith(second.id(), first.id());
        assertThat(postRepository.findPage("home", 20, null))
                .extracting(Post::id)
                .startsWith(second.id(), first.id());
    }

    @Test
    void pageSizeIsBounded() {
        assertThat(postRepository.findPage(null, 2, null)).hasSize(2);
        assertThat(postRepository.findPage("tech", 2, null)).hasSize(2);
    }

    @Test
    void cursorUsesTimestampAndIdAcrossEqualTimestamps() {
        Post first = insertAt("First post", "home", "9999-12-31 23:59:59");
        Post second = insertAt("Second post", "home", "9999-12-31 23:59:59");
        Post third = insertAt("Third post", "home", "9999-12-31 23:59:59");

        List<Post> firstPage = postRepository.findPage("home", 2, null);
        Post boundary = firstPage.get(1);
        List<Post> secondPage = postRepository.findPage(
                "home",
                2,
                new PostCursor(boundary.createdAt(), boundary.id()));

        assertThat(firstPage).extracting(Post::id).containsExactly(third.id(), second.id());
        assertThat(secondPage).extracting(Post::id).startsWith(first.id());
    }

    @Test
    void cursorIgnoresNewerPostsInsertedBetweenPages() {
        Post oldest = insertAt("Oldest post", "home", "9999-12-31 23:59:57");
        Post middle = insertAt("Middle post", "home", "9999-12-31 23:59:58");
        Post latest = insertAt("Latest post", "home", "9999-12-31 23:59:59");

        List<Post> firstPage = postRepository.findPage("home", 2, null);
        Post boundary = firstPage.get(1);
        Post newlyInserted = insertAt("New post", "home", "9999-12-31 23:59:59");
        List<Post> secondPage = postRepository.findPage(
                "home",
                20,
                new PostCursor(boundary.createdAt(), boundary.id()));

        assertThat(firstPage).extracting(Post::id).containsExactly(latest.id(), middle.id());
        assertThat(secondPage).extracting(Post::id)
                .startsWith(oldest.id())
                .doesNotContain(latest.id(), middle.id(), newlyInserted.id());
    }

    @Test
    void channelPageNeverContainsAnotherChannel() {
        insertAt("Newest home post", "home", "9999-12-31 23:59:59");
        insertAt("Newest tech post", "tech", "9999-12-31 23:59:59");

        assertThat(postRepository.findPage("tech", 100, null))
                .extracting(Post::channel)
                .containsOnly("tech");
    }

    private Post insertAt(String content, String channel, String createdAt) {
        Post post = postRepository.insert("Tester", content, channel);
        jdbcClient
                .sql("UPDATE posts SET created_at = ? WHERE id = ?")
                .param(createdAt)
                .param(post.id())
                .update();
        return postRepository.findById(post.id()).orElseThrow();
    }
}

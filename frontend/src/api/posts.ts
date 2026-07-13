import type { Channel, CreatePostRequest, Post } from "@/types/post"

export async function fetchPosts(channel?: Channel): Promise<Post[]> {
  const url = channel ? `/api/posts?channel=${channel}` : "/api/posts"
  const response = await fetch(url)
  if (!response.ok) {
    throw new Error("Failed to load posts")
  }
  return response.json() as Promise<Post[]>
}

export async function createPost(request: CreatePostRequest): Promise<Post> {
  const response = await fetch("/api/posts", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(request),
  })

  if (!response.ok) {
    throw new Error("Failed to create post")
  }

  return response.json() as Promise<Post>
}
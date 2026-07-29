import type { Channel, CreatePostRequest, Post, PostPage } from "@/types/post"

export type FetchPostsOptions = {
  channel?: Channel
  before?: string
  limit?: number
}

export async function fetchPosts({
  channel,
  before,
  limit = 20,
}: FetchPostsOptions = {}): Promise<PostPage> {
  const params = new URLSearchParams({ limit: limit.toString() })
  if (channel) params.set("channel", channel)
  if (before) params.set("before", before)

  const response = await fetch(`/api/posts?${params.toString()}`)
  if (!response.ok) {
    throw new Error("Failed to load posts")
  }
  return response.json() as Promise<PostPage>
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

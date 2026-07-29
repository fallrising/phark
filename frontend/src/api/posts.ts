import type {
  Channel,
  CreatePostRequest,
  CreateReplyRequest,
  Post,
  PostPage,
  Reply,
  ReplyPage,
} from "@/types/post"

export type FetchPostsOptions = {
  channel?: Channel
  before?: string
  limit?: number
}

export type FetchRepliesOptions = {
  limit?: number
  after?: string
}

export async function fetchReplies(
  postId: number,
  { after, limit = 20 }: FetchRepliesOptions = {}
): Promise<ReplyPage> {
  const params = new URLSearchParams({ limit: limit.toString() })
  if (after) params.set("after", after)

  const response = await fetch(`/api/posts/${postId}/replies?${params.toString()}`)
  if (!response.ok) {
    throw new Error("Failed to load replies")
  }
  return response.json() as Promise<ReplyPage>
}

export async function createReply(
  postId: number,
  request: CreateReplyRequest
): Promise<Reply> {
  const response = await fetch(`/api/posts/${postId}/replies`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(request),
  })

  if (!response.ok) {
    throw new Error("Failed to create reply")
  }

  return response.json() as Promise<Reply>
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

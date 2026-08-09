export type Channel = "home" | "tech" | "ops"

export interface Post {
  id: number
  author: string
  authorHandle: string | null
  content: string
  channel: Channel
  createdAt: string
  replyCount: number
}

export interface Reply {
  id: number
  postId: number
  author: string
  authorHandle: string | null
  content: string
  createdAt: string
}

export interface CreateReplyRequest {
  content: string
}

export interface ReplyPage {
  items: Reply[]
  nextCursor: string | null
}

export interface CreatePostRequest {
  content: string
  channel: Channel
}

export interface PostPage {
  items: Post[]
  nextCursor: string | null
}

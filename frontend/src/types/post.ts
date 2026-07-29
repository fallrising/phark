export type Channel = "home" | "tech" | "ops"

export interface Post {
  id: number
  author: string
  content: string
  channel: Channel
  createdAt: string
  replyCount: number
}

export interface Reply {
  id: number
  postId: number
  author: string
  content: string
  createdAt: string
}

export interface CreateReplyRequest {
  author: string
  content: string
}

export interface ReplyPage {
  items: Reply[]
  nextCursor: string | null
}

export interface CreatePostRequest {
  author: string
  content: string
  channel: Channel
}

export interface PostPage {
  items: Post[]
  nextCursor: string | null
}

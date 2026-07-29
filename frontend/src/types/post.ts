export type Channel = "home" | "tech" | "ops"

export interface Post {
  id: number
  author: string
  content: string
  channel: Channel
  createdAt: string
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

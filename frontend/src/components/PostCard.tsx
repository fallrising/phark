import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import type { Post } from "@/types/post"

interface PostCardProps {
  post: Post
}

function formatTimestamp(value: string): string {
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(value))
}

export function PostCard({ post }: PostCardProps) {
  return (
    <Card className="border-border/80 bg-card/95">
      <CardHeader>
        <CardTitle className="text-base">{post.author}</CardTitle>
        <p className="text-xs text-muted-foreground">{formatTimestamp(post.createdAt)}</p>
      </CardHeader>
      <CardContent>
        <p className="whitespace-pre-wrap text-sm leading-relaxed">{post.content}</p>
      </CardContent>
    </Card>
  )
}
import type { AccountProfile } from "@/api/accounts"
import { AuthorLink } from "@/components/AuthorLink"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { ReplyThread } from "@/components/ReplyThread"
import type { Post } from "@/types/post"

interface PostCardProps {
  post: Post
  sessionAccount: AccountProfile | null
  onAuthRequest: () => void
  onNavigateProfile: (handle: string) => void
  onReplyCreated: (postId: number) => void
}

function formatTimestamp(value: string): string {
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(value))
}

export function PostCard({
  post,
  sessionAccount,
  onAuthRequest,
  onNavigateProfile,
  onReplyCreated,
}: PostCardProps) {
  return (
    <Card className="border-border/80 bg-card/95">
      <CardHeader>
        <CardTitle className="text-base">
          <AuthorLink
            author={post.author}
            handle={post.authorHandle}
            className="hover:text-primary hover:underline"
            onNavigateProfile={onNavigateProfile}
          />
        </CardTitle>
        <p className="text-xs text-muted-foreground">{formatTimestamp(post.createdAt)}</p>
      </CardHeader>
      <CardContent className="space-y-4">
        <p className="whitespace-pre-wrap text-sm leading-relaxed">{post.content}</p>
        <ReplyThread
          postId={post.id}
          replyCount={post.replyCount}
          sessionAccount={sessionAccount}
          onAuthRequest={onAuthRequest}
          onNavigateProfile={onNavigateProfile}
          onReplyCreated={onReplyCreated}
        />
      </CardContent>
    </Card>
  )
}

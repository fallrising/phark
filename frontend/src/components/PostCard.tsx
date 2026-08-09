import type { AccountProfile } from "@/api/accounts"
import { Heart } from "lucide-react"
import { AuthorLink } from "@/components/AuthorLink"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { ReplyThread } from "@/components/ReplyThread"
import type { Post } from "@/types/post"

interface PostCardProps {
  post: Post
  sessionAccount: AccountProfile | null
  likePending: boolean
  likeError: string | null
  onAuthRequest: () => void
  onNavigateProfile: (handle: string) => void
  onReplyCreated: (postId: number) => void
  onToggleLike: (post: Post) => Promise<void>
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
  likePending,
  likeError,
  onAuthRequest,
  onNavigateProfile,
  onReplyCreated,
  onToggleLike,
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
        <div className="space-y-2">
          <Button
            type="button"
            variant={post.likedByViewer ? "default" : "outline"}
            size="sm"
            aria-pressed={post.likedByViewer}
            aria-busy={likePending}
            disabled={likePending}
            onClick={() => void onToggleLike(post)}
          >
            <Heart
              className={post.likedByViewer ? "size-4 fill-current" : "size-4"}
            />
            {post.likeCount} {post.likeCount === 1 ? "like" : "likes"}
          </Button>
          {likeError ? (
            <p role="alert" className="text-xs text-destructive">
              {likeError}
            </p>
          ) : null}
        </div>
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

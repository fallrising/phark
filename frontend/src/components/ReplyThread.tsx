import { useRef, useState, type FormEvent } from "react"
import { ChevronDown, ChevronUp, MessageCircle, Send } from "lucide-react"

import { createReply, fetchReplies } from "@/api/posts"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Textarea } from "@/components/ui/textarea"
import type { Reply } from "@/types/post"

interface ReplyThreadProps {
  postId: number
  replyCount: number
  onReplyCreated: (postId: number) => void
}

function formatTimestamp(value: string): string {
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(value))
}

function mergeReplies(current: Reply[], incoming: Reply[]): Reply[] {
  const repliesById = new Map(current.map((reply) => [reply.id, reply]))
  incoming.forEach((reply) => repliesById.set(reply.id, reply))
  return [...repliesById.values()].sort((left, right) => {
    const timestampOrder = left.createdAt.localeCompare(right.createdAt)
    return timestampOrder === 0 ? left.id - right.id : timestampOrder
  })
}

export function ReplyThread({
  postId,
  replyCount,
  onReplyCreated,
}: ReplyThreadProps) {
  const [expanded, setExpanded] = useState(false)
  const [loaded, setLoaded] = useState(false)
  const [items, setItems] = useState<Reply[]>([])
  const [nextCursor, setNextCursor] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)
  const [loadingMore, setLoadingMore] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [author, setAuthor] = useState("")
  const [content, setContent] = useState("")
  const [error, setError] = useState<string | null>(null)
  const loadingPage = useRef(false)
  const submittingReply = useRef(false)
  const threadId = `reply-thread-${postId}`

  async function loadPage(after?: string) {
    if (loadingPage.current) {
      return
    }

    loadingPage.current = true
    setError(null)
    if (after) {
      setLoadingMore(true)
    } else {
      setLoading(true)
    }

    try {
      const page = await fetchReplies(postId, { after })
      setItems((current) => mergeReplies(current, page.items))
      setNextCursor(page.nextCursor)
      setLoaded(true)
    } catch {
      setError("Unable to load replies. Please try again.")
    } finally {
      loadingPage.current = false
      setLoading(false)
      setLoadingMore(false)
    }
  }

  function handleToggle() {
    const shouldExpand = !expanded
    setExpanded(shouldExpand)
    if (shouldExpand && !loaded) {
      void loadPage()
    }
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (submittingReply.current) {
      return
    }

    const trimmedAuthor = author.trim()
    const trimmedContent = content.trim()
    if (!trimmedAuthor || !trimmedContent) {
      setError("Author and reply are required.")
      return
    }

    submittingReply.current = true
    setSubmitting(true)
    setError(null)
    try {
      const reply = await createReply(postId, {
        author: trimmedAuthor,
        content: trimmedContent,
      })
      setItems((current) => mergeReplies(current, [reply]))
      setContent("")
      onReplyCreated(postId)
    } catch {
      setError("Unable to publish reply. Please try again.")
    } finally {
      submittingReply.current = false
      setSubmitting(false)
    }
  }

  return (
    <div className="border-t border-border/70 pt-3">
      <Button
        type="button"
        variant="outline"
        size="sm"
        className="w-full justify-between"
        aria-expanded={expanded}
        aria-controls={threadId}
        onClick={handleToggle}
      >
        <span className="flex items-center gap-2">
          <MessageCircle className="size-4" />
          {replyCount} {replyCount === 1 ? "reply" : "replies"}
        </span>
        {expanded ? (
          <ChevronUp className="size-4" />
        ) : (
          <ChevronDown className="size-4" />
        )}
      </Button>

      {expanded ? (
        <div id={threadId} className="mt-3 space-y-3">
          {loading ? (
            <p className="text-xs text-muted-foreground">Loading replies...</p>
          ) : loaded && items.length === 0 ? (
            <p className="text-xs text-muted-foreground">No replies yet.</p>
          ) : items.length > 0 ? (
            <ol className="space-y-2">
              {items.map((reply) => (
                <li
                  key={reply.id}
                  className="rounded-lg border border-border/60 bg-muted/40 px-3 py-2"
                >
                  <div className="flex items-baseline justify-between gap-2">
                    <span className="text-xs font-medium">{reply.author}</span>
                    <time
                      dateTime={reply.createdAt}
                      className="text-[0.65rem] text-muted-foreground"
                    >
                      {formatTimestamp(reply.createdAt)}
                    </time>
                  </div>
                  <p className="mt-1 whitespace-pre-wrap text-xs leading-relaxed">
                    {reply.content}
                  </p>
                </li>
              ))}
            </ol>
          ) : null}

          {nextCursor ? (
            <Button
              type="button"
              variant="outline"
              size="sm"
              className="w-full"
              disabled={loadingMore}
              onClick={() => void loadPage(nextCursor)}
            >
              {loadingMore ? "Loading..." : "Load more replies"}
            </Button>
          ) : null}

          <form className="space-y-2" onSubmit={handleSubmit}>
            <div className="space-y-1">
              <Label htmlFor={`${threadId}-author`} className="text-xs">
                Author
              </Label>
              <Input
                id={`${threadId}-author`}
                value={author}
                maxLength={80}
                placeholder="Your name"
                onChange={(event) => setAuthor(event.target.value)}
              />
            </div>
            <div className="space-y-1">
              <Label htmlFor={`${threadId}-content`} className="text-xs">
                Reply
              </Label>
              <Textarea
                id={`${threadId}-content`}
                value={content}
                maxLength={500}
                rows={2}
                className="min-h-16"
                placeholder="Join the conversation"
                onChange={(event) => setContent(event.target.value)}
              />
            </div>
            <div className="flex items-center justify-between gap-3">
              <span className="text-[0.65rem] text-muted-foreground">
                {content.length}/500
              </span>
              <Button type="submit" size="sm" disabled={submitting}>
                <Send className="size-3.5" />
                {submitting ? "Replying..." : "Reply"}
              </Button>
            </div>
          </form>

          {error ? (
            <p role="alert" className="text-xs text-destructive">
              {error}
            </p>
          ) : null}
        </div>
      ) : null}
    </div>
  )
}

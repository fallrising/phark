import { useState, type FormEvent } from "react"
import { LayoutGrid, Send } from "lucide-react"

import { createPost, getApiErrorMessage } from "@/api/posts"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { Textarea } from "@/components/ui/textarea"
import type { Channel } from "@/types/post"

interface ComposerProps {
  onPostCreated: () => Promise<void>
}

export function Composer({ onPostCreated }: ComposerProps) {
  const [author, setAuthor] = useState("")
  const [content, setContent] = useState("")
  const [channel, setChannel] = useState<Channel>("home")
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)

    const trimmedAuthor = author.trim()
    const trimmedContent = content.trim()

    if (!trimmedAuthor || !trimmedContent) {
      setError("Author and content are required.")
      return
    }

    if (trimmedContent.length > 500) {
      setError("Content must be 500 characters or fewer.")
      return
    }

    setSubmitting(true)
    try {
      await createPost({
        author: trimmedAuthor,
        content: trimmedContent,
        channel,
      })
      setContent("")
      await onPostCreated()
    } catch (error) {
      setError(getApiErrorMessage(error, "Unable to publish post. Please try again."))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <section className="border-b border-border/70 bg-card/80 backdrop-blur">
      <div className="mx-auto flex w-full max-w-7xl flex-col gap-4 px-4 py-5 sm:px-6">
        <div className="flex items-center gap-3">
          <div className="flex size-10 items-center justify-center rounded-xl bg-primary/10 text-primary">
            <LayoutGrid className="size-5" />
          </div>
          <div>
            <h1 className="text-xl font-semibold tracking-tight">Stream Deck</h1>
            <p className="text-sm text-muted-foreground">
              Three columns. One composer. No bird logos.
            </p>
          </div>
        </div>

        <form
          className="grid gap-4 rounded-2xl border border-border/70 bg-background p-4 shadow-sm md:grid-cols-[1fr_1fr_auto] md:items-end"
          onSubmit={handleSubmit}
        >
          <div className="space-y-2">
            <Label htmlFor="author">Author</Label>
            <Input
              id="author"
              placeholder="Your name"
              value={author}
              onChange={(event) => setAuthor(event.target.value)}
              maxLength={80}
            />
          </div>

          <div className="space-y-2">
            <Label htmlFor="channel">Channel</Label>
            <Select value={channel} onValueChange={(value) => setChannel(value as Channel)}>
              <SelectTrigger id="channel">
                <SelectValue placeholder="Select channel" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="home">Home</SelectItem>
                <SelectItem value="tech">Tech</SelectItem>
                <SelectItem value="ops">Ops</SelectItem>
              </SelectContent>
            </Select>
          </div>

          <Button type="submit" disabled={submitting} className="md:mb-0.5">
            <Send className="size-4" />
            Post
          </Button>

          <div className="space-y-2 md:col-span-3">
            <Label htmlFor="content">Content</Label>
            <Textarea
              id="content"
              placeholder="What is happening?"
              value={content}
              onChange={(event) => setContent(event.target.value)}
              maxLength={500}
              rows={3}
            />
            <div className="flex items-center justify-between text-xs text-muted-foreground">
              <span>{error ?? "Posts refresh all columns after publishing."}</span>
              <span>{content.length}/500</span>
            </div>
          </div>
        </form>
      </div>
    </section>
  )
}
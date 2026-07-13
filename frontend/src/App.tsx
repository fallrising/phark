import { useCallback, useEffect, useState } from "react"

import { fetchPosts } from "@/api/posts"
import { Column } from "@/components/Column"
import { Composer } from "@/components/Composer"
import type { Channel, Post } from "@/types/post"

const CHANNELS: Channel[] = ["home", "tech", "ops"]

export default function App() {
  const [postsByChannel, setPostsByChannel] = useState<Record<Channel, Post[]>>({
    home: [],
    tech: [],
    ops: [],
  })
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const loadPosts = useCallback(async () => {
    setError(null)
    try {
      const results = await Promise.all(
        CHANNELS.map(async (channel) => [channel, await fetchPosts(channel)] as const)
      )
      setPostsByChannel(Object.fromEntries(results) as Record<Channel, Post[]>)
    } catch {
      setError("Failed to load posts.")
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void loadPosts()
  }, [loadPosts])

  return (
    <div className="flex min-h-screen flex-col bg-gradient-to-b from-background to-muted/40">
      <Composer onPostCreated={loadPosts} />

      <main className="mx-auto flex w-full max-w-7xl flex-1 flex-col px-4 py-5 sm:px-6">
        {loading ? (
          <p className="text-sm text-muted-foreground">Loading columns...</p>
        ) : error ? (
          <p className="text-sm text-destructive">{error}</p>
        ) : (
          <div className="flex h-[calc(100vh-17rem)] gap-4 overflow-x-auto pb-4 md:grid md:grid-cols-3 md:overflow-x-visible">
            {CHANNELS.map((channel) => (
              <Column key={channel} channel={channel} posts={postsByChannel[channel]} />
            ))}
          </div>
        )}
      </main>
    </div>
  )
}
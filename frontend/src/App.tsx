import { useCallback, useEffect, useRef, useState } from "react"

import { fetchPosts, getApiErrorMessage } from "@/api/posts"
import { Column } from "@/components/Column"
import { Composer } from "@/components/Composer"
import type { Channel, Post } from "@/types/post"

const CHANNELS: Channel[] = ["home", "tech", "ops"]

interface ChannelFeed {
  items: Post[]
  nextCursor: string | null
  loadingMore: boolean
  error: string | null
}

function emptyFeeds(): Record<Channel, ChannelFeed> {
  return {
    home: { items: [], nextCursor: null, loadingMore: false, error: null },
    tech: { items: [], nextCursor: null, loadingMore: false, error: null },
    ops: { items: [], nextCursor: null, loadingMore: false, error: null },
  }
}

export default function App() {
  const [feeds, setFeeds] = useState<Record<Channel, ChannelFeed>>(emptyFeeds)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const refreshVersion = useRef(0)
  const loadingChannels = useRef(new Set<Channel>())

  const loadPosts = useCallback(async () => {
    const requestVersion = ++refreshVersion.current
    loadingChannels.current.clear()
    setLoading(true)
    setError(null)

    try {
      const results = await Promise.all(
        CHANNELS.map(async (channel) => {
          const page = await fetchPosts({ channel })
          return [
            channel,
            {
              items: page.items,
              nextCursor: page.nextCursor,
              loadingMore: false,
              error: null,
            },
          ] as const
        })
      )

      if (requestVersion === refreshVersion.current) {
        setFeeds(Object.fromEntries(results) as Record<Channel, ChannelFeed>)
      }
    } catch (error) {
      if (requestVersion === refreshVersion.current) {
        setError(getApiErrorMessage(error, "Failed to load posts."))
      }
    } finally {
      if (requestVersion === refreshVersion.current) {
        setLoading(false)
      }
    }
  }, [])

  const loadMore = useCallback(
    async (channel: Channel) => {
      const feed = feeds[channel]
      if (!feed.nextCursor || loadingChannels.current.has(channel)) {
        return
      }

      const requestVersion = refreshVersion.current
      const before = feed.nextCursor
      loadingChannels.current.add(channel)
      setFeeds((current) => ({
        ...current,
        [channel]: {
          ...current[channel],
          loadingMore: true,
          error: null,
        },
      }))

      try {
        const page = await fetchPosts({ channel, before })
        if (requestVersion !== refreshVersion.current) {
          return
        }

        setFeeds((current) => {
          const knownIds = new Set(current[channel].items.map((post) => post.id))
          const newItems = page.items.filter((post) => !knownIds.has(post.id))
          return {
            ...current,
            [channel]: {
              items: [...current[channel].items, ...newItems],
              nextCursor: page.nextCursor,
              loadingMore: false,
              error: null,
            },
          }
        })
      } catch (error) {
        if (requestVersion === refreshVersion.current) {
          setFeeds((current) => ({
            ...current,
            [channel]: {
              ...current[channel],
              loadingMore: false,
              error: getApiErrorMessage(error, "Failed to load more posts."),
            },
          }))
        }
      } finally {
        loadingChannels.current.delete(channel)
      }
    },
    [feeds]
  )

  const handleReplyCreated = useCallback((postId: number) => {
    setFeeds((current) => {
      const updateFeed = (feed: ChannelFeed): ChannelFeed => ({
        ...feed,
        items: feed.items.map((post) =>
          post.id === postId
            ? { ...post, replyCount: post.replyCount + 1 }
            : post
        ),
      })

      return {
        home: updateFeed(current.home),
        tech: updateFeed(current.tech),
        ops: updateFeed(current.ops),
      }
    })
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
            {CHANNELS.map((channel) => {
              const feed = feeds[channel]
              return (
                <Column
                  key={channel}
                  channel={channel}
                  posts={feed.items}
                  hasMore={feed.nextCursor !== null}
                  loadingMore={feed.loadingMore}
                  error={feed.error}
                  onLoadMore={() => void loadMore(channel)}
                  onReplyCreated={handleReplyCreated}
                />
              )
            })}
          </div>
        )}
      </main>
    </div>
  )
}

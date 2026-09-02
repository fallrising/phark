import { useCallback, useEffect, useRef, useState } from "react"
import { LayoutGrid } from "lucide-react"

import {
  fetchSession,
  refreshCsrfToken,
  type AccountProfile,
} from "@/api/accounts"
import { getApiErrorMessage } from "@/api/client"
import {
  fetchPosts,
  likePost,
  repostPost,
  unlikePost,
  unrepostPost,
} from "@/api/posts"
import { AccountControls } from "@/components/AccountControls"
import { Column } from "@/components/Column"
import { Composer } from "@/components/Composer"
import { ProfileView } from "@/components/ProfileView"
import { Button } from "@/components/ui/button"
import {
  applyLikeStateToPosts,
  optimisticLikeState,
  snapshotLikeState,
} from "@/lib/postLikes"
import {
  applyRepostStateToPosts,
  optimisticRepostState,
  snapshotRepostState,
} from "@/lib/postReposts"
import type { Channel, LikeState, Post, RepostState } from "@/types/post"

const CHANNELS: Channel[] = ["home", "tech", "ops"]

interface ChannelFeed {
  items: Post[]
  nextCursor: string | null
  loadingMore: boolean
  error: string | null
}

type Route =
  | { kind: "home" }
  | { kind: "profile"; handle: string }
  | { kind: "not-found" }

function emptyFeeds(): Record<Channel, ChannelFeed> {
  return {
    home: { items: [], nextCursor: null, loadingMore: false, error: null },
    tech: { items: [], nextCursor: null, loadingMore: false, error: null },
    ops: { items: [], nextCursor: null, loadingMore: false, error: null },
  }
}

function applyLikeStateToFeeds(
  feeds: Record<Channel, ChannelFeed>,
  state: LikeState
): Record<Channel, ChannelFeed> {
  const updateFeed = (feed: ChannelFeed): ChannelFeed => ({
    ...feed,
    items: applyLikeStateToPosts(feed.items, state),
  })
  return {
    home: updateFeed(feeds.home),
    tech: updateFeed(feeds.tech),
    ops: updateFeed(feeds.ops),
  }
}

function applyRepostStateToFeeds(
  feeds: Record<Channel, ChannelFeed>,
  state: RepostState
): Record<Channel, ChannelFeed> {
  const updateFeed = (feed: ChannelFeed): ChannelFeed => ({
    ...feed,
    items: applyRepostStateToPosts(feed.items, state),
  })
  return {
    home: updateFeed(feeds.home),
    tech: updateFeed(feeds.tech),
    ops: updateFeed(feeds.ops),
  }
}

function readRoute(): Route {
  const profileMatch = window.location.pathname.match(/^\/profiles\/([^/]+)\/?$/)
  if (profileMatch) {
    try {
      const handle = decodeURIComponent(profileMatch[1])
      return handle ? { kind: "profile", handle } : { kind: "not-found" }
    } catch {
      return { kind: "not-found" }
    }
  }
  return window.location.pathname === "/"
    ? { kind: "home" }
    : { kind: "not-found" }
}

export default function App() {
  const [feeds, setFeeds] = useState<Record<Channel, ChannelFeed>>(emptyFeeds)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [sessionAccount, setSessionAccount] = useState<AccountProfile | null>(null)
  const [securityReady, setSecurityReady] = useState<boolean | null>(null)
  const [identityError, setIdentityError] = useState<string | null>(null)
  const [route, setRoute] = useState<Route>(readRoute)
  const [profileRefreshVersion, setProfileRefreshVersion] = useState(0)
  const [pendingLikeIds, setPendingLikeIds] = useState<Set<number>>(new Set())
  const [likeErrors, setLikeErrors] = useState<Record<number, string>>({})
  const [pendingRepostIds, setPendingRepostIds] = useState<Set<number>>(new Set())
  const [repostErrors, setRepostErrors] = useState<Record<number, string>>({})
  const refreshVersion = useRef(0)
  const loadingChannels = useRef(new Set<Channel>())
  const postMutations = useRef(new Set<number>())
  const bootStarted = useRef(false)
  const accountControlsRef = useRef<HTMLDivElement>(null)

  const loadPosts = useCallback(async () => {
    const requestVersion = ++refreshVersion.current
    loadingChannels.current.clear()
    setLoading(true)
    setError(null)
    setLikeErrors({})
    setRepostErrors({})

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

  const initializeIdentity = useCallback(async () => {
    setSecurityReady(null)
    setIdentityError(null)

    try {
      const [, session] = await Promise.all([
        refreshCsrfToken(),
        fetchSession(),
      ])
      setSessionAccount(session.account)
      setSecurityReady(true)
    } catch (error) {
      setSecurityReady(false)
      setIdentityError(
        getApiErrorMessage(error, "Unable to initialize secure account actions.")
      )
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
          const knownIds = new Set(
            current[channel].items.map((post) => post.timelineEntryId)
          )
          const newItems = page.items.filter(
            (post) => !knownIds.has(post.timelineEntryId)
          )
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

  const requestAuthentication = useCallback(() => {
    accountControlsRef.current?.scrollIntoView({
      behavior: "smooth",
      block: "start",
    })
  }, [])

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

  const handleToggleLike = useCallback(
    async (post: Post) => {
      if (sessionAccount === null) {
        setLikeErrors((current) => ({
          ...current,
          [post.id]: "Sign in to like this post.",
        }))
        requestAuthentication()
        return
      }
      if (securityReady !== true) {
        setLikeErrors((current) => ({
          ...current,
          [post.id]:
            securityReady === null
              ? "Secure actions are still initializing."
              : "Secure actions are unavailable. Retry account security setup.",
        }))
        return
      }
      if (postMutations.current.has(post.id)) return

      const snapshot = snapshotLikeState(post)
      const optimistic = optimisticLikeState(snapshot)
      const requestVersion = refreshVersion.current
      postMutations.current.add(post.id)
      setPendingLikeIds((current) => new Set(current).add(post.id))
      setLikeErrors((current) => {
        const next = { ...current }
        delete next[post.id]
        return next
      })
      setFeeds((current) => applyLikeStateToFeeds(current, optimistic))

      try {
        const state = snapshot.likedByViewer
          ? await unlikePost(post.id)
          : await likePost(post.id)
        if (requestVersion === refreshVersion.current) {
          setFeeds((current) => applyLikeStateToFeeds(current, state))
        }
      } catch (error) {
        if (requestVersion === refreshVersion.current) {
          setFeeds((current) => applyLikeStateToFeeds(current, snapshot))
          setLikeErrors((current) => ({
            ...current,
            [post.id]: getApiErrorMessage(
              error,
              "Unable to update this like. Please try again."
            ),
          }))
        }
      } finally {
        postMutations.current.delete(post.id)
        setPendingLikeIds((current) => {
          const next = new Set(current)
          next.delete(post.id)
          return next
        })
      }
    },
    [requestAuthentication, securityReady, sessionAccount]
  )

  const handleToggleRepost = useCallback(
    async (post: Post) => {
      if (sessionAccount === null) {
        setRepostErrors((current) => ({
          ...current,
          [post.id]: "Sign in to repost this post.",
        }))
        requestAuthentication()
        return
      }
      if (securityReady !== true) {
        setRepostErrors((current) => ({
          ...current,
          [post.id]:
            securityReady === null
              ? "Secure actions are still initializing."
              : "Secure actions are unavailable. Retry account security setup.",
        }))
        return
      }
      if (postMutations.current.has(post.id)) return

      const snapshot = snapshotRepostState(post)
      const optimistic = optimisticRepostState(snapshot)
      const requestVersion = refreshVersion.current
      postMutations.current.add(post.id)
      setPendingRepostIds((current) => new Set(current).add(post.id))
      setRepostErrors((current) => {
        const next = { ...current }
        delete next[post.id]
        return next
      })
      setFeeds((current) => applyRepostStateToFeeds(current, optimistic))

      try {
        let mutationSucceeded = false
        try {
          const state = snapshot.repostedByViewer
            ? await unrepostPost(post.id)
            : await repostPost(post.id)
          mutationSucceeded = true
          if (requestVersion === refreshVersion.current) {
            setFeeds((current) => applyRepostStateToFeeds(current, state))
          }
        } catch (error) {
          if (requestVersion === refreshVersion.current) {
            setFeeds((current) => applyRepostStateToFeeds(current, snapshot))
            setRepostErrors((current) => ({
              ...current,
              [post.id]: getApiErrorMessage(
                error,
                "Unable to update this repost. Please try again."
              ),
            }))
          }
        }

        if (mutationSucceeded) {
          await loadPosts()
        }
      } finally {
        postMutations.current.delete(post.id)
        setPendingRepostIds((current) => {
          const next = new Set(current)
          next.delete(post.id)
          return next
        })
      }
    },
    [requestAuthentication, loadPosts, securityReady, sessionAccount]
  )

  const navigateHome = useCallback(() => {
    window.history.pushState(null, "", "/")
    setRoute({ kind: "home" })
    window.scrollTo({ top: 0, behavior: "smooth" })
  }, [])

  const navigateProfile = useCallback((handle: string) => {
    const path = `/profiles/${encodeURIComponent(handle)}`
    window.history.pushState(null, "", path)
    setRoute({ kind: "profile", handle })
    window.scrollTo({ top: 0, behavior: "smooth" })
  }, [])

  const handleAccountChanged = useCallback(
    async (account: AccountProfile | null) => {
      setSessionAccount(account)
      setIdentityError(null)
      await loadPosts()
      setProfileRefreshVersion((current) => current + 1)
    },
    [loadPosts]
  )

  const handleProfileUpdated = useCallback(
    async (profile: AccountProfile) => {
      setSessionAccount(profile)
      try {
        const session = await fetchSession()
        setSessionAccount(session.account)
      } catch (error) {
        setIdentityError(
          getApiErrorMessage(error, "Profile saved, but session refresh failed.")
        )
      }
      await loadPosts()
    },
    [loadPosts]
  )

  const handlePostCreated = useCallback(async () => {
    await loadPosts()
    setProfileRefreshVersion((current) => current + 1)
  }, [loadPosts])

  useEffect(() => {
    if (bootStarted.current) return
    bootStarted.current = true

    void (async () => {
      await initializeIdentity()
      await loadPosts()
    })()
  }, [initializeIdentity, loadPosts])

  useEffect(() => {
    function handlePopState() {
      setRoute(readRoute())
    }
    window.addEventListener("popstate", handlePopState)
    return () => window.removeEventListener("popstate", handlePopState)
  }, [])

  useEffect(() => {
    document.title =
      route.kind === "profile" ? `@${route.handle} · Phark` : "Phark"
  }, [route])

  return (
    <div className="flex min-h-screen flex-col bg-gradient-to-b from-background to-muted/40">
      <header
        ref={accountControlsRef}
        className="border-b border-border/70 bg-background/95 px-4 py-3 backdrop-blur sm:px-6"
      >
        <div className="mx-auto flex w-full max-w-7xl flex-col justify-between gap-4 md:flex-row md:items-center">
          <button
            type="button"
            className="flex items-center gap-3 text-left"
            onClick={navigateHome}
          >
            <span className="flex size-9 items-center justify-center rounded-xl bg-primary text-primary-foreground">
              <LayoutGrid className="size-4" />
            </span>
            <span>
              <span className="block text-sm font-semibold">Phark</span>
              <span className="block text-xs text-muted-foreground">
                Account-owned conversations
              </span>
            </span>
          </button>

          <AccountControls
            account={sessionAccount}
            securityReady={securityReady}
            onAccountChanged={handleAccountChanged}
            onNavigateProfile={navigateProfile}
            onRetrySecurity={initializeIdentity}
          />
        </div>
        {identityError ? (
          <p role="alert" className="mx-auto mt-2 w-full max-w-7xl text-xs text-destructive">
            {identityError}
          </p>
        ) : null}
      </header>

      <Composer
        account={sessionAccount}
        onAuthRequest={requestAuthentication}
        onPostCreated={handlePostCreated}
      />

      {route.kind === "profile" ? (
        <ProfileView
          key={`${route.handle}:${profileRefreshVersion}`}
          handle={route.handle}
          sessionAccount={sessionAccount}
          securityReady={securityReady}
          onBack={navigateHome}
          onAuthRequest={requestAuthentication}
          onNavigateProfile={navigateProfile}
          onProfileUpdated={handleProfileUpdated}
        />
      ) : route.kind === "not-found" ? (
        <main className="mx-auto w-full max-w-7xl flex-1 px-4 py-8 sm:px-6">
          <p className="text-sm text-destructive">This page does not exist.</p>
          <Button
            type="button"
            variant="outline"
            className="mt-4"
            onClick={navigateHome}
          >
            Return to timeline
          </Button>
        </main>
      ) : (
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
                    sessionAccount={sessionAccount}
                    pendingLikeIds={pendingLikeIds}
                    likeErrors={likeErrors}
                    pendingRepostIds={pendingRepostIds}
                    repostErrors={repostErrors}
                    hasMore={feed.nextCursor !== null}
                    loadingMore={feed.loadingMore}
                    error={feed.error}
                    onLoadMore={() => void loadMore(channel)}
                    onAuthRequest={requestAuthentication}
                    onNavigateProfile={navigateProfile}
                    onReplyCreated={handleReplyCreated}
                    onToggleLike={handleToggleLike}
                    onToggleRepost={handleToggleRepost}
                  />
                )
              })}
            </div>
          )}
        </main>
      )}
    </div>
  )
}

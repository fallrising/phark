import { useCallback, useEffect, useRef, useState } from "react"
import { LayoutGrid } from "lucide-react"

import {
  fetchSession,
  refreshCsrfToken,
  type AccountProfile,
} from "@/api/accounts"
import { getApiErrorMessage } from "@/api/client"
import { fetchPosts } from "@/api/posts"
import { AccountControls } from "@/components/AccountControls"
import { Column } from "@/components/Column"
import { Composer } from "@/components/Composer"
import { ProfileView } from "@/components/ProfileView"
import { Button } from "@/components/ui/button"
import type { Channel, Post } from "@/types/post"

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
  const refreshVersion = useRef(0)
  const loadingChannels = useRef(new Set<Channel>())
  const bootStarted = useRef(false)
  const accountControlsRef = useRef<HTMLDivElement>(null)

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

  const requestAuthentication = useCallback(() => {
    accountControlsRef.current?.scrollIntoView({
      behavior: "smooth",
      block: "start",
    })
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
                    hasMore={feed.nextCursor !== null}
                    loadingMore={feed.loadingMore}
                    error={feed.error}
                    onLoadMore={() => void loadMore(channel)}
                    onAuthRequest={requestAuthentication}
                    onNavigateProfile={navigateProfile}
                    onReplyCreated={handleReplyCreated}
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

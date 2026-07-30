import { useCallback, useEffect, useRef, useState, type FormEvent } from "react"
import { ArrowLeft, CalendarDays, Pencil } from "lucide-react"

import {
  fetchProfile,
  fetchProfilePosts,
  updateProfile,
  type AccountProfile,
} from "@/api/accounts"
import { getApiErrorMessage } from "@/api/client"
import { PostCard } from "@/components/PostCard"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Textarea } from "@/components/ui/textarea"
import type { Post } from "@/types/post"

interface ProfileViewProps {
  handle: string
  sessionAccount: AccountProfile | null
  onBack: () => void
  onAuthRequest: () => void
  onNavigateProfile: (handle: string) => void
  onProfileUpdated: (profile: AccountProfile) => Promise<void>
}

function formatJoined(value: string): string {
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: "long",
  }).format(new Date(value))
}

export function ProfileView({
  handle,
  sessionAccount,
  onBack,
  onAuthRequest,
  onNavigateProfile,
  onProfileUpdated,
}: ProfileViewProps) {
  const [profile, setProfile] = useState<AccountProfile | null>(null)
  const [posts, setPosts] = useState<Post[]>([])
  const [nextCursor, setNextCursor] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [editing, setEditing] = useState(false)
  const [displayName, setDisplayName] = useState("")
  const [bio, setBio] = useState("")
  const [saving, setSaving] = useState(false)
  const requestVersion = useRef(0)

  const loadProfile = useCallback(async () => {
    const version = ++requestVersion.current
    setLoading(true)
    setError(null)
    setEditing(false)

    try {
      const [nextProfile, page] = await Promise.all([
        fetchProfile(handle),
        fetchProfilePosts(handle),
      ])
      if (version !== requestVersion.current) return

      setProfile(nextProfile)
      setDisplayName(nextProfile.displayName)
      setBio(nextProfile.bio)
      setPosts(page.items)
      setNextCursor(page.nextCursor)
    } catch (error) {
      if (version === requestVersion.current) {
        setProfile(null)
        setPosts([])
        setNextCursor(null)
        setError(getApiErrorMessage(error, "Unable to load this profile."))
      }
    } finally {
      if (version === requestVersion.current) {
        setLoading(false)
      }
    }
  }, [handle])

  useEffect(() => {
    void loadProfile()
    return () => {
      requestVersion.current += 1
    }
  }, [loadProfile])

  async function loadMore() {
    if (nextCursor === null || loadingMore) return

    setLoadingMore(true)
    setError(null)
    try {
      const page = await fetchProfilePosts(handle, { before: nextCursor })
      setPosts((current) => {
        const knownIds = new Set(current.map((post) => post.id))
        return [...current, ...page.items.filter((post) => !knownIds.has(post.id))]
      })
      setNextCursor(page.nextCursor)
    } catch (error) {
      setError(getApiErrorMessage(error, "Unable to load more posts."))
    } finally {
      setLoadingMore(false)
    }
  }

  async function handleUpdate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const trimmedDisplayName = displayName.trim()
    const trimmedBio = bio.trim()

    if (!trimmedDisplayName || trimmedDisplayName.length > 50) {
      setError("Display name must be 1–50 characters.")
      return
    }
    if (trimmedBio.length > 160) {
      setError("Bio must be 160 characters or fewer.")
      return
    }

    setSaving(true)
    setError(null)
    try {
      const updated = await updateProfile({
        displayName: trimmedDisplayName,
        bio: trimmedBio,
      })
      setProfile(updated)
      setEditing(false)
      await onProfileUpdated(updated)

      try {
        const page = await fetchProfilePosts(handle)
        setPosts(page.items)
        setNextCursor(page.nextCursor)
      } catch (refreshError) {
        setError(
          getApiErrorMessage(
            refreshError,
            "Profile saved, but the post list could not be refreshed."
          )
        )
      }
    } catch (error) {
      setError(getApiErrorMessage(error, "Unable to update your profile."))
    } finally {
      setSaving(false)
    }
  }

  function handleReplyCreated(postId: number) {
    setPosts((current) =>
      current.map((post) =>
        post.id === postId
          ? { ...post, replyCount: post.replyCount + 1 }
          : post
      )
    )
  }

  if (loading) {
    return (
      <main className="mx-auto w-full max-w-4xl flex-1 px-4 py-8 sm:px-6">
        <p className="text-sm text-muted-foreground">Loading profile...</p>
      </main>
    )
  }

  if (profile === null) {
    return (
      <main className="mx-auto w-full max-w-4xl flex-1 px-4 py-8 sm:px-6">
        <Button type="button" variant="outline" size="sm" onClick={onBack}>
          <ArrowLeft className="size-4" />
          Back to timeline
        </Button>
        <p role="alert" className="mt-6 text-sm text-destructive">
          {error ?? "Profile not found."}
        </p>
      </main>
    )
  }

  const isOwner = sessionAccount?.handle === profile.handle

  return (
    <main className="mx-auto w-full max-w-4xl flex-1 px-4 py-6 sm:px-6">
      <Button type="button" variant="outline" size="sm" onClick={onBack}>
        <ArrowLeft className="size-4" />
        Back to timeline
      </Button>

      <section className="mt-4 rounded-2xl border border-border/70 bg-card p-6 shadow-sm">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <h1 className="text-2xl font-semibold tracking-tight">
              {profile.displayName}
            </h1>
            <p className="text-sm text-muted-foreground">@{profile.handle}</p>
          </div>
          {isOwner ? (
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => setEditing((current) => !current)}
            >
              <Pencil className="size-3.5" />
              {editing ? "Cancel" : "Edit profile"}
            </Button>
          ) : null}
        </div>

        {profile.bio ? (
          <p className="mt-4 whitespace-pre-wrap text-sm leading-relaxed">
            {profile.bio}
          </p>
        ) : (
          <p className="mt-4 text-sm text-muted-foreground">No bio yet.</p>
        )}
        <p className="mt-4 flex items-center gap-2 text-xs text-muted-foreground">
          <CalendarDays className="size-3.5" />
          Joined {formatJoined(profile.createdAt)}
        </p>

        {editing ? (
          <form
            className="mt-6 grid gap-4 border-t border-border/70 pt-5"
            onSubmit={handleUpdate}
          >
            <div className="space-y-2">
              <Label htmlFor="profile-display-name">Display name</Label>
              <Input
                id="profile-display-name"
                value={displayName}
                maxLength={50}
                onChange={(event) => setDisplayName(event.target.value)}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="profile-bio">Bio</Label>
              <Textarea
                id="profile-bio"
                value={bio}
                maxLength={160}
                rows={3}
                onChange={(event) => setBio(event.target.value)}
              />
              <p className="text-right text-xs text-muted-foreground">
                {bio.length}/160
              </p>
            </div>
            <Button type="submit" disabled={saving} className="justify-self-start">
              {saving ? "Saving..." : "Save profile"}
            </Button>
          </form>
        ) : null}

        {error ? (
          <p role="alert" className="mt-4 text-sm text-destructive">
            {error}
          </p>
        ) : null}
      </section>

      <section className="mt-6">
        <h2 className="mb-3 text-lg font-semibold">Posts</h2>
        {posts.length === 0 ? (
          <p className="text-sm text-muted-foreground">No posts yet.</p>
        ) : (
          <div className="space-y-4">
            {posts.map((post) => (
              <PostCard
                key={post.id}
                post={post}
                sessionAccount={sessionAccount}
                onAuthRequest={onAuthRequest}
                onNavigateProfile={onNavigateProfile}
                onReplyCreated={handleReplyCreated}
              />
            ))}
          </div>
        )}
        {nextCursor ? (
          <Button
            type="button"
            variant="outline"
            className="mt-4 w-full"
            disabled={loadingMore}
            onClick={() => void loadMore()}
          >
            {loadingMore ? "Loading..." : "Load more posts"}
          </Button>
        ) : null}
      </section>
    </main>
  )
}

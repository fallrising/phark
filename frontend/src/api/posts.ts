import type {
  Channel,
  CreatePostRequest,
  CreateReplyRequest,
  Post,
  PostPage,
  Reply,
  ReplyPage,
} from "@/types/post"

const PROBLEM_MEDIA_TYPE = "application/problem+json"
const SAFE_REQUEST_ID = /^[A-Za-z0-9._-]{1,64}$/

export type Violation = {
  readonly field: string
  readonly message: string
}

export type ApiProblem = {
  readonly type: string
  readonly title: string
  readonly status: number
  readonly detail: string
  readonly instance: string
  readonly code: string
  readonly requestId: string
  readonly violations?: readonly Violation[]
}

export class ApiError extends Error {
  readonly type: string
  readonly title: string
  readonly status: number
  readonly detail: string
  readonly instance: string
  readonly code: string
  readonly requestId: string
  readonly violations: readonly Violation[]

  constructor(problem: ApiProblem) {
    super(problem.detail)
    this.name = "ApiError"
    this.type = problem.type
    this.title = problem.title
    this.status = problem.status
    this.detail = problem.detail
    this.instance = problem.instance
    this.code = problem.code
    this.requestId = problem.requestId
    this.violations = problem.violations ?? []
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value)
}

function isNonEmptyString(value: unknown): value is string {
  return typeof value === "string" && value.trim() !== ""
}

function isViolationArray(value: unknown): value is readonly Violation[] {
  return (
    Array.isArray(value) &&
    value.every(
      (v): v is Violation =>
        isRecord(v) && isNonEmptyString(v.field) && isNonEmptyString(v.message),
    )
  )
}

function parseProblem(response: Response, body: unknown): ApiProblem | null {
  if (!isRecord(body)) return null
  if (!isNonEmptyString(body.type)) return null
  if (!isNonEmptyString(body.title)) return null
  if (typeof body.status !== "number" || !Number.isInteger(body.status)) return null
  if (!isNonEmptyString(body.detail)) return null
  if (!isNonEmptyString(body.instance)) return null
  if (!isNonEmptyString(body.code)) return null
  if (!isNonEmptyString(body.requestId) || !SAFE_REQUEST_ID.test(body.requestId)) return null
  if (body.status !== response.status) return null
  if (body.violations !== undefined && !isViolationArray(body.violations)) return null
  return {
    type: body.type,
    title: body.title,
    status: body.status,
    detail: body.detail,
    instance: body.instance,
    code: body.code,
    requestId: body.requestId,
    violations: body.violations,
  }
}

async function throwForNonOk(response: Response, fallback: string): Promise<never> {
  const contentType = response.headers
    .get("content-type")
    ?.split(";", 1)[0]
    .trim()
    .toLowerCase()
  if (contentType === PROBLEM_MEDIA_TYPE) {
    let body: unknown
    try {
      body = await response.json()
    } catch {
      throw new Error(fallback)
    }
    const problem = parseProblem(response, body)
    if (problem !== null) {
      throw new ApiError(problem)
    }
  }
  throw new Error(fallback)
}

export function getApiErrorMessage(error: unknown, fallback: string): string {
  if (error instanceof ApiError) {
    const detail = error.status >= 400 && error.status < 500 ? error.detail : fallback
    return `${detail} (Request ID: ${error.requestId})`
  }
  return fallback
}

export type FetchPostsOptions = {
  channel?: Channel
  before?: string
  limit?: number
}

export type FetchRepliesOptions = {
  limit?: number
  after?: string
}

export async function fetchReplies(
  postId: number,
  { after, limit = 20 }: FetchRepliesOptions = {}
): Promise<ReplyPage> {
  const params = new URLSearchParams({ limit: limit.toString() })
  if (after) params.set("after", after)

  const response = await fetch(`/api/posts/${postId}/replies?${params.toString()}`)
  if (!response.ok) {
    await throwForNonOk(response, "Failed to load replies")
  }
  return response.json() as Promise<ReplyPage>
}

export async function createReply(
  postId: number,
  request: CreateReplyRequest
): Promise<Reply> {
  const response = await fetch(`/api/posts/${postId}/replies`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(request),
  })

  if (!response.ok) {
    await throwForNonOk(response, "Failed to create reply")
  }

  return response.json() as Promise<Reply>
}

export async function fetchPosts({
  channel,
  before,
  limit = 20,
}: FetchPostsOptions = {}): Promise<PostPage> {
  const params = new URLSearchParams({ limit: limit.toString() })
  if (channel) params.set("channel", channel)
  if (before) params.set("before", before)

  const response = await fetch(`/api/posts?${params.toString()}`)
  if (!response.ok) {
    await throwForNonOk(response, "Failed to load posts")
  }
  return response.json() as Promise<PostPage>
}

export async function createPost(request: CreatePostRequest): Promise<Post> {
  const response = await fetch("/api/posts", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(request),
  })

  if (!response.ok) {
    await throwForNonOk(response, "Failed to create post")
  }

  return response.json() as Promise<Post>
}

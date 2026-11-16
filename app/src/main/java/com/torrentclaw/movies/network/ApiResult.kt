package com.torrentclaw.movies.network

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * A safe wrapper around every network call: callers never see raw exceptions.
 * [Error] carries a message that is already user-presentable plus a flag telling
 * the UI whether retrying can help (offline vs. rate limit vs. bad request).
 */
sealed interface ApiResult<out T> {

    data class Success<T>(val data: T) : ApiResult<T>

    data class Error(
        val type: ErrorType,
        val message: String,
        val httpCode: Int? = null,
        val retryable: Boolean = type.retryable,
        val cause: Throwable? = null,
    ) : ApiResult<Nothing>

    val isSuccess: Boolean
        get() = this is Success

    fun getOrNull(): T? = (this as? Success)?.data

    /** Transforms a success payload and passes errors through untouched. */
    fun <R> map(transform: (T) -> R): ApiResult<R> = when (this) {
        is Success -> Success(transform(data))
        is Error -> this
    }

    /** Runs [action] for a success payload; returns the receiver for chaining. */
    fun onSuccess(action: (T) -> Unit): ApiResult<T> {
        if (this is Success) action(data)
        return this
    }

    fun onError(action: (Error) -> Unit): ApiResult<T> {
        if (this is Error) action(this)
        return this
    }

    enum class ErrorType(val retryable: Boolean) {
        NETWORK(false),
        TIMEOUT(true),
        RATE_LIMITED(true),
        UNAUTHORIZED(false),
        NOT_FOUND(false),
        SERVER(true),
        PARSE(true),
        UNKNOWN(true),
    }

    companion object {

        /**
         * Runs [block] and converts any failure into an [Error]. Coroutine
         * cancellation is rethrown so structured concurrency keeps working.
         */
        fun <T> safeCall(block: () -> T): ApiResult<T> = try {
            Success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            e.toApiError()
        }
    }
}

/** Retrofit `Response`-returning call that also validates the HTTP status. */
suspend fun <T> safeApiCall(call: suspend () -> retrofit2.Response<T>): ApiResult<T> =
    try {
        val response = call()
        val body = response.body()
        when {
            response.isSuccessful() && body != null -> ApiResult.Success(body)
            response.isSuccessful() && body == null -> ApiResult.Error(
                type = ApiResult.ErrorType.PARSE,
                message = "The server returned an empty response.",
            )
            else -> ApiResult.Error(
                type = response.code().toErrorType(),
                message = response.code().toFriendlyMessage(),
                httpCode = response.code(),
            )
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        e.toApiError()
    }

fun Throwable.toApiError(): ApiResult.Error = when (this) {
    is UnknownHostException -> ApiResult.Error(
        type = ApiResult.ErrorType.NETWORK,
        message = "No internet connection. Check your network and try again.",
        cause = this,
    )
    is SocketTimeoutException -> ApiResult.Error(
        type = ApiResult.ErrorType.TIMEOUT,
        message = "TorrentClaw took too long to answer. Retrying usually helps.",
        cause = this,
    )
    is SSLException -> ApiResult.Error(
        type = ApiResult.ErrorType.NETWORK,
        message = "A secure connection couldn't be established.",
        cause = this,
    )
    is IOException -> ApiResult.Error(
        type = ApiResult.ErrorType.NETWORK,
        message = "Network error. Check your connection and try again.",
        cause = this,
    )
    is SerializationException -> ApiResult.Error(
        type = ApiResult.ErrorType.PARSE,
        message = "The server sent data this app didn't understand.",
        cause = this,
    )
    is HttpException -> ApiResult.Error(
        type = code().toErrorType(),
        message = code().toFriendlyMessage(),
        httpCode = code(),
        cause = this,
    )
    else -> ApiResult.Error(
        type = ApiResult.ErrorType.UNKNOWN,
        message = message ?: "Something went wrong. Please try again.",
        cause = this,
    )
}

private fun Int.toErrorType(): ApiResult.ErrorType = when (this) {
    401, 403 -> ApiResult.ErrorType.UNAUTHORIZED
    404 -> ApiResult.ErrorType.NOT_FOUND
    429 -> ApiResult.ErrorType.RATE_LIMITED
    in 500..599 -> ApiResult.ErrorType.SERVER
    else -> ApiResult.ErrorType.UNKNOWN
}

private fun Int.toFriendlyMessage(): String = when (this) {
    400 -> "That request wasn't valid. Try changing your filters."
    401 -> "This TorrentClaw API key was rejected. Check your configuration."
    403 -> "Your TorrentClaw tier doesn't allow that request."
    404 -> "We couldn't find that movie."
    429 -> "TorrentClaw is rate limiting us. Wait a few seconds and retry."
    in 500..599 -> "TorrentClaw is having trouble right now. Try again shortly."
    else -> "Unexpected server response ($this)."
}

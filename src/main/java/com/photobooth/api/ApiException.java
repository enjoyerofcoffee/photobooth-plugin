package com.photobooth.api;

/**
 * Thrown (inside a failed future) when the API is unreachable, misconfigured, rate-limiting
 * us, or returns something we can't use. A plain 404 is NOT an error: it is an empty Optional.
 */
public class ApiException extends Exception
{
	private final int httpStatus;

	public ApiException(String message, int httpStatus)
	{
		super(message);
		this.httpStatus = httpStatus;
	}

	public ApiException(String message, Throwable cause)
	{
		super(message, cause);
		this.httpStatus = -1;
	}

	/** HTTP status, or -1 for network/parse failures. */
	public int getHttpStatus()
	{
		return httpStatus;
	}
}

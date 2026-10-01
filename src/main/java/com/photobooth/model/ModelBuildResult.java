package com.photobooth.model;

/** Outcome of turning an Appearance into geometry. */
public final class ModelBuildResult
{
	private final MeshSnapshot mesh;
	private final String error;

	private ModelBuildResult(MeshSnapshot mesh, String error)
	{
		this.mesh = mesh;
		this.error = error;
	}

	public static ModelBuildResult success(MeshSnapshot mesh)
	{
		return new ModelBuildResult(mesh, null);
	}

	public static ModelBuildResult failure(String error)
	{
		return new ModelBuildResult(null, error);
	}

	public boolean isSuccess()
	{
		return mesh != null;
	}

	public MeshSnapshot getMesh()
	{
		return mesh;
	}

	/** User-facing reason, set when {@link #isSuccess()} is false. */
	public String getError()
	{
		return error;
	}
}

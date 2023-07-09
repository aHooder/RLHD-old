package rs117.hd.model;

public class ModelOffsets {
	public final int vertexOffset;
	public final int uvOffset;
	public final int totalFaceCount;
	public final int translucentFaceCount;

	public ModelOffsets(int vertexOffset, int uvOffset, int[] modelPusherResults) {
		this.vertexOffset = vertexOffset;
		this.uvOffset = modelPusherResults[1] == 0 ? -1 : uvOffset;
		totalFaceCount = modelPusherResults[0];
		translucentFaceCount = modelPusherResults[2];
	}
}

package route.circuit.resource;

public class SLLRouteNode {
	public final String direction;
	public final short xlow, xhigh;
	public final short ylow, yhigh;
	
	private RouteNode interposerNode;
	private RouteNode sourceReplaceNode;
	private RouteNode sinkReplaceNode;
	private boolean hasSourceNode = false;
	private boolean hasSinkNode = false;
	
	public SLLRouteNode(String direction, int xlow, int xhigh, int ylow, int yhigh) {
		this.direction = direction;
		this.xlow = (short) xlow;
		this.xhigh = (short) xhigh;
		this.ylow = (short) ylow;
		this.yhigh = (short) yhigh;
	}
	
	
	//At this point, the interposr will behave as the child node, hence sourceNode data is derived from the parent to child Map.
	public void setSourceReplaceNode(RouteNode sourceNode) {
		this.sourceReplaceNode = sourceNode;
		this.hasSourceNode = true;
	}
	
	public boolean hasSourceNode() {
		return this.hasSourceNode;
	}
	
	public RouteNode getSourceReplaceNode() {
		return this.sourceReplaceNode;
	}
	
	//At this point, the interposr will behave as the parent node, hence sinkNode data is derived by going over the children
	public void setSinkReplaceNode(RouteNode sinkNode) {
		this.sinkReplaceNode = sinkNode;
		this.hasSinkNode = true;
	}
	
	public boolean hasSinkNode() {
		return this.hasSinkNode;
	}
	
	public RouteNode getSinkReplaceNode() {
		return this.sinkReplaceNode;
	}
	
	public void setInterposerNode(RouteNode interposer) {
		this.interposerNode = interposer;
	}
	
	public RouteNode getInterposerNode() {
		return this.interposerNode;
	}
	
}

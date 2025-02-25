package route.circuit.resource;

public class Chany extends RouteNode {
	public Chany(int index, int xlow, int xhigh, int ylow, int yhigh, int n, float r, float c, IndexedData indexedData, String direction, int numChildren, int segID) {
		super(index, xlow, xhigh, ylow, yhigh, n, 1, RouteNodeType.CHANY, r, c, indexedData, direction , numChildren, segID);
	}
	public Chany(int index, int xlow, int xhigh, int ylow, int yhigh, int n, float r, float c, IndexedData indexedData, String direction) {
		super(index, xlow, xhigh, ylow, yhigh, n, 1, RouteNodeType.CHANY, r, c, indexedData, direction);
	}
}

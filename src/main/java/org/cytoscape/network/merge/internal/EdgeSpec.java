package org.cytoscape.network.merge.internal;

import org.cytoscape.model.CyEdge;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNode;
import org.cytoscape.network.merge.internal.task.NetworkMergeCommandTask;

public class EdgeSpec implements Comparable<EdgeSpec>
{
	CyNetwork net;
	CyEdge edge;
	
	public EdgeSpec(CyNetwork nt, CyEdge ed)
	{
		net = nt;
		edge = ed;
	}
	
	public CyNetwork getNet()	{ return net;	}
	public CyEdge getEdge()	{ return edge;	}
	public CyNode getSource()	{ return edge.getSource();	}
	public CyNode getTarget()	{ return edge.getTarget();	}
	public boolean isDirected()	{ return edge.isDirected();	}
	public String getInteraction() { return net.getRow(edge).get(CyEdge.INTERACTION, String.class); }
	
	public String toString()
	{
		return NetworkMergeCommandTask.getNetworkName(net) + ": " +  NetworkMergeCommandTask.edgeName(net, edge);
	}

	@Override
	public int compareTo(EdgeSpec other) {
		
		if (other.getNet() != getNet())
			return (getNet().getSUID() > other.getNet().getSUID()) ? 1 : -1;
		if (other.getEdge() != getEdge())
			return (getEdge().getSUID() > other.getEdge().getSUID()) ? 1 : -1;
		return 0;
	}
	
	
}

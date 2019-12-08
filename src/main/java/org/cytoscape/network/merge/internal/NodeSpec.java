package org.cytoscape.network.merge.internal;

import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNode;
import org.cytoscape.network.merge.internal.task.NetworkMergeCommandTask;

public class NodeSpec implements Comparable<NodeSpec>
{
	CyNetwork net;
	CyNode node;
	
	public NodeSpec(CyNetwork nt, CyNode no)
	{
		net = nt;
		node = no;
	}
	
	public CyNetwork getNet()	{ return net;	}
	public CyNode getNode()	{ return node;	}
	
	boolean equals(NodeSpec other)
	{
		return (other.net.getSUID() == net.getSUID()) && (other.node.getSUID() == node.getSUID());
	}
	
	public String toString()
	{
		return NetworkMergeCommandTask.getNetworkName(net) + ": " +  NetworkMergeCommandTask.getNodeName(net, node);
	}

	@Override
	public int compareTo(NodeSpec other) {
		
		if (other.getNet() != getNet())
			return (getNet().getSUID() > other.getNet().getSUID()) ? 1 : -1;
		if (other.getNode() != getNode())
			return (getNode().getSUID() > other.getNode().getSUID()) ? 1 : -1;
		return 0;
	}

	public void addToMatchList(CyNetwork net2, CyNode node2) {
		// TODO Auto-generated method stub
		
	}
}

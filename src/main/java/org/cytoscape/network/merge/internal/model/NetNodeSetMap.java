package org.cytoscape.network.merge.internal.model;

import java.util.HashMap;
import java.util.Set;

import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNode;

// a map to hold the nodes in a network
public class NetNodeSetMap 
{
	 HashMap<CyNetwork, Set<CyNode>> map = new HashMap<CyNetwork, Set<CyNode>>();
	
	 String dump(String s)
	{
		StringBuilder str = new StringBuilder(s);
		for (CyNetwork net : map.keySet())
			str.append("(" + net.getSUID() + " " + dumpNodeSet(map.get(net)) + ")");
		return str.toString();
	}

	public String toString()		{	return dump("");	}
	
	private String dumpNodeSet(Set<CyNode> set) {
		StringBuilder str = new StringBuilder("{");
		for (CyNode n : set)	str.append(n.getSUID() + ", ");
		str.append("}");
		return str.toString();
		
	}

	public Set<CyNetwork> keySet() 						{	return map.keySet();		}
	public int size() 									{	return map.size();		}
	public Set<CyNode> get(CyNetwork net) 				{	return map.get(net);	}
	public void put(CyNetwork net1, Set<CyNode> nodes) 	{ 	map.put(net1, nodes); }
	public boolean containsKey(CyNetwork net1) 			{	return map.containsKey(net1);		}
}

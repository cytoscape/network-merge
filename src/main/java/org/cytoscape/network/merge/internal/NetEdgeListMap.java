package org.cytoscape.network.merge.internal;

import java.util.HashMap;
import java.util.List;
import java.util.Set;

import org.cytoscape.model.CyEdge;
import org.cytoscape.model.CyNetwork;

public class NetEdgeListMap {
	 HashMap<CyNetwork, List<CyEdge>> map = new HashMap<CyNetwork, List<CyEdge>>();
		
	 String dump(String s)
	{
		StringBuilder str = new StringBuilder(s);
		for (CyNetwork net : map.keySet())
			str.append("(" + net.getSUID() + " " + dumpEdgeList(map.get(net)) + ")");
		return str.toString();
	}

	public String toString()		{	return dump("");	}
	
	private String dumpEdgeList(List<CyEdge> set) {
		StringBuilder str = new StringBuilder("{");
		for (CyEdge n : set)	str.append(n.getSUID() + ", ");
		str.append("}");
		return str.toString();
		
	}

	public Set<CyNetwork> keySet() 						{	return map.keySet();		}
	public int size() 									{	return map.size();		}
	public List<CyEdge> get(CyNetwork net) 				{	return map.get(net);	}
	public void put(CyNetwork net1, List<CyEdge> edges) 	{ 	map.put(net1, edges); }
	public boolean containsKey(CyNetwork net1) 			{	return map.containsKey(net1);		}

}

package org.cytoscape.network.merge.internal.model;

import java.util.ArrayList;
import java.util.List;

//----------------------------------------------------------------------------
public class NodeListList extends ArrayList<List<NodeSpec>>
{
	private static final long serialVersionUID = 1L;

	void dump()
	{
		for (List<NodeSpec> nodes :  this)
		{
			System.out.print("[");
			for (NodeSpec node :  nodes)
				System.out.print(node + ", ");
			System.out.println("]");
		}
	}
}
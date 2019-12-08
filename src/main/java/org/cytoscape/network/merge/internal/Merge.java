package org.cytoscape.network.merge.internal;

import java.util.ArrayList;

/*
 * #%L
 * Cytoscape Merge Impl (network-merge-impl)
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2006 - 2013 The Cytoscape Consortium
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as 
 * published by the Free Software Foundation, either version 2.1 of the 
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 * 
 * You should have received a copy of the GNU General Lesser Public 
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-2.1.html>.
 * #L%
 */

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.cytoscape.model.CyColumn;
import org.cytoscape.model.CyEdge;
import org.cytoscape.model.CyIdentifiable;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNode;
import org.cytoscape.model.CyRow;
import org.cytoscape.model.CyTable;
import org.cytoscape.network.merge.internal.NetworkMerge.Operation;
import org.cytoscape.network.merge.internal.model.AttributeMap;
import org.cytoscape.network.merge.internal.model.NetColumnMap;
import org.cytoscape.network.merge.internal.task.NetworkMergeCommandTask;
import org.cytoscape.network.merge.internal.util.AttributeValueMatcher;
import org.cytoscape.network.merge.internal.util.ColumnType;
import org.cytoscape.work.TaskMonitor;

/**
 * Column based network merge
 * 
 * 
 */
public class Merge  {

	private final NetColumnMap matchingAttribute;
	private final AttributeMap nodeAttributeMap;
	private final AttributeMap edgeAttributeMap;
	private final AttributeValueMatcher attributeValueMatcher;
	private final EdgeMerger edgeMerger;
	private final NodeMerger nodeMerger;
	private final boolean asCommand;
	public static boolean verbose = true;
	protected boolean withinNetworkMerge = false;
	protected final TaskMonitor taskMonitor;
	//Maps a node and its position in the match list
//	protected Map<CyNode,Integer> mapNodesIndex = new HashMap<CyNode,Integer>();
	
	//There are two different maps to differentiate directed and undirected edges
	//Each map does a first map based on type of interactions and then a second map that maps
	//a Long index made of combination of two integer indexes from the two nodes and the
	//index of that edge in the matched list
//	private long edgeID(int a, int b)	{		return ((long) a << 32) | b ;	}
	//There is also a second set of maps for the case when the edges do not have a value 
	//in the interaction column. This would be a special case that needs to be considered too
//	protected Map<String,Map<Long,Integer>> mapEdgeDirectedInteractions = new HashMap<String,Map<Long,Integer>>();
//	protected Map<String,Map<Long,Integer>> mapEdgeInteractions = new HashMap<String,Map<Long,Integer>>();
//	protected Map<Long,Integer> mapEdgeNoInteractions = new HashMap<Long,Integer>();
//	protected Map<Long,Integer> mapEdgeDirectedNoInteractions = new HashMap<Long,Integer>();
	
	// For canceling task
	private volatile boolean interrupted;

//	/**
//	 * 
//	 * @param matchingAttribute
//	 * @param nodeAttributeMapping
//	 * @param edgeAttributeMapping
//	 * @param attributeMerger
//	 */
//	public Merge(
//			final NetColumnMap matchingAttribute,
//			final AttributeMap nodeAttributeMapping, 
//			final AttributeMap edgeAttributeMapping,
//			final NodeMerger nodeMerger, 
//			final EdgeMerger edgeMerger, 
//			boolean asCommand,
//			final TaskMonitor taskMonitor) {
//		
//		this(matchingAttribute, nodeAttributeMapping, edgeAttributeMapping, nodeMerger, edgeMerger,
//				new DefaultAttributeValueMatcher(), asCommand, taskMonitor);
//	}

	/**
	 * 
	 * @param matchingAttribute  -- a map of network to column
	 * @param nodeAttributeMapping
	 * @param edgeAttributeMapping
	 * @param attributeMerger
	 * @param attributeValueMatcher
	 */
	public Merge(
			final NetColumnMap matchingAttribute,
			final AttributeMap nodeAttributeMapping, 
			final AttributeMap edgeAttributeMapping,
			final NodeMerger nodeMerger, 
			final EdgeMerger edgMerger, 
			AttributeValueMatcher attributeValueMatcher,
			boolean asCommand,
			final TaskMonitor taskMonitor) {

		if (matchingAttribute == null) 		throw new java.lang.NullPointerException("matchingAttribute");
		if (nodeAttributeMapping == null) 	throw new java.lang.NullPointerException("nodeAttributeMapping");
		if (edgeAttributeMapping == null) 	throw new java.lang.NullPointerException("edgeAttributeMapping");
		if (nodeMerger == null) 			throw new java.lang.NullPointerException("nodeMerger");
		if (edgMerger == null) 				throw new java.lang.NullPointerException("edgMerger");
		if (attributeValueMatcher == null) 	throw new java.lang.NullPointerException("attributeValueMatcher");
		
		this.matchingAttribute = matchingAttribute;
		this.nodeAttributeMap = nodeAttributeMapping;
		this.edgeAttributeMap = edgeAttributeMapping;
		this.nodeMerger = nodeMerger;
		this.edgeMerger = edgMerger;
		this.attributeValueMatcher = attributeValueMatcher;
		this.asCommand = asCommand;
		this.taskMonitor = taskMonitor;
		}

	
	/*
	 *  Logic
	 *  	A:	Create a list of networks
	 *  	B: Merge Nodes:  
	 *  		Build Match lists
	 *  		for each network
	 *  			check if each node matches something in the matched, then unmatched list
	 *  				if so add to list of matches, otherwise add it to unmatched
	 *  
	 *  	C: determine target node set based on union, intersection, difference
	 *  	D: build nodeNodeMap from each source to its equivalent in target
	 *  	for each network
	 *  		for each edge
	 *  			if both source and target have equivalents
	 *  				add edge to match list
	 *  
	 *  	E:	Merge Edges
	 *  		build list from each network,
	 *  			if edge source and target have equi
	 */
	//----------------------------------------------------------------
	static String MATCH = "Matching.Attribute";
	static String COUNT = "Matching.Count";
	CyColumn matchColumn = null;
	CyColumn countColumn = null;
	CyColumn edgeMatchColumn = null;
	CyColumn edgeCountColumn = null;
	
	//=================================================================================
		private List<CyNetwork> networks;
		private Map<CyNetwork, CyColumn> keyColumns = new HashMap<CyNetwork, CyColumn>();
		private CyNetwork targetNetwork;
		private Operation operation;
		private NodeListList matchList;
		private List<NodeSpec> unmatchedList;
		private final Map<CyNode, CyNode> nodeNodeMap = new HashMap<CyNode, CyNode>();

//		NetNodeSetMap differenceNodeList = null;
		
		public CyNetwork mergeNetwork(final CyNetwork mergedNetwork, final List<CyNetwork> fromNetworks, final Operation op, final boolean subtractOnlyUnconnectedNodes) {
			// Null checks for required fields...
			if (verbose) System.out.println("B: mergeNetwork");

			networks = fromNetworks;
			operation = op;
			matchList = new NodeListList();
			unmatchedList = new ArrayList<NodeSpec>();

			if (mergedNetwork == null) 			throw new NullPointerException("Merged networks wasn't created.");
			if (fromNetworks == null) 			throw new NullPointerException("No networks selected.");
			if (op == null) 					throw new NullPointerException("Operation parameter is missing.");
			if (fromNetworks.isEmpty()) 		throw new IllegalArgumentException("No source networks!");
			if (operation == Operation.DIFFERENCE && networks.size() != 2) 		throw new IllegalArgumentException("Difference only works with two networks");
			//long startTime = System.currentTimeMillis();
			preprocess(mergedNetwork);

			if (verbose) System.err.println("B:  perform the set operation on the node level " );	
			if (verbose) System.out.println("C:  build the list of node list that will merge------------" );
			
			if(interrupted) 			return null;		// Check cancel status
			mergeNodes();
			if(!interrupted) 			
				buildEdgeList();
		
			if (verbose) System.err.println("H return mergedNetwork ---------------------------------------" );
			return mergedNetwork;
		}
	
		//=================================================================================
	protected void preprocess(CyNetwork target) {
		
		targetNetwork = target;
//		matchingAttribute.addNetwork(target);
		setAttributeTypes(targetNetwork.getDefaultNodeTable(), nodeAttributeMap);
		setAttributeTypes(targetNetwork.getDefaultEdgeTable(), edgeAttributeMap);
		if (asCommand)
		{
			
			matchColumn = targetNetwork.getDefaultNodeTable().getColumn(MATCH);
			if (matchColumn == null)
			{
				targetNetwork.getDefaultNodeTable().createColumn(MATCH, String.class, true);
				matchColumn = targetNetwork.getDefaultNodeTable().getColumn(MATCH);
			}
//			countColumn = targetNetwork.getDefaultNodeTable().getColumn(COUNT);
//			if (countColumn == null)
//			{
//				targetNetwork.getDefaultNodeTable().createColumn(COUNT, Integer.class, true);
//				countColumn = targetNetwork.getDefaultNodeTable().getColumn(COUNT);
//			}
			edgeMatchColumn = targetNetwork.getDefaultEdgeTable().getColumn(MATCH);
			if (edgeMatchColumn == null)
			{
				targetNetwork.getDefaultEdgeTable().createColumn(MATCH, String.class, true);
				edgeMatchColumn = targetNetwork.getDefaultEdgeTable().getColumn(MATCH);
			}
//			edgeCountColumn = targetNetwork.getDefaultEdgeTable().getColumn(COUNT);
//			if (edgeCountColumn == null)
//			{
//				targetNetwork.getDefaultEdgeTable().createColumn(COUNT, Integer.class, true);
//				edgeCountColumn = targetNetwork.getDefaultEdgeTable().getColumn(COUNT);
//			}
		}
	}

	private void setAttributeTypes(final CyTable table, AttributeMap attributeMapping) 
	{
		int n = attributeMapping.getSizeMergedAttributes();
//		System.out.println("setAttributeTypes has size "  + n);
		for (int i = 0; i < n; i++) 
		{
			String attr = attributeMapping.getMergedAttribute(i);
			if (table.getColumn(attr) != null) 		continue; // TODO: check if the type is the same
				
			final ColumnType type = attributeMapping.getMergedAttributeType(i);
			final boolean isImmutable = attributeMapping.getMergedAttributeMutability(i);
			if (type.isList()) 	table.createListColumn(attr, type.getType(), isImmutable);
			else 				table.createColumn(attr, type.getType(), isImmutable);
			if (verbose) System.out.println("Creating column: " + attr);
			
		}
		if (verbose) System.out.println(table.getTitle() + " has size "  + n);
	}
//===============================================================================================


	private void mergeNodes() {
		if (verbose) System.out.println("D:  merge nodes in the matchedList ---------------------------" );

		for (CyNetwork net : networks)		// build matchedList and unmatchedList
			for (CyNode node : net.getNodeList())
				match(new NodeSpec(net, node));
//		if (verbose) matchList.dump();
//		if (verbose)
//		{
//			System.out.println("Unmatched:");
//			for (NodeSpec n : unmatchedList)
//				System.out.println(n);
//		}
////		if (verbose) unmatchedList.dump();
		
		// if not all nets have a node in the match, remove from intersection
		if (operation == Operation.INTERSECTION)
			for (int i = matchList.size()-1; i>=0; i--)
			{
				List<NodeSpec> nodes = matchList.get(i);
				 if (nodes.size() < networks.size())	
					matchList.remove(nodes);
			}
		
		if (operation != Operation.DIFFERENCE)		// for Union OR Intersection, add the matches to our target
		{
			for (List<NodeSpec> nodes : matchList)
				addNodeListToTarget(nodes);
		}
		

		if (operation == Operation.UNION)			//for Union, also add unmatched nodes to target network and nodeNodeMap
		{
			for (NodeSpec node : unmatchedList)
				addNodeToTarget(node);
		}
		
		if (operation == Operation.DIFFERENCE)			//for difference,  add ONLY nodes from first network that aren't matches
		{
			CyNetwork firstNet = networks.get(0);
			for (CyNode node : firstNet.getNodeList())
			{
				CyNode equiv = nodeNodeMap.get(node);
				boolean matched = equiv != null;
				if (!matched)
					addNodeToTarget(new NodeSpec(firstNet, node));
			}
		}	
		
		if (verbose) System.out.println("D:  NodeNodeMap has " + nodeNodeMap.size() + " has keys: " + nodeNodeMap.keySet());
		if (verbose) System.out.println("mergedNetwork.size = " + targetNetwork.getNodeCount());
			
	}
	//----------------------------------------------------------------
	public void addNodeToTarget(NodeSpec node)
	{
		List<NodeSpec> list = new ArrayList<NodeSpec>();
		list.add(node);
		addNodeListToTarget(list);
	}

	private void addNodeListToTarget(List<NodeSpec> nodes)
	{
		CyNode targetNode = targetNetwork.addNode();
		nodeAttributeMap.mergeNodes(nodes, targetNetwork, targetNode, nodeMerger );
		for (NodeSpec node : nodes)
			nodeNodeMap.put(node.node, targetNode);
	}
	
	
	private void match(final NodeSpec spec) {
		
		for (List<NodeSpec> matches : matchList) 
		{
			for (NodeSpec node : matches)
				if (matchNode(spec, node))
				{
					matches.add(spec);
					return;
				}
		}
		for (int i = unmatchedList.size()-1; i >= 0; i--)
		{
			NodeSpec unmatched = unmatchedList.get(i);  
			if (matchNode(spec, unmatched))
			{
				List<NodeSpec>  matches = new ArrayList<NodeSpec>();
				matches.add(unmatched);
				matches.add(spec);
				matchList.add(matches);
				unmatchedList.remove(i);
				return;
			}
		
		}
		unmatchedList.add(spec);
	}
	
	private boolean matchNode(NodeSpec a, NodeSpec b)
	{
		return matchNode(a.net, a.node, b.net, b.node);
	}

	protected boolean matchNode(final CyNetwork net1, final CyNode n1, final CyNetwork net2, final CyNode n2) {
		if (net1 == null || n1 == null || net2 == null || n2 == null)
			throw new NullPointerException();

		// it matches if the same node is sent twice, but they are nodes in different networks, so we shouldn't see this case
		if (n1 == n2)   return true;

		CyColumn col1 = matchingAttribute.get(net1);
		CyColumn col2 = matchingAttribute.get(net2);
		
		if (col1 == null || col2 == null)
			throw new IllegalArgumentException("Please specify the matching table column first");
		
		boolean result = attributeValueMatcher.matched(n1, col1, n2, col2);
		if (result)
			System.out.println((result? "MATCH " : "NOMATCH   ") + nodeName(net1, n1) + " " + net1.getSUID() + ": " + col1.getName() 
				+ ", "  + nodeName(net2, n2) + " " + net2.getSUID() + ": " + col2.getName());
		return result; 
	}
	
	//===============================================================================================
	//===============================================================================================
	public void setWithinNetworkMerge(boolean within) {		withinNetworkMerge = within;	}
	
	public void interrupt() {		interrupted = true;	}
//	CyEdge findEdgeInNetwork(CyNetwork net, CyNode source, CyNode target, boolean directed)
//	{
//		List<CyEdge> existingEdges = net.getEdgeList();
//		for (CyEdge e : existingEdges)
//			if (edgeMatch(e, net, source, target, directed))
//				return e;
//		return null;
//	}
	
//	private boolean edgeMatch(CyEdge edge, CyNetwork net, CyNode source, CyNode target, boolean directed) {
//		boolean sourceMatches = matchNode(net, edge.getSource(), net, source);
//		boolean targetMatches = matchNode(net, edge.getTarget(), net, target);
//		if (!directed)
//		{
//			sourceMatches |= matchNode(net, edge.getSource(), net, target);
//			targetMatches |= matchNode(net, edge.getTarget(), net, source);
//		}
//		return sourceMatches && targetMatches;
//		
//	}
	
	//===============================================================================================
	private boolean edgeMatch(EdgeSpec test, EdgeSpec e) {
		
		boolean sourceMatches = matchNode(test.net, test.getSource(),e.net, e.getSource());
		boolean targetMatches = matchNode(test.net, test.getTarget(), e.net, e.getTarget());
		boolean fullMatch =  sourceMatches && targetMatches;
		if (fullMatch || test.isDirected())  return fullMatch;
		
		// undirected graphs can also match in opposite order
		sourceMatches = matchNode(test.net, test.getSource(), e.net, e.getTarget());
		targetMatches = matchNode(test.net, test.getTarget(), e.net, e.getSource());
		return sourceMatches && targetMatches;
	}
//
	//----------------------------------------------------------------------------
	//----------------------------------------------------------------------------
	
	
	//----------------------------------------------------------------------------
	class NodeListList extends ArrayList<List<NodeSpec>>
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
//	private void dumpMatchedNodeList(NetNodeSetMap map) {
//		if (!Merge.verbose) return;
//		System.out.println("dumpMatchedNodeList size: " + map.size());
//		for (CyNetwork net : map.keySet())
//		{
//			System.out.print(net.getSUID() + ": ");
//			for (CyNode node : map.get(net))
//				System.out.print(node.getSUID() + ", ");
//		}
//		System.out.println();
//		
//	}
//
	// see if a node matches any seen before



//	NetEdgeListMap allEdges;
	List<EdgeSpec> unmatchedEdges = new ArrayList<EdgeSpec>();
	List<List<EdgeSpec>> matchedEdges = new ArrayList<List<EdgeSpec>>();
	/**
	*
	*	buildEdgeList starts with all edges from source networks
	*	It makes EdgeSpecs and edgeMatch searches existing specs while tracking matched vs. unmatched
	*	Then, process the lists of matched and unmatched edges into the target network
 	 */
	private void buildEdgeList(	) {

		// match edges
		if (verbose) System.err.println(" E  ----------EDGE MERGE-----------------");
		taskMonitor.setStatusMessage("Merging edges...");
//		System.err.print("matchedEdgeList: " );

		// create matchedEdges and unmatchedEdges lists
		for (CyNetwork net : networks)
		{
			System.out.println("adding edges from " + NetworkMergeCommandTask.getNetworkName(net));
			for (CyEdge edge : net.getEdgeList())
				processEdge(new EdgeSpec(net,edge));
			System.out.println(matchedEdges.size() + " / " + unmatchedEdges.size());
		}
		
		// dump
		if (verbose) 
		{
			for (List<EdgeSpec> match : matchedEdges)
			{
				for (EdgeSpec e : match)
					System.out.print(e.toString() + ", ");
				System.out.println();
			}
			for (EdgeSpec e : unmatchedEdges)
			{
				System.out.println(e.toString());
			}
		}
		
		
		// if its the intersection, there must be an edge for every network
		if (operation == Operation.INTERSECTION) 
		{
			for (int i = matchedEdges.size()-1; i >= 0; i--)
				if (matchedEdges.get(i).size() < networks.size())
					matchedEdges.remove(i);
		} 
 
		
		// if it's the difference, add edges for each unmatched edge from the first network
		if (operation == Operation.DIFFERENCE) 
		{
			final CyNetwork net1 = networks.get(0);
			for (CyEdge edge : net1.getEdgeList())
			{
				EdgeSpec e = new EdgeSpec(net1, edge);
				for (List<EdgeSpec> specs : matchedEdges)
					if (!edgeMatch(specs.get(0), e ))	
						addEdgeToTarget(new EdgeSpec(net1, edge));
			}
		}
		else		// union and intersection both include matchedEdges
			for (List<EdgeSpec> edges: matchedEdges)
				addEdgesToTarget(edges);
		
		// for union operation, we want unmatched edges too
		if (operation == Operation.UNION) 
		{
			for (EdgeSpec unmatched: unmatchedEdges)
				addEdgeToTarget(unmatched);
		}		
	}
	private void addEdgesToTarget(List<EdgeSpec> edges) {
//		for (EdgeSpec edge : edges)
//			addEdgeToTarget(edge);
		addEdgeToTarget(edges.get(0));

	}

	private void addEdgeToTarget(EdgeSpec edge) {
		
		CyNode  targSrc = nodeNodeMap.get(edge.getSource());
		CyNode targTarg = nodeNodeMap.get(edge.getTarget());
		if (targSrc == null)  {  System.err.println("targSrc == null");    return; 	};
		if (targTarg == null)  {  System.err.println("targTarg == null");   return; 	};
		
		CyEdge newEdge = targetNetwork.addEdge(targSrc, targTarg, true);			// edge.isDirected()
		List<EdgeSpec> list = new ArrayList<EdgeSpec>();
		list.add(edge);
		edgeAttributeMap.mergeEdge(list, newEdge, targetNetwork, edgeMerger);
		System.out.println(edgeName(edge));
	}



	private String nodeName(CyNetwork net, CyNode target) {
		return NetworkMergeCommandTask.getNodeName(net, target);
	}

	private String edgeName(EdgeSpec target) {
		return nodeName(target.net, target.getSource()) + " -> "+ nodeName(target.net, target.getTarget());
	}

	private void processEdge(EdgeSpec edge) {

		// first, inspect all edges in all known matches, add to match list if found
		for (List<EdgeSpec> matches : matchedEdges)
		{
			for (EdgeSpec spec : matches)
				if (edgeMatch(spec, edge))
				{
					matches.add(edge);
					return;
				}
		}
		// next, iterate backwards thru unmatchedEdges, so we can delete entries as they match
		for (int i = unmatchedEdges.size()-1; i >= 0; i--)
		{
			EdgeSpec unmatched = unmatchedEdges.get(i);
			if (edgeMatch(unmatched, edge))
			{
				List<EdgeSpec> matches = new ArrayList<EdgeSpec>();
				matches.add(edge);
				matches.add(unmatched);
				matchedEdges.add(matches);
				unmatchedEdges.remove(i);
				System.out.println("adding edge match");
				return;
			}
		}
		unmatchedEdges.add(edge);
	}



	//----------------------------------------------------------------
	/**
	 * Check whether an edge match the other edges already considered, if so it will 
	 * return the position in the match list
	 * 
	 * @param network1 The source network of the edge to evaluate
	 * @param e1 The edge to check if it has a match
	 * @param position The position in the match list that the new edge belong if no match is found
	 * 
	 * @return the index in the match list where this edge has found a match or -1 if no match found
	 */
//	protected List<EdgeSpec> matchEdge( CyNetwork network1, CyEdge e1, int position) {
//		
//		int index = -1;
//		long id1, id2 = 0;
//		Map<Long,Integer> mapNodesEdges = null;
//		Map<Long,Integer> mapNodesDirectedEdges = null;
//		if (e1 == null ) 			throw new NullPointerException("e1 == null");
//		
//		String i1 = network1.getRow(e1).get(CyEdge.INTERACTION, String.class);
//		
//		
//		CyNode source = e1.getSource();
//		CyNode target = e1.getTarget();
//		
//		if (source == null) 	throw new NullPointerException("source == null");
//		if (target == null )	throw new NullPointerException("target == null ");
//		CyNode iSource = nodeNodeMap.get(source);
//		CyNode iTarget = nodeNodeMap.get(target);
//		
//		if (e1.isDirected())
//		{
//			mapNodesDirectedEdges = (i1 == null) ? mapEdgeDirectedNoInteractions : mapEdgeDirectedInteractions.get(i1);
//			id1 = edgeID(iSource, iTarget);
//			if( mapNodesDirectedEdges != null)
//				if(mapNodesDirectedEdges.get(id1) != null)
//					index = mapNodesDirectedEdges.get(id1);
//		}
//		else
//		{
//			mapNodesEdges = (i1 == null) ? mapEdgeNoInteractions : mapEdgeInteractions.get(i1);
//			id1 = edgeID(iSource, iTarget);
//			id2 = edgeID(iTarget,iSource);
//			if(mapNodesEdges != null)
//				if(mapNodesEdges.get(id1) != null && mapNodesEdges.get(id2) != null && mapNodesEdges.get(id1).equals(mapNodesEdges.get(id2)))
//					index = mapNodesEdges.get(id1);
//		}
//		
//		
//		if(index == -1)
//		{			
//			if (e1.isDirected())
//			{
//				if( mapNodesDirectedEdges != null)
//					mapNodesDirectedEdges.put(id1, position);
//				else
//				{
//					mapNodesDirectedEdges = new HashMap<Long,Integer>();
//					mapNodesDirectedEdges.put(id1, position);
//					mapEdgeDirectedInteractions.put(i1, mapNodesDirectedEdges);
//				}
//			}
//			else
//			{
//				if( mapNodesEdges != null)
//				{
//					mapNodesEdges.put(id1, position);
//					mapNodesEdges.put(id2, position);
//				}
//				else
//				{
//					mapNodesEdges = new HashMap<Long,Integer>();
//					mapNodesEdges.put(id1, position);
//					mapNodesEdges.put(id2, position);
//					mapEdgeInteractions.put(i1, mapNodesEdges);
//				}
//			}
//		}
//		
//		return null;
//	}
	

	//----------------------------------------------------------------
	static public void dumpRow(CyNode node)
	{
		if (!Merge.verbose) return;
		CyNetwork net = node.getNetworkPointer();
		if (net == null) { System.out.println("NULL net");   return; 	} 
		CyRow row = net.getRow(node);
		if (row == null) { System.out.println("NULL row");   return; 	} 
		Map<String, Object> vals = row.getAllValues();
		System.out.print("{");
		for (String s : vals.keySet())
			System.out.print(s + ":" + vals.get(s) + ", ") ;
		System.out.println("}");
	}
	
	private void dumpNodeColumnMap(Map<CyNode, CyColumn> map)
	{
		for (CyNode id : map.keySet())
			System.out.print(id.getSUID() + ">" + map.get(id).getName() + ". ");
		System.out.println();
	}
	
//	private void dumpEdgeMap(Map<CyEdge, CyColumn> map)
//	{
//		if (!Merge.verbose) return;
//		for (CyIdentifiable id : map.keySet())
//			System.out.print(id.getSUID() + ">" + map.get(id).getName() + ". ");
//		System.out.println();
//	}
}

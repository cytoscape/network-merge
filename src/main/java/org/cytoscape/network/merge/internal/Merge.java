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
import java.util.Iterator;
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
import org.cytoscape.network.merge.internal.util.DefaultAttributeValueMatcher;
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
	public static boolean verbose = false;
	protected boolean withinNetworkMerge = false;
	protected final TaskMonitor taskMonitor;
	//Maps a node and its position in the match list
	protected Map<CyNode,Integer> mapNodesIndex = new HashMap<CyNode,Integer>();
	
	//There are two different maps to differentiate directed and undirected edges
	//Each map does a first map based on type of interactions and then a second map that maps
	//a Long index made of combination of two integer indexes from the two nodes and the
	//index of that edge in the matched list
	private long edgeID(int a, int b)	{		return ((long) a << 32) | b ;	}
	//There is also a second set of maps for the case when the edges do not have a value 
	//in the interaction column. This would be a special case that needs to be considered too
	protected Map<String,Map<Long,Integer>> mapEdgeDirectedInteractions = new HashMap<String,Map<Long,Integer>>();
	protected Map<String,Map<Long,Integer>> mapEdgeInteractions = new HashMap<String,Map<Long,Integer>>();
	protected Map<Long,Integer> mapEdgeNoInteractions = new HashMap<Long,Integer>();
	protected Map<Long,Integer> mapEdgeDirectedNoInteractions = new HashMap<Long,Integer>();
	
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

	//----------------------------------------------------------------
	static String MATCH = "Matching.Attribute";
	static String COUNT = "Matching.Count";
	CyColumn matchColumn = null;
	CyColumn countColumn = null;
	CyColumn edgeMatchColumn = null;
	CyColumn edgeCountColumn = null;
	
	protected void preprocess(CyNetwork targetNetwork) {
		
		mapNodesIndex.clear();
		mapEdgeDirectedInteractions.clear();
		mapEdgeInteractions.clear();
		mapEdgeDirectedNoInteractions.clear();
		mapEdgeNoInteractions.clear();

		setAttributeTypes(targetNetwork.getDefaultNodeTable(), nodeAttributeMap);
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
		}
		setAttributeTypes(targetNetwork.getDefaultEdgeTable(), edgeAttributeMap);
		{
			
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

	
	public CyNetwork mergeNetwork(final CyNetwork mergedNetwork, final List<CyNetwork> fromNetworks, final Operation op, final boolean subtractOnlyUnconnectedNodes) {
		// Null checks for required fields...
		if (mergedNetwork == null) { 
			throw new NullPointerException("Merged networks wasn't created.");
		}
		if (verbose) System.out.println("mergeNetwork");
		if (fromNetworks == null) 			throw new NullPointerException("No networks selected.");
		if (op == null) 					throw new NullPointerException("Operation parameter is missing.");
		if (fromNetworks.isEmpty()) 		throw new IllegalArgumentException("No source networks!");

		//long startTime = System.currentTimeMillis();
		preprocess(mergedNetwork);

		if (verbose) System.err.println("B:  perform the set operation on the node level " );
		// get node matching list
		List<NetNodeSetMap> matchedNodeList = getMatchedNodeList(fromNetworks);
		List<NetNodeSetMap> differenceNodeList = null;
		if(op == Operation.DIFFERENCE && subtractOnlyUnconnectedNodes) 
			differenceNodeList = matchedNodeList;
	
		if(interrupted) 			return null;		// Check cancel status
		
		if (verbose) System.out.println("B1:  differenceNodeList------------" );
		if (verbose) dumpMatchedNodeList(differenceNodeList);
		
		
		if (verbose) System.out.println("C:  build the list of node list that will merge------------" );
		matchedNodeList = getMatchedNodeList(matchedNodeList, op, fromNetworks);
		if (verbose) dumpMatchedNodeList(matchedNodeList);
		
		
		
		Map<CyNode, NetNodeSetMap> differenceNodeMap = null;
		if(differenceNodeList != null) {
			if (verbose) System.out.println("D1:  difference list --------------" );
			differenceNodeList.removeAll(matchedNodeList);
			differenceNodeMap = new HashMap<CyNode, NetNodeSetMap>();
			for(NetNodeSetMap mapNetNode: differenceNodeList) {
				Set<CyNode> nodes = mapNetNode.get(fromNetworks.get(0));
				if(nodes != null) {
					//remove networks besides the first
					for(int i=1; i<fromNetworks.size(); i++)
						mapNetNode.map.remove(fromNetworks.get(i));
					for(CyNode node: nodes)
						differenceNodeMap.put(node, mapNetNode);
				}
			}
		}
		
		// merge nodes in the list
		if (verbose) System.out.println("D:  merge nodes in the matchedNodeList ---------------------------" );
		final Map<CyNode, CyNode> nodeNodeMap = new HashMap<CyNode, CyNode>();
		taskMonitor.setStatusMessage("Merging nodes...");
		int i=0;
		final long nNode = matchedNodeList.size();
		for (NetNodeSetMap netNodeMap : matchedNodeList) {
			if (interrupted)				return null;
			taskMonitor.setProgress(((double)(i++)/ nNode)*0.5d);

//			final NetNodeSetMap netNodeMap = matchedNodeList.get(i);
			if (verbose) System.out.println("netNodeMap.size - " + netNodeMap.map.size() + " " + netNodeMap.toString());
			if (netNodeMap == null || netNodeMap.map.isEmpty())			continue;

			CyNode targetNode = mergedNetwork.addNode();
			if (verbose) System.out.println("targetNode: " + targetNode);
			mergeNode(netNodeMap, targetNode, mergedNetwork);

			for (Set<CyNode> nodeSet : netNodeMap.map.values())
				for (CyNode n : nodeSet) 
					nodeNodeMap.put(n, targetNode);
		}
		if (verbose) System.out.println("D:  NodeNodeMap has " + nodeNodeMap.size() + " has keys: " + nodeNodeMap.keySet());
		
		// match edges
		taskMonitor.setStatusMessage("Merging edges...");
		List<Map<CyNetwork, Set<CyEdge>>> matchedEdgeList = getMatchedEdgeList(fromNetworks);
//		System.err.print("matchedEdgeList: " );

		// Check cancel status
		if(interrupted)  return null;
		
		matchedEdgeList = selectMatchedEdgeList(matchedEdgeList, op, fromNetworks);
		if (verbose) System.err.println(" G  ----------EDGE MERGE-----------------\nsize = " +  matchedEdgeList.size());

		// merge edges
		final double nEdge = matchedEdgeList.size();
		i = 0;
		for (Map<CyNetwork, Set<CyEdge>> netEdgeSetMap : matchedEdgeList) {
			if (interrupted) return null;
			taskMonitor.setProgress(((double)(i + 1) / nEdge)*0.5d + 0.5d);

			if (netEdgeSetMap == null || netEdgeSetMap.isEmpty())		continue;

			// get the source and target nodes in merged network
			final Iterator<Set<CyEdge>> itEdges = netEdgeSetMap.values().iterator();

			final Set<CyEdge> edgeSet = itEdges.next();
			if (edgeSet == null || edgeSet.isEmpty()) {
				throw new IllegalStateException("Null or empty edge set");
			}

			final CyEdge originalEdge = edgeSet.iterator().next();
			CyNode source = nodeNodeMap.get(originalEdge.getSource());
			CyNode target = nodeNodeMap.get(originalEdge.getTarget());
			
			if(differenceNodeMap != null) {
				// For difference, need to create nodes if necessary.
				
				if(source == null) {
					CyNode originalSource = originalEdge.getSource();
					source = mergedNetwork.addNode();
					NetNodeSetMap mapNetNode = differenceNodeMap.get(originalSource);
					mergeNode(mapNetNode, source, mergedNetwork);
					for(Set<CyNode> nodes: mapNetNode.map.values())
						for(CyNode node: nodes)
							nodeNodeMap.put(node, source);
				}
				if(target == null) {
					CyNode originalTarget = originalEdge.getTarget();
					target = mergedNetwork.addNode();
					NetNodeSetMap mapNetNode = differenceNodeMap.get(originalTarget);
					mergeNode(mapNetNode, target, mergedNetwork);
					for(Set<CyNode> nodes: mapNetNode.map.values())
						for(CyNode node: nodes)
							nodeNodeMap.put(node, target);
				}
			} 
			// some of the nodes may be excluded when intersection or difference
			else if (source == null || target == null) 		continue;
			

			final boolean directed = originalEdge.isDirected();
			CyEdge edge = mergedNetwork.addEdge(source, target, directed);
			mergeEdge(netEdgeSetMap, edge, mergedNetwork);
		}
		//System.out.println("Run time: " + (System.currentTimeMillis() - startTime));

		if (verbose) System.err.println("H return mergedNetwork ---------------------------------------" );
		return mergedNetwork;
	}

	public void setWithinNetworkMerge(boolean within) {		withinNetworkMerge = within;	}

	public void interrupt() {		interrupted = true;	}
	
	//----------------------------------------------------------------------------
	class NetNodeSetMap 
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
		public Set<CyNode> get(CyNetwork net) 				{	return map.get(net);	}
		public void put(CyNetwork net1, Set<CyNode> nodes) 	{ 	map.put(net1, nodes); }
		public boolean containsKey(CyNetwork net1) 			{	return map.containsKey(net1);		}
	}
	//----------------------------------------------------------------------------

	private void dumpMatchedNodeList(List<NetNodeSetMap> matchedNodeList) {
		if (!Merge.verbose) return;
		if (matchedNodeList == null) { System.out.println("NULL " );  return;  }
		System.out.println("dumpMatchedNodeList size: " + matchedNodeList.size());
		for (NetNodeSetMap map : matchedNodeList)
		{
			for (CyNetwork net : map.keySet())
			{
				System.out.print(net.getSUID() + ": ");
				for (CyNode node : map.get(net))
					System.out.print(node.getSUID() + ", ");
			}
			System.out.println();
		}
		
	}

	/**
	 * Get a list of matched nodes/edges
	 * 
	 * @param networks
	 *            Networks to be merged
	 * @param isNode
	 *            true if for node
	 * 
	 * 
	 * @return list of map from network to node/edge
	 */
	private List<Map<CyNetwork, Set<CyEdge>>> getMatchedEdgeList(final List<CyNetwork> networks) {
		int index = 0;
		if (networks == null)   throw new NullPointerException("networks == null");
		if (networks.isEmpty())	throw new IllegalArgumentException("No merging network");
		
		if (verbose) System.out.println("EdgeMatch: ");

		final List<Map<CyNetwork, Set<CyEdge>>> matchedList = new ArrayList<Map<CyNetwork, Set<CyEdge>>>();
		final int nNet = networks.size();

		if (verbose) 
			for (int i = 0; i < nNet; i++) 
				System.out.println(networks.get(i).getEdgeCount() );
		
		
		for (int i = 0; i < nNet; i++) {
			final CyNetwork net1 = networks.get(i);
			final List<CyEdge> graphObjectList = net1.getEdgeList();

			for(CyEdge go1: graphObjectList) {
				if (interrupted)
					return null;
				
				// check whether any nodes in the matchedNodeList match with
				// this node if yes, add to the list, else add a new map to the list
				boolean matched = false;
				final int n = matchedList.size();
				//The search for a match has been split for nodes and edges. Edges don't need to go through the loop
				//since they can take advantage of node's information in the previous found node match list
				{
					index = matchEdge(net1,(CyEdge) go1,n);
					if(index >= 0)
					{
						//check if the edge belongs to the same network.  if so, the match is not valid
						if(matchedList.get(index).containsKey(net1) && matchedList.get(index).keySet().size() == 1 && !withinNetworkMerge)
							matched = false;
						else
							matched = true;
					}
					else matched = false;
				}
				if (!matched) {
					// no matched node/edge found, add new map to the list
					final Map<CyNetwork, Set<CyEdge>> matchedGO = new HashMap<CyNetwork, Set<CyEdge>>();
					Set<CyEdge> gos1 = new HashSet<CyEdge>();
					gos1.add(go1);
					matchedGO.put(net1, gos1);
					matchedList.add(matchedGO);
				}
				else
				{
					Set<CyEdge> gos1 = matchedList.get(index).get(net1);
					if (gos1 == null) {
						gos1 = new HashSet<CyEdge>();
						matchedList.get(index).put(net1, gos1);
					}
					gos1.add(go1);
				}
			}
		}
		return matchedList;
	}


	////
	private List<NetNodeSetMap> getMatchedNodeList(final List<CyNetwork> networks) {
		int index = 0;
		if (networks == null)   throw new NullPointerException("networks == null");
		if (networks.isEmpty())	throw new IllegalArgumentException("No merging network");
		
		boolean saveVerbose = verbose;
		verbose = false;
		if (verbose) System.out.println("++++++ NodeMatch: ");

		final List<NetNodeSetMap> matchedList = new ArrayList<NetNodeSetMap>();
		final int nNet = networks.size();

		if (verbose) 
			for (int i = 0; i < nNet; i++) {
			final CyNetwork net1 = networks.get(i);
				System.out.println(NetworkMergeCommandTask.getNetworkName(net1) + ": " + net1.getNodeCount());
		}
		if (verbose) System.out.println();
		
		for (int i = 0; i < nNet; i++) {
			final CyNetwork net1 = networks.get(i);
			final List<CyNode> nodeList = (List<CyNode>) net1.getNodeList();
			if (verbose) System.out.println(NetworkMergeCommandTask.getNetworkName(net1) + " " + net1.getSUID() + ": " + nodeList.size());

			for(CyNode node1: nodeList) 
			{
				if (interrupted)	return null;
				
			// check whether any nodes in the matchedNodeList match with this node
			// if yes, add to the list, else add a new map to the list
				boolean matched = false;
				final int n = matchedList.size();
					//The search for a match has been split for nodes 
			
				for (int j = 0; j < n; j++) {
					final NetNodeSetMap matchedNodes = matchedList.get(j);
					for (CyNetwork net2 : matchedNodes.keySet())
					{
						if (!withinNetworkMerge && net1 == net2)	continue;

						final Set<CyNode> nodelist2 = matchedNodes.get(net2);
						for (CyNode node2 : nodelist2) 
						{
							matched = matchNode(net1, node1, net2, node2);
							if (matched) 
							{
								index = j;
								mapNodesIndex.put(node1, index);
								if (verbose) System.out.println(node1.getSUID() + " - " + index);
								break;
							}
						}
					}
					
					if (matched) 	break;
				}
					
				if (!matched) {
					// no matched node found, add new map to the list
					final NetNodeSetMap matchedNodes = new NetNodeSetMap();
					Set<CyNode> nodes = new HashSet<CyNode>();
					nodes.add(node1);
					matchedNodes.put(net1, nodes);
					mapNodesIndex.put((CyNode) node1, n);
					matchedList.add(matchedNodes);
					
					if (verbose) System.out.println("putting " + node1.getSUID());
				else
				{
					Set<CyNode> gos1 = matchedList.get(index).get(net1);
					if (gos1 == null) {
						gos1 = new HashSet<CyNode>();
						matchedList.get(index).put(net1, gos1);
					}
					gos1.add(node1);
				}
			}
		}
		if (verbose) System.out.println("eo " + NetworkMergeCommandTask.getNetworkName(net1));
	}
	if (verbose) System.out.println("eo " + nNet + " networks, matchedList size = " + matchedList.size() + ".  " + matchedList);
	verbose = saveVerbose;
	return matchedList;
	}
////
	/**
	 * Select nodes for merge according to different operations
	 * 
	 * @param networks
	 *            Networks to be merged
	 * @param op
	 *            Operation - union, intersection, difference
	 * @param networks
	 *            List of networks to merge
	 * 
	 * @return list of matched nodes
	 */
	private List<NetNodeSetMap> getMatchedNodeList(
			final List<NetNodeSetMap> matchedNodeList,  final Operation op, final List<CyNetwork> networks) 
	{
		if (matchedNodeList == null)	throw new NullPointerException("matchedGraphObjectsList == null");
		if (op == null)					throw new NullPointerException("op == null");

		int nnet = networks.size();

		if (op == Operation.UNION) 		return matchedNodeList;
		
		if (op == Operation.INTERSECTION) {
			List<NetNodeSetMap> list = new ArrayList<NetNodeSetMap>();
			for (NetNodeSetMap map : matchedNodeList) 
				if (map.map.size() == nnet) // if contained in all the networks
					list.add(map);
			return list;
		} 
 
		// For Operation.DIFFERENCE
		if (verbose) System.out.print(" getMatchedNodeList Difference (");
		final List<NetNodeSetMap> list = new ArrayList<NetNodeSetMap>();
		if (nnet < 2)
			return list;

		final CyNetwork net1 = networks.get(0);
		final CyNetwork net2 = networks.get(1);
		if (verbose) System.out.print(net1.getSUID() + " - " + net2.getSUID() + ") z=" + matchedNodeList.size());
		for (NetNodeSetMap map : matchedNodeList) 
		{
			if (map.containsKey(net1))		// nodes in the first network
			{
				boolean found = false;
				for (int i=1; i < nnet; i++)
				{
					CyNetwork net = networks.get(i);
					if (map.containsKey(net))
						found = true;
				}
				if (!found) list.add(map);	// but not in any subsequent network
			}
		}
		return list;

	}
	/**
	 * Select edges for merge according to different operation
	 * 
	 * @param networks
	 *            Networks to be merged
	 * @param op
	 *            Operation
	 * @param size
	 *            Number of networks
	 * 
	 * @return list of matched edges
	 */
	private List<Map<CyNetwork, Set<CyEdge>>> selectMatchedEdgeList(
			final List<Map<CyNetwork, Set<CyEdge>>> matchedGraphObjectsList, 
			final Operation op, final List<CyNetwork> networks) {
		if (matchedGraphObjectsList == null)	throw new NullPointerException("matchedGraphObjectsList == null");
		if (op == null)							throw new NullPointerException("op == null");

		int nnet = networks.size();

		if (op == Operation.UNION) 		return matchedGraphObjectsList;
		
		if (op == Operation.INTERSECTION) {
			List<Map<CyNetwork, Set<CyEdge>>> list = new ArrayList<Map<CyNetwork, Set<CyEdge>>>();
			for (Map<CyNetwork, Set<CyEdge>> map : matchedGraphObjectsList) 
				if (map.size() == nnet) // if contained in all the networks
					list.add(map);
			return list;
		} 
 
		// For Operation.DIFFERENCE
		final List<Map<CyNetwork, Set<CyEdge>>> list = new ArrayList<Map<CyNetwork, Set<CyEdge>>>();
		if (nnet < 2)
			return list;

		final CyNetwork net1 = networks.get(0);
		for (Map<CyNetwork, Set<CyEdge>> map : matchedGraphObjectsList) 
		{
			if (map.containsKey(net1))
			{
				boolean found = false;
				for (int i=1; i < nnet; i++)
				{
					CyNetwork net = networks.get(i);
					if (map.containsKey(net))
						found = true;
				}
				if (!found) list.add(map);
			}
		}
		return list;
		
	}
	//----------------------------------------------------------------
	protected void mergeNode(final NetNodeSetMap mapNetToNodes, CyNode targetNode, CyNetwork targetNetwork) {

		if (mapNetToNodes == null || mapNetToNodes.map.isEmpty())
			return;
		if (verbose) ((AttributeMap) nodeAttributeMap).dump("E mergeNode --------------");
//		AttributeMappingImpl.dumpStrs(nodeAttributeMapping.getMergedAttributes());
		final int nattr = nodeAttributeMap.getSizeMergedAttributes();
		for (int i = 0; i < nattr; i++) 
		{
			String attribute = nodeAttributeMap.getMergedAttribute(i);
					
			CyRow row = targetNetwork.getRow(targetNode);
			CyTable t = row.getTable();
			CyColumn targetColumn = t.getColumn(attribute);
			CyColumn countColumn = t.getColumn(attribute);

			// merge
			Map<CyNode, CyColumn> nodeToColMap = new HashMap<CyNode, CyColumn>();
			
			for (CyNetwork net : mapNetToNodes.map.keySet())
			{
				Set<CyNode> nodeList = mapNetToNodes.get(net);
				final CyTable table = nodeAttributeMap.getCyTable(net);
				if (table == null) continue;

				final String attrName = nodeAttributeMap.getOriginalAttribute(net, i);
				if (attrName == null) continue;
				if (targetNode == null) { System.out.println("null target node");continue; }
				if (targetColumn == null) continue;
				if (attrName.equals(attribute))
					nodeMerger.mergeAttribute(nodeToColMap, targetNode, targetColumn, null, targetNetwork);
				
				final CyColumn colum = (table == null) ? null : table.getColumn(attrName);
				for (CyNode node : nodeList)
					nodeToColMap.put(node, colum);
			}
//			dumpNodeColumnMap(nodeToColMap);
			nodeMerger.mergeAttribute(nodeToColMap, targetNode, targetColumn, null, targetNetwork);
			for (CyNetwork net : mapNetToNodes.map.keySet())
			{
				boolean isJoinColumn = matchingAttribute.contains(net, attribute);
				if (isJoinColumn)
					nodeMerger.mergeAttribute(nodeToColMap, targetNode, matchColumn, countColumn, targetNetwork);
			}
		}
		if (Merge.verbose) System.out.println("Node Merged");
	}
	//----------------------------------------------------------------
	protected boolean matchNode(final CyNetwork net1, final CyNode n1, final CyNetwork net2, final CyNode n2) {
		if (net1 == null || n1 == null || net2 == null || n2 == null)
			throw new NullPointerException();

		// it matches if n1==n2, but they are nodes in different networks, so we shouldn't see this case
		if (n1 == n2)   return true;

		CyColumn col1 = matchingAttribute.getColumn(net1);
		CyColumn col2 = matchingAttribute.getColumn(net2);
		
		if (col1 == null || col2 == null)
			throw new IllegalArgumentException("Please specify the matching table column first");
		
		boolean result = attributeValueMatcher.matched(n1, col1, n2, col2);
//		System.out.println((result? "MATCH " : "NOMATCH    ") + net1.getSUID() + ": " + col1.getName() + ", " + net2.getSUID() + ": " + col2.getName());
		return result; 
	}


	//----------------------------------------------------------------
	public void mergeEdge(final Map<CyNetwork, Set<CyEdge>> mapNetEdge, CyEdge newEdge, CyNetwork newNetwork) 
	{
		if (mapNetEdge == null || mapNetEdge.isEmpty() || newEdge == null) 		throw new IllegalArgumentException();

		((AttributeMap) edgeAttributeMap).dump("setEdgeAttribute");
		final int nattr = edgeAttributeMap.getSizeMergedAttributes();
		for (int i = 0; i < nattr; i++) {
			CyTable t = newNetwork.getRow(newEdge).getTable();
			CyColumn attr_merged = t.getColumn(edgeAttributeMap.getMergedAttribute(i));
	
				// merge
			Map<CyEdge, CyColumn> edgeToColMap = new HashMap<CyEdge, CyColumn>();
			for (CyNetwork net : mapNetEdge.keySet()) 
			{
				final String attrName = edgeAttributeMap.getOriginalAttribute(net, i);
				final CyTable table = edgeAttributeMap.getCyTable(net);
				if (attrName != null) 
				{
					Set<CyEdge> edges = mapNetEdge.get(net);
					for (CyEdge ed : edges) 
							edgeToColMap.put(ed, table.getColumn(attrName));
				}
				edgeMerger.mergeAttribute(edgeToColMap, newEdge, attr_merged, newNetwork);
			}
		}
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
	protected int matchEdge( CyNetwork network1, CyEdge e1, int position) {
		
		int index = -1;
		long id1, id2 = 0;
		Map<Long,Integer> mapNodesEdges = null;
		Map<Long,Integer> mapNodesDirectedEdges = null;
		if (e1 == null ) 			throw new NullPointerException("e1 == null");
		
		String i1 = network1.getRow(e1).get(CyEdge.INTERACTION, String.class);
		
		
		CyNode source = e1.getSource();
		CyNode target = e1.getTarget();
		
		if (source == null) 	throw new NullPointerException("source == null");
		if (target == null )	throw new NullPointerException("target == null ");
		int iSource = mapNodesIndex.get(source);
		int iTarget = mapNodesIndex.get(target);
		
		if (e1.isDirected())
		{
			mapNodesDirectedEdges = (i1 == null) ? mapEdgeDirectedNoInteractions : mapEdgeDirectedInteractions.get(i1);
			id1 = edgeID(iSource, iTarget);
			if( mapNodesDirectedEdges != null)
				if(mapNodesDirectedEdges.get(id1) != null)
					index = mapNodesDirectedEdges.get(id1);
		}
		else
		{
			mapNodesEdges = (i1 == null) ? mapEdgeNoInteractions : mapEdgeInteractions.get(i1);
			id1 = edgeID(iSource, iTarget);
			id2 = edgeID(iTarget,iSource);
			if(mapNodesEdges != null)
				if(mapNodesEdges.get(id1) != null && mapNodesEdges.get(id2) != null && mapNodesEdges.get(id1).equals(mapNodesEdges.get(id2)))
					index = mapNodesEdges.get(id1);
		}
		
		
		if(index == -1)
		{			
			if (e1.isDirected())
			{
				if( mapNodesDirectedEdges != null)
					mapNodesDirectedEdges.put(id1, position);
				else
				{
					mapNodesDirectedEdges = new HashMap<Long,Integer>();
					mapNodesDirectedEdges.put(id1, position);
					mapEdgeDirectedInteractions.put(i1, mapNodesDirectedEdges);
				}
			}
			else
			{
				if( mapNodesEdges != null)
				{
					mapNodesEdges.put(id1, position);
					mapNodesEdges.put(id2, position);
				}
				else
				{
					mapNodesEdges = new HashMap<Long,Integer>();
					mapNodesEdges.put(id1, position);
					mapNodesEdges.put(id2, position);
					mapEdgeInteractions.put(i1, mapNodesEdges);
				}
			}
		}
		
		return index;
	}
	

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
	
//	private void dumpNodeColumnMap(Map<CyNode, CyColumn> map)
//	{
//		for (CyNode id : map.keySet())
//			System.out.print(id.getSUID() + ">" + map.get(id).getName() + ". ");
//		System.out.println();
//	}
	
	private void dumpEdgeMap(Map<CyEdge, CyColumn> map)
	{
		if (!Merge.verbose) return;
		for (CyIdentifiable id : map.keySet())
			System.out.print(id.getSUID() + ">" + map.get(id).getName() + ". ");
		System.out.println();
	}
}

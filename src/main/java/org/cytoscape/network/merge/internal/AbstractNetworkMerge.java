package org.cytoscape.network.merge.internal;

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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Iterator;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;

import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNode;
import org.cytoscape.model.CyEdge;
import org.cytoscape.model.CyIdentifiable;
import org.cytoscape.model.CyRow;
import org.cytoscape.work.TaskMonitor;

/**
 * NetworkMerge implement
 */
public abstract class AbstractNetworkMerge implements NetworkMerge {
	
	protected boolean withinNetworkMerge = false;
	protected final TaskMonitor taskMonitor;
	//Maps a node and its position in the match list
	protected Map<CyNode,Integer> mapNodesIndex;
	
	//There are two different maps to differentiate directed and undirected edges
	//Each map does a first map based on type of interactions and then a second map that maps
	//a Long index made of combination of two integer indexes from the two nodes and the
	//index of that edge in the matched list
	//There is also a second set of maps for the case when the edges do not have a value 
	//in the interaction column. This would be a special case that needs to be considered too
	protected Map<String,Map<Long,Integer>> mapEdgeDirectedInteractions;
	protected Map<String,Map<Long,Integer>> mapEdgeInteractions;
	protected Map<Long,Integer> mapEdgeNoInteractions;
	protected Map<Long,Integer> mapEdgeDirectedNoInteractions;
	
	// For canceling task
	private volatile boolean interrupted;

	public AbstractNetworkMerge(final TaskMonitor taskMonitor) {
		this.taskMonitor = taskMonitor;
		interrupted = false;
		mapNodesIndex = new HashMap<CyNode,Integer>();
		mapEdgeDirectedInteractions = new HashMap<String,Map<Long,Integer>>();
		mapEdgeInteractions = new HashMap<String,Map<Long,Integer>>();
		mapEdgeNoInteractions = new HashMap<Long,Integer>();
		mapEdgeDirectedNoInteractions = new HashMap<Long,Integer>();
	}

	public void setWithinNetworkMerge(boolean within) {		withinNetworkMerge = within;	}

	public void interrupt() {		interrupted = true;	}
	
	class NetNodeSet 
	{
		 HashMap<CyNetwork, Set<CyNode>> map = new HashMap<CyNetwork, Set<CyNode>>();
		
		 String dump(String s)
		{
			StringBuilder str = new StringBuilder(s);
			for (CyNetwork net : map.keySet())
				str.append("(" + net.getSUID() + " " + dumpNodeSet(map.get(net)) + ")");
			return str.toString();
		}

		public String toString()
		{
			return dump("");
		}
		
		private String dumpNodeSet(Set<CyNode> set) {
			StringBuilder str = new StringBuilder();
			System.out.print("{");
			for (CyNode n : set)	str.append(n.getSUID() + ", ");
			str.append("}");
			return str.toString();
			
		}

		public Set<CyNetwork> keySet() 						{	return map.keySet();		}
		public Set<CyNode> get(CyNetwork net) 				{	return map.get(net);	}
		public void put(CyNetwork net1, Set<CyNode> nodes) 	{ 	map.put(net1, nodes); }
		public boolean containsKey(CyNetwork net1) 			{	return map.containsKey(net1);		}
	}

	/**
	 * Check whether two nodes match
	 * 
	 * @param n1
	 *            ,n2 two nodes belongs to net1 and net2 respectively
	 * 
	 * @return true if n1 and n2 matches
	 */
	protected abstract boolean matchNode(CyNetwork net1, CyNode n1, CyNetwork net2, CyNode n2);

	/**
	 * Merge (matched) nodes into one
	 * 
	 * @param mapNetNode
	 *            map of network to node, node in the network to be merged
	 * @param newNode
	 *            merge data to this new node
	 */
	protected abstract void mergeNode(NetNodeSet mapNetNode, CyNode newNode, CyNetwork newNetwork);

	/**
	 * Merge (matched) nodes into one. This method will be refactored in
	 * Cytoscape3
	 * 
	 * @param mapNetEdge
	 *            map from network to Edge, Edge in the network to be merged
	 * @param newEdge
	 *            merge data to this edge
	 * 
	 * @return merged Edge
	 */
	protected abstract void mergeEdge(Map<CyNetwork, Set<CyEdge>> mapNetEdge, CyEdge newEdge, CyNetwork newNetwork);

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
	
	private long edgeID(int a, int b)
	{
		return ((long) a << 32) | b ;
	}

	protected abstract void preprocess(CyNetwork toNetwork);

	/**
	 * {@inheritDoc}
	 */
	@Override
	public CyNetwork mergeNetwork(final CyNetwork mergedNetwork, final List<CyNetwork> fromNetworks, final Operation op, final boolean subtractOnlyUnconnectedNodes) {
		// Null checks for required fields...
		if (mergedNetwork == null) { 
			throw new NullPointerException("Merged networks wasn't created.");
		}
		System.out.println("mergeNetwork");
		if (fromNetworks == null) 			throw new NullPointerException("No networks selected.");
		if (op == null) 					throw new NullPointerException("Operation parameter is missing.");
		if (fromNetworks.isEmpty()) 		throw new IllegalArgumentException("No source networks!");

		//long startTime = System.currentTimeMillis();
		preprocess(mergedNetwork);

		System.err.println("B:  perform the set operation on the node level " );
		
		mapNodesIndex.clear();
		mapEdgeDirectedInteractions.clear();
		mapEdgeInteractions.clear();
		mapEdgeDirectedNoInteractions.clear();
		mapEdgeNoInteractions.clear();
		// get node matching list
		List<NetNodeSet> matchedNodeList = getMatchedNodeList(fromNetworks);
		List<NetNodeSet> differenceNodeList = null;
		if(op == Operation.DIFFERENCE && subtractOnlyUnconnectedNodes) 
			differenceNodeList = matchedNodeList;
	
		if(interrupted) 			return null;		// Check cancel status
		
		
		matchedNodeList = getMatchedNodeList(matchedNodeList, op, fromNetworks);
		System.out.println("C:  build the list of node list that will merge------------" );
		dumpMatchedNodeList(matchedNodeList);
		System.out.println("D:  merge nodes in the list ----------------776--------------" );
		
		
		
		Map<CyNode, NetNodeSet> differenceNodeMap = null;
		if(differenceNodeList != null) {
			System.out.println("D1:  difference list --------------" );
			differenceNodeList.removeAll(matchedNodeList);
			differenceNodeMap = new HashMap<CyNode, NetNodeSet>();
			for(NetNodeSet mapNetNode: differenceNodeList) {
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
		
		final Map<CyNode, CyNode> nodeNodeMap = new HashMap<CyNode, CyNode>();
		// merge nodes in the list
		taskMonitor.setStatusMessage("Merging nodes...");
		final long nNode = matchedNodeList.size();
		for (int i = 0; i < nNode; i++) {
			if (interrupted)				return null;
			taskMonitor.setProgress(((double)(i + 1)/ nNode)*0.5d);

			final NetNodeSet netNodeMap = matchedNodeList.get(i);
			System.out.println("netNodeMap.size - " + netNodeMap.map.size() + " " + netNodeMap.toString());
			if (netNodeMap == null || netNodeMap.map.isEmpty())			continue;

			CyNode targetNode = mergedNetwork.addNode();
			mergeNode(netNodeMap, targetNode, mergedNetwork);
			nodedump(targetNode);

			
			for (Set<CyNode> nodeSet : netNodeMap.map.values())
				for (CyNode n : nodeSet) 
					nodeNodeMap.put(n, targetNode);
		}
		System.out.println(nodeNodeMap.size() + " has keys: " + nodeNodeMap.keySet());
		
		
		
		// match edges
		taskMonitor.setStatusMessage("Merging edges...");
		List<Map<CyNetwork, Set<CyEdge>>> matchedEdgeList = getMatchedEdgeList(fromNetworks);
//		System.err.print("matchedEdgeList: " );

		// Check cancel status
		if(interrupted)  return null;
		
		matchedEdgeList = selectMatchedEdgeList(matchedEdgeList, op, fromNetworks);
		System.err.println(" ----------EDGE MERGE----STOP---------------\nsize = " +  matchedEdgeList.size());

		// merge edges
		final double nEdge = matchedEdgeList.size();
		
		for (int i = 0; i < nEdge; i++) {
			if (interrupted) return null;
			taskMonitor.setProgress(((double)(i + 1) / nEdge)*0.5d + 0.5d);

			final Map<CyNetwork, Set<CyEdge>> netEdgeSetMap = matchedEdgeList.get(i);
			if (netEdgeSetMap == null || netEdgeSetMap.isEmpty())
				continue;

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
					NetNodeSet mapNetNode = differenceNodeMap.get(originalSource);
					mergeNode(mapNetNode, source, mergedNetwork);
					for(Set<CyNode> nodes: mapNetNode.map.values())
						for(CyNode node: nodes)
							nodeNodeMap.put(node, source);
				}
				if(target == null) {
					CyNode originalTarget = originalEdge.getTarget();
					target = mergedNetwork.addNode();
					NetNodeSet mapNetNode = differenceNodeMap.get(originalTarget);
					mergeNode(mapNetNode, target, mergedNetwork);
					for(Set<CyNode> nodes: mapNetNode.map.values())
						for(CyNode node: nodes)
							nodeNodeMap.put(node, target);
				}
			} 
			else if (source == null || target == null) { // some of the nodes may be
														// excluded when intersection or difference
				continue;
			}

			final boolean directed = originalEdge.isDirected();
			CyEdge edge = mergedNetwork.addEdge(source, target, directed);
			mergeEdge(netEdgeSetMap, edge, mergedNetwork);
		}
		//System.out.println("Run time: " + (System.currentTimeMillis() - startTime));

		System.err.println("return mergedNetwork ---------------------------------------" );
		return mergedNetwork;
	}

	private void nodedump(CyNode node) {
		System.out.print("NodeDump: " + node.getSUID() + " > ");
		AttributeBasedNetworkMerge.dumpRow(node);
	}

	private void dumpMatchedNodeList(List<NetNodeSet> matchedNodeList) {
		System.out.println("dumpMatchedNodeList size: " + matchedNodeList.size());
		for (NetNodeSet map : matchedNodeList)
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
		
		System.out.println("EdgeMatch: ");

		final List<Map<CyNetwork, Set<CyEdge>>> matchedList = new ArrayList<Map<CyNetwork, Set<CyEdge>>>();
		final int nNet = networks.size();

		for (int i = 0; i < nNet; i++) {
			final CyNetwork net1 = networks.get(i);
				System.out.println(net1.getEdgeCount() );
		}
		
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
	String netName(CyNetwork net) {
		return "";
	}
////
	private List<NetNodeSet> getMatchedNodeList(final List<CyNetwork> networks) {
		int index = 0;
		if (networks == null)   throw new NullPointerException("networks == null");
		if (networks.isEmpty())	throw new IllegalArgumentException("No merging network");
		
		System.out.println("+++++++++++++++++++ NodeMatch: ");

		final List<NetNodeSet> matchedList = new ArrayList<NetNodeSet>();
		final int nNet = networks.size();

		for (int i = 0; i < nNet; i++) {
			final CyNetwork net1 = networks.get(i);
				System.out.println(net1.getSUID() + ": " + net1.getNodeCount());
		}
		System.out.println();
		
		for (int i = 0; i < nNet; i++) {
			final CyNetwork net1 = networks.get(i);
			final List<CyNode> nodeList = (List<CyNode>) net1.getNodeList();
			System.out.println(net1.getSUID() + ": " + nodeList.size());

			for(CyNode node1: nodeList) 
			{
				if (interrupted)	return null;
				
			// check whether any nodes in the matchedNodeList match with
			// this node if yes, add to the list, else add a new map to the list
				boolean matched = false;
				final int n = matchedList.size();
					//The search for a match has been split for nodes 
			
				for (int j = 0; j < n; j++) {
					final NetNodeSet matchedNodes = matchedList.get(j);
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
								System.out.println(node1.getSUID() + " - " + index);
								break;
							}
						}
					}
					
					if (matched) 	break;
				}
				
				if (!matched) {
					// no matched node found, add new map to the list
					final NetNodeSet matchedNodes = new NetNodeSet();
					Set<CyNode> nodes = new HashSet<CyNode>();
					nodes.add(node1);
					matchedNodes.put(net1, nodes);
					mapNodesIndex.put((CyNode) node1, n);
					matchedList.add(matchedNodes);
					
					System.out.println("putting " + node1.getSUID());
				}
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
			System.out.println("A");
		}
		System.out.println("B");
		return matchedList;
	}

////
	/**
	 * Select nodes for merge according to different op
	 * 
	 * @param networks
	 *            Networks to be merged
	 * @param op
	 *            Operation
	 * @param size
	 *            Number of networks
	 * 
	 * @return list of matched nodes
	 */
	private List<NetNodeSet> getMatchedNodeList(
			final List<NetNodeSet> matchedNodeList,  final Operation op, final List<CyNetwork> networks) 
	{
		if (matchedNodeList == null)	throw new NullPointerException("matchedGraphObjectsList == null");
		if (op == null)					throw new NullPointerException("op == null");

		int nnet = networks.size();

		if (op == Operation.UNION) 		return matchedNodeList;
		
		if (op == Operation.INTERSECTION) {
			List<NetNodeSet> list = new ArrayList<NetNodeSet>();
			for (NetNodeSet map : matchedNodeList) 
				if (map.map.size() == nnet) // if contained in all the networks
					list.add(map);
			return list;
		} 
 
		// For Operation.DIFFERENCE
		final List<NetNodeSet> list = new ArrayList<NetNodeSet>();
		if (nnet < 2)
			return list;

		final CyNetwork net1 = networks.get(0);
		final CyNetwork net2 = networks.get(1);
		for (NetNodeSet map : matchedNodeList) 
			if ((map.containsKey(net1) && !map.containsKey(net2))) 
				list.add(map);
		return list;
		
	}
	/**
	 * Select edges for merge according to different op
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
		final CyNetwork net2 = networks.get(1);
		for (Map<CyNetwork, Set<CyEdge>> map : matchedGraphObjectsList) 
			if ((map.containsKey(net1) && !map.containsKey(net2))) 
				list.add(map);
		return list;
		
	}
}

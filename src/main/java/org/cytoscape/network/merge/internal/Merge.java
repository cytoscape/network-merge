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
	public static boolean verbose = false;
	protected boolean withinNetworkMerge = false;
	protected final TaskMonitor taskMonitor;

	// For canceling task
	private volatile boolean interrupted;

	/**
	 * 
	 * @param matchingAttribute  -- a map of network to column
	 * @param nodeAttributeMapping	
	 * @param edgeAttributeMapping
	 * @param nodeMerger
	 * @param edgMerger
	 * @param attributeValueMatcher
	 * @param asCommand
	 * @param taskMonitor
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
	 *  	
	 *  	E: Build Edge List
	 *  		for each network
	 *  		for each edge
	 *  			if both source and target have equivalents
	 *  				add edge to match list
	 *  
	 *  	F:	Merge Edges
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
		private CyNetwork targetNetwork;
		private Operation operation;

//		NetNodeSetMap differenceNodeList = null;
		
		public CyNetwork mergeNetwork(final CyNetwork mergedNetwork, final List<CyNetwork> fromNetworks, final Operation op, final boolean subtractOnlyUnconnectedNodes) {
			// Null checks for required fields...
			if (verbose) System.out.println("B: mergeNetwork");

			networks = fromNetworks;
			operation = op;
			targetNetwork = mergedNetwork;

			if (mergedNetwork == null) 			throw new NullPointerException("Merged networks wasn't created.");
			if (fromNetworks == null) 			throw new NullPointerException("No networks selected.");
			if (op == null) 					throw new NullPointerException("Operation parameter is missing.");
			if (fromNetworks.isEmpty()) 		throw new IllegalArgumentException("No source networks!");
			if (operation == Operation.DIFFERENCE && networks.size() != 2) 		throw new IllegalArgumentException("Difference only works with two networks");
			//long startTime = System.currentTimeMillis();
			
			preprocess();
			if(interrupted) 			return null;		// Check cancel status
			
			taskMonitor.setStatusMessage("Merging nodes...");
			nodeMerger.mergeNodes(networks, targetNetwork, operation, nodeAttributeMap, matchingAttribute, attributeValueMatcher, matchColumn, countColumn);
			if(!interrupted) 			
			{
				taskMonitor.setStatusMessage("Merging edges...");
				edgeMerger.mergeEdges(networks, targetNetwork, operation, nodeMerger, edgeAttributeMap);
			}
		
			if (verbose) System.err.println("H return mergedNetwork ---------------------------------------" );
			return mergedNetwork;
		}
	public void interrupt()	{		interrupted = true;	}
		//=================================================================================
	protected void preprocess() {
		
//		matchingAttribute.addNetwork(target);
		setAttributeTypes(targetNetwork.getDefaultNodeTable(), nodeAttributeMap);
		setAttributeTypes(targetNetwork.getDefaultEdgeTable(), edgeAttributeMap);
		if (asCommand)
		{
			// create two columns in each of the node and edge tables
			matchColumn = targetNetwork.getDefaultNodeTable().getColumn(MATCH);
			if (matchColumn == null)
			{
				targetNetwork.getDefaultNodeTable().createColumn(MATCH, String.class, true);
				matchColumn = targetNetwork.getDefaultNodeTable().getColumn(MATCH);
			}
			countColumn = targetNetwork.getDefaultNodeTable().getColumn(COUNT);
			if (countColumn == null)
			{
				targetNetwork.getDefaultNodeTable().createColumn(COUNT, Integer.class, true);
				countColumn = targetNetwork.getDefaultNodeTable().getColumn(COUNT);
			}
			edgeMatchColumn = targetNetwork.getDefaultEdgeTable().getColumn(MATCH);
			if (edgeMatchColumn == null)
			{
				targetNetwork.getDefaultEdgeTable().createColumn(MATCH, String.class, true);
				edgeMatchColumn = targetNetwork.getDefaultEdgeTable().getColumn(MATCH);
			}
			edgeCountColumn = targetNetwork.getDefaultEdgeTable().getColumn(COUNT);
			if (edgeCountColumn == null)
			{
				targetNetwork.getDefaultEdgeTable().createColumn(COUNT, Integer.class, true);
				edgeCountColumn = targetNetwork.getDefaultEdgeTable().getColumn(COUNT);
			}
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

	//===============================================================================================
	public void setWithinNetworkMerge(boolean within) {		withinNetworkMerge = within;	}
	

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
	


}

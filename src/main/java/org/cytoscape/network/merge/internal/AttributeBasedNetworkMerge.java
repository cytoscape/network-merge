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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.cytoscape.model.CyColumn;
import org.cytoscape.model.CyEdge;
import org.cytoscape.model.CyIdentifiable;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNode;
import org.cytoscape.model.CyRow;
import org.cytoscape.model.CyTable;
import org.cytoscape.network.merge.internal.model.AttributeMapping;
import org.cytoscape.network.merge.internal.model.AttributeMappingImpl;
import org.cytoscape.network.merge.internal.model.NetColumnMap;
import org.cytoscape.network.merge.internal.util.AttributeValueMatcher;
import org.cytoscape.network.merge.internal.util.ColumnType;
import org.cytoscape.network.merge.internal.util.DefaultAttributeValueMatcher;
import org.cytoscape.work.TaskMonitor;

/**
 * Column based network merge
 * 
 * 
 */
public class AttributeBasedNetworkMerge extends AbstractNetworkMerge {

	private final NetColumnMap matchingAttribute;
	private final AttributeMapping nodeAttributeMapping;
	private final AttributeMapping edgeAttributeMapping;
	private final AttributeValueMatcher attributeValueMatcher;
	private final EdgeMerger edgeMerger;
	private final NodeMerger nodeMerger;
	private final boolean asCommand;

	/**
	 * 
	 * @param matchingAttribute
	 * @param nodeAttributeMapping
	 * @param edgeAttributeMapping
	 * @param attributeMerger
	 */
	public AttributeBasedNetworkMerge(
			final NetColumnMap matchingAttribute,
			final AttributeMapping nodeAttributeMapping, 
			final AttributeMapping edgeAttributeMapping,
			final NodeMerger nodeMerger, 
			final EdgeMerger edgeMerger, 
			boolean asCommand,
			final TaskMonitor taskMonitor) {
		
		this(matchingAttribute, nodeAttributeMapping, edgeAttributeMapping, nodeMerger, edgeMerger,
				new DefaultAttributeValueMatcher(), asCommand, taskMonitor);
	}

	/**
	 * 
	 * @param matchingAttribute  -- a map of network to column
	 * @param nodeAttributeMapping
	 * @param edgeAttributeMapping
	 * @param attributeMerger
	 * @param attributeValueMatcher
	 */
	public AttributeBasedNetworkMerge(
			final NetColumnMap matchingAttribute,
			final AttributeMapping nodeAttributeMapping, 
			final AttributeMapping edgeAttributeMapping,
			final NodeMerger nodeMerger, 
			final EdgeMerger edgMerger, 
			AttributeValueMatcher attributeValueMatcher,
			boolean asCommand,
			final TaskMonitor taskMonitor) {
		super(taskMonitor);

		if (matchingAttribute == null) 		throw new java.lang.NullPointerException("matchingAttribute");
		if (nodeAttributeMapping == null) 	throw new java.lang.NullPointerException("nodeAttributeMapping");
		if (edgeAttributeMapping == null) 	throw new java.lang.NullPointerException("edgeAttributeMapping");
		if (nodeMerger == null) 			throw new java.lang.NullPointerException("nodeMerger");
		if (edgMerger == null) 				throw new java.lang.NullPointerException("edgMerger");
		if (attributeValueMatcher == null) 	throw new java.lang.NullPointerException("attributeValueMatcher");
		
		this.matchingAttribute = matchingAttribute;
		this.nodeAttributeMapping = nodeAttributeMapping;
		this.edgeAttributeMapping = edgeAttributeMapping;
		this.nodeMerger = nodeMerger;
		this.edgeMerger = edgMerger;
		this.attributeValueMatcher = attributeValueMatcher;
		this.asCommand = asCommand;
	}

	@Override
	protected boolean matchNode(final CyNetwork net1, final CyNode n1, final CyNetwork net2, final CyNode n2) {
		if (net1 == null || n1 == null || net2 == null || n2 == null)
			throw new NullPointerException();

		// TODO: should it match if n1==n2?
		if (n1 == n2)
			return true;

		CyColumn col1 = matchingAttribute.getColumn(net1);
		CyColumn col2 = matchingAttribute.getColumn(net2);
		
		if (col1 == null || col2 == null)
			throw new IllegalArgumentException("Please specify the matching table column first");
		
		boolean result = attributeValueMatcher.matched(n1, col1, n2, col2);
//		System.out.println((result? "MATCH " : "NOMATCH    ") + net1.getSUID() + ": " + col1.getName() + ", " + net2.getSUID() + ": " + col2.getName());
		return result; 
	}

	//----------------------------------------------------------------
	static String MATCH = "Matching.Attribute";
	CyColumn matchColumn = null;
	
	@Override
	protected void preprocess(CyNetwork toNetwork) {
		setAttributeTypes(toNetwork.getDefaultNodeTable(), nodeAttributeMapping);
		if (asCommand)
		{
			toNetwork.getDefaultNodeTable().createColumn(MATCH, String.class, true);
			matchColumn = toNetwork.getDefaultNodeTable().getColumn(MATCH);
		}
		setAttributeTypes(toNetwork.getDefaultEdgeTable(), edgeAttributeMapping);
	}

	private void setAttributeTypes(final CyTable table, AttributeMapping attributeMapping) 
	{
		int n = attributeMapping.getSizeMergedAttributes();
		System.out.println("setAttributeTypes has size "  + n);
		for (int i = 0; i < n; i++) 
		{
			String attr = attributeMapping.getMergedAttribute(i);
			if (table.getColumn(attr) != null) 		continue; // TODO: check if the type is the same
				
			final ColumnType type = attributeMapping.getMergedAttributeType(i);
			final boolean isImmutable = attributeMapping.getMergedAttributeMutability(i);
			if (type.isList()) 	table.createListColumn(attr, type.getType(), isImmutable);
			else 				table.createColumn(attr, type.getType(), isImmutable);
			
		}
		System.out.println("setAttributeTypes has size "  + n);
	}
	//----------------------------------------------------------------
	@Override
	protected void mergeNode(final NetNodeSetMap mapNetToNodes, CyNode targetNode, CyNetwork targetNetwork) {

		if (mapNetToNodes == null || mapNetToNodes.map.isEmpty())
			return;
		((AttributeMappingImpl) nodeAttributeMapping).dump("E mergeNode --------------");
//		AttributeMappingImpl.dumpStrs(nodeAttributeMapping.getMergedAttributes());
		final int nattr = nodeAttributeMapping.getSizeMergedAttributes();
		for (int i = 0; i < nattr; i++) 
		{
			String attribute = nodeAttributeMapping.getMergedAttribute(i);
					
			CyRow row = targetNetwork.getRow(targetNode);
			CyTable t = row.getTable();
			CyColumn targetColumn = t.getColumn(attribute);

			// merge
			Map<CyNode, CyColumn> nodeToColMap = new HashMap<CyNode, CyColumn>();
			
			for (CyNetwork net : mapNetToNodes.map.keySet())
			{
				Set<CyNode> nodeList = mapNetToNodes.get(net);
				final CyTable table = nodeAttributeMapping.getCyTable(net);
				if (table == null) continue;

				final String attrName = nodeAttributeMapping.getOriginalAttribute(net, i);
				if (attrName == null) continue;
				if (attrName.equals(attribute))
					nodeMerger.mergeAttribute(nodeToColMap, targetNode, targetColumn, targetNetwork);
				
				final CyColumn colum = (table == null) ? null : table.getColumn(attrName);
				for (CyNode node : nodeList)
					nodeToColMap.put(node, colum);
			}
//			dumpNodeColumnMap(nodeToColMap);
			nodeMerger.mergeAttribute(nodeToColMap, targetNode, targetColumn, targetNetwork);
			for (CyNetwork net : mapNetToNodes.map.keySet())
			{
				boolean isJoinColumn = matchingAttribute.contains(net, attribute);
				if (isJoinColumn)
					nodeMerger.mergeAttribute(nodeToColMap, targetNode, matchColumn, targetNetwork);
			}
		}
		System.out.println("Node Merged");
	}
//	}

	//----------------------------------------------------------------
	/**
	 * {@inheritDoc}
	 */
	@Override
	public void mergeEdge(final Map<CyNetwork, Set<CyEdge>> mapNetEdge, CyEdge newEdge, CyNetwork newNetwork) {
		if (mapNetEdge == null || mapNetEdge.isEmpty() || newEdge == null) {
			throw new IllegalArgumentException();
		}

		// set other attributes as indicated in attributeMapping
		setEdgeAttribute(newNetwork, newEdge, mapNetEdge, edgeAttributeMapping);
	}

	/*
	 * set attribute for the merge node/edge according to attribute mapping
	 */
	
	/*
	 * set attribute for the merge node/edge according to attribute mapping
	 */
	protected void setEdgeAttribute(CyNetwork newNetwork, CyEdge toEntry,
			final Map<CyNetwork, Set<CyEdge>> mapNetGOs, final AttributeMapping attributeMapping) {

	((AttributeMappingImpl) attributeMapping).dump("setEdgeAttribute");
	final int nattr = attributeMapping.getSizeMergedAttributes();
	for (int i = 0; i < nattr; i++) {
		CyTable t = newNetwork.getRow(toEntry).getTable();
		CyColumn attr_merged = t.getColumn(attributeMapping.getMergedAttribute(i));

			// merge
		Map<CyEdge, CyColumn> edgeToColMap = new HashMap<CyEdge, CyColumn>();
		final Iterator<Map.Entry<CyNetwork, Set<CyEdge>>> itEntryNetGOs = mapNetGOs.entrySet().iterator();
		while (itEntryNetGOs.hasNext()) {
			final Map.Entry<CyNetwork, Set<CyEdge>> entryNetGOs = itEntryNetGOs.next();
			final CyNetwork net = entryNetGOs.getKey();
			final String attrName = attributeMapping.getOriginalAttribute(net, i);
//				System.out.print(attrName + ", ");
			final CyTable table = attributeMapping.getCyTable(net);
			if (attrName != null) {
				final Iterator<CyEdge> object = entryNetGOs.getValue().iterator();
				while (object.hasNext()) {
						final CyEdge idGO = object.next();						
						edgeToColMap.put(idGO, table.getColumn(attrName));
					}
				}
			}
			System.out.println();
			dump(edgeToColMap);
			try {
				// TODO how to handle network?
				edgeMerger.mergeAttribute(edgeToColMap, toEntry, attr_merged, newNetwork);
			} catch (Exception e) {
				e.printStackTrace();
				continue;
			}
		}
	}

	//----------------------------------------------------------------
	//----------------------------------------------------------------
//	static public void dumpRow(CyNode node)
//	{
//		CyNetwork net = node.getNetworkPointer();
//		if (net == null) { System.out.println("NULL net");   return; 	} 
//		CyRow row = net.getRow(node);
//		if (row == null) { System.out.println("NULL row");   return; 	} 
//		Map<String, Object> vals = row.getAllValues();
//		System.out.print("{");
//		for (String s : vals.keySet())
//			System.out.print(s + ":" + vals.get(s) + ", ") ;
//		System.out.println("}");
//	}
	
	private void dumpNodeColumnMap(Map<CyNode, CyColumn> map)
	{
		for (CyNode id : map.keySet())
			System.out.print(id.getSUID() + ">" + map.get(id).getName() + ". ");
		System.out.println();
	}
	
	private void dump(Map<CyEdge, CyColumn> map)
	{
		for (CyIdentifiable id : map.keySet())
			System.out.print(id.getSUID() + ">" + map.get(id).getName() + ". ");
		System.out.println();
	}
}

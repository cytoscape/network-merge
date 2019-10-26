package org.cytoscape.network.merge.internal.task;

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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNetworkFactory;
import org.cytoscape.model.CyNetworkManager;
import org.cytoscape.network.merge.internal.AttributeBasedNetworkMerge;
import org.cytoscape.network.merge.internal.EdgeMerger;
import org.cytoscape.network.merge.internal.NetworkMerge.Operation;
import org.cytoscape.network.merge.internal.NodeMerger;
import org.cytoscape.network.merge.internal.conflict.AttributeConflictCollector;
import org.cytoscape.network.merge.internal.model.AttributeMapping;
import org.cytoscape.network.merge.internal.model.MatchingAttribute;
import org.cytoscape.network.merge.internal.util.AttributeValueMatcher;
import org.cytoscape.network.merge.internal.util.DefaultAttributeValueMatcher;
import org.cytoscape.task.create.CreateNetworkViewTaskFactory;
import org.cytoscape.work.AbstractTask;
import org.cytoscape.work.TaskMonitor;


public class NetworkMergeTask extends AbstractTask {	
	private final List<CyNetwork> selectedNetworkList;
	private final Operation operation;
	private final boolean subtractOnlyUnconnectedNodes;
	private final AttributeConflictCollector conflictCollector;

	
	final private CreateNetworkViewTaskFactory netViewCreator;

	private MatchingAttribute matchingAttribute;
	private AttributeMapping nodeAttributeMapping;
	private AttributeMapping edgeAttributeMapping;

	private boolean inNetworkMerge;

	private final CyNetworkFactory cnf;
	private final CyNetworkManager networkManager;
	private final String networkName;
	
	private AttributeBasedNetworkMerge networkMerge;

	/**
	 * Constructor.<br>
	 * 
	 */
	public NetworkMergeTask(final CyNetworkFactory cnf, 
			final CyNetworkManager networkManager,
			final String networkName, 
			final MatchingAttribute matchingAttribute,
			final AttributeMapping nodeAttributeMapping, 
			final AttributeMapping edgeAttributeMapping,
			final List<CyNetwork> selectedNetworkList, 
			final Operation operation, 
			final boolean subtractOnlyUnconnectedNodes, 
			final AttributeConflictCollector conflictCollector,
			final String tgtType,   //final Map<String, Map<String, Set<String>>> selectedNetworkAttributeIDType,
			final boolean inNetworkMerge, 
			final CreateNetworkViewTaskFactory netViewCreator) 
	{
		this.selectedNetworkList = selectedNetworkList;
		this.operation = operation;
		this.subtractOnlyUnconnectedNodes = subtractOnlyUnconnectedNodes;
		this.conflictCollector = conflictCollector;
		this.netViewCreator = netViewCreator;
		this.matchingAttribute = matchingAttribute;
		this.inNetworkMerge = inNetworkMerge;
		this.nodeAttributeMapping = nodeAttributeMapping;
		this.edgeAttributeMapping = edgeAttributeMapping;
		this.networkName = networkName;
		this.cnf = cnf;
		this.networkManager = networkManager;
	}

	@Override
	public void cancel() {
		cancelled = true;
		if(networkMerge != null)
			this.networkMerge.interrupt();
	}
static boolean verbose = true;
	@Override
	public void run(TaskMonitor taskMonitor) throws Exception {
	
		taskMonitor.setProgress(0.0d);
		taskMonitor.setTitle("Merging Networks");

		// Create new network (merged network)
		taskMonitor.setStatusMessage("Creating new merged network...");
		CyNetwork newNetwork = cnf.createNetwork();
		newNetwork.getRow(newNetwork).set(CyNetwork.NAME, networkName);

		// Register merged network
		networkManager.addNetwork(newNetwork);
		
		taskMonitor.setStatusMessage("Merging networks...");
		final NodeMerger nodeMerger = new NodeMerger(conflictCollector);
		final EdgeMerger edgeMerger = new EdgeMerger(conflictCollector);
		final AttributeValueMatcher attributeValueMatcher = new DefaultAttributeValueMatcher();

		networkMerge = new AttributeBasedNetworkMerge(
				matchingAttribute, 
				nodeAttributeMapping, 
				edgeAttributeMapping,
				nodeMerger, 
				edgeMerger, 
				attributeValueMatcher, 
				taskMonitor);
		networkMerge.setWithinNetworkMerge(inNetworkMerge);
		if (verbose) 
			System.err.println("new AttributeBasedNetworkMerge" );
		
		// Merge everything
		networkMerge.mergeNetwork(newNetwork, selectedNetworkList, operation, subtractOnlyUnconnectedNodes);

		if (verbose) 
			System.err.println("mergeNetwork" );
		taskMonitor.setStatusMessage("Processing conflicts...");
		// Perform conflict handling if necessary
		if (conflictCollector != null && !conflictCollector.isEmpty() && !cancelled) {
			HandleConflictsTask hcTask = new HandleConflictsTask(conflictCollector);
			insertTasksAfterCurrentTask(hcTask);
		}

		// Cancellation check...
		if(cancelled) {
			taskMonitor.setStatusMessage("Network merge canceled.");
			taskMonitor.setProgress(1.0d);
			networkManager.destroyNetwork(newNetwork);
			newNetwork = null;
			this.networkMerge = null;
			return;
		}

		// Create view
		taskMonitor.setStatusMessage("Creating view...");
		final Set<CyNetwork> networks = new HashSet<CyNetwork>();
		networks.add(newNetwork);
		insertTasksAfterCurrentTask(netViewCreator.createTaskIterator(networks));	
		
		taskMonitor.setProgress(1.0d);
	}
}

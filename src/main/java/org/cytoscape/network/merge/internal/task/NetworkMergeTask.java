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

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNetworkFactory;
import org.cytoscape.model.CyNetworkManager;
import org.cytoscape.network.merge.internal.AttributeBasedNetworkMerge;
import org.cytoscape.network.merge.internal.NetworkMerge.Operation;
import org.cytoscape.network.merge.internal.conflict.AttributeConflictCollector;
import org.cytoscape.network.merge.internal.model.AttributeMapping;
import org.cytoscape.network.merge.internal.model.MatchingAttribute;
import org.cytoscape.network.merge.internal.util.AttributeMerger;
import org.cytoscape.network.merge.internal.util.AttributeValueMatcher;
import org.cytoscape.network.merge.internal.util.DefaultAttributeMerger;
import org.cytoscape.network.merge.internal.util.DefaultAttributeValueMatcher;
import org.cytoscape.service.util.CyServiceRegistrar;
import org.cytoscape.task.create.CreateNetworkViewTaskFactory;
import org.cytoscape.util.json.CyJSONUtil;
import org.cytoscape.work.AbstractTask;
import org.cytoscape.work.ObservableTask;
import org.cytoscape.work.TaskMonitor;
import org.cytoscape.work.json.JSONResult;


public class NetworkMergeTask extends AbstractTask implements ObservableTask {	
	private final List<CyNetwork> selectedNetworkList;
	private final Operation operation;
	private final boolean subtractOnlyUnconnectedNodes;
	private final AttributeConflictCollector conflictCollector;

	
	final private CreateNetworkViewTaskFactory netViewCreator;
	final private CyServiceRegistrar serviceRegistrar; 

	private final MatchingAttribute matchingAttribute;
	private final AttributeMapping nodeAttributeMapping;
	private final AttributeMapping edgeAttributeMapping;

	private boolean inNetworkMerge;

	private final CyNetworkFactory cnf;
	private final CyNetworkManager networkManager;
	private final String networkName;
	private CyNetwork newNetwork;
	
	private AttributeBasedNetworkMerge networkMerge;

	/**
	 * Constructor.<br>
	 * 
	 */
	public NetworkMergeTask( final CyServiceRegistrar serviceRegistrar,
			final String networkName, final MatchingAttribute matchingAttribute,
			final AttributeMapping nodeAttributeMapping, final AttributeMapping edgeAttributeMapping,
			final List<CyNetwork> selectedNetworkList, final Operation operation, 
			final boolean subtractOnlyUnconnectedNodes, final AttributeConflictCollector conflictCollector,
			final boolean inNetworkMerge) {
		this.serviceRegistrar = serviceRegistrar;
		this.selectedNetworkList = selectedNetworkList;
		this.operation = operation;
		this.subtractOnlyUnconnectedNodes = subtractOnlyUnconnectedNodes;
		this.conflictCollector = conflictCollector;
		this.matchingAttribute = matchingAttribute;
		this.inNetworkMerge = inNetworkMerge;
		this.nodeAttributeMapping = nodeAttributeMapping;
		this.edgeAttributeMapping = edgeAttributeMapping;
		this.networkName = networkName;

		this.cnf = serviceRegistrar.getService(CyNetworkFactory.class);
		this.networkManager = serviceRegistrar.getService(CyNetworkManager.class);
		this.netViewCreator = serviceRegistrar.getService(CreateNetworkViewTaskFactory.class);
	}

	@Override
	public void cancel() {
		cancelled = true;
		if(networkMerge != null)
			this.networkMerge.interrupt();
	}

	@Override
	public void run(TaskMonitor taskMonitor) throws Exception {

		taskMonitor.setProgress(0.0d);
		taskMonitor.setTitle("Merging Networks");

		// Create new network (merged network)
		taskMonitor.setStatusMessage("Creating new merged network...");
		newNetwork = cnf.createNetwork();
		newNetwork.getRow(newNetwork).set(CyNetwork.NAME, networkName);

		// Register merged network
		networkManager.addNetwork(newNetwork);
		
		taskMonitor.setStatusMessage("Merging networks...");
		final AttributeValueMatcher attributeValueMatcher = new DefaultAttributeValueMatcher();
		final AttributeMerger attributeMerger = new DefaultAttributeMerger(conflictCollector);

		this.networkMerge = new AttributeBasedNetworkMerge(matchingAttribute, nodeAttributeMapping, edgeAttributeMapping,
				attributeMerger, attributeValueMatcher, taskMonitor);
		networkMerge.setWithinNetworkMerge(inNetworkMerge);
		
		// Merge everything
		networkMerge.mergeNetwork(newNetwork, selectedNetworkList, operation, subtractOnlyUnconnectedNodes);

		taskMonitor.setStatusMessage("Processing conflicts...");
		// Perform conflict handling if necessary
		if (!conflictCollector.isEmpty() && !cancelled) {
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

	@Override
	public List<Class<?>> getResultClasses() { 
		return Arrays.asList(String.class, CyNetwork.class, JSONResult.class);
	}

	@Override
	public <R> R getResults(Class<? extends R> type) {
    if (type.equals(CyNetwork.class)) {
      return (R)newNetwork;
    } else if (type.equals(String.class)){
      if (newNetwork == null)
        return (R)"<none>";
      return (R)newNetwork.toString();
    } else if (type.equals(JSONResult.class)) {
      JSONResult res = () -> {if (newNetwork == null)
        return "{}";
      else {
        CyJSONUtil cyJSONUtil = serviceRegistrar.getService(CyJSONUtil.class);
        return cyJSONUtil.toJson(newNetwork);
      }};
      return (R)res;
    }
    return (R)newNetwork;
	}

}

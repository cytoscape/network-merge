package org.cytoscape.network.merge.internal.task;

import org.cytoscape.service.util.CyServiceRegistrar;
import org.cytoscape.work.TaskFactory;
import org.cytoscape.work.TaskIterator;

public class NetworkMergeCommandTaskFactory implements TaskFactory {

	final CyServiceRegistrar registrar;

	public NetworkMergeCommandTaskFactory(CyServiceRegistrar reg) {  
		registrar = reg;
	}

	public boolean isReady() {
		return true;
	}

	@Override
	public TaskIterator createTaskIterator() {
		return new TaskIterator(new NetworkMergeCommandTask(registrar));		//, application, merge
	}
}

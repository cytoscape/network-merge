package org.cytoscape.network.merge.internal.task;

import org.cytoscape.service.util.CyServiceRegistrar;
import org.cytoscape.work.TaskFactory;
import org.cytoscape.work.TaskIterator;

public class NetworkMergeCommandTaskFactory implements TaskFactory {

	final CyServiceRegistrar registrar;
//	final CySwingApplication application;
//	final MergeManager merge;

	public NetworkMergeCommandTaskFactory(CyServiceRegistrar reg) {  //, CySwingApplication app, MergeManager mrg
		registrar = reg;
//		application = app;
//		merge = mrg;
	}

	public boolean isReady() {
		return true;
	}

	@Override
	public TaskIterator createTaskIterator() {
		return new TaskIterator(new NetworkMergeCommandTask(registrar));		//, application, merge
	}
}

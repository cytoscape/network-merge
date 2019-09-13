package org.cytoscape.network.merge.internal;

import org.cytoscape.application.swing.CySwingApplication;
import org.cytoscape.service.util.CyServiceRegistrar;

public class MergeManager {

	CyServiceRegistrar registrar; 
	CySwingApplication application;
	NetworkMerge merge;
	
	public MergeManager(CyServiceRegistrar reg, CySwingApplication app)
	{
		registrar = reg;
		application = app;
//		merge = mrg;
	}
}

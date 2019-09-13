package org.cytoscape.network.merge.internal.task;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.cytoscape.application.swing.CySwingApplication;
import org.cytoscape.command.StringToModel;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNetworkFactory;
import org.cytoscape.model.CyNetworkManager;
import org.cytoscape.network.merge.internal.MergeManager;
import org.cytoscape.network.merge.internal.NetworkMerge;
import org.cytoscape.network.merge.internal.model.AttributeMapping;
import org.cytoscape.network.merge.internal.model.MatchingAttribute;
import org.cytoscape.service.util.CyServiceRegistrar;
import org.cytoscape.session.CyNetworkNaming;
import org.cytoscape.task.create.CreateNetworkViewTaskFactory;
import org.cytoscape.work.AbstractTask;
import org.cytoscape.work.ContainsTunables;
import org.cytoscape.work.FinishStatus;
import org.cytoscape.work.ObservableTask;
import org.cytoscape.work.SynchronousTaskManager;
import org.cytoscape.work.TaskIterator;
import org.cytoscape.work.TaskManager;
import org.cytoscape.work.TaskMonitor;
import org.cytoscape.work.TaskObserver;
import org.cytoscape.work.Tunable;
import org.cytoscape.work.json.JSONResult;

public class NetworkMergeCommandTask extends AbstractTask implements ObservableTask {

	@ContainsTunables
	@Tunable(description="Network", context="nogui", longDescription="foo", exampleStringValue=StringToModel.CY_NETWORK_EXAMPLE_STRING)
	public CyNetwork network;

	@Tunable (description="Namespace for table", context="nogui", longDescription="long description")
	public String namespace = "default";

//	  @Tunable(
//				description = "NetworkList", context= Tunable.NOGUI_CONTEXT,
//				longDescription="The list of networks to merge", 
//				exampleStringValue = "net1, net2"
//		)
//	String networks = "<this and that>";
//
	  @Tunable(
				description = "Type of Merge", context= Tunable.NOGUI_CONTEXT,
				longDescription="Whether the networks are merged by union, intersection or difference", 
				exampleStringValue = "union"
		)
	  public String operation;
//	  
	  @Tunable(
				description = "Arguments", context= Tunable.NOGUI_CONTEXT,
				longDescription="The body of the call", 
				exampleStringValue = "{{ }}"
		)
	  public String body;
	  
	CyServiceRegistrar registrar;
	CySwingApplication application;
	MergeManager merge;

	public NetworkMergeCommandTask(CyServiceRegistrar reg, CySwingApplication app, MergeManager mrg) {
		registrar = reg;
		application = app;
		merge = mrg;
	}

	@Override
	public void run(TaskMonitor taskMonitor) throws Exception {

		System.out.println("run3");

		CyNetworkManager cnm = registrar.getService(CyNetworkManager.class);
		CyNetworkNaming cyNetworkNaming = registrar.getService(CyNetworkNaming.class);
		CyNetworkFactory cyNetworkFactory = registrar.getService(CyNetworkFactory.class);
		CreateNetworkViewTaskFactory netViewCreator = registrar.getService(CreateNetworkViewTaskFactory.class);
		System.out.println(cnm);
		System.out.println(cyNetworkNaming);
		System.out.println(cyNetworkFactory);
		System.out.println(netViewCreator);

		final String netName = cyNetworkNaming.getSuggestedNetworkTitle("Merged Table");
//		AttributeConflictCollectorImpl conflictCollector = new AttributeConflictCollectorImpl();
		MatchingAttribute matchingAttribute = null;
		AttributeMapping nodeAttributeMapping = null;
		AttributeMapping edgeAttributeMapping = null;
		final NetworkMerge.Operation op = NetworkMerge.lookup("Intersection");
		List<CyNetwork> networkList = new ArrayList<CyNetwork>();
		boolean useDiference = false; // getDifference1Btn().isSelected();
		boolean inNetworkMerge = false; // getInNetMergeCkb().isSelected();
		boolean ignoreDirection = false;
		boolean retainPosition = false;
		boolean nodesOnly = false;

		Map<String, Map<String, Set<String>>> selectedNetworkAttributeIDType = null;
		//nodeColumns="{display name,name,new display,String},{...}" #n+2
		//edgeColumns="{n1 col,n2 col,merged net col,merged net col type},{...}" #n+2

		String tgtType = "";

		final TaskManager<?, ?> taskMgr = registrar.getService(SynchronousTaskManager.class);
		final NetworkMergeTask nmTask = new NetworkMergeTask(cyNetworkFactory, cnm, netName, matchingAttribute,
				nodeAttributeMapping, edgeAttributeMapping, networkList, op, useDiference, null,
				selectedNetworkAttributeIDType, tgtType, inNetworkMerge, netViewCreator);

	      final TaskIterator taskIterator = new TaskIterator(nmTask);
	      taskIterator.append(new AbstractTask() {
	        public void run(TaskMonitor monitor) {
	          super.insertTasksAfterCurrentTask(taskIterator);
	        }
	      });
	     taskMgr.execute(taskIterator, new TaskObserver() {
	        public void taskFinished(ObservableTask t) {}
	        public void allFinished(FinishStatus status) { }
	 });	}

	@Override
	public List<Class<?>> getResultClasses() {
		return Arrays.asList(String.class, JSONResult.class);
	}	

	@Override
	public <R> R getResults(Class<? extends R> type) {
	    String response = "String Response";
		if (type.equals(String.class)) {
      return (R)response;
    } else if (type.equals(JSONResult.class)) {
			JSONResult res = () -> { return "{\"response\":\"value\"}";  };			//analyzer.getStats().jsonOutput();
			return (R)res;
		}
    return null;
  }

	
	public void cancel() {
	}

}

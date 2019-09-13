package org.cytoscape.network.merge.internal.task;


import java.util.Arrays;
import java.util.List;

import org.cytoscape.work.AbstractTask;
import org.cytoscape.work.ObservableTask;
import org.cytoscape.work.TaskMonitor;
import org.cytoscape.work.json.JSONResult;

public class VersionTask extends AbstractTask implements ObservableTask {

  final String version;
  public VersionTask(final String version) {
      this.version = version;
  }

  public boolean isReady() {
    return true;
  }
	@Override
  public void run(TaskMonitor monitor) {}

	@SuppressWarnings("unchecked")
	@Override
  public <R> R getResults(Class<? extends R> type) {
    if (type.equals(String.class)) {
      String response = "Version: "+version+"\n";
      return (R)response;
    } 
    else if (type.equals(JSONResult.class)) {
			JSONResult res = () -> { return "{\"version\":\""+version+"\"}"; };
		return (R)res;
		}
    return null;
  }

	public List<Class<?>> getResultClasses() {
		return Arrays.asList(String.class, JSONResult.class);	// JSONResult.class
	}
}


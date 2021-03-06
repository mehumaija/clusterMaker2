package edu.ucsf.rbvi.clusterMaker2.internal.ui;

import java.util.Collections;
import java.util.List;

//Cytoscape imports
import org.cytoscape.model.CyNetwork;
import org.cytoscape.work.TaskIterator;


import edu.ucsf.rbvi.clusterMaker2.internal.api.ClusterManager;
import edu.ucsf.rbvi.clusterMaker2.internal.api.ClusterTaskFactory;
import edu.ucsf.rbvi.clusterMaker2.internal.api.ClusterTaskFactory.ClusterType;
import edu.ucsf.rbvi.clusterMaker2.internal.api.ClusterViz;
import edu.ucsf.rbvi.clusterMaker2.internal.api.ClusterVizFactory;

public class HeatMapViewTaskFactory implements ClusterVizFactory   {
	ClusterManager clusterManager;
	HeatMapContext context;
	
	public HeatMapViewTaskFactory(ClusterManager clusterManager) {
		this.clusterManager = clusterManager;
		context = new HeatMapContext();
	}
	
	public String getName() {
		return HeatMapView.NAME;
	}

	public String getShortName() {
		return HeatMapView.SHORTNAME;
	}

	public ClusterViz getVisualizer() {
		// return new TreeViewTask(true);
		return null;
	}

	public boolean isReady() {
		return true;
	}

	public boolean isAvailable(CyNetwork network) {
		return false;
	}

	public List<ClusterType> getTypeList() {
		return Collections.singletonList(ClusterType.UI); 
	}

	public TaskIterator createTaskIterator() {
		// Not sure why we need to do this, but it looks like
		// the tunable stuff "remembers" objects that it's already
		// processed this tunable.  So, we use a copy constructor
		return new TaskIterator(new HeatMapView(context, clusterManager));
	}

	@Override
	public String getLongDescription() { return "Display an unclustered heatmap using the JTreeView heatmap viewer.  Neighter the rows or columns or sorted in any way.";}
}


package edu.ucsf.rbvi.clusterMaker2.internal.algorithms.networkClusterers.Leiden;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.cytoscape.application.CyApplicationManager;
import org.cytoscape.group.CyGroup;
import org.cytoscape.model.CyEdge;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNode;
import org.cytoscape.model.CyRow;
import org.cytoscape.model.CyTable;
import org.cytoscape.service.util.CyServiceRegistrar;
import org.cytoscape.work.ContainsTunables;
import org.cytoscape.work.TaskMonitor;
import org.json.simple.JSONArray;
import org.cytoscape.jobs.CyJob;
import org.cytoscape.jobs.CyJobData;
import org.cytoscape.jobs.CyJobDataService;
import org.cytoscape.jobs.CyJobExecutionService;
import org.cytoscape.jobs.CyJobManager;
import org.cytoscape.jobs.CyJobStatus;
import org.cytoscape.jobs.SUIDUtil;

import edu.ucsf.rbvi.clusterMaker2.internal.algorithms.AbstractClusterResults;
import edu.ucsf.rbvi.clusterMaker2.internal.algorithms.NodeCluster;
import edu.ucsf.rbvi.clusterMaker2.internal.algorithms.networkClusterers.AbstractNetworkClusterer;
import edu.ucsf.rbvi.clusterMaker2.internal.api.ClusterManager;
import edu.ucsf.rbvi.clusterMaker2.internal.api.ClusterResults;
import edu.ucsf.rbvi.clusterMaker2.internal.utils.ModelUtils;
import edu.ucsf.rbvi.clusterMaker2.internal.utils.remoteUtils.ClusterJob;
import edu.ucsf.rbvi.clusterMaker2.internal.utils.remoteUtils.ClusterJobData;
import edu.ucsf.rbvi.clusterMaker2.internal.utils.remoteUtils.ClusterJobDataService;
import edu.ucsf.rbvi.clusterMaker2.internal.utils.remoteUtils.ClusterJobExecutionService;
import edu.ucsf.rbvi.clusterMaker2.internal.utils.remoteUtils.RemoteServer;
import edu.ucsf.rbvi.clusterMaker2.internal.utils.remoteUtils.ClusterJobHandler;

public class LeidenCluster extends AbstractNetworkClusterer {
	public static String NAME = "Leiden Clusterer";
	public static String SHORTNAME = "leiden";
	final CyServiceRegistrar registrar;
	public final static String GROUP_ATTRIBUTE = "__LeidenGroups.SUID";
	

	@ContainsTunables
	public LeidenContext context = null;
	
	public LeidenCluster(LeidenContext context, ClusterManager manager, CyServiceRegistrar registrar) {
		super(manager);
		this.context = context;
		if (network == null)
			network = clusterManager.getNetwork();
		context.setNetwork(network);
		this.registrar = registrar;
	}

	@Override
	public String getShortName() {return SHORTNAME;}

	@Override
	public String getName() {return NAME;}

	@Override
	public void run(TaskMonitor taskMonitor) throws Exception {
		// Get the execution service
		CyJobExecutionService executionService = 
						registrar.getService(CyJobExecutionService.class, "(title=ClusterJobExecutor)");
		CyApplicationManager appManager = registrar.getService(CyApplicationManager.class);
		CyNetwork currentNetwork = appManager.getCurrentNetwork(); //gets the network presented in Cytoscape
		
		clusterAttributeName = context.getClusterAttribute();
		createGroups = context.advancedAttributes.createGroups;
				
		HashMap<Long, String> nodeMap = getNetworkNodes(currentNetwork);
		List<String> nodeArray = new ArrayList<>();
		for (Long nodeSUID : nodeMap.keySet()) {
			nodeArray.add(nodeMap.get(nodeSUID));
		}
				
		List<String[]> edgeArray = getNetworkEdges(currentNetwork, nodeMap);
				
		String basePath = RemoteServer.getBasePath();
				
				// Get our initial job
		ClusterJob job = (ClusterJob) executionService.createCyJob("ClusterJob"); //creates a new ClusterJob object
				// Get the data service
		CyJobDataService dataService = job.getJobDataService(); //gets the dataService of the execution service
				// Add our data
		CyJobData jobData = dataService.addData(null, "nodes", nodeArray);
		jobData = dataService.addData(jobData, "edges", edgeArray);
		job.storeClusterData(clusterAttributeName, currentNetwork, clusterManager, createGroups, GROUP_ATTRIBUTE);
				// Create our handler
		ClusterJobHandler jobHandler = new ClusterJobHandler(job, network);
		job.setJobMonitor(jobHandler);	
				// Submit the job
		CyJobStatus exStatus = executionService.executeJob(job, basePath, null, jobData);
		if (exStatus.getStatus().equals(CyJobStatus.Status.ERROR) ||
					exStatus.getStatus().equals(CyJobStatus.Status.UNKNOWN)) {
			monitor.showMessage(TaskMonitor.Level.ERROR, exStatus.toString());
			return;
		}
		
				// Save our SUIDs in case we get saved and restored
		SUIDUtil.saveSUIDs(job, currentNetwork, currentNetwork.getNodeList());

		CyJobManager manager = registrar.getService(CyJobManager.class);
		manager.addJob(job, jobHandler, 5);
	}
	
	private HashMap<Long, String> getNetworkNodes(CyNetwork currentNetwork) {
		List<CyNode> cyNodeList = currentNetwork.getNodeList();
		
		HashMap<Long, String> nodeMap = new HashMap<>();
		for (CyNode node : cyNodeList) {
			String nodeName = currentNetwork.getRow(node).get(CyNetwork.NAME, String.class);
			nodeMap.put(node.getSUID(), nodeName);
		}
		
		return nodeMap;
	}
	
	private List<String[]> getNetworkEdges(CyNetwork currentNetwork, Map<Long, String> nodeMap) {
		CyTable edgeTable = currentNetwork.getDefaultEdgeTable();
		List<CyEdge> cyEdgeList = currentNetwork.getEdgeList();
		
		List<String[]> edgeArray = new ArrayList<>();
		for (CyEdge edge : cyEdgeList) {
			
			String[] sourceTargetWeight = new String[3];
			
			CyNode source = edge.getSource();
			CyNode target = edge.getTarget();
			String sourceName = nodeMap.get(source.getSUID());
			sourceTargetWeight[0] = sourceName;
			String targetName = nodeMap.get(target.getSUID());
			sourceTargetWeight[1] = targetName;
			
			String attribute = context.getattribute().getSelectedValue();
			
			Double weight = currentNetwork.getRow(edge).get(attribute, Double.class); // pull the "weight" value from the Context. If it's null --> then 1.0
			
			if (attribute == "None") weight = null;
			
			if (weight == null) {
				sourceTargetWeight[2] = "1.0";
			} else {
				sourceTargetWeight[2] = String.valueOf(weight);
			}
			
			edgeArray.add(sourceTargetWeight);
		}
		
		return edgeArray;
	}

	public static List<NodeCluster> createClusters(CyJobData data, String clusterAttributeName, CyNetwork network) {
		JSONArray partitions = (JSONArray) data.get("partitions");
		
		List<NodeCluster> nodeClusters = new ArrayList<>();
		int i = 1;
		for (Object partition : partitions) {
			List<String> cluster = (ArrayList<String>) partition;
			List<CyNode> cyNodes = new ArrayList<>();
			for (String nodeName : cluster) {
				for (CyNode cyNode : network.getNodeList())
					if (network.getRow(cyNode).get(CyNetwork.NAME, String.class).equals(nodeName)) {
						cyNodes.add(cyNode);
					}
			}
			
			//how to get the CyNodes with their names?
			
			NodeCluster nodeCluster = new NodeCluster(i, cyNodes);
			nodeClusters.add(nodeCluster);
			i++;
		}
		return nodeClusters;
	}
	
	public List<List<CyNode>> createGroups(CyNetwork network, List<NodeCluster> clusters, String group_attr) {
		return createGroups(network, clusters, group_attr, clusterAttributeName, clusterManager, createGroups);
	}

	public static List<List<CyNode>> createGroups(CyNetwork network, List<NodeCluster> clusters, String group_attr, String clusterAttributeName, 
			ClusterManager clusterManager, Boolean createGroups) {
		
		List<List<CyNode>> clusterList = new ArrayList<List<CyNode>>(); // List of node lists
		List<Long>groupList = new ArrayList<Long>(); // keep track of the groups we create

		List<Double>clusterScores = new ArrayList<Double>(clusters.size());
		// Initialize
		for (NodeCluster cluster: clusters) {
			clusterScores.add(null);
		}
		boolean haveScores = NodeCluster.getScoreList(clusters) != null;

		// Remove the old column, if it's there.  Some of the algorithms don't put
		// all nodes into clusters, so we might wind up with old data lingering
		ModelUtils.deleteColumnLocal(network, CyNode.class, clusterAttributeName);

		for (NodeCluster cluster: clusters) {
			int clusterNumber = cluster.getClusterNumber();
			if (cluster.hasScore()) {
				clusterScores.set(clusterNumber-1, cluster.getClusterScore());
				haveScores = true;
			}
			String groupName = clusterAttributeName+"_"+clusterNumber;
			List<CyNode>nodeList = new ArrayList<CyNode>();

			for (CyNode node: cluster) {
				nodeList.add(node);
				ModelUtils.createAndSetLocal(network, node, clusterAttributeName, clusterNumber, Integer.class, null);
			}

			if (createGroups) {
        CyGroup group = clusterManager.createGroup(network, clusterAttributeName+"_"+clusterNumber, nodeList, null, true);
				if (group != null) {
					groupList.add(group.getGroupNode().getSUID());
					if (NodeCluster.hasScore()) {
						ModelUtils.createAndSetLocal(network, group.getGroupNode(), 
						                             clusterAttributeName+"_Score", cluster.getClusterScore(), Double.class, null);
					}
				}
			}
			clusterList.add(nodeList);
		}

		if (haveScores)
			ModelUtils.createAndSetLocal(network, network, clusterAttributeName+"_Scores", clusterScores, List.class, Double.class);

		ModelUtils.createAndSetLocal(network, network, GROUP_ATTRIBUTE, groupList, List.class, Long.class);

		ModelUtils.createAndSetLocal(network, network, ClusterManager.CLUSTER_TYPE_ATTRIBUTE, SHORTNAME, 
		                             String.class, null);
		ModelUtils.createAndSetLocal(network, network, ClusterManager.CLUSTER_ATTRIBUTE, clusterAttributeName, 
		                             String.class, null);
//		if (params != null)
//			ModelUtils.createAndSetLocal(network, network, ClusterManager.CLUSTER_PARAMS_ATTRIBUTE, params, 
//		                               List.class, String.class);

		return clusterList;
	}

}

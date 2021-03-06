package edu.ucsf.rbvi.clusterMaker2.internal.algorithms.dimensionalityReduction.umap;

import org.cytoscape.model.CyNetwork;
import org.cytoscape.work.ContainsTunables;
import org.cytoscape.work.Tunable;
import org.cytoscape.work.swing.TunableUIHelper;
import org.cytoscape.work.util.ListMultipleSelection;
import org.cytoscape.work.util.ListSingleSelection;

import edu.ucsf.rbvi.clusterMaker2.internal.algorithms.AdvancedProperties;
import edu.ucsf.rbvi.clusterMaker2.internal.algorithms.networkClusterers.NetworkVizProperties;
import edu.ucsf.rbvi.clusterMaker2.internal.utils.ModelUtils;

public class UMAPContext {

	CyNetwork network;
	TunableUIHelper helper;
	
	//Tunables
	
	
	public ListMultipleSelection<String> nodeAttributeList = null;
	@Tunable(description="Node attributes for cluster", groups="Array sources", 
	         longDescription="Select the node table columns to be used for calculating the cluster.  "+
	                         "Note that at least 2 node columns are usually required.",
	         exampleStringValue="gal1RGexp,gal4RGExp,gal80Rexp",
	         tooltip="You must choose at least 2 node columns for an attribute cluster", gravity=50 )
    public ListMultipleSelection<String> getnodeAttributeList() {
		if (network != null && nodeAttributeList == null)
			nodeAttributeList = ModelUtils.updateNodeAttributeList(network, nodeAttributeList);
        return nodeAttributeList;
    }
    public void setnodeAttributeList(ListMultipleSelection<String> nal) { }
	
	
	@Tunable(description = "Number of neighbors",
			longDescription = "This parameter controls how UMAP balances local versus "
			+ "global structure in the data. It does this by constraining the size of "
			+ "the local neighborhood UMAP will look at when attempting to learn the manifold "
			+ "structure of the data. This means that low values of n_neighbors will force UMAP "
			+ "to concentrate on very local structure (potentially to the detriment of the big picture), "
			+ "while large values will push UMAP to look at larger neighborhoods of each point when "
			+ "estimating the manifold structure of the data, losing fine detail structure for the sake "
			+ "of getting the broader of the data.",
			exampleStringValue = "2",
			groups = {"UMAP Advanced Settings"}, gravity = 1.0)
	public int n_neighbors = 2;
	
	@Tunable(description = "Minumum distance",
			longDescription = " The min_dist parameter controls how tightly UMAP is allowed to pack points together. "
			+ "It, quite literally, provides the minimum distance apart that points are allowed to be in the low "
			+ "dimensional representation. This means that low values of min_dist will result in clumpier embeddings. "
			+ "This can be useful if you are interested in clustering, or in finer topological structure. "
			+ "Larger values of min_dist will prevent UMAP from packing points together and will focus on the preservation "
			+ "of the broad topological structure instead.",
			exampleStringValue = "1.0",
			groups = {"UMAP Advanced Settings"}, gravity = 2.0)
	public double min_dist = 1.0;
	
	@Tunable(description = "Metric",
			longDescription = "The final UMAP parameter we will be considering in this notebook is the metric parameter. "
			+ "This controls how distance is computed in the ambient space of the input data. By default UMAP supports a wide variety of metrics, including:",
			exampleStringValue = "euclidean",
			groups = {"UMAP Advanced Settings"}, gravity = 3.0)
	public ListSingleSelection<String> metric = new ListSingleSelection<String>("euclidean", "manhattan", "chebyshev", "minkowski", "canberra", "braycurtis",
			"haversine", "mahalanobis", "wminkowski", "seuclidean", "cosine", "correlation", "hamming", "jaccard", "dice", "russellrao", "kulsinski", "rogerstanimoto",
			"sokalmichener", "sokalsneath", "yule");

	@Tunable(description = "Scale",
			longDescription = "true/false.  If true, preprocess the data to scale the matrix",
			exampleStringValue = "True",
			groups = {"UMAP Advanced Settings"}, gravity = 4.0)
    public Boolean scale = true;
	
	//@ContainsTunables
	//public AdvancedProperties advancedAttributes;

	//@ContainsTunables
	//public NetworkVizProperties vizProperties = new NetworkVizProperties();
	
	
	public UMAPContext() {
		// advancedAttributes = new AdvancedProperties("__umap", false); //this is the name of the column Integer that is created when click LOAD
	}

	public UMAPContext(UMAPContext origin) {
		// if (origin.advancedAttributes != null)
		// 	advancedAttributes = new AdvancedProperties(origin.advancedAttributes);
		// else
		// 	advancedAttributes = new AdvancedProperties("__umap", false);
		
		nodeAttributeList = origin.nodeAttributeList;
		n_neighbors = origin.n_neighbors;
		min_dist = origin.min_dist;
		metric = origin.metric;
		scale = origin.scale;
	}

	public void setNetwork(CyNetwork network) {
		if (this.network != null && this.network.equals(network))
			return;

		this.network = network;
    this.nodeAttributeList = null;
	}

	public CyNetwork getNetwork() { return network; }

	// public String getClusterAttribute() { return advancedAttributes.clusterAttribute;}

	public void setUIHelper(TunableUIHelper helper) {
		this.helper = helper;

	}
	
}

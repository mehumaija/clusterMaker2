package edu.ucsf.rbvi.clusterMaker2.internal.algorithms.networkClusterers.LeadingEigenVector;

	import java.util.Collections;
	import java.util.List;

	import org.cytoscape.service.util.CyServiceRegistrar;
	import org.cytoscape.work.TaskIterator;

	import edu.ucsf.rbvi.clusterMaker2.internal.algorithms.AbstractClusterTaskFactory;
	import edu.ucsf.rbvi.clusterMaker2.internal.algorithms.networkClusterers.LeadingEigenVector.LEVCluster;
	import edu.ucsf.rbvi.clusterMaker2.internal.algorithms.networkClusterers.LeadingEigenVector.LEVContext;
	import edu.ucsf.rbvi.clusterMaker2.internal.api.ClusterManager;
	import edu.ucsf.rbvi.clusterMaker2.internal.api.ClusterViz;

	public class LEVClusterTaskFactory extends AbstractClusterTaskFactory {
		LEVContext context = null;
		final CyServiceRegistrar registrar;
		
		public LEVClusterTaskFactory(ClusterManager clusterManager, CyServiceRegistrar registrar) {
			super(clusterManager);
			context = new LEVContext();
			this.registrar = registrar;
		}
		
		public String getName() {return LEVCluster.NAME;}
		
		public String getShortName() {return LEVCluster.SHORTNAME;}
		
		@Override
		public String getLongDescription() {
			return "";
		}

		@Override
		public ClusterViz getVisualizer() {
			return null;
		}

		@Override
		public List<ClusterType> getTypeList() {
			return Collections.singletonList(ClusterType.NETWORK);
		}

		@Override
		public TaskIterator createTaskIterator() {
			return new TaskIterator(new LEVCluster(context, clusterManager, registrar));
		}
	}


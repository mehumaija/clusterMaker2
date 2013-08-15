package edu.ucsf.rbvi.clusterMaker2.internal.algorithms.networkClusterers.FCM;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.lang.Math;

import org.cytoscape.model.CyColumn;
import org.cytoscape.model.CyEdge;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNode;
import org.cytoscape.model.CyNetworkManager;
import org.cytoscape.model.CyRow;
import org.cytoscape.model.CyTable;
import org.cytoscape.work.Tunable;
import org.cytoscape.work.TunableHandler;
import org.cytoscape.group.*;
import org.cytoscape.work.TaskMonitor;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import edu.ucsf.rbvi.clusterMaker2.internal.algorithms.FuzzyNodeCluster;
import edu.ucsf.rbvi.clusterMaker2.internal.algorithms.NodeCluster;
import edu.ucsf.rbvi.clusterMaker2.internal.algorithms.DistanceMatrix;
import edu.ucsf.rbvi.clusterMaker2.internal.algorithms.attributeClusterers.Matrix;
import edu.ucsf.rbvi.clusterMaker2.internal.algorithms.attributeClusterers.DistanceMetric;
import cern.colt.function.IntIntDoubleFunction;
import cern.colt.matrix.DoubleFactory2D;
import cern.colt.matrix.DoubleMatrix1D;
import cern.colt.matrix.DoubleMatrix2D;
import cern.colt.matrix.impl.SparseDoubleMatrix2D;

public class RunFCM {

	Random random = null;
	HashMap<String,List<CyNode>> groupMap = null;

	private int number_iterations; //number of inflation/expansion cycles
	private int number_clusters;
	private double findex; // fuzziness index
	private double beta; // Termination Criterion
	private DistanceMetric metric;
	private List<CyNode> nodes;
	private List<CyEdge> edges;
	private boolean canceled = false;
	private TaskMonitor monitor;
	public final static String GROUP_ATTRIBUTE = "__FCMGroups";
	protected int clusterCount = 0;
	private boolean createMetaNodes = false;
	private DistanceMatrix distanceMatrix = null;
	private DoubleMatrix2D matrix = null;
	private Matrix data = null;
	private boolean debug = false;
	private int nThreads = Runtime.getRuntime().availableProcessors()-1;
	
	public RunFCM (Matrix data,DistanceMatrix dMat, int num_iterations, int cClusters,DistanceMetric metric, double findex, double beta, TaskMonitor monitor ){
		
		this.distanceMatrix = dMat;
		this.data = data;
		this.number_iterations = num_iterations;
		this.number_clusters = cClusters;
		this.findex = findex;
		this.beta = beta;
		this.metric = metric;
		this.monitor = monitor;
		nodes = distanceMatrix.getNodes();
		edges = distanceMatrix.getEdges();
		this.matrix = distanceMatrix.getDistanceMatrix();
		/*
		if (maxThreads > 0)
			nThreads = maxThreads;
		else
			nThreads = Runtime.getRuntime().availableProcessors()-1;
			*/
	}
	
	public void cancel () { canceled = true; }
	
	public void halt () { canceled = true; }

	public void setDebug(boolean debug) { this.debug = debug; }
	
	/**
	 * The method run has the actual implementation of the fuzzy c-means code
	 * @param monitor, Task monitor for the process
	 * @return method returns a 2D array of cluster membership values
	 */
	public List<FuzzyNodeCluster> run(CyNetwork network, TaskMonitor monitor){
		
		Long networkID = network.getSUID();		

		CyTable netAttributes = network.getDefaultNetworkTable();	
		CyTable nodeAttributes = network.getDefaultNodeTable();			

		long startTime = System.currentTimeMillis();
		
		random = null;
		int nelements = data.nRows();
		
		//Matrix to store the temporary cluster membership values of elements 
		double [][] tClusterMemberships = new double[nelements][number_clusters];
		
		//Initializing all membership values to 0
		for (int i = 0; i < nelements; i++){
			for (int j = 0; j < number_clusters; j++){
				tClusterMemberships[i][j] = 0;
			}
		}
		
		// Matrix to store cluster memberships of the previous iteration
		double [][] prevClusterMemberships = new double[nelements][number_clusters];
		
		// This matrix will store the centroid data
		Matrix cData = new Matrix(number_clusters, data.nColumns());
		
		int iteration = 0;
		boolean end = false;
		do{
			
			if (monitor != null)
				monitor.setProgress(((double)iteration/(double)number_iterations));
			
			// Initializing the membership values by randomly assigning a cluster to each element
			if(iteration == 0 && number_iterations != 0){
				
				randomAssign(tClusterMemberships);
				prevClusterMemberships = tClusterMemberships;
				// Find the centers
				getFuzzyCenters(cData, tClusterMemberships);
			}
			
			//Calculate Fuzzy Memberships
			getClusterMemberships(cData,tClusterMemberships);
			
			// Now calculate the new fuzzy centers
			getFuzzyCenters(cData,tClusterMemberships);
			
			end = checkEndCriterion(tClusterMemberships,prevClusterMemberships);
			if (end){
				break;
			}
							
		}
		while (++iteration < number_iterations);
		
		HashMap <CyNode, double[]> membershipMap = createMembershipMap(tClusterMemberships);
		
		List<FuzzyNodeCluster> fuzzyClusters = new ArrayList<FuzzyNodeCluster>();
		
		List<CyNode> clusterNodes = new ArrayList<CyNode>();
		for (int j = 0; j<data.nRows();j++){
			clusterNodes.add(data.getRowNode(j));
		}
		
		for(int i = 0 ; i< number_clusters; i++){
			
			fuzzyClusters.add(new FuzzyNodeCluster(clusterNodes,membershipMap));
			
		}
		
		
		return fuzzyClusters;
	}
	
	/**
	 * The method getFuzzyCenters calculates the fuzzy centers from the cluster memberships and node attributes.
	 * 
	 *  @param cData is a matrix to store the attribute values for the fuzzy cluster centers
	 *  @param tClusterMemberships has the fuzzy membership values of elements for the clusters 
	 */
	
	public void getFuzzyCenters(Matrix cData, double [][] tClusterMemberships){
		
		// To store the sum of memberships(raised to fuzziness index) corresponding to each cluster
		double[] totalMemberships = new double [number_clusters];
		int nelements = data.nRows();
		
		//Calculating the total membership values
		for (int i = 0; i < number_clusters; i++){
			totalMemberships[i] = 0;
			for(int j = 0; j < nelements; j++ ){
				totalMemberships[i] += Math.pow(tClusterMemberships[j][i],findex);
			}
						
		}
		
		for(int c= 0 ; c < number_clusters; c++){
			
			for(int d = 0; d <  data.nColumns(); d++ ){
				double numerator = 0;
				for (int e = 0; e < nelements; e++){
					numerator += Math.pow(tClusterMemberships[e][c],findex) * data.getValue(e,d).doubleValue();
					
				}
				
				cData.setValue(c,d,( numerator/totalMemberships[c]));
			}
		}
		
	}
	
	/**
	 * The method getClusterMemberships calculates the new cluster memberships of elements
	 * 
	 * @param cData is a matrix has the attribute values for the fuzzy cluster centers
	 * @param the new fuzzy membership values of elements for the clusters will be stored in tClusterMemberships
	 */
	
	public void getClusterMemberships(Matrix cData, double [][]tClusterMemberships){
		
		int nelements = data.nRows();
		double fpower = 2/(findex - 1);
		
		
		for (int i = 0; i < nelements; i++) {
			
			double distance_ic;
			for(int c = 0; c < number_clusters; c++){
				double sumDistanceRatios = 0;
				double distance_ik;
				distance_ic = metric.getMetric(data, cData, data.getWeights(), i, c);
				
				for(int k = 0; k < number_clusters; k++){
					
					distance_ik = metric.getMetric(data, cData, data.getWeights(), i, k);
					sumDistanceRatios += Math.pow((distance_ic/distance_ik), fpower);
					
				}
				
				tClusterMemberships[i][c] = 1/sumDistanceRatios;
				
			}
			
		}
			
	}
	
	/**
	 * The method checkEndCriterion checks whether the maximum change in the cluster membership values is less than beta or not
	 * 
	 * @param tClusterMemberships has the fuzzy membership values of the current iteration
	 * @param prevClusterMemberships has the fuzzy membership values of the last iteration
	 * @return endCheck is true if the maximum change in membership values is less than beta, false otherwise.
	 */
	
	public boolean checkEndCriterion(double[][] tClusterMemberships,double[][] prevClusterMemberships){
		
		boolean endCheck = false;
		
		double[][] differences = new double [data.nRows()][number_clusters] ;
		double maxdiff = -1;
		for (int i = 0; i < data.nRows(); i++){
			
			for (int j = 0; j < number_clusters; j++){
				
				differences[i][j] = Math.abs( tClusterMemberships[i][j] - prevClusterMemberships[i][j]);
				
				if (differences[i][j] > maxdiff){
					maxdiff = differences[i][j];
				}
			}
		}
		
		if( maxdiff != -1 && maxdiff < beta){
			endCheck = true;
		}
		
		return endCheck;
	}
	
	/**
	 *  randomAssign assigns cluster memberships randomly for the purpose of initialization. 
	 *  
	 *  @param tClusterMemberships is the 2D array to store the membership values
	 */
	private void randomAssign(double[][] tClusterMemberships){
		
		Random randomGenerator = new Random();
		
		for(int i = 0; i < tClusterMemberships.length; i++){
			double sum = 0;
			//Randomly assign a membership value to each element for every cluster
			for(int j = 0; j < number_clusters; j++){
				double temp = randomGenerator.nextInt(100);
				//temp = Math.random();
				sum += temp;
				tClusterMemberships[i][j] = temp;
			}
			
			for(int k = 0; k < number_clusters ; k++ ){
				tClusterMemberships[i][k] /= sum;
			}
			
		}		
	}
	
	/**
	 * The method createMembershipMap creates a map from Nodes in the network to an array
	 *  of membership values corresponding to the various clusters.
	 *  
	 * @param membershipArray a 2D array of membership values
	 * @return membershipHM the Map from CyNodes to their membership value arrays
	 */
	public HashMap <CyNode, double[]> createMembershipMap(double[][] membershipArray){
		
		HashMap<CyNode, double[]> membershipHM = new HashMap<CyNode, double[]>();
		
		for ( int i = 0; i<data.nRows(); i++){
			
			membershipHM.put(data.getRowNode(i), membershipArray[i]);
		}
		
		return membershipHM;
	}

}






/**
 * Copyright (C) 2007-2011, Jens Lehmann
 *
 * This file is part of DL-Learner.
 *
 * DL-Learner is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * DL-Learner is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.dllearner.algorithms.celoe;

import java.io.File;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.FileAppender;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.SimpleLayout;
import org.dllearner.core.AbstractCELA;
import org.dllearner.core.AbstractKnowledgeSource;
import org.dllearner.core.AbstractLearningProblem;
import org.dllearner.core.AbstractReasonerComponent;
import org.dllearner.core.ComponentAnn;
import org.dllearner.core.ComponentInitException;
import org.dllearner.core.EvaluatedDescription;
import org.dllearner.core.config.ConfigOption;
import org.dllearner.core.owl.ClassHierarchy;
import org.dllearner.core.owl.Description;
import org.dllearner.core.owl.Individual;
import org.dllearner.core.owl.Intersection;
import org.dllearner.core.owl.NamedClass;
import org.dllearner.core.owl.Restriction;
import org.dllearner.core.owl.Thing;
import org.dllearner.kb.OWLFile;
import org.dllearner.learningproblems.ClassLearningProblem;
import org.dllearner.learningproblems.PosNegLP;
import org.dllearner.learningproblems.PosNegLPStandard;
import org.dllearner.learningproblems.PosOnlyLP;
import org.dllearner.reasoning.FastInstanceChecker;
import org.dllearner.refinementoperators.OperatorInverter;
import org.dllearner.refinementoperators.RefinementOperator;
import org.dllearner.refinementoperators.RhoDRDown;
import org.dllearner.utilities.Files;
import org.dllearner.utilities.Helper;
import org.dllearner.utilities.owl.ConceptComparator;
import org.dllearner.utilities.owl.ConceptTransformation;
import org.dllearner.utilities.owl.DescriptionMinimizer;
import org.dllearner.utilities.owl.EvaluatedDescriptionSet;
import org.dllearner.utilities.owl.PropertyContext;
import org.springframework.beans.factory.annotation.Autowired;

import com.jamonapi.Monitor;
import com.jamonapi.MonitorFactory;

/**
 * The CELOE (Class Expression Learner for Ontology Engineering) algorithm.
 * It adapts and extends the standard supervised learning algorithm for the
 * ontology engineering use case. 
 * 
 * @author Jens Lehmann
 *
 */
@ComponentAnn(name="PCELOE", shortName="pceloe", version=1.0, description="CELOE is an adapted and extended version of the OCEL algorithm applied for the ontology engineering use case. See http://jens-lehmann.org/files/2011/celoe.pdf for reference.")
public class PCELOE extends AbstractCELA {
	
	//parameters for thread pool
		//Parallel running Threads(Executor) on System
		private static int corePoolSize = 5;
		//Maximum Threads allowed in Pool
		private static int maximumPoolSize = 20;
		//Keep alive time for waiting threads for jobs(Runnable)
		private static long keepAliveTime = 10;
	

	private static Logger logger = Logger.getLogger(PCELOE.class);
//	private CELOEConfigurator configurator;
	
	private boolean isRunning = false;
	private boolean stop = false;	
	
//	private OEHeuristicStable heuristicStable = new OEHeuristicStable();
//	private OEHeuristicRuntime heuristicRuntime = new OEHeuristicRuntime();
	
	private RefinementOperator operator;
	private DescriptionMinimizer minimizer;
	@ConfigOption(name="useMinimizer", defaultValue="true", description="Specifies whether returned expressions should be minimised by removing those parts, which are not needed. (Basically the minimiser tries to find the shortest expression which is equivalent to the learned expression). Turning this feature off may improve performance.")
	private boolean useMinimizer = true;
	
	// all nodes in the search tree (used for selecting most promising node)
	private TreeSet<OENode> nodes;
	private OEHeuristicRuntime heuristic; // = new OEHeuristicRuntime();
	// root of search tree
	private OENode startNode;
	// the class with which we start the refinement process
	@ConfigOption(name = "startClass", defaultValue="owl:Thing", description="You can specify a start class for the algorithm. To do this, you have to use Manchester OWL syntax without using prefixes.")
	private Description startClass;
	
	// all descriptions in the search tree plus those which were too weak (for fast redundancy check)
	private TreeSet<Description> descriptions;
	
	private EvaluatedDescriptionSet bestEvaluatedDescriptions;
	
	// if true, then each solution is evaluated exactly instead of approximately
	// private boolean exactBestDescriptionEvaluation = false;
	@ConfigOption(name = "singleSuggestionMode", defaultValue="false", description="Use this if you are interested in only one suggestion and your learning problem has many (more than 1000) examples.")
	private boolean singleSuggestionMode;
	private Description bestDescription;
	private double bestAccuracy = Double.MIN_VALUE;
	
	private NamedClass classToDescribe;
	// examples are either 1.) instances of the class to describe 2.) positive examples
	// 3.) union of pos.+neg. examples depending on the learning problem at hand
	private Set<Individual> examples;
	
	// CELOE was originally created for learning classes in ontologies, but also
	// works for other learning problem types
	private boolean isClassLearningProblem;
	private boolean isEquivalenceProblem;
	
	private long nanoStartTime;
	
	// important parameters (non-config options but internal)
	private double noise;

	private boolean filterFollowsFromKB;	
	
	// less important parameters
	// forces that one solution cannot be subexpression of another expression; this option is useful to get diversity
	// but it can also suppress quite useful expressions
	private boolean forceMutualDifference = false;
	
	// utility variables
	private String baseURI;
	private Map<String, String> prefixes;
	private DecimalFormat dfPercent = new DecimalFormat("0.00%");
	private ConceptComparator descriptionComparator = new ConceptComparator();
	
	// statistical variables
	private int expressionTests = 0;
	private int minHorizExp = 0;
	private int maxHorizExp = 0;
	
	// TODO: turn those into config options
	
	// important: do not initialise those with empty sets
	// null = no settings for allowance / ignorance
	// empty set = allow / ignore nothing (it is often not desired to allow no class!)
	Set<NamedClass> allowedConcepts = null;
	Set<NamedClass> ignoredConcepts = null;

	@ConfigOption(name = "writeSearchTree", defaultValue="false", description="specifies whether to write a search tree")
	private boolean writeSearchTree = false;

	@ConfigOption(name = "searchTreeFile", defaultValue="log/searchTree.txt", description="file to use for the search tree")
	private String searchTreeFile = "log/searchTree.txt";

	@ConfigOption(name = "replaceSearchTree", defaultValue="false", description="specifies whether to replace the search tree in the log file after each run or append the new search tree")
	private boolean replaceSearchTree = false;
	
	@ConfigOption(name = "maxNrOfResults", defaultValue="10", description="Sets the maximum number of results one is interested in. (Setting this to a lower value may increase performance as the learning algorithm has to store/evaluate/beautify less descriptions).")	
	private int maxNrOfResults = 10;

	@ConfigOption(name = "noisePercentage", defaultValue="0.0", description="the (approximated) percentage of noise within the examples")
	private double noisePercentage = 0.0;

	@ConfigOption(name = "filterDescriptionsFollowingFromKB", defaultValue="false", description="If true, then the results will not contain suggestions, which already follow logically from the knowledge base. Be careful, since this requires a potentially expensive consistency check for candidate solutions.")
	private boolean filterDescriptionsFollowingFromKB = false;

	@ConfigOption(name = "reuseExistingDescription", defaultValue="false", description="If true, the algorithm tries to find a good starting point close to an existing definition/super class of the given class in the knowledge base.")
	private boolean reuseExistingDescription = false;

	@ConfigOption(name = "maxClassDescriptionTests", defaultValue="0", description="The maximum number of candidate hypothesis the algorithm is allowed to test (0 = no limit). The algorithm will stop afterwards. (The real number of tests can be slightly higher, because this criterion usually won't be checked after each single test.)")
	private int maxClassDescriptionTests = 0;

	@ConfigOption(defaultValue = "10", name = "maxExecutionTimeInSeconds", description = "maximum execution of the algorithm in seconds")
	private int maxExecutionTimeInSeconds = 10;

	@ConfigOption(name = "terminateOnNoiseReached", defaultValue="false", description="specifies whether to terminate when noise criterion is met")
	private boolean terminateOnNoiseReached = false;
	
	@ConfigOption(name = "maxDepth", defaultValue="7", description="maximum depth of description")
	private double maxDepth = 7;
	
	private Set<OENode> currentlyProcessedNodes = Collections.synchronizedSet(new HashSet<OENode>());
	
//	public CELOEConfigurator getConfigurator() {
//		return configurator;
//	}
	
	public PCELOE() {
		
	}
	
	public PCELOE(AbstractLearningProblem problem, AbstractReasonerComponent reasoner) {
		super(problem, reasoner);
//		configurator = new CELOEConfigurator(this);
	}

//	public static Collection<Class<? extends AbstractLearningProblem>> supportedLearningProblems() {
//		Collection<Class<? extends AbstractLearningProblem>> problems = new LinkedList<Class<? extends AbstractLearningProblem>>();
//		problems.add(AbstractLearningProblem.class);
//		return problems;
//	}
	
	public static String getName() {
		return "CELOE";
	}
	
	@Override
	public void init() throws ComponentInitException {
			
		// compute used concepts/roles from allowed/ignored
		// concepts/roles
		Set<NamedClass> usedConcepts;
//		Set<NamedClass> allowedConcepts = configurator.getAllowedConcepts()==null ? null : CommonConfigMappings.getAtomicConceptSet(configurator.getAllowedConcepts());
//		Set<NamedClass> ignoredConcepts = configurator.getIgnoredConcepts()==null ? null : CommonConfigMappings.getAtomicConceptSet(configurator.getIgnoredConcepts());
		if(allowedConcepts != null) {
			// sanity check to control if no non-existing concepts are in the list
			Helper.checkConcepts(reasoner, allowedConcepts);
			usedConcepts = allowedConcepts;
		} else if(ignoredConcepts != null) {
			usedConcepts = Helper.computeConceptsUsingIgnoreList(reasoner, ignoredConcepts);
		} else {
			usedConcepts = Helper.computeConcepts(reasoner);
		}
		
		// copy class hierarchy and modify it such that each class is only
		// reachable via a single path
//		ClassHierarchy classHierarchy = reasoner.getClassHierarchy().clone();
		ClassHierarchy classHierarchy = reasoner.getClassHierarchy().cloneAndRestrict(usedConcepts);
		classHierarchy.thinOutSubsumptionHierarchy();

		// if no one injected a heuristic, we use a default one
		if(heuristic == null) {
			heuristic = new OEHeuristicRuntime();
		}
		
		minimizer = new DescriptionMinimizer(reasoner);
		
		// start at owl:Thing by default
		if(startClass == null) {
			startClass = Thing.instance;
		}
		
//		singleSuggestionMode = configurator.getSingleSuggestionMode();
		/*
		// create refinement operator
		if(operator == null) {
			operator = new RhoDRDown();
			((RhoDRDown)operator).setStartClass(startClass);
			((RhoDRDown)operator).setReasoner(reasoner);
		}
		((RhoDRDown)operator).setSubHierarchy(classHierarchy);
		((RhoDRDown)operator).setObjectPropertyHierarchy(reasoner.getObjectPropertyHierarchy());
		((RhoDRDown)operator).setDataPropertyHierarchy(reasoner.getDatatypePropertyHierarchy());
		((RhoDRDown)operator).init();		
		*/
		// create a refinement operator and pass all configuration
		// variables to it
		if(operator == null) {
			// we use a default operator and inject the class hierarchy for now
			operator = new RhoDRDown();
			((RhoDRDown)operator).setStartClass(startClass);
			((RhoDRDown)operator).setReasoner(reasoner);
			((RhoDRDown)operator).init();
		}
		// TODO: find a better solution as this is quite difficult to debug
		((RhoDRDown)operator).setSubHierarchy(classHierarchy);
		((RhoDRDown)operator).setObjectPropertyHierarchy(reasoner.getObjectPropertyHierarchy());
		((RhoDRDown)operator).setDataPropertyHierarchy(reasoner.getDatatypePropertyHierarchy());
		
		
//		operator = new RhoDRDown(reasoner, classHierarchy, startClass, configurator);
		baseURI = reasoner.getBaseURI();
		prefixes = reasoner.getPrefixes();		
		if(writeSearchTree) {
			File f = new File(searchTreeFile );
			Files.clearFile(f);
		}
		
		bestEvaluatedDescriptions = new EvaluatedDescriptionSet(maxNrOfResults);
		
		isClassLearningProblem = (learningProblem instanceof ClassLearningProblem);
		
		// we put important parameters in class variables
		noise = noisePercentage/100d;
//		System.out.println("noise " + noise);
//		maxDepth = configurator.getMaxDepth();
		// (filterFollowsFromKB is automatically set to false if the problem
		// is not a class learning problem
		filterFollowsFromKB = filterDescriptionsFollowingFromKB && isClassLearningProblem;
		
//		Set<Description> concepts = operator.refine(Thing.instance, 5);
//		for(Description concept : concepts) {
//			System.out.println(concept);
//		}
//		System.out.println("refinements of thing: " + concepts.size());
		
		// actions specific to ontology engineering
		if(isClassLearningProblem) {
			ClassLearningProblem problem = (ClassLearningProblem) learningProblem;
			classToDescribe = problem.getClassToDescribe();
			isEquivalenceProblem = problem.isEquivalenceProblem();
			
			examples = reasoner.getIndividuals(classToDescribe);
			
			// start class: intersection of super classes for definitions (since it needs to
			// capture all instances), but owl:Thing for learning subclasses (since it is
			// superfluous to add super classes in this case)
			if(isEquivalenceProblem) {
				Set<Description> existingDefinitions = reasoner.getAssertedDefinitions(classToDescribe);
				if(reuseExistingDescription && (existingDefinitions.size() > 0)) {
					// the existing definition is reused, which in the simplest case means to
					// use it as a start class or, if it is already too specific, generalise it
					
					// pick the longest existing definition as candidate
					Description existingDefinition = null;
					int highestLength = 0;
					for(Description exDef : existingDefinitions) {
						if(exDef.getLength() > highestLength) {
							existingDefinition = exDef;
							highestLength = exDef.getLength();
						}
					}
					
					LinkedList<Description> startClassCandidates = new LinkedList<Description>();
					startClassCandidates.add(existingDefinition);
					((RhoDRDown)operator).setDropDisjuncts(true);
					RefinementOperator upwardOperator = new OperatorInverter(operator);
					
					// use upward refinement until we find an appropriate start class
					boolean startClassFound = false;
					Description candidate;
					do {
						candidate = startClassCandidates.pollFirst();
						if(((ClassLearningProblem)learningProblem).getRecall(candidate)<1.0) {
							// add upward refinements to list
							Set<Description> refinements = upwardOperator.refine(candidate, candidate.getLength());
//							System.out.println("ref: " + refinements);
							LinkedList<Description> refinementList = new LinkedList<Description>(refinements);
//							Collections.reverse(refinementList);
//							System.out.println("list: " + refinementList);
							startClassCandidates.addAll(refinementList);
//							System.out.println("candidates: " + startClassCandidates);
						} else {
							startClassFound = true;
						}
					} while(!startClassFound);
					startClass = candidate;
					
					if(startClass.equals(existingDefinition)) {
						logger.info("Reusing existing description " + startClass.toManchesterSyntaxString(baseURI, prefixes) + " as start class for learning algorithm.");
					} else {
						logger.info("Generalised existing description " + existingDefinition.toManchesterSyntaxString(baseURI, prefixes) + " to " + startClass.toManchesterSyntaxString(baseURI, prefixes) + ", which is used as start class for the learning algorithm.");
					}
					
//					System.out.println("start class: " + startClass);
//					System.out.println("existing def: " + existingDefinition);
//					System.out.println(reasoner.getIndividuals(existingDefinition));
					
					((RhoDRDown)operator).setDropDisjuncts(false);
					
				} else {
					Set<Description> superClasses = reasoner.getClassHierarchy().getSuperClasses(classToDescribe);
					if(superClasses.size() > 1) {
						startClass = new Intersection(new LinkedList<Description>(superClasses));
					} else if(superClasses.size() == 1){
						startClass = (Description) superClasses.toArray()[0];
					} else {
						startClass = Thing.instance;
						logger.warn(classToDescribe + " is equivalent to owl:Thing. Usually, it is not " +
								"sensible to learn a description in this case.");
					}					
				}
			}				
		} else if(learningProblem instanceof PosOnlyLP) {
			examples = ((PosOnlyLP)learningProblem).getPositiveExamples();
		} else if(learningProblem instanceof PosNegLP) {
			examples = Helper.union(((PosNegLP)learningProblem).getPositiveExamples(),((PosNegLP)learningProblem).getNegativeExamples());
		}
	}

	@Override
	public Description getCurrentlyBestDescription() {
		EvaluatedDescription ed = getCurrentlyBestEvaluatedDescription();
		return ed == null ? null : ed.getDescription();
	}

	@Override
	public List<Description> getCurrentlyBestDescriptions() {
		return bestEvaluatedDescriptions.toDescriptionList();
	}
	
	@Override
	public EvaluatedDescription getCurrentlyBestEvaluatedDescription() {
		return bestEvaluatedDescriptions.getBest();
	}	
	
	@Override
	public TreeSet<? extends EvaluatedDescription> getCurrentlyBestEvaluatedDescriptions() {
		return bestEvaluatedDescriptions.getSet();
	}	
	
	public double getCurrentlyBestAccuracy() {
		return bestEvaluatedDescriptions.getBest().getAccuracy();
	}
	
	@Override
	public void start() {
//		System.out.println(configurator.getMaxExecutionTimeInSeconds());
		
		stop = false;
		isRunning = true;
		reset();
		nanoStartTime = System.nanoTime();
		
		addNode(startClass, null);
		
		int nrOfThreads = Runtime.getRuntime().availableProcessors();
		nrOfThreads = 8;//only for tests TODO make number of threads configurable
		ExecutorService service = Executors.newFixedThreadPool(nrOfThreads);
		
		List<Runnable> tasks = new ArrayList<Runnable>();
		
		for(int i = 0; i < nrOfThreads; i++){
			RhoDRDown operator = new RhoDRDown();
			operator.setStartClass(startClass);
			operator.setReasoner(reasoner);
			try {
				operator.init();
			} catch (ComponentInitException e) {
				e.printStackTrace();
			}
			operator.setSubHierarchy(reasoner.getClassHierarchy().clone());
			operator.setObjectPropertyHierarchy(reasoner.getObjectPropertyHierarchy());
			operator.setDataPropertyHierarchy(reasoner.getDatatypePropertyHierarchy());
			
			tasks.add(new Worker(operator));
		}
		
		for(Runnable task : tasks){
			service.submit(task);
		}
		
		try {
			service.awaitTermination(maxExecutionTimeInSeconds, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		if(singleSuggestionMode) {
			bestEvaluatedDescriptions.add(bestDescription, bestAccuracy, learningProblem);
		}	
		
		if (stop) {
			logger.info("Algorithm stopped ("+expressionTests+" descriptions tested). " + nodes.size() + " nodes in the search tree.\n");
		} else {
			logger.info("Algorithm terminated successfully ("+expressionTests+" descriptions tested). "  + nodes.size() + " nodes in the search tree.\n");
            logger.info(reasoner.toString());
		}
		
		// print solution(s)
		
//		System.out.println("isRunning: " + isRunning);
		
		logger.info("solutions:\n" + getSolutionString());
		
//		System.out.println(startNode.toTreeString(baseURI));
		
		isRunning = false;
		service.shutdown();
		
	}

	synchronized private OENode getNextNodeToExpand() {
		// we expand the best node of those, which have not achieved 100% accuracy
		// already and have a horizontal expansion equal to their length
		// (rationale: further extension is likely to add irrelevant syntactical constructs)
		synchronized (nodes) {logger.info("in 1.lock of getNextNodeToExpand method");
		Iterator<OENode> it = nodes.descendingIterator();//logger.info(nodes.size());
		logger.info("search tree size: " + nodes.size());
			while(it.hasNext()) {
				OENode node = it.next();
				logger.info("Checking node " + node + "...");
				if(!currentlyProcessedNodes.contains(node) && (node.getAccuracy() < 1.0 || node.getHorizontalExpansion() < node.getDescription().getLength())) {
					currentlyProcessedNodes.add(node);
					return node;
				}
				logger.info("...checked.");
			}
		}
		
		
		// this should practically never be called, since for any reasonable learning
		// task, we will always have at least one node with less than 100% accuracy
		return null;
	}
	
	// expand node horizontically
	private TreeSet<Description> refineNode(OENode node, RhoDRDown operator) {
		// we have to remove and add the node since its heuristic evaluation changes through the expansion
		// (you *must not* include any criteria in the heuristic which are modified outside of this method,
		// otherwise you may see rarely occurring but critical false ordering in the nodes set)
		synchronized (nodes) {
			nodes.remove(node);
		}
		
//		System.out.println("refining: " + node);
		int horizExp = node.getHorizontalExpansion();
//		TreeSet<Description> refinements = (TreeSet<Description>) operator.refine(node.getDescription(), horizExp+1);
		TreeSet<Description> refinements = (TreeSet<Description>) operator.refine(node.getDescription(), horizExp+1);
		node.incHorizontalExpansion();
		node.setRefinementCount(refinements.size());
		synchronized (refinements) {
			nodes.add(node);
		}
		
		return refinements;
	}
	
	// add node to search tree if it is not too weak
	// returns true if node was added and false otherwise
	private boolean addNode(Description description, OENode parentNode) {
		
//		System.out.println("d: " + description);
		
		// redundancy check (return if redundant)
		boolean nonRedundant = descriptions.add(description);
		if(!nonRedundant) {
			return false;
		}
		logger.info("Check if description is allowed...");
		// check whether the description is allowed
		if(!isDescriptionAllowed(description, parentNode)) {
			return false;
		}
		logger.info("...done");
		
//		System.out.println("Test " + new Date());
		// quality of description (return if too weak)
		double accuracy = learningProblem.getAccuracyOrTooWeak(description, noise);
		logger.info("Accuracy: " + accuracy);
		// issue a warning if accuracy is not between 0 and 1 or -1 (too weak)
		if(accuracy > 1.0 || (accuracy < 0.0 && accuracy != -1)) {
			logger.warn("Invalid accuracy value " + accuracy + " for description " + description + ". This could be caused by a bug in the heuristic measure and should be reported to the DL-Learner bug tracker.");
			System.exit(0);
		}
		
//		System.out.println("Test2 " + new Date());
		expressionTests++;
//		System.out.println("acc: " + accuracy);
//		System.out.println(description + " " + accuracy);
		if(accuracy == -1) {
			return false;
		}
		
		OENode node = new OENode(parentNode, description, accuracy);
			
		// link to parent (unless start node)
		if(parentNode == null) {
			startNode = node;
		} else {
			parentNode.addChild(node);
		}
	
		nodes.add(node);
//		System.out.println("Test3 " + new Date());
		
		// in some cases (e.g. mutation) fully evaluating even a single description is too expensive
		// due to the high number of examples -- so we just stick to the approximate accuracy
		if(singleSuggestionMode) {
			if(accuracy > bestAccuracy) {
				bestAccuracy = accuracy;
				bestDescription = description;
				logger.info("more accurate (" + dfPercent.format(bestAccuracy) + ") class expression found: " + descriptionToString(bestDescription)); // + getTemporaryString(bestDescription)); 
			}
			return true;
		} 
		
//		System.out.println("description " + description + " accuracy " + accuracy);
		
		// maybe add to best descriptions (method keeps set size fixed);
		// we need to make sure that this does not get called more often than
		// necessary since rewriting is expensive
		boolean isCandidate = !bestEvaluatedDescriptions.isFull();
		logger.info("Is candidate:" + isCandidate);
		if(!isCandidate) {
			EvaluatedDescription worst = bestEvaluatedDescriptions.getWorst();
			double accThreshold = worst.getAccuracy();
			isCandidate = 
				(accuracy > accThreshold ||
				(accuracy >= accThreshold && description.getLength() < worst.getDescriptionLength()));
		}
		
//		System.out.println(isCandidate);
		
//		System.out.println("Test4 " + new Date());
		if(isCandidate) {
			
			Description niceDescription = rewriteNode(node);
			ConceptTransformation.transformToOrderedForm(niceDescription, descriptionComparator);
//			Description niceDescription = node.getDescription();
			
			// another test: none of the other suggested descriptions should be 
			// a subdescription of this one unless accuracy is different
			// => comment: on the one hand, this appears to be too strict, because once A is a solution then everything containing
			// A is not a candidate; on the other hand this suppresses many meaningless extensions of A
			boolean shorterDescriptionExists = false;
			if(forceMutualDifference) {
				synchronized (bestEvaluatedDescriptions) {
					for(EvaluatedDescription ed : bestEvaluatedDescriptions.getSet()) {
						if(Math.abs(ed.getAccuracy()-accuracy) <= 0.00001 && ConceptTransformation.isSubdescription(niceDescription, ed.getDescription())) {
//							System.out.println("shorter: " + ed.getDescription());
							shorterDescriptionExists = true;
							break;
						}
					}	
				}
							
			}
			logger.info("Point 2");
			
//			System.out.println("shorter description? " + shorterDescriptionExists + " nice: " + niceDescription);
			filterFollowsFromKB = false;
			if(!shorterDescriptionExists) {
				if(!filterFollowsFromKB || !((ClassLearningProblem)learningProblem).followsFromKB(niceDescription)) {
//					System.out.println("Test2");
					synchronized (bestEvaluatedDescriptions) {
						bestEvaluatedDescriptions.add(niceDescription, accuracy, learningProblem);
					}
					
//					System.out.println("acc: " + accuracy);
//					System.out.println(bestEvaluatedDescriptions);
				}
			}
			logger.info("Point 3");
						
//			System.out.println(bestEvaluatedDescriptions.getSet().size());
		}
		
//		System.out.println("Test5 " + new Date());
//		System.out.println("best evaluated descriptions size: " + bestEvaluatedDescriptions.size() + " worst: " + bestEvaluatedDescriptions.getWorst());
		return true;
	}	
	
	// checks whether the description is allowed
	private boolean isDescriptionAllowed(Description description, OENode parentNode) {
		if(isClassLearningProblem) {
			if(isEquivalenceProblem) {
				// the class to learn must not appear on the outermost property level
				if(occursOnFirstLevel(description, classToDescribe)) {
					return false;
				}
			} else {
				// none of the superclasses of the class to learn must appear on the
				// outermost property level
				TreeSet<Description> toTest = new TreeSet<Description>(descriptionComparator);
				toTest.add(classToDescribe);
				while(!toTest.isEmpty()) {
					Description d = toTest.pollFirst();
					if(occursOnFirstLevel(description, d)) {
						return false;
					}
					toTest.addAll(reasoner.getClassHierarchy().getSuperClasses(d));
				}
			}			
		}
		
		// perform forall sanity tests
		if(parentNode != null && ConceptTransformation.getForallOccurences(description) > ConceptTransformation.getForallOccurences(parentNode.getDescription())) {
			// we have an additional \forall construct, so we now fetch the contexts
			// in which it occurs
			SortedSet<PropertyContext> contexts = ConceptTransformation.getForallContexts(description);
			SortedSet<PropertyContext> parentContexts = ConceptTransformation.getForallContexts(parentNode.getDescription());
			contexts.removeAll(parentContexts);
//			System.out.println("parent description: " + parentNode.getDescription());
//			System.out.println("description: " + description);
//			System.out.println("contexts: " + contexts);
			// we now have to perform sanity checks: if \forall is used, then there
			// should be at least on class instance which has a filler at the given context
			for(PropertyContext context : contexts) {
				// transform [r,s] to \exists r.\exists s.\top
				Description existentialContext = context.toExistentialContext();
				boolean fillerFound = false;
				for(Individual instance : examples) {
					if(reasoner.hasType(existentialContext, instance)) {
//						System.out.println(instance + "  " + existentialContext);
						fillerFound = true;
						break;
					}
				}
				// if we do not find a filler, this means that putting \forall at
				// that position is not meaningful
				if(!fillerFound) {
					return false;
				}
			}
		}		
		
		// we do not want to have negations of sibling classes on the outermost level
		// (they are expressed more naturally by saying that the siblings are disjoint,
		// so it is reasonable not to include them in solutions)
//		Set<Description> siblingClasses = reasoner.getClassHierarchy().getSiblingClasses(classToDescribe);
//		for now, we just disable negation
		
		return true;
	}
	
	// determine whether a named class occurs on the outermost level, i.e. property depth 0
	// (it can still be at higher depth, e.g. if intersections are nested in unions)
	private boolean occursOnFirstLevel(Description description, Description clazz) {
		if(description instanceof NamedClass) {
			if(description.equals(clazz)) {
				return true;
			}
		} 
		
		if(description instanceof Restriction) {
			return false;
		}
		
		for(Description child : description.getChildren()) {
			if(occursOnFirstLevel(child, clazz)) {
				return true;
			}
		}
		
		return false;
	}
	
	// check whether the node is a potential solution candidate
	private Description rewriteNode(OENode node) {
		Description description = node.getDescription();
		// minimize description (expensive!) - also performes some human friendly rewrites
		Description niceDescription;
		if(useMinimizer) {
			niceDescription = minimizer.minimizeClone(description);
		} else {
			niceDescription = description;
		}
		// replace \exists r.\top with \exists r.range(r) which is easier to read for humans
		ConceptTransformation.replaceRange(niceDescription, reasoner);
		return niceDescription;
	}
	
	private boolean terminationCriteriaSatisfied() {
		boolean ret = stop || 
				(maxClassDescriptionTests != 0 && (expressionTests >= maxClassDescriptionTests)) ||
				(maxExecutionTimeInSeconds != 0 && ((System.nanoTime() - nanoStartTime) >= (maxExecutionTimeInSeconds*1000000000l))) ||
				(terminateOnNoiseReached && (100*getCurrentlyBestAccuracy()>=100-noisePercentage));
		logger.info("terminate: " + ret);
		return ret;
		
	}
	
	private void reset() {
		// set all values back to their default values (used for running
		// the algorithm more than once)
		nodes = new TreeSet<OENode>(heuristic);
		descriptions = new TreeSet<Description>(new ConceptComparator());
		bestEvaluatedDescriptions.getSet().clear();
		expressionTests = 0;
	}
	
	@Override
	public boolean isRunning() {
		return isRunning;
	}	
	
	@Override
	public void stop() {
		stop = true;
	}

	public OENode getSearchTreeRoot() {
		return startNode;
	}
	
	// central function for printing description
	private String descriptionToString(Description description) {
		return description.toManchesterSyntaxString(baseURI, prefixes);
	}
	
	@SuppressWarnings("unused")
	private String bestDescriptionToString() {
		EvaluatedDescription best = bestEvaluatedDescriptions.getBest();
		return best.getDescription().toManchesterSyntaxString(baseURI, prefixes) + " (accuracy: " + dfPercent.format(best.getAccuracy()) + ")";
	}	
	
	private String getSolutionString() {
		int current = 1;
		String str = "";
		for(EvaluatedDescription ed : bestEvaluatedDescriptions.getSet().descendingSet()) {
			// temporary code
			if(learningProblem instanceof PosNegLPStandard) {
				str += current + ": " + descriptionToString(ed.getDescription()) + " (pred. acc.: " + dfPercent.format(((PosNegLPStandard)learningProblem).getPredAccuracyOrTooWeakExact(ed.getDescription(),1)) + ", F-measure: "+ dfPercent.format(((PosNegLPStandard)learningProblem).getFMeasureOrTooWeakExact(ed.getDescription(),1)) + ")\n";
			} else {
				str += current + ": " + descriptionToString(ed.getDescription()) + " " + dfPercent.format(ed.getAccuracy()) + "\n";
//				System.out.println(ed);
			}
			current++;
		}
		return str;
	}

//	private String getTemporaryString(Description description) {
//		return descriptionToString(description) + " (pred. acc.: " + dfPercent.format(((PosNegLPStandard)learningProblem).getPredAccuracyOrTooWeakExact(description,1)) + ", F-measure: "+ dfPercent.format(((PosNegLPStandard)learningProblem).getFMeasureOrTooWeakExact(description,1)) + ")";
//	}
	
	private void updateMinMaxHorizExp(OENode node) {
		int newHorizExp = node.getHorizontalExpansion();
		
		// update maximum value
		maxHorizExp = Math.max(maxHorizExp, newHorizExp);
		
		// we just expanded a node with minimum horizontal expansion;
		// we need to check whether it was the last one
		if(minHorizExp == newHorizExp - 1) {
			
			// the best accuracy that a node can achieve 
			double scoreThreshold = heuristic.getNodeScore(node) + 1 - node.getAccuracy();
			
			for(OENode n : nodes.descendingSet()) {
				if(n != node) {
					if(n.getHorizontalExpansion() == minHorizExp) {
						// we can stop instantly when another node with min. 
						return;
					}
					if(heuristic.getNodeScore(n) < scoreThreshold) {
						// we can stop traversing nodes when their score is too low
						break;
					}
				}
			}
			
			// inc. minimum since we found no other node which also has min. horiz. exp.
			minHorizExp++;
			
//			System.out.println("minimum horizontal expansion is now " + minHorizExp);
		}
	}
	
	public int getMaximumHorizontalExpansion() {
		return maxHorizExp;
	}

	public int getMinimumHorizontalExpansion() {
		return minHorizExp;
	}
	
	/**
	 * @return the expressionTests
	 */
	public int getClassExpressionTests() {
		return expressionTests;
	}

	public RefinementOperator getOperator() {
		return operator;
	}

	@Autowired(required=false)
	public void setOperator(RefinementOperator operator) {
		this.operator = operator;
	}

	public Description getStartClass() {
		return startClass;
	}

	public void setStartClass(Description startClass) {
		this.startClass = startClass;
	}

	public Set<NamedClass> getAllowedConcepts() {
		return allowedConcepts;
	}

	public void setAllowedConcepts(Set<NamedClass> allowedConcepts) {
		this.allowedConcepts = allowedConcepts;
	}

	public Set<NamedClass> getIgnoredConcepts() {
		return ignoredConcepts;
	}

	public void setIgnoredConcepts(Set<NamedClass> ignoredConcepts) {
		this.ignoredConcepts = ignoredConcepts;
	}

	public boolean isWriteSearchTree() {
		return writeSearchTree;
	}

	public void setWriteSearchTree(boolean writeSearchTree) {
		this.writeSearchTree = writeSearchTree;
	}

	public String getSearchTreeFile() {
		return searchTreeFile;
	}

	public void setSearchTreeFile(String searchTreeFile) {
		this.searchTreeFile = searchTreeFile;
	}

	public int getMaxNrOfResults() {
		return maxNrOfResults;
	}

	public void setMaxNrOfResults(int maxNrOfResults) {
		this.maxNrOfResults = maxNrOfResults;
	}

	public double getNoisePercentage() {
		return noisePercentage;
	}

	public void setNoisePercentage(double noisePercentage) {
		this.noisePercentage = noisePercentage;
	}

	public boolean isFilterDescriptionsFollowingFromKB() {
		return filterDescriptionsFollowingFromKB;
	}

	public void setFilterDescriptionsFollowingFromKB(boolean filterDescriptionsFollowingFromKB) {
		this.filterDescriptionsFollowingFromKB = filterDescriptionsFollowingFromKB;
	}

	public boolean isReplaceSearchTree() {
		return replaceSearchTree;
	}

	public void setReplaceSearchTree(boolean replaceSearchTree) {
		this.replaceSearchTree = replaceSearchTree;
	}

	public int getMaxClassDescriptionTests() {
		return maxClassDescriptionTests;
	}

	public void setMaxClassDescriptionTests(int maxClassDescriptionTests) {
		this.maxClassDescriptionTests = maxClassDescriptionTests;
	}

	public int getMaxExecutionTimeInSeconds() {
		return maxExecutionTimeInSeconds;
	}

	public void setMaxExecutionTimeInSeconds(int maxExecutionTimeInSeconds) {
		this.maxExecutionTimeInSeconds = maxExecutionTimeInSeconds;
	}

	public boolean isTerminateOnNoiseReached() {
		return terminateOnNoiseReached;
	}

	public void setTerminateOnNoiseReached(boolean terminateOnNoiseReached) {
		this.terminateOnNoiseReached = terminateOnNoiseReached;
	}

	public boolean isReuseExistingDescription() {
		return reuseExistingDescription;
	}

	public void setReuseExistingDescription(boolean reuseExistingDescription) {
		this.reuseExistingDescription = reuseExistingDescription;
	}

	public boolean isUseMinimizer() {
		return useMinimizer;
	}

	public void setUseMinimizer(boolean useMinimizer) {
		this.useMinimizer = useMinimizer;
	}

	public OEHeuristicRuntime getHeuristic() {
		return heuristic;
	}

	public void setHeuristic(OEHeuristicRuntime heuristic) {
		this.heuristic = heuristic;
	}	
	
	
	public static void main(String[] args) throws Exception{
		Logger.getLogger(PCELOE.class).addAppender(new FileAppender(new PatternLayout( "[%t] %c: %m%n" ), "log/parallel_run.txt", false));
		
		AbstractKnowledgeSource ks = new OWLFile("../examples/family/father_oe.owl");
		ks.init();
		
		AbstractReasonerComponent rc = new FastInstanceChecker(ks);
		rc.init();
		
		ClassLearningProblem lp = new ClassLearningProblem(rc);
		lp.setClassToDescribe(new NamedClass("http://example.com/father#father"));
		lp.setCheckConsistency(false);
		lp.init();
		
		PCELOE alg = new PCELOE(lp, rc);
		alg.setMaxExecutionTimeInSeconds(10);
//		alg.setMaxClassDescriptionTests(200);
		alg.init();
		
		alg.start();
		
	}
	
	class Worker implements Runnable{
		
		private RhoDRDown operator;
		
		public Worker(RhoDRDown operator) {
			this.operator = operator;
		}

		@Override
		public void run() {
			logger.info("Started thread...");
			
			OENode nextNode;
			double highestAccuracy = 0.0;
			int loop = 0;
			while (!terminationCriteriaSatisfied()) {
				
				
				if(!singleSuggestionMode && bestEvaluatedDescriptions.getBestAccuracy() > highestAccuracy) {
					highestAccuracy = bestEvaluatedDescriptions.getBestAccuracy();
					logger.info("more accurate (" + dfPercent.format(highestAccuracy) + ") class expression found: " + descriptionToString(bestEvaluatedDescriptions.getBest().getDescription()));	
				}

				// chose best node according to heuristics
				logger.info("Get next node to expand...");
				nextNode = getNextNodeToExpand();
				logger.info("...done");
				try {
					Thread.sleep(10);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				logger.info("next Node: " + nextNode);
				if(nextNode != null){
					int horizExp = nextNode.getHorizontalExpansion();
					
					// apply operator
					Monitor mon = MonitorFactory.start("refineNode");
					logger.info("Refine node...");
					TreeSet<Description> refinements = refineNode(nextNode, operator);
					mon.stop();
					logger.info("...done");
					
					while(refinements.size() != 0) {
						// pick element from set
						Description refinement = refinements.pollFirst();
						int length = refinement.getLength();
										
						// we ignore all refinements with lower length and too high depth
						// (this also avoids duplicate node children)
						if(length > horizExp && refinement.getDepth() <= maxDepth) {
							
//							System.out.println("potentially adding " + refinement + " to search tree as child of " + nextNode + " " + new Date());
							Monitor mon2 = MonitorFactory.start("addNode");
							logger.info("Add node...");
							addNode(refinement, nextNode);
							mon2.stop();
							logger.info("...done");
							// adding nodes is potentially computationally expensive, so we have
							// to check whether max time is exceeded	
							if(terminationCriteriaSatisfied()) {
								break;
							}
//							System.out.println("addNode finished" + " " + new Date());
						}
				
//						System.out.println("  refinement queue length: " + refinements.size());
					}
					
//					updateMinMaxHorizExp(nextNode);
						
					loop++;
					currentlyProcessedNodes.remove(nextNode);
				}
				
			}
			
		}
		
	}
	
}
package org.dllearner.tools.ore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.dllearner.tools.ore.explanation.AxiomUsageChecker;
import org.dllearner.tools.ore.explanation.CachedExplanationGenerator;
import org.dllearner.tools.ore.explanation.Explanation;
import org.dllearner.tools.ore.explanation.ExplanationException;
import org.dllearner.tools.ore.explanation.ExplanationType;
import org.dllearner.tools.ore.explanation.RemainingAxiomPartsGenerator;
import org.dllearner.tools.ore.explanation.RootFinder;
import org.dllearner.tools.ore.explanation.laconic.LaconicExplanationGenerator;
import org.dllearner.tools.ore.explanation.laconic.OPlus;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyChangeException;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLSubClassOfAxiom;

import uk.ac.manchester.cs.bhig.util.Tree;
import uk.ac.manchester.cs.owl.explanation.ordering.DefaultExplanationOrderer;
import uk.ac.manchester.cs.owl.explanation.ordering.ExplanationTree;

import com.clarkparsia.pellet.owlapiv3.PelletReasoner;
import com.clarkparsia.pellet.owlapiv3.PelletReasonerFactory;

public class ExplanationManager implements OREManagerListener{

	private static ExplanationManager instance;
	
	private OWLOntologyManager manager;
	private OWLDataFactory dataFactory;
	private OWLOntology ontology;
	private PelletReasoner reasoner;

	private RootFinder rootFinder;

	private boolean isComputeAllExplanations = false;
	private int maxExplanationCount = 1;
	private boolean allExplanationWarningChecked = false;
	
	private Map<Explanation, List<Map<OWLAxiom, Integer>>> orderingMap;
	private DefaultExplanationOrderer explanationOrderer;
	
	private List<ExplanationManagerListener> listeners;
	
	private AxiomUsageChecker usageChecker;
	
	private CachedExplanationGenerator gen;
	
	private RemainingAxiomPartsGenerator remainingAxGen;
	
	
	private ExplanationManager(OREManager oreMan) {
		OREManager.getInstance().addListener(this);
		this.reasoner = oreMan.getReasoner().getReasoner();
		this.manager = reasoner.getManager();
		this.ontology = oreMan.getReasoner().getOWLAPIOntologies();
		
		dataFactory = manager.getOWLDataFactory();
		
		explanationOrderer = new DefaultExplanationOrderer();
		orderingMap = new HashMap<Explanation, List<Map<OWLAxiom, Integer>>>();

		rootFinder = new RootFinder();
		
		usageChecker = new AxiomUsageChecker(ontology);

		listeners = new ArrayList<ExplanationManagerListener>();
		
		gen = new CachedExplanationGenerator(Collections.singleton(reasoner.getRootOntology()));
		
		remainingAxGen = new RemainingAxiomPartsGenerator(ontology, dataFactory);

	}
	
	public static synchronized ExplanationManager getInstance(
			OREManager oreMan) {
		if (instance == null) {
			instance = new ExplanationManager(oreMan);
		}
		return instance;
	}
	
	public Set<OWLClass> getDerivedClasses(){
		return rootFinder.getDerivedClasses();
	}
	
	public Set<OWLClass> getRootUnsatisfiableClasses(){
		if(rootFinder != null){
			return rootFinder.getRootClasses();
		} else {
			return Collections.<OWLClass>emptySet();
		}
	}
	
	public Set<OWLClass> getUnsatisfiableClasses(){
		Set<OWLClass> unsat = new HashSet<OWLClass>();
		unsat.addAll(rootFinder.getRootClasses());
		unsat.addAll(rootFinder.getDerivedClasses());
		return unsat;
	}
	
	public Set<Explanation> getUnsatisfiableExplanations(OWLClass unsat) {

		OWLSubClassOfAxiom entailment = dataFactory.getOWLSubClassOfAxiom(unsat,
				dataFactory.getOWLNothing());

		Set<Explanation> explanations;
		if (isComputeAllExplanations) {
			explanations = gen.getExplanations(entailment);
		} else {
			explanations = gen.getExplanations(entailment, maxExplanationCount);
		}

		return explanations;
	}
	
	public Set<Explanation> getInconsistencyExplanations(){
		OWLSubClassOfAxiom entailment = dataFactory.getOWLSubClassOfAxiom(dataFactory.getOWLThing(),
				dataFactory.getOWLNothing());

		Set<Explanation> explanations;
		if (isComputeAllExplanations) {
			explanations = gen.getExplanations(entailment);
		} else {
			explanations = gen.getExplanations(entailment, maxExplanationCount);
		}
		
		return explanations;
	}
	
	public Set<Explanation> getEntailmentExplanations(OWLAxiom entailment){
		boolean before = gen.isLaconicMode();
		gen.setComputeLaconicExplanations(false);
		Set<Explanation> explanations = gen.getExplanations(entailment);
		gen.setComputeLaconicExplanations(before);
		return explanations;
	}
	
	public List<Map<OWLAxiom, Integer>> getOrdering(Explanation exp){
		List<Map<OWLAxiom, Integer>> orderedAxioms = orderingMap.get(exp);
		if(orderedAxioms == null){
			orderedAxioms = new ArrayList<Map<OWLAxiom, Integer>>(exp.getAxioms().size());
			orderedAxioms.addAll(orderExplanation(exp));
			orderingMap.put(exp, orderedAxioms);
		}
		return orderedAxioms;
	}
	
	private List<Map<OWLAxiom, Integer>> orderExplanation(Explanation exp){
		explanationOrderer = new DefaultExplanationOrderer();
		List<Map<OWLAxiom, Integer>> ordering = new ArrayList<Map<OWLAxiom, Integer>>(exp.getAxioms().size());
		ExplanationTree tree = explanationOrderer.getOrderedExplanation(exp.getEntailment(), exp.getAxioms());
		for(Tree<OWLAxiom> child : tree.getChildren()){
			ordering.addAll(getTree2List(child));
		}
		return ordering;
	}
	
	public Set<OWLEntity> getUsage(OWLAxiom axiom){
		return usageChecker.getUsage(axiom);
	}
	
	public Explanation getLaconicExplanation(Explanation explanation){
		Explanation exp = null;
		try {
			OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
			PelletReasonerFactory reasonerFactory = new PelletReasonerFactory();
			OWLOntology ontology = manager.createOntology(explanation.getAxioms());
			LaconicExplanationGenerator gen = new LaconicExplanationGenerator(manager, reasonerFactory, ontology);
			exp = gen.getExplanations(explanation.getEntailment(), 1).iterator().next();
		} catch (OWLOntologyCreationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (OWLOntologyChangeException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ExplanationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return exp;
	}
	
	
	
	private ArrayList<Map<OWLAxiom, Integer>> getTree2List(Tree<OWLAxiom> tree){
		ArrayList<Map<OWLAxiom, Integer>> ordering = new ArrayList<Map<OWLAxiom, Integer>>();
		Map<OWLAxiom, Integer> axiom2Depth = new HashMap<OWLAxiom, Integer>();
		axiom2Depth.put((OWLAxiom)tree.getUserObject(), Integer.valueOf(tree.getUserObjectPathToRoot().size()));
		ordering.add(axiom2Depth);
		for(Tree<OWLAxiom> child : tree.getChildren()){
			ordering.addAll(getTree2List(child));
		}
		return ordering;
	}
	
	public int getArity(OWLClass cl, OWLAxiom ax) {
		int arity = 0;
		
		Set<Explanation> explanations = gen.getExplanations(dataFactory.getOWLSubClassOfAxiom(cl, dataFactory.getOWLNothing()));
		
		if(explanations != null){
			
			for (Explanation explanation : explanations) {
				if (explanation.getAxioms().contains(ax)) {
					arity++;
				}
			}
		}
		return arity;
	}
	
	public int getGlobalArity(OWLAxiom ax){
		return gen.getArity(ax);
	}
	
	public double getInconsistencyValue(OWLAxiom ax){
		
		return Math.round( gen.getInconsistencyValue(ax) * 100. ) / 100.;
	}
	
	public void setLaconicMode(boolean laconic){
		gen.setComputeLaconicExplanations(laconic);
		fireExplanationTypeChanged();
		
	}
	
	public void setExplanationType(ExplanationType type){
		gen.setExplanationType(type);
		fireExplanationTypeChanged();
	}
	
	public ExplanationType getExplanationType(){
		return gen.getExplanationType();	
	}
	
	public boolean isLaconicMode(){
		return gen.isLaconicMode();
	}
	
	public void setComputeAllExplanationsMode(boolean value){
		isComputeAllExplanations = value;
		fireExplanationLimitChanged();
	}

	public boolean isComputeAllExplanationsMode(){
		return isComputeAllExplanations;
	}
	
	public void setMaxExplantionCount(int limit){
		maxExplanationCount = limit;
		fireExplanationLimitChanged();
	}
	
	public int getMaxExplantionCount(){
		return maxExplanationCount;
	}
	
	public void addListener(ExplanationManagerListener l){
		listeners.add(l);
	}
	
	public void removeListener(ExplanationManagerListener l){
		listeners.remove(l);
	}
	
	public void fireExplanationLimitChanged(){
		for(ExplanationManagerListener listener : listeners){
			listener.explanationLimitChanged();
		}
	}
	
	public void fireExplanationTypeChanged(){
		for(ExplanationManagerListener listener : listeners){
			listener.explanationTypeChanged();
		}
	}
	
	public void setAllExplanationWarningChecked(){
		allExplanationWarningChecked = true;
	}
	
	public boolean isAllExplanationWarningChecked(){
		return allExplanationWarningChecked;
	}
	
	public Set<OWLAxiom> getSourceAxioms(OWLAxiom ax){
		return gen.getSourceAxioms(ax);
	}
	
	public Set<OWLAxiom> getLaconicSourceAxioms(OWLAxiom ax){
		return gen.getLaconicSourceAxioms(ax);
	}
	
	public Set<OWLAxiom> getRemainingAxioms(OWLAxiom source, OWLAxiom part){
		return gen.getRemainingAxioms(source, part);
	}

	public Set<OWLAxiom> getLaconicAxioms(OWLAxiom ax){
		OPlus oPlus = new OPlus(dataFactory);
		return ax.accept(oPlus);
	}
	
	public Map<OWLAxiom, Set<OWLAxiom>> getRemainingAxiomParts(OWLAxiom laconicAxiom){
		return remainingAxGen.getRemainingAxiomParts(laconicAxiom);
	}
	
	@Override
	public void activeOntologyChanged() {
		reasoner = OREManager.getInstance().getReasoner().getReasoner();
		ontology = OREManager.getInstance().getReasoner().getOWLAPIOntologies();
		gen = new CachedExplanationGenerator(Collections.singleton(reasoner.getRootOntology()));
		orderingMap.clear();
		usageChecker = new AxiomUsageChecker(ontology);
	}
	
}

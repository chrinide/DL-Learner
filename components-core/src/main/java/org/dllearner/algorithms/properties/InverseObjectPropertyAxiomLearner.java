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

package org.dllearner.algorithms.properties;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedSet;

import org.aksw.commons.collections.multimaps.BiHashMultimap;
import org.dllearner.core.AbstractAxiomLearningAlgorithm;
import org.dllearner.core.ComponentAnn;
import org.dllearner.core.EvaluatedAxiom;
import org.dllearner.core.config.ConfigOption;
import org.dllearner.core.config.ObjectPropertyEditor;
import org.dllearner.core.owl.Individual;
import org.dllearner.core.owl.InverseObjectPropertyAxiom;
import org.dllearner.core.owl.ObjectProperty;
import org.dllearner.core.owl.SymmetricObjectPropertyAxiom;
import org.dllearner.kb.SparqlEndpointKS;
import org.dllearner.kb.sparql.SparqlEndpoint;
import org.dllearner.reasoning.SPARQLReasoner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;

@ComponentAnn(name="inverse objectproperty domain axiom learner", shortName="oplinv", version=0.1)
public class InverseObjectPropertyAxiomLearner extends AbstractAxiomLearningAlgorithm {
	
	private static final Logger logger = LoggerFactory.getLogger(InverseObjectPropertyAxiomLearner.class);
	
	@ConfigOption(name="propertyToDescribe", description="", propertyEditorClass=ObjectPropertyEditor.class)
	private ObjectProperty propertyToDescribe;
	
	public InverseObjectPropertyAxiomLearner(SparqlEndpointKS ks){
		this.ks = ks;
	}
	
	public ObjectProperty getPropertyToDescribe() {
		return propertyToDescribe;
	}

	public void setPropertyToDescribe(ObjectProperty propertyToDescribe) {
		this.propertyToDescribe = propertyToDescribe;
	}
	
	@Override
	public void start() {
		logger.info("Start learning...");
		startTime = System.currentTimeMillis();
		fetchedRows = 0;
		currentlyBestAxioms = new ArrayList<EvaluatedAxiom>();
		
		if(reasoner.isPrepared()){
			//get existing inverse object property axioms
			SortedSet<ObjectProperty> existingInverseObjectProperties = reasoner.getInverseObjectProperties(propertyToDescribe);
			for(ObjectProperty invProp : existingInverseObjectProperties){
				existingAxioms.add(new InverseObjectPropertyAxiom(invProp, propertyToDescribe));
			}
		}
		
		if(ks.supportsSPARQL_1_1()){
			runSPARQL1_1_Mode();
		} else {
			runSPARQL1_0_Mode();
		}
		
		logger.info("...finished in {}ms.", (System.currentTimeMillis()-startTime));
	}
	
	private void runSPARQL1_0_Mode(){
		Map<ObjectProperty, Integer> prop2CountMap = new HashMap<ObjectProperty, Integer>();
		boolean repeat = true;
		int limit = 1000;
		int total = 0;
		while(!terminationCriteriaSatisfied() && repeat){
			String query = String.format("SELECT ?s ?p WHERE {?s <%s> ?o. OPTIONAL{?o ?p ?s.}} LIMIT %d OFFSET %d", propertyToDescribe.getName(), limit, fetchedRows);
			ResultSet rs = executeSelectQuery(query);
			QuerySolution qs;
			ObjectProperty p;
			int cnt = 0;
			while(rs.hasNext()){
				qs = rs.next();
				if(qs.getResource("p") != null){
					p = new ObjectProperty(qs.getResource("p").getURI());
					Integer oldCnt = prop2CountMap.get(p);
					if(oldCnt == null){
						oldCnt = Integer.valueOf(0);
					}
					prop2CountMap.put(p, Integer.valueOf(oldCnt + 1));
				}
				cnt++;
			}
			total += cnt;
			for(Entry<ObjectProperty, Integer> entry : prop2CountMap.entrySet()){
				currentlyBestAxioms = Collections.singletonList(new EvaluatedAxiom(new InverseObjectPropertyAxiom(entry.getKey(), propertyToDescribe),
						computeScore(total, entry.getValue())));
			}
			fetchedRows += limit;
			repeat = (cnt == limit);
		}
	}
	
	private void runSPARQL1_1_Mode(){
		String query = "SELECT (COUNT(?s) AS ?total) WHERE {?s <%s> ?o.}";
		query = query.replace("%s", propertyToDescribe.getURI().toString());
		ResultSet rs = executeSelectQuery(query);
		QuerySolution qs;
		int total = 0;
		while(rs.hasNext()){
			qs = rs.next();
			total = qs.getLiteral("total").getInt();
		}
		
		query = String.format("SELECT ?p (COUNT(?s) AS ?cnt) WHERE {?s <%s> ?o. ?o ?p ?s.} GROUP BY ?p", propertyToDescribe.getName());
		rs = executeSelectQuery(query);
		while(rs.hasNext()){
			qs = rs.next();
			currentlyBestAxioms.add(new EvaluatedAxiom(
					new InverseObjectPropertyAxiom(new ObjectProperty(qs.getResource("p").getURI()), propertyToDescribe),
					computeScore(total, qs.getLiteral("cnt").getInt())));
		}
		
	}
	
	public static void main(String[] args) throws Exception{
		SparqlEndpointKS ks = new SparqlEndpointKS(new SparqlEndpoint(new URL("http://dbpedia.aksw.org:8902/sparql")));//.getEndpointDBpediaLiveAKSW()));
		
		SPARQLReasoner reasoner = new SPARQLReasoner(ks);
		reasoner.prepareSubsumptionHierarchy();
		
		
		InverseObjectPropertyAxiomLearner l = new InverseObjectPropertyAxiomLearner(ks);
		l.setReasoner(reasoner);
		l.setPropertyToDescribe(new ObjectProperty("http://dbpedia.org/ontology/officialLanguage"));
		l.setMaxExecutionTimeInSeconds(10);
//		l.setReturnOnlyNewAxioms(true);
		l.init();
		l.start();
		
		System.out.println(l.getCurrentlyBestEvaluatedAxioms(10, 0.2));
	}
	
}

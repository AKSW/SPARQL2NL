/**
 * 
 */
package org.aksw.sparql2nl.entitysummarizer;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import org.aksw.jena_sparql_api.cache.core.QueryExecutionFactoryCacheEx;
import org.aksw.jena_sparql_api.cache.extra.CacheFrontend;
import org.aksw.jena_sparql_api.cache.h2.CacheUtilsH2;
import org.aksw.jena_sparql_api.core.QueryExecutionFactory;
import org.aksw.jena_sparql_api.http.QueryExecutionFactoryHttp;
import org.aksw.sparql2nl.entitysummarizer.clustering.hardening.HardeningFactory.HardeningType;
import org.aksw.sparql2nl.entitysummarizer.dataset.DatasetBasedGraphGenerator.Cooccurrence;
import org.aksw.sparql2nl.entitysummarizer.rouge.Rouge;
import org.apache.commons.math3.stat.descriptive.SummaryStatistics;
import org.apache.log4j.Logger;
import org.dllearner.core.owl.Individual;
import org.dllearner.core.owl.NamedClass;
import org.dllearner.kb.SparqlEndpointKS;
import org.dllearner.kb.sparql.SparqlEndpoint;
import org.dllearner.reasoning.SPARQLReasoner;
import simplenlg.framework.NLGElement;
import com.hp.hpl.jena.query.ParameterizedSparqlString;
import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QueryExecution;
import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;

/**
 * @author Lorenz Buehmann
 *
 */
public class Evaluation {
	
	private static Logger logger = Logger.getLogger(Evaluation.class);

	
	SparqlEndpoint endpoint = SparqlEndpoint.getEndpointDBpedia();;
	String cacheDirectory = "verbalization-evaluation";
	int nrOfClasses = 10;
	int nrOfInstancePerClass = 10;
	Cooccurrence cooccurrence = Cooccurrence.PROPERTIES;
	HardeningType hardeningType = HardeningType.AVERAGE;
	double threshold = 0.5;
	String abstractProperty = "http://dbpedia.org/ontology/abstract";
	
	
	SPARQLReasoner reasoner = new SPARQLReasoner(new SparqlEndpointKS(endpoint), cacheDirectory);
	QueryExecutionFactory qef;
	Verbalizer verbalizer = new Verbalizer(endpoint);
	
	Rouge rouge = new Rouge();
	int rougeMode = Rouge.MULTIPLE_MAX;
	
	
	public Evaluation() {
		qef = new QueryExecutionFactoryHttp(endpoint.getURL().toString(), endpoint.getDefaultGraphURIs());
		CacheFrontend frontend = CacheUtilsH2.createCacheFrontend("evaluation",true, TimeUnit.DAYS.toMillis(30));
		qef = new QueryExecutionFactoryCacheEx(qef, frontend);
		
		rouge.setMultipleReferenceMode(rougeMode);
	}
	
	public void run(){
		//get the classes
		Collection<NamedClass> classes = getClasses();
		//for each class
		for (NamedClass cls : classes) {
			System.out.println("Processing class " + cls);
			
			SummaryStatistics pStats = new SummaryStatistics();
			SummaryStatistics rStats = new SummaryStatistics();
			SummaryStatistics fStats = new SummaryStatistics();
			
			//get some instances with abstracts
			Map<Individual, String> individualsWithAbstract = getIndividualsWithAbstract(cls);
			
			//get the verbalizations
			Map<Individual, List<NLGElement>> verbalizations = verbalizer.verbalize(individualsWithAbstract.keySet(), cls, threshold, cooccurrence, hardeningType);
		
			//compare the verbalization with the abstract by using ROUGE
			for (Entry<Individual, String> entry : individualsWithAbstract.entrySet()) {
				Individual ind = entry.getKey();
				String abstr = entry.getValue();
				List<NLGElement> verbalization = verbalizations.get(ind);
				String realization = verbalizer.realize(verbalization);
				
				//compute ROUGE
				rouge.evaluate(realization, new String[]{abstr});
				
				double precision = rouge.getPrecision();
				double recall = rouge.getRecall();
				double fScore = rouge.getFScore();
				
				System.out.println("######################################");
				System.out.println("Individual:" + ind);
				System.out.println("Realization:\n" + realization);
				System.out.println("Abstract:\n" + abstr);
				System.out.printf("P=%f|R=%f|F_1=%f\n", precision, recall, fScore);
				
				pStats.addValue(precision);
				rStats.addValue(recall);
				fStats.addValue(fScore);
			}
			
			logger.info("\n*************\nClass: " + cls + "\nAvg. precision: " + pStats.getMean() + "\nAvg. recall: " + rStats.getMean() + "\nAvg. FScore: " + fStats.getMean());
			
		}
		
	}
	
	/**
	 * Get x randomly chosen classes which contain at least y instances
	 * @return
	 */
	private Collection<NamedClass> getClasses(){
		Collection<NamedClass> classes = new TreeSet<NamedClass>();
		
		List<NamedClass> allClasses = new ArrayList<NamedClass>(reasoner.getOWLClasses());
		Collections.shuffle(allClasses, new Random(123));
		
		Iterator<NamedClass> iter = allClasses.iterator();
		NamedClass cls;
		while(iter.hasNext() && classes.size() < nrOfClasses){
			cls = iter.next();
			int cnt = reasoner.getIndividualsCount(cls);
			if(cnt >= nrOfInstancePerClass){
				classes.add(cls);
			}
		}
		return classes;
	}
	
	private Map<Individual, String> getIndividualsWithAbstract(NamedClass cls){
		Map<Individual, String> individualsWithAbstract = new HashMap<Individual, String>();
		ParameterizedSparqlString template = new ParameterizedSparqlString(
				"SELECT ?s ?abstract WHERE {?s a ?type. ?s ?abstractProperty ?abstract. FILTER(LANGMATCHES(LANG(?abstract),'en'))}");
		template.setIri("abstractProperty", abstractProperty);
		template.setIri("type", cls.getName());
		
		Query q = template.asQuery();
		q.setLimit(nrOfInstancePerClass);
		
		QueryExecution qe = qef.createQueryExecution(q);
		ResultSet rs = qe.execSelect();
		QuerySolution qs;
		while(rs.hasNext()){
			qs = rs.next();
			individualsWithAbstract.put(new Individual(qs.getResource("s").getURI()), qs.getLiteral("abstract").getLexicalForm());
		}
		return individualsWithAbstract;
	}
	
	public static void main(String[] args) throws Exception {
		new Evaluation().run();
	}
}

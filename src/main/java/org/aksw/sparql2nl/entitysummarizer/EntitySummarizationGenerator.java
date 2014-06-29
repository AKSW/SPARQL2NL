/**
 * 
 */
package org.aksw.sparql2nl.entitysummarizer;

import java.io.File;

import org.aksw.jena_sparql_api.core.QueryExecutionFactory;
import org.aksw.sparql2nl.entitysummarizer.clustering.WeightedGraph;
import org.aksw.sparql2nl.entitysummarizer.dataset.CachedDatasetBasedGraphGenerator;
import org.aksw.sparql2nl.entitysummarizer.dataset.DatasetBasedGraphGenerator;
import org.dllearner.core.owl.NamedClass;
import org.dllearner.kb.sparql.SparqlEndpoint;

import com.hp.hpl.jena.rdf.model.Resource;

/**
 * @author Lorenz Buehmann
 *
 */
public class EntitySummarizationGenerator {
	
	private File cacheDirectory = new File("cache");
	private DatasetBasedGraphGenerator graphGenerator;
	
	private double propertyFrequencyThreshold;

	public EntitySummarizationGenerator(SparqlEndpoint endpoint, File cacheDirectory, double propertyFrequencyThreshold) {
		this.cacheDirectory  = cacheDirectory;
		this.propertyFrequencyThreshold = propertyFrequencyThreshold;
		
		graphGenerator = new CachedDatasetBasedGraphGenerator(endpoint, cacheDirectory);
	}
	
	public EntitySummarizationGenerator(QueryExecutionFactory qef, File cacheDirectory) {
		this.cacheDirectory  = cacheDirectory;
		graphGenerator = new CachedDatasetBasedGraphGenerator(qef, cacheDirectory);
	}
	
	public EntitySummarization generateEntitySummarization(Resource entity){
		//determine the most specific class of the entity
		NamedClass cls = null;
		
		return generateEntitySummarization(entity, cls);
	}
	
	public EntitySummarization generateEntitySummarization(Resource entity, NamedClass cls){
		//generate a graph with the most interesting properties
		WeightedGraph graph = graphGenerator.generateGraph(cls, propertyFrequencyThreshold);
		
		return null;
	}

}
